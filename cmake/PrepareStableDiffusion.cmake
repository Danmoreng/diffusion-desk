function(prepare_patched_stable_diffusion upstream_dir patch_file output_var)
    if(NOT EXISTS "${upstream_dir}/CMakeLists.txt")
        message(FATAL_ERROR "stable-diffusion.cpp submodule is missing at ${upstream_dir}")
    endif()
    if(NOT EXISTS "${patch_file}")
        message(FATAL_ERROR "stable-diffusion.cpp patch is missing at ${patch_file}")
    endif()

    file(SHA256 "${patch_file}" patch_hash)
    execute_process(
        COMMAND git -C "${upstream_dir}" rev-parse HEAD
        RESULT_VARIABLE git_result
        OUTPUT_VARIABLE upstream_commit
        OUTPUT_STRIP_TRAILING_WHITESPACE
        ERROR_QUIET
    )
    if(NOT git_result EQUAL 0)
        message(FATAL_ERROR "Could not determine the stable-diffusion.cpp submodule commit")
    endif()

    set(patched_dir "${CMAKE_BINARY_DIR}/patched/stable-diffusion.cpp")
    set(stamp_file "${patched_dir}/.diffusion-desk-patch-stamp")
    set(patch_pipeline_version 3)
    set(expected_stamp "${patch_pipeline_version}:${upstream_commit}:${patch_hash}")
    set(current_stamp "")
    if(EXISTS "${stamp_file}")
        file(READ "${stamp_file}" current_stamp)
    endif()
    set(patch_marker_present FALSE)
    if(EXISTS "${patched_dir}/include/stable-diffusion.h")
        file(READ "${patched_dir}/include/stable-diffusion.h" patched_header)
        if(patched_header MATCHES "sd_set_cancel_callback")
            set(patch_marker_present TRUE)
        endif()
    endif()

    if(NOT current_stamp STREQUAL expected_stamp OR NOT patch_marker_present)
        message(STATUS "Preparing patched stable-diffusion.cpp build copy from ${upstream_commit}")
        file(REMOVE_RECURSE "${patched_dir}")
        file(MAKE_DIRECTORY "${CMAKE_BINARY_DIR}/patched")
        execute_process(
            COMMAND git clone --quiet "${upstream_dir}" "${patched_dir}"
            RESULT_VARIABLE git_clone_result
            ERROR_VARIABLE git_clone_error
        )
        if(NOT git_clone_result EQUAL 0)
            file(REMOVE_RECURSE "${patched_dir}")
            message(FATAL_ERROR "Could not clone isolated stable-diffusion.cpp workspace: ${git_clone_error}")
        endif()
        execute_process(
            COMMAND git checkout --quiet --detach "${upstream_commit}"
            WORKING_DIRECTORY "${patched_dir}"
            RESULT_VARIABLE git_checkout_result
            ERROR_VARIABLE git_checkout_error
        )
        if(NOT git_checkout_result EQUAL 0)
            file(REMOVE_RECURSE "${patched_dir}")
            message(FATAL_ERROR "Could not checkout stable-diffusion.cpp commit ${upstream_commit}: ${git_checkout_error}")
        endif()
        # Local clones do not populate nested submodules. Copy the exact files
        # from the already initialized source worktree without its Git metadata.
        file(COPY "${upstream_dir}/" DESTINATION "${patched_dir}"
            PATTERN ".git" EXCLUDE
        )
        execute_process(
            COMMAND git apply --ignore-space-change --ignore-whitespace "${patch_file}"
            WORKING_DIRECTORY "${patched_dir}"
            RESULT_VARIABLE patch_result
            OUTPUT_VARIABLE patch_output
            ERROR_VARIABLE patch_error
        )
        if(NOT patch_result EQUAL 0)
            file(REMOVE_RECURSE "${patched_dir}")
            message(FATAL_ERROR
                "The Diffusion Desk stable-diffusion.cpp patch is incompatible with submodule commit ${upstream_commit}.\n"
                "${patch_output}${patch_error}"
            )
        endif()
        file(READ "${patched_dir}/include/stable-diffusion.h" patched_header)
        if(NOT patched_header MATCHES "sd_set_cancel_callback")
            file(REMOVE_RECURSE "${patched_dir}")
            message(FATAL_ERROR "stable-diffusion.cpp patch completed without the expected cancel API")
        endif()
        file(WRITE "${stamp_file}" "${expected_stamp}")
    endif()

    set(${output_var} "${patched_dir}" PARENT_SCOPE)
endfunction()
