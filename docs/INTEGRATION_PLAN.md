# Integration Plan: VLLM Support for Image Analysis & Tagging

## Overview
This plan outlines the steps to integrate Vision-Language Models (VLLMs) like Qwen2-VL and Pixtral (Mistral Vision) into MystiCanvas. This will enable advanced features such as auto-tagging generated images using visual content, analyzing existing images, and extracting styles, replacing the text-only LLM approach.

## Objectives
1.  **Enable VLLM Loading:** Update the LLM Worker to support loading multimodal projectors (`mmproj`) required for vision models.
2.  **Enhance Auto-Tagging:** Upgrade the `TaggingService` to send generated images (base64 encoded) to the VLLM for analysis, instead of relying solely on the text prompt.
3.  **Database Updates:** Ensure the database service provides necessary file paths for image analysis.

## 1. Backend & LLM Worker Updates

### 1.1 Update `LlamaServer` (src/server/llama_server.cpp)
The `LlamaServer` class wraps the `llama.cpp` server context. It currently initializes `common_params` but ignores the `mmproj` (multimodal projector) field.

*   **Change:** Modify `load_model` signature and implementation to accept an optional `mmproj_path`.
*   **Implementation:**
    ```cpp
    bool load_model(const std::string& model_path, const std::string& mmproj_path, int n_gpu_layers, int n_ctx);
    // Inside:
    llama_params.mmproj.path = mmproj_path;
    ```

### 1.2 Update `LLM Worker` (src/workers/llm_worker.cpp)
The worker's HTTP API needs to expose the ability to load a projector.

*   **Change:** Update `handle_load_llm_model` to parse `mmproj_path` (or `mmproj_id`) from the JSON request body.
*   **Logic:**
    *   If `mmproj_id` is provided, resolve it relative to `svr_params.model_dir`.
    *   Pass the resolved path to `LlamaServer::load_model`.

## 2. Orchestrator Service Updates

### 2.1 Database Access (src/orchestrator/database.cpp)
The `TaggingService` relies on `get_untagged_generations` to find work. Currently, this function only returns the ID, UUID, and Prompt. It needs the file path to access the image.

*   **Change:** Update `get_untagged_generations` SQL query.
*   **New Signature:** `std::vector<std::tuple<int, std::string, std::string, std::string>> get_untagged_generations(int limit);`
*   **Result Tuple:** `(id, uuid, prompt, file_path)`

### 2.2 Tagging Service (src/orchestrator/services/tagging_service.cpp)
This is the core logic update.

*   **Change:** Refactor `loop()` to handle image data.
*   **Implementation:**
    *   Retrieve `file_path` from the updated database query.
    *   **Read & Encode:** Read the image file from disk and convert it to a Base64 string.
    *   **Payload Construction:** Construct a `chat/completions` request using the OpenAI Vision format:
        ```json
        {
          "messages": [
            {
              "role": "user",
              "content": [
                {"type": "text", "text": "Describe this image..."},
                {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}}
              ]
            }
          ]
        }
        ```
    *   **Model Compatibility Check:** (Optional but recommended) Verify if the currently loaded model supports vision (e.g., check `capabilities` in `LlmPreset` if available, or rely on user configuration).

## 3. Configuration & Migration

*   **Database Schema:** The `llm_presets` table already contains an `mmproj_path` column (checked in `database.hpp` and `database.cpp`), so no schema migration is needed.
*   **Preset Management:** Ensure the frontend or API allows saving `mmproj_path` into the `LlmPreset`.

## 4. Execution Steps

1.  **Refactor `LlamaServer`:** Add `mmproj` support.
2.  **Update `LLM Worker`:** specific `mmproj` parsing in API.
3.  **Update `Database`:** return `file_path` for tagging.
4.  **Update `TaggingService`:** Implement image reading, Base64 encoding, and new payload format.
5.  **Verify:** Test with a supported VLLM (e.g., Qwen2-VL, Pixtral) and a corresponding projector if needed (note: some newer GGUFs bundle them, but `llama.cpp` often requires explicit loading for certain architectures).

## Dependencies
*   `stb_image` or similar for reading images (already present in `src/stb_image_writer.cpp`, might need `stb_image.h` for reading if not available, otherwise generic file reading for Base64 conversion is sufficient as we don't need to decode pixels, just raw bytes -> base64).
*   Base64 encoding utility (likely need to add `utils/base64.hpp` if not present).

