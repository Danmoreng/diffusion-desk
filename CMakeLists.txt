cmake_minimum_required(VERSION 3.14)
project(MystiCanvas)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)

# --- Dependencies ---

# Llama.cpp options (MUST come first to define ggml for others)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_CUDA ON CACHE BOOL "" FORCE)

# Stable Diffusion.cpp options
set(SD_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SD_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_EXTERNAL_GGML ON CACHE BOOL "" FORCE) # Use llama.cpp's ggml to avoid target collision
set(SD_CUDA ON CACHE BOOL "" FORCE)

# IXWebSocket options
set(USE_TLS OFF CACHE BOOL "" FORCE)
set(USE_ZLIB OFF CACHE BOOL "" FORCE)

# SQLiteCpp options
set(SQLITECPP_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(SQLITECPP_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SQLITECPP_INTERNAL_SQLITE ON CACHE BOOL "" FORCE)

# Add subdirectories
add_subdirectory(libs/llama.cpp)
add_subdirectory(libs/stable-diffusion.cpp)
add_subdirectory(libs/ixwebsocket)
add_subdirectory(libs/SQLiteCpp)

# Optimization: Exclude unnecessary llama.cpp tools from default build
# We need LLAMA_BUILD_TOOLS=ON to get 'server-context', but we don't want the CLI tools.
set(LLAMA_TOOLS_TO_EXCLUDE
    llama-batched-bench llama-bench llama-cli llama-completion 
    llama-cvector-generator llama-export-lora llama-fit-params 
    llama-gemma3-cli llama-gguf-split llama-imatrix llama-llava-cli 
    llama-minicpmv-cli llama-mtmd-cli llama-perplexity llama-quantize 
    llama-qwen2vl-cli llama-run llama-server llama-tokenize llama-tts
)

foreach(TOOL_TARGET ${LLAMA_TOOLS_TO_EXCLUDE})
    if(TARGET ${TOOL_TARGET})
        set_target_properties(${TOOL_TARGET} PROPERTIES EXCLUDE_FROM_ALL TRUE)
    endif()
endforeach()

if(MSVC)
    if(TARGET stable-diffusion)
        target_compile_options(stable-diffusion PRIVATE /bigobj)
    endif()
    if(TARGET server-context)
        target_compile_options(server-context PRIVATE /bigobj)
    endif()
endif()

# --- Common Sources ---
set(COMMON_SOURCES
    src/utils/common.cpp
    src/stb_image_writer.cpp
)

# --- Orchestrator (mysti_server) ---
# Light executable, no heavy CUDA/Model dependencies linked
add_executable(mysti_server
    src/main_orchestrator.cpp
    src/orchestrator/process_manager.cpp
    src/orchestrator/proxy.cpp
    src/orchestrator/orchestrator_main.cpp
    src/orchestrator/ws_manager.cpp
    src/orchestrator/database.cpp
    src/orchestrator/services/health_service.cpp
    src/orchestrator/services/resource_manager.cpp
    src/orchestrator/services/tagging_service.cpp
    src/orchestrator/services/import_service.cpp
    src/orchestrator/services/service_controller.cpp
    src/orchestrator/services/tool_service.cpp
    src/utils/common.cpp

)

# Include paths for dependencies
target_include_directories(mysti_server PRIVATE
    src
    src/utils
    src/orchestrator
    src/orchestrator/services
    libs/stable-diffusion.cpp # For types in common.hpp
    libs/stable-diffusion.cpp/thirdparty # For stb_image.h
    libs/llama.cpp/vendor/nlohmann
    libs/llama.cpp/vendor/cpp-httplib
    libs/ixwebsocket
    libs/SQLiteCpp/include
)

# Only link system libs, no SD/LLM libs - wait, we need SD for common.cpp symbols
if(WIN32)
    target_link_libraries(mysti_server PRIVATE stable-diffusion ixwebsocket SQLiteCpp ws2_32 shell32)
else()
    target_link_libraries(mysti_server PRIVATE stable-diffusion ixwebsocket SQLiteCpp)
endif()

# Copy WebUI assets to build directory
add_custom_command(TARGET mysti_server POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_directory
    ${CMAKE_SOURCE_DIR}/public
    $<TARGET_FILE_DIR:mysti_server>/public
    COMMENT "Copying WebUI assets to output directory"
)

# --- SD Worker (mysti_sd_worker) ---
add_executable(mysti_sd_worker
    src/main_sd.cpp
    src/workers/sd_worker.cpp
    src/sd/api_endpoints.cpp
    src/sd/api_utils.cpp
    src/sd/server_state.cpp
    src/sd/model_loader.cpp
    ${COMMON_SOURCES}
)

target_include_directories(mysti_sd_worker PRIVATE
    src
    src/sd
    src/utils
    src/workers
    libs/stable-diffusion.cpp
    libs/stable-diffusion.cpp/thirdparty # For stb_image.h
    libs/llama.cpp/vendor/nlohmann
    libs/llama.cpp/vendor/cpp-httplib
    libs/llama.cpp/ggml/include
)

target_link_libraries(mysti_sd_worker PRIVATE
    stable-diffusion
    # We don't link 'llama' lib, but we rely on 'ggml' which is provided by llama.cpp project
    # stable-diffusion target already links against it.
    common # llama.cpp common (args etc) - wait, src/utils/common.cpp uses local utils.
    # The 'common' target in libs/llama.cpp refers to llama's common utils. 
    # Our src/utils/common.cpp is distinct.
    # Check if we use symbols from llama's common lib in SD worker?
    # src/utils/common.hpp includes "util.h" which might be from llama.cpp/common
    # Yes, SD worker uses stable-diffusion which uses ggml.
)

# NOTE: The 'common' target comes from llama.cpp (LLAMA_BUILD_COMMON=ON).
# src/utils/common.cpp implementation doesn't seem to rely on llama's common library symbols heavily
# EXCEPT for `log_printf` maybe?
# Let's link 'common' (llama's common) just in case, as stable-diffusion often needs it for some utils if shared.
# Actually, stable-diffusion.cpp has its own deps.
# Let's link 'llama' just in case SD_BUILD_EXTERNAL_GGML pulls in stuff that needs it, or just rely on transitive deps.

target_link_libraries(mysti_sd_worker PRIVATE stable-diffusion)

if(WIN32)
    target_link_libraries(mysti_sd_worker PRIVATE ws2_32)
endif()

# --- LLM Worker (mysti_llm_worker) ---
add_executable(mysti_llm_worker
    src/main_llm.cpp
    src/workers/llm_worker.cpp
    src/server/llama_server.cpp
    ${COMMON_SOURCES}
)

target_include_directories(mysti_llm_worker PRIVATE
    src
    src/server
    src/utils
    src/workers
    libs/llama.cpp/include
    libs/llama.cpp/common
    libs/llama.cpp/ggml/include
    libs/llama.cpp/tools/server
    libs/llama.cpp/tools/mtmd
    libs/llama.cpp/vendor/cpp-httplib
    libs/llama.cpp/vendor/nlohmann
    libs/stable-diffusion.cpp
    libs/stable-diffusion.cpp/thirdparty # For stb_image.h
    ${CMAKE_BINARY_DIR}/libs/llama.cpp/tools/server
)

target_link_libraries(mysti_llm_worker PRIVATE
    llama
    server-context
    common # This is llama's common library
    stable-diffusion # For utility symbols in common.cpp
)

if(WIN32)
    target_link_libraries(mysti_llm_worker PRIVATE ws2_32)
endif()