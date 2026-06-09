cmake_minimum_required(VERSION 3.14)
project(DiffusionDeskSdWorker LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

get_filename_component(DIFFUSION_DESK_ROOT "${CMAKE_CURRENT_LIST_DIR}/../.." ABSOLUTE)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${DIFFUSION_DESK_ROOT}/build/bin")

set(SD_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SD_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_EXTERNAL_GGML OFF CACHE BOOL "" FORCE)
set(SD_CUDA ON CACHE BOOL "" FORCE)
set(GGML_CUDA ON CACHE BOOL "" FORCE)
set(GGML_CUDA_GRAPHS OFF CACHE BOOL "" FORCE)

add_subdirectory("${DIFFUSION_DESK_ROOT}/libs/stable-diffusion.cpp" stable-diffusion)

add_executable(diffusion_desk_sd_worker
    "${DIFFUSION_DESK_ROOT}/src/main_sd.cpp"
    "${DIFFUSION_DESK_ROOT}/src/workers/sd_worker.cpp"
    "${DIFFUSION_DESK_ROOT}/src/sd/api_endpoints.cpp"
    "${DIFFUSION_DESK_ROOT}/src/sd/api_utils.cpp"
    "${DIFFUSION_DESK_ROOT}/src/sd/server_state.cpp"
    "${DIFFUSION_DESK_ROOT}/src/sd/model_loader.cpp"
    "${DIFFUSION_DESK_ROOT}/src/utils/sd_common.cpp"
    "${DIFFUSION_DESK_ROOT}/src/utils/common.cpp"
    "${DIFFUSION_DESK_ROOT}/src/stb_image_writer.cpp"
)

target_include_directories(diffusion_desk_sd_worker PRIVATE
    "${DIFFUSION_DESK_ROOT}/src"
    "${DIFFUSION_DESK_ROOT}/src/sd"
    "${DIFFUSION_DESK_ROOT}/src/utils"
    "${DIFFUSION_DESK_ROOT}/src/workers"
    "${DIFFUSION_DESK_ROOT}/libs/stable-diffusion.cpp"
    "${DIFFUSION_DESK_ROOT}/libs/stable-diffusion.cpp/thirdparty"
    "${DIFFUSION_DESK_ROOT}/libs/llama.cpp/vendor/nlohmann"
    "${DIFFUSION_DESK_ROOT}/libs/llama.cpp/vendor/cpp-httplib"
)

target_link_libraries(diffusion_desk_sd_worker PRIVATE stable-diffusion)
if(MSVC)
    target_compile_options(stable-diffusion PRIVATE /bigobj)
endif()
if(WIN32)
    target_link_libraries(diffusion_desk_sd_worker PRIVATE ws2_32)
endif()
