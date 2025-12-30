# MystiCanvas Architectural Review & Improvement Plan

**Date:** December 30, 2025  
**Reviewer:** Gemini (AI Software Engineer)

## 1. Executive Summary

The MystiCanvas project employs a robust **Orchestrator-Worker** architecture. By separating the Orchestrator, Stable Diffusion (SD) Worker, and LLM Worker into distinct processes, the system achieves high stability and resource isolation. A crash in the experimental LLM worker does not bring down the main application.

However, as the project grows, several areas require attention to maintain maintainability, safety, and performance. This document outlines specific, actionable improvements.

---

## 2. Backend Improvements (C++)

### A. Resource Safety (RAII & Smart Pointers)
**Current State:**
The workers (`sd_worker.cpp`, `upscaler_ctx`) likely manage raw C pointers (`sd_ctx_t*`, `upscaler_ctx_t*`) manually. This risks memory leaks or undefined behavior if exceptions occur during loading or generation.

**Recommendation:**
Replace raw pointers with `std::unique_ptr` using custom deleters. This ensures resources are automatically freed when they go out of scope, even during errors.

**Implementation Detail:**
```cpp
// In sd_worker.hpp or a common utils header
struct SdCtxDeleter {
    void operator()(sd_ctx_t* ptr) const {
        if (ptr) free_sd_ctx(ptr);
    }
};

// In your class
using SdCtxPtr = std::unique_ptr<sd_ctx_t, SdCtxDeleter>;

// Usage
SdCtxPtr m_sdCtx;
// ...
m_sdCtx.reset(new_sd_ctx(model_path.c_str(), ...)); 
// No manual free needed!
```

### B. Refactoring `orchestrator_main.cpp`
**Current State:**
The `run_orchestrator` function is becoming a "God Function," handling:
1. HTTP Server setup & Routing
2. Process Management (starting/stopping workers)
3. Business Logic (VRAM arbitration)
4. Signal Handling

**Recommendation:**
Refactor this into dedicated classes to improve testability and readability.

1.  **`ServiceController`**: Handles the API routes (`/v1/...`).
2.  **`ResourceManager`**: Encapsulates the VRAM arbitration logic (e.g., "Unload LLM if SD needs memory").

**Implementation Detail:**
```cpp
// resource_manager.hpp
class ResourceManager {
public:
    bool prepare_for_sd_generation(int required_vram_mb);
    void release_locks();
private:
    ProcessManager& m_procMgr;
};

// orchestrator_main.cpp
// Routes become clean one-liners
server.Post("/v1/generation/text-to-image", [&](const Request& req, Response& res) {
    if (!resourceMgr.prepare_for_sd_generation(4000)) {
        res.status = 503;
        return;
    }
    proxy.forward(req, res);
});
```

### C. Database Transaction Safety
**Current State:**
`Database.cpp` uses a mutex for thread safety. However, complex operations (e.g., "Insert Image" -> "Get ID" -> "Insert Tags") involve multiple function calls. If the app crashes in between, you might have an image without tags.

**Recommendation:**
Implement a scoped Transaction helper.

**Implementation Detail:**
```cpp
// database.cpp
void Database::save_image_with_tags(const Image& img, const std::vector<std::string>& tags) {
    std::lock_guard<std::mutex> lock(m_mutex);
    try {
        m_db->exec("BEGIN TRANSACTION");
        // ... insert image ...
        // ... insert tags ...
        m_db->exec("COMMIT");
    } catch (...) {
        m_db->exec("ROLLBACK");
        throw;
    }
}
```

---

## 3. Frontend Improvements (Vue.js)

### A. Component Modularity
**Current State:**
`Sidebar.vue` is doing too much. It handles navigation, model switching, LLM status, and detailed VRAM visualization.

**Recommendation:**
Extract the VRAM bar into a dedicated component.

*   **`components/status/VramIndicator.vue`**: Handles the polling and rendering of the multi-colored bar.
*   **`components/navigation/ModelSelector.vue`**: Encapsulates the dropdown logic for model switching.

### B. Business Logic in Stores
**Current State:**
Some views (`GenerationForm.vue`) contain logic about default steps, parameter constraints, etc.

**Recommendation:**
Move "Business Rules" into the Pinia store actions. The Component should just say `store.validateAndGenerate()`, and the Store should handle the "If width > 1024, adjust steps" logic.

---

## 4. Inter-Process Communication (IPC) Analysis

**Question:** "Would it be a good idea to change the HTTP communication between workers to something different?"

**Current Approach:** **HTTP (REST/JSON)** on localhost ports.

