cmake_minimum_required(VERSION 3.14) # SD cpp needs at least 3.14
project(MystiCanvas)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)

# --- Dependencies ---

# Llama.cpp options (MUST come first to define ggml for others)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# Stable Diffusion.cpp options
set(SD_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SD_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_EXTERNAL_GGML ON CACHE BOOL "" FORCE) # Use llama.cpp's ggml

# Add subdirectories
add_subdirectory(libs/llama.cpp)
add_subdirectory(libs/stable-diffusion.cpp)

if(MSVC)
    target_compile_options(stable-diffusion PRIVATE /bigobj)
endif()

# --- Main Server ---

set(SOURCE_FILES
    src/main.cpp
    src/utils/common.cpp
    src/sd/api_endpoints.cpp
    src/sd/api_utils.cpp
    src/sd/server_state.cpp
    src/sd/model_loader.cpp
)

add_executable(mysti_server ${SOURCE_FILES})

# Include paths for dependencies
target_include_directories(mysti_server PRIVATE
    src
    src/sd
    src/server
    src/utils
    libs/llama.cpp/include
    libs/llama.cpp/common
    libs/llama.cpp/ggml/include
    libs/stable-diffusion.cpp
)

# Link libraries
target_link_libraries(mysti_server PRIVATE
    stable-diffusion
    llama
)

# Copy WebUI assets to build directory (Optional, for convenience)
add_custom_command(TARGET mysti_server POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_directory
    ${CMAKE_SOURCE_DIR}/public
    $<TARGET_FILE_DIR:mysti_server>/public
    COMMENT "Copying WebUI assets to output directory"
)
