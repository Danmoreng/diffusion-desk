# Build Fix for Arch Linux

This document outlines the changes required to successfully build the MystiCanvas project on Arch Linux with GCC. The original codebase had issues related to the inclusion and implementation of the `stb_image_write.h` single-header library.

## Problem

The build failed due to linker errors, specifically `undefined reference` to `stbi_write_` functions. This was caused by incorrect handling of the `stb_image_write.h` header, which led to issues with symbol visibility and linkage across different parts of the project.

The root causes were:
1.  **Static Linkage:** The `STB_IMAGE_WRITE_STATIC` macro was used, causing the `stb` functions to be declared as `static`. This limited their scope to a single file, making them invisible to other parts of the codebase that needed them.
2.  **Multiple Implementations:** The `STB_IMAGE_WRITE_IMPLEMENTATION` macro was defined in more than one source file, leading to redefinition errors.

## Solution

The following changes were made to resolve these issues:

1.  **Centralized Implementation:** A new file, `src/stb_image_writer.cpp`, was created to act as the single translation unit for the implementation of `stb_image_write.h`.
    ```cpp
    // src/stb_image_writer.cpp
    #define STB_IMAGE_WRITE_IMPLEMENTATION
    #include "stb_image_write.h"
    ```

2.  **Updated `CMakeLists.txt`:** The new source file was added to the `mysti_server` executable's source list in `CMakeLists.txt`:
    ```cmake
    set(SOURCE_FILES
        src/main.cpp
        src/utils/common.cpp
        src/stb_image_writer.cpp # <-- Added this line
        # ... other files
    )
    ```

3.  **Removed Static Linkage:** The `#define STB_IMAGE_WRITE_STATIC` directive was removed from `src/utils/common.cpp` (and not added to the new `stb_image_writer.cpp`). This change ensures that the functions have external linkage, allowing the linker to find them across different object files.

4.  **Corrected Header Inclusion:**
    - The implementation-related macros (`STB_IMAGE_WRITE_IMPLEMENTATION` and `STB_IMAGE_WRITE_STATIC`) were removed from `src/utils/common.cpp`.
    - The `#include "stb_image_write.h"` directive was added to `src/sd/api_utils.cpp` to provide the necessary function declarations for the image writing utilities.

With these modifications, the project now builds successfully on a Linux environment.