| Protocol | Pros | Cons | Recommendation |
| :--- | :--- | :--- | :--- |
| **HTTP (Current)** | • **Debuggable:** Easy to curl/test manually.<br>• **Simple:** Standard libraries (httplib) available.<br>• **Decoupled:** Workers can be written in any language (Python, Node, C++). | • **Overhead:** Parsing JSON and HTTP headers adds latency (though usually negligible for GenAI tasks taking seconds).<br>• **Connection Management:** Dealing with keep-alive, timeouts. | **KEEP (For now)**. The bottleneck in GenAI is the GPU computation (seconds), not the IPC overhead (microseconds). HTTP's debuggability is invaluable. |
| **gRPC (Protobuf)** | • **Strict Types:** Interface Definition Language (IDL) ensures contracts.<br>• **Performance:** Binary format is faster than JSON.<br>• **Streaming:** Native support for progress bars. | • **Complexity:** Requires build steps (protoc), extra dependencies.<br>• **Rigid:** Harder to "hack" or test with simple tools. | **CONSIDER LATER**. If the project scales to a distributed cluster or if the overhead becomes measurable. |
| **ZeroMQ / NNG** | • **Fast:** extremely low latency.<br>• **Patterns:** Native Pub/Sub, Req/Rep patterns. | • **Custom Protocol:** You effectively define your own wire format.<br>• **Debugging:** Harder to inspect traffic. | **SKIP**. Adds complexity without solving a critical bottleneck. |
| **Shared Memory** | • **Instant:** Zero-copy data transfer. | • **Unsafe:** One crash corrupts the segment.<br>• **Complex:** Synchronization is manual (semaphores). | **USE FOR IMAGES ONLY**. Pass metadata via HTTP, but write the generated Image directly to disk/shared mem. (You already write to disk, which is good). |

**Verdict on IPC:**
Stick with **HTTP**.
*   **Why?** The latency of Stable Diffusion generation is 2s - 20s. The overhead of HTTP (1-2ms) is irrelevant.
*   **Improvement:** Instead of replacing HTTP, **standardize** the API. Ensure all workers implement a `/health` endpoint and a standard Error JSON format.

---

## 5. Documentation vs. Codebase Gap Analysis

After reviewing the `docs/` folder, the following discrepancies and missing implementations were identified:

### A. VRAM Management (VAE Tiling & Offloading)
*   **Doc:** `VRAM_MANAGEMENT.md` suggests **VAE Tiling** and **CPU Offloading** for the VAE step to prevent OOM on large images.
*   **Code:** `sd_worker.cpp` lacks VAE tiling or dynamic CPU offloading logic. It relies on the orchestrator unloading the LLM.
*   **Gap:** The "Surgical" VAE offloading logic is missing.

### B. Database Features
*   **Doc:** `database_design.md` proposes `params_json`, `jobs` table (auto-tagging), `prompt_templates`, and FTS5.
*   **Code:** `database.cpp` implements a simpler schema (`generations`, `models`, `tags`, `image_tags`) without these advanced features.
*   **Gap:** The database is an MVP "v1" compared to the "v2" vision.

### C. LLM Auto-Tagging
*   **Doc:** Mentions background auto-tagging post-generation.
*   **Code:** **Implemented** in `src/orchestrator/services/tagging_service.cpp`. The service runs in a background thread, wakes up on new generations, and respects VRAM usage by pausing during SD generation.
*   **Gap:** Currently relies on **Prompt Analysis** (text-to-text). Future iterations could use Vision-Language Models (LLaVA) to tag the actual image content. The system prompt is hardcoded in C++.

### D. Security
*   **Doc:** Mentions "Header-based Auth" with transient tokens.
*   **Code:** Implementation verification required in `proxy.cpp` and workers.

---

## 6. Implementation Plan Recommendation

**Phase 1: Stabilization (High Priority)**
1.  **Smart Pointers:** Refactor `sd_worker.cpp` to use `std::unique_ptr` for safety.
2.  **Transaction Safety:** Add transaction support to `Database.cpp` to ensure atomic saves for images and tags.

**Phase 2: VRAM Optimization (High Impact)**
1.  **VAE Tiling:** Implement VAE tiling support in `sd_worker.cpp` to enable large image generation without OOM.
2.  **VramIndicator:** Refactor the frontend VRAM component.

**Phase 3: Features (Strategic)**
1.  **Tagging Optimization:** Move the hardcoded tagging system prompt to `config.json` for easier tuning. Ensure tagging DB operations are transactional.
2.  **Database V2:** Migrate schema to support `params_json` and FTS5.