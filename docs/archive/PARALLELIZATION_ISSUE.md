I need your help: I am developing a StableDiffusion UI that uses stable-diffusion.cpp and llama.cpp in one project. However, currently I cannot load an SD model and an LLM model in parallel - it does not work, the server crashes. I have my Gemini Coding Agent gather all relevant information. Maybe you have any ideas how to use both libraries together and make it work:

# Parallel Execution Analysis: Stable Diffusion & Llama.cpp

## 1. Executive Summary
**Current Status:** The DiffusionDesk server explicitly prevents parallel execution of Stable Diffusion (image generation) and Llama (text generation) on the same GPU.
**Mechanism:** A global `std::mutex` (`sd_ctx_mutex`) is shared between the SD generation endpoints and the Llama server bridge.
**Goal:** Enable true parallel execution to allow "Chat while Generating" or "Prompt Enhancement during Generation" workflows.
**Constraint:** Both models run on the same GPU (sharing VRAM and Compute) and share the same underlying `ggml` library code (linked via `llama.cpp` submodule).

## 2. Current Architecture & Bottleneck

### 2.1 The "Safety Lock"
The application uses a coarse-grained locking strategy to serialize access to the GPU.

*   **Location:** `src/main.cpp`
    ```cpp
    std::mutex sd_ctx_mutex; // The global lock
    ...
    llm_server.init_server(svr, ..., &sd_ctx_mutex); // Passed to LLM
    ServerContext ctx = { ..., sd_ctx_mutex, ... }; // Passed to SD
    ```

*   **Llama Side (`src/server/llama_server.cpp`):**
    Every request to the LLM API is wrapped in a lock:
    ```cpp
    void LlamaServer::bridge_handler(...) {
        // ...
        std::unique_ptr<std::lock_guard<std::mutex>> lock;
        if (ggml_mutex) {
            lock = std::make_unique<std::lock_guard<std::mutex>>(*ggml_mutex);
        }
        // Native handler called here
    }
    ```

*   **Stable Diffusion Side (`src/sd/api_endpoints.cpp`):**
    Image generation is similarly protected:
    ```cpp
    void handle_generate_image(...) {
        // ...
        {
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            results = generate_image(ctx.sd_ctx, &img_gen_params);
        }
        // ...
    }
    ```

**Result:** If an image is generating (taking ~5-10s), the LLM API is unresponsive. If the LLM is processing a long prompt, image generation waits.

## 3. Library & Backend Analysis

### 3.1 Shared GGML Backend
The project successfully resolved dependency conflicts ("GGML Hell") by forcing `stable-diffusion.cpp` to use `llama.cpp`'s `ggml` library (`-DSD_BUILD_EXTERNAL_GGML=ON`).
*   **Implication:** Both engines run on the *exact same* compiled `ggml-cuda` backend code.
*   **Initialization:** Both call `ggml_backend_cuda_init(device)`.

### 3.2 CUDA Context & Streams
An analysis of `libs/llama.cpp/ggml/src/ggml-cuda/ggml-cuda.cu` reveals:
1.  **Instantiation:** `ggml_backend_cuda_init` creates a **new** `ggml_backend_cuda_context` and a new `ggml_backend` instance for each call.
    *   *Result:* SD and Llama have separate backend instances.
2.  **Resource Management:**
    *   **Streams:** The context appears to manage its own CUDA streams (`streams[i][j]`). This suggests that—in theory—operations from SD and Llama should run on separate CUDA streams and not block each other at the driver level (except for hardware resource contention).
    *   **CuBLAS:** Handles are also managed per-context (`cublas_handles[i]`), reducing the risk of handle conflict.
3.  **Memory Pool:**
    *   `ggml-cuda` uses a custom memory pool (`ggml_cuda_pool`) to reduce `cudaMalloc` overhead.
    *   This pool is instantiated **per backend context** (`new_pool_for_device` returns a `unique_ptr`).
    *   **Risk:** Since pools are separate, Model A does not know about Model B's pool. Model A might hoard empty VRAM blocks in its pool that Model B desperately needs, leading to premature Out-Of-Memory (OOM) errors even if total free VRAM exists.

## 4. Risks of Removing the Mutex

If we simply remove `sd_ctx_mutex`, we face the following risks:

### 4.1 VRAM Fragmentation & OOM
*   **Scenario:** SD allocates 4GB. LLM allocates 4GB. GPU has 12GB.
*   **The Conflict:** During generation, SD needs temporary buffers for attention layers. LLM needs temporary buffers for KV cache or logits.
*   **The Crash:** If both try to `cudaMalloc` (or expand their pools) simultaneously and hit the VRAM limit, the application will crash or return a backend error. Since they don't share a single allocator, they cannot "negotiate" memory usage.

### 4.2 Compute Contention
*   Running two heavy CUDA kernels simultaneously (e.g., SD convolution + LLM matrix mult) works on modern GPUs, but might cause **latency spikes**. The system might become unresponsive if the GPU scheduler prioritizes the massive SD workload, causing the LLM token generation to "stutter".

### 4.3 Thread Safety of Global Singletons
*   `ggml` uses some global state (e.g., `ggml_cuda_info()` singleton). This appears thread-safe, but obscure bugs in `ggml`'s global state management (if any exists for caching or optimization) could be triggered by concurrent access from the same process.

## 5. Technical Questions for Solution
To safely enable parallel execution, we need to ask an expert (ChatGPT Pro / DeepSeek R1) the following:

1.  **Shared Memory Pool:** Is it possible to configure `ggml` (via `llama.cpp` headers) to share a *single* `ggml_cuda_pool` instance between two different backend contexts?
2.  **VRAM Partitioning:** How can we limit the VRAM growth of the LLM (e.g., restricting KV cache or batch size) dynamically when SD starts generating?
3.  **Priority Streams:** Can we assign different CUDA stream priorities to SD (Low) and LLM (High) to ensure the Chat UI remains responsive during image generation?
4.  **Compute Preemption:** Does `ggml` support any form of "pause/resume" or cooperative multitasking that we could leverage?

## 6. Proposed Experiment
1.  **Remove Mutex:** Comment out the lock acquisition in `LlamaServer::bridge_handler` and `handle_generate_image`.
2.  **Stress Test:**
    *   Load SDXL (Heavy VRAM).
    *   Load Qwen-2.5-7B (Moderate VRAM).
    *   Start infinite image generation.
    *   Spam Chat requests.
3.  **Monitor:** Watch `nvidia-smi` for memory usage and potential OOM errors.

## 7. Relevant Source Code

### CMakeLists.txt
```cmake
cmake_minimum_required(VERSION 3.14) # SD cpp needs at least 3.14
project(DiffusionDesk)

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

# Stable Diffusion.cpp options
set(SD_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SD_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_EXTERNAL_GGML ON CACHE BOOL "" FORCE) # Use llama.cpp's ggml

# Add subdirectories
add_subdirectory(libs/llama.cpp)
add_subdirectory(libs/stable-diffusion.cpp)

if(MSVC)
    if(TARGET stable-diffusion)
        target_compile_options(stable-diffusion PRIVATE /bigobj)
    endif()
    if(TARGET server-context)
        target_compile_options(server-context PRIVATE /bigobj)
    endif()
endif()

# --- Main Server ---

set(SOURCE_FILES
    src/main.cpp
    src/utils/common.cpp
    src/sd/api_endpoints.cpp
    src/sd/api_utils.cpp
    src/sd/server_state.cpp
    src/sd/model_loader.cpp
    src/server/llama_server.cpp
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
    libs/llama.cpp/tools/server
    libs/llama.cpp/tools/mtmd
    libs/llama.cpp/vendor/cpp-httplib
    libs/llama.cpp/vendor/nlohmann
    libs/stable-diffusion.cpp
    ${CMAKE_BINARY_DIR}/libs/llama.cpp/tools/server
)

# Link libraries
target_link_libraries(mysti_server PRIVATE
    stable-diffusion
    llama
    server-context
    common
)

if(WIN32)
    target_link_libraries(mysti_server PRIVATE ws2_32)
endif()

# Copy WebUI assets to build directory (Optional, for convenience)
add_custom_command(TARGET mysti_server POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_directory
    ${CMAKE_SOURCE_DIR}/public
    $<TARGET_FILE_DIR:mysti_server>/public
    COMMENT "Copying WebUI assets to output directory"
)
```

### src/main.cpp
```cpp
#include <chrono>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <mutex>
#include <sstream>
#include <vector>

#include "httplib.h"
#include "stable-diffusion.h"
#include "llama.h"

#include "utils/common.hpp"
#include "sd/api_utils.hpp"
#include "sd/server_state.hpp"
#include "sd/model_loader.hpp"
#include "sd/api_endpoints.hpp"

namespace fs = std::filesystem;

void print_usage(int argc, const char* argv[], const std::vector<ArgOptions>& options_list) {
    std::cout << "DiffusionDesk Server v0.1\n";
    std::cout << "Usage: " << argv[0] << " [options]\n\n";
    std::cout << "Svr Options:\n";
    options_list[0].print();
    std::cout << "\nContext Options:\n";
    options_list[1].print();
    std::cout << "\nDefault Generation Options:\n";
    options_list[2].print();
}

void parse_args(int argc, const char** argv, SDSvrParams& svr_params, SDContextParams& ctx_params, SDGenerationParams& default_gen_params) {
    std::vector<ArgOptions> options_vec = {svr_params.get_options(), ctx_params.get_options(), default_gen_params.get_options()};

    if (!parse_options(argc, argv, options_vec)) {
        print_usage(argc, argv, options_vec);
        exit(svr_params.normal_exit ? 0 : 1);
    }

    bool has_model = (ctx_params.model_path.length() > 0 || ctx_params.diffusion_model_path.length() > 0);
    
    if (!svr_params.process_and_check()) {
        print_usage(argc, argv, options_vec);
        exit(1);
    }

    if (!has_model && svr_params.model_dir.empty()) {
        LOG_ERROR("error: either --model/--diffusion-model or --model-dir must be specified");
        exit(1);
    }

    if (has_model) {
        std::string active_path = ctx_params.diffusion_model_path.empty() ? ctx_params.model_path : ctx_params.diffusion_model_path;
        load_model_config(ctx_params, active_path, svr_params.model_dir);
    }

    if (ctx_params.n_threads <= 0) {
        ctx_params.n_threads = sd_get_num_physical_cores();
    }

    if (!default_gen_params.process_and_check(IMG_GEN, ctx_params.lora_model_dir)) {
        print_usage(argc, argv, options_vec);
        exit(1);
    }
}

void sd_log_cb(enum sd_log_level_t level, const char* log, void* data) {
    SDSvrParams* svr_params = (SDSvrParams*)data;
    log_print(level, log, svr_params->verbose, svr_params->color);
}

int main(int argc, const char** argv) {
    SDSvrParams svr_params;
    SDContextParams ctx_params;
    SDGenerationParams default_gen_params;
    parse_args(argc, argv, svr_params, ctx_params, default_gen_params);

    sd_set_log_callback(sd_log_cb, (void*)&svr_params);
    sd_set_progress_callback(on_progress, &progress_state);
    set_log_verbose(svr_params.verbose);
    set_log_color(svr_params.color);

    LOG_INFO("DiffusionDesk Server Starting...");
    LOG_INFO("SD Info: %s", sd_get_system_info());
    // LOG_INFO("LLM Info: %s", llama_print_system_info());

    sd_ctx_params_t sd_ctx_params_raw = ctx_params.to_sd_ctx_params_t(false, false, false);
    sd_ctx_t* sd_ctx              = nullptr;
    upscaler_ctx_t* upscaler_ctx  = nullptr;
    std::string current_upscale_model_path;
    
    if (!ctx_params.model_path.empty() || !ctx_params.diffusion_model_path.empty()) {
        sd_ctx = new_sd_ctx(&sd_ctx_params_raw);
        if (sd_ctx == nullptr) {
            LOG_ERROR("new_sd_ctx failed for initial model");
            return 1;
        }
    }

    std::mutex sd_ctx_mutex;
    LlamaServer llm_server;
    llm_server.set_idle_timeout(svr_params.llm_idle_timeout);

    ServerContext ctx = {
        svr_params,
        ctx_params,
        default_gen_params,
        sd_ctx,
        sd_ctx_mutex,
        upscaler_ctx,
        current_upscale_model_path,
        llm_server
    };

    httplib::Server svr;

    llm_server.init_server(svr, [&ctx]() {
        ensure_llm_loaded(ctx);
    }, &sd_ctx_mutex);

    svr.Get(R"(/outputs/(.*))", [&](const httplib::Request& req, httplib::Response& res) {
        handle_get_outputs(req, res, ctx);
    });

    if (!svr.set_mount_point("/", "./public")) {
        LOG_WARN("failed to mount ./public directory");
    }

    svr.set_pre_routing_handler([](const httplib::Request& req, httplib::Response& res) {
        res.set_header("Access-Control-Allow-Origin", "*");
        res.set_header("Access-Control-Allow-Methods", "*");
        res.set_header("Access-Control-Allow-Headers", "*");
        if (req.method == "OPTIONS") {
            res.status = 204;
            return httplib::Server::HandlerResponse::Handled;
        }
        return httplib::Server::HandlerResponse::Unhandled;
    });

    svr.Get("/health", handle_health);
    svr.Get("/v1/config", [&](const httplib::Request& req, httplib::Response& res) { handle_get_config(req, res, ctx); });
    svr.Post("/v1/config", [&](const httplib::Request& req, httplib::Response& res) { handle_post_config(req, res, ctx); });
    svr.Get("/v1/progress", handle_get_progress);
    svr.Get("/v1/stream/progress", handle_stream_progress);
    svr.Get("/v1/models", [&](const httplib::Request& req, httplib::Response& res) { handle_get_models(req, res, ctx); });
    svr.Post("/v1/models/load", [&](const httplib::Request& req, httplib::Response& res) { handle_load_model(req, res, ctx); });
    svr.Post("/v1/upscale/load", [&](const httplib::Request& req, httplib::Response& res) { handle_load_upscale_model(req, res, ctx); });
    svr.Post("/v1/images/upscale", [&](const httplib::Request& req, httplib::Response& res) { handle_upscale_image(req, res, ctx); });
    svr.Get("/v1/history/images", [&](const httplib::Request& req, httplib::Response& res) { handle_get_history(req, res, ctx); });
    svr.Post("/v1/images/generations", [&](const httplib::Request& req, httplib::Response& res) { handle_generate_image(req, res, ctx); });
    svr.Post("/v1/images/edits", [&](const httplib::Request& req, httplib::Response& res) { handle_edit_image(req, res, ctx); });

    // LLM Endpoints
    svr.Post("/v1/llm/load", [&](const httplib::Request& req, httplib::Response& res) { handle_load_llm_model(req, res, ctx); });
    svr.Post("/v1/llm/unload", [&](const httplib::Request& req, httplib::Response& res) { handle_unload_llm_model(req, res, ctx); });

    LOG_INFO("listening on: %s:%d\n", svr_params.listen_ip.c_str(), svr_params.listen_port);
    svr.listen(svr_params.listen_ip, svr_params.listen_port);

    if (sd_ctx) free_sd_ctx(sd_ctx);
    if (upscaler_ctx) free_upscaler_ctx(upscaler_ctx);
    
    return 0;
}
```

### src/sd/server_state.hpp
```cpp
#pragma once

#include <mutex>
#include <condition_variable>
#include <string>
#include "utils/common.hpp"

struct ProgressState {
    int step = 0;
    int steps = 0;
    float time = 0;
    std::string phase = "";
    uint64_t version = 0;
    std::mutex mutex;
    std::condition_variable cv;
};

extern ProgressState progress_state;

void on_progress(int step, int steps, float time, void* data);
void reset_progress();
void set_progress_phase(const std::string& phase);
```

### src/server/llama_server.hpp
```cpp
#pragma once

#include "server-context.h"
#include "httplib.h"
#include "utils/common.hpp"
#include <memory>
#include <string>
#include <thread>
#include <mutex>

class LlamaServer {
public:
    LlamaServer();
    ~LlamaServer();

    bool load_model(const std::string& model_path, int n_gpu_layers = -1, int n_ctx = 2048);
    void stop();

    void init_server(httplib::Server& svr, std::function<void()> before_handler = nullptr, std::mutex* external_mutex = nullptr);

    bool is_loaded() const { return !!server_ctx; }
    void set_idle_timeout(int seconds) { idle_timeout_seconds = seconds; }

private:
    std::unique_ptr<server_context> server_ctx;
    std::unique_ptr<server_routes> routes;
    std::thread loop_thread;
    std::thread idle_thread;
    std::mutex state_mutex;
    std::mutex* ggml_mutex = nullptr;
    common_params llama_params;

    std::chrono::steady_clock::time_point last_access;
    int idle_timeout_seconds = 300;
    bool running = true;

    void update_last_access();
    void idle_check_loop();
    void bridge_handler(httplib::Server& svr, const std::string& method, const std::string& path, std::function<server_http_context::handler_t(LlamaServer*)> get_handler, std::function<void()> before_handler = nullptr);
};
```

### src/server/llama_server.cpp
```cpp
#include "llama_server.hpp"
#include "utils/common.hpp"
#include "server-common.h"
#include "ggml-backend.h"
#include <iostream>

LlamaServer::LlamaServer() {
    common_init();
    last_access = std::chrono::steady_clock::now();
    idle_thread = std::thread(&LlamaServer::idle_check_loop, this);
}

LlamaServer::~LlamaServer() {
    {
        std::lock_guard<std::mutex> lock(state_mutex);
        running = false;
    }
    if (idle_thread.joinable()) {
        idle_thread.join();
    }
    stop();
}

bool LlamaServer::load_model(const std::string& model_path, int n_gpu_layers, int n_ctx) {
    std::lock_guard<std::mutex> lock(state_mutex);
    stop();

    llama_params = common_params();
    llama_params.model.path = model_path;

    if (n_gpu_layers >= 0) {
        llama_params.n_gpu_layers = n_gpu_layers;
    } else {
        // Use GPU for the small LLM as it should fit alongside SD
        llama_params.n_gpu_layers = 99; 
    }
    llama_params.n_ctx = n_ctx > 0 ? n_ctx : 2048;
    llama_params.n_parallel = 1;
    llama_params.n_predict = 512;
    llama_params.warmup = false;
    llama_params.fit_params = true; // Let llama.cpp fit into remaining VRAM
    llama_params.fit_params_target = 4ULL * 1024 * 1024 * 1024; // Leave 4GB free for SD

    LOG_INFO("Initializing LLM server context: ctx=%d, parallel=%d, predict=%d, gpu_layers=%d, fit_margin=4GB", 
             llama_params.n_ctx, llama_params.n_parallel, llama_params.n_predict, llama_params.n_gpu_layers);

    server_ctx = std::make_unique<server_context>();
    if (!server_ctx->load_model(llama_params)) {
        LOG_ERROR("Failed to load LLM model: %s", model_path.c_str());
        server_ctx.reset();
        return false;
    }

    routes = std::make_unique<server_routes>(llama_params, *server_ctx);

    loop_thread = std::thread([this]() {
        server_ctx->start_loop();
    });

    update_last_access();
    LOG_INFO("LLM Server logic initialized with model: %s", model_path.c_str());
    return true;
}

void LlamaServer::stop() {
    // Note: state_mutex should be held by caller or we need a internal_stop
    if (server_ctx) {
        server_ctx->terminate();
        if (loop_thread.joinable()) {
            loop_thread.join();
        }
        routes.reset();
        server_ctx.reset();
        LOG_INFO("LLM Model unloaded.");
    }
}

void LlamaServer::update_last_access() {
    last_access = std::chrono::steady_clock::now();
}

void LlamaServer::idle_check_loop() {
    while (true) {
        std::this_thread::sleep_for(std::chrono::seconds(10));
        
        std::lock_guard<std::mutex> lock(state_mutex);
        if (!running) break;
        
        if (server_ctx && idle_timeout_seconds > 0) {
            auto now = std::chrono::steady_clock::now();
            auto duration = std::chrono::duration_cast<std::chrono::seconds>(now - last_access).count();
            if (duration > idle_timeout_seconds) {
                LOG_INFO("LLM idle timeout reached (%d seconds). Unloading...", idle_timeout_seconds);
                stop();
            }
        }
    }
}

void LlamaServer::bridge_handler(httplib::Server& svr, const std::string& method, const std::string& path, std::function<server_http_context::handler_t(LlamaServer*)> get_handler, std::function<void()> before_handler) {
    auto wrapper = [this, get_handler, before_handler, method, path](const httplib::Request& req, httplib::Response& res) {
        if (before_handler) {
            before_handler();
        }
        update_last_access();
        
        LOG_INFO("LLM Request: %s %s", method.c_str(), path.c_str());

        // Ensure we don't conflict with SD's GGML usage
        std::unique_ptr<std::lock_guard<std::mutex>> lock;
        if (ggml_mutex) {
            lock = std::make_unique<std::lock_guard<std::mutex>>(*ggml_mutex);
        }
        
        auto handler = get_handler(this);
        if (!handler) {
            LOG_ERROR("LLM handler not found for %s", path.c_str());
            res.status = 503;
            res.set_content(R"({\"error\":\"LLM model not loaded and no default available\"})", "application/json");
            return;
        }

        std::function<bool()> should_stop = []() { return false; };
        server_http_req llama_req = {
            {}, // params
            {}, // headers
            req.path,
            req.body,
            should_stop
        };

        // Map params
        for (auto& p : req.params) {
            llama_req.params[p.first] = p.second;
        }
        // Map headers
        for (auto& h : req.headers) {
            llama_req.headers[h.first] = h.second;
        }

        LOG_DEBUG("LLM calling native handler...");
        auto llama_res = handler(llama_req);
        if (!llama_res) {
            LOG_ERROR("LLM native handler returned null");
            res.status = 500;
            return;
        }

        LOG_INFO("LLM Response status: %d", llama_res->status);
        res.status = llama_res->status;
        for (auto& h : llama_res->headers) {
            res.set_header(h.first, h.second);
        }

        if (llama_res->is_stream()) {
            res.set_chunked_content_provider(llama_res->content_type, 
                [llama_res = std::shared_ptr<server_http_res>(std::move(llama_res))](size_t offset, httplib::DataSink &sink) {
                    std::string chunk;
                    if (llama_res->next(chunk)) {
                        sink.write(chunk.c_str(), chunk.size());
                        return true;
                    }
                    sink.done();
                    return false;
                });
        } else {
            res.set_content(llama_res->data, llama_res->content_type);
        }
    };

    if (method == "GET") {
        svr.Get(path, wrapper);
    } else if (method == "POST") {
        svr.Post(path, wrapper);
    }
}

void LlamaServer::init_server(httplib::Server& svr, std::function<void()> before_handler, std::mutex* external_mutex) {
    ggml_mutex = external_mutex;
    bridge_handler(svr, "GET",  "/v1/llm/models",        [](LlamaServer* s) { return s->routes ? s->routes->get_models : nullptr; }, before_handler);
    bridge_handler(svr, "POST", "/v1/chat/completions",  [](LlamaServer* s) { return s->routes ? s->routes->post_chat_completions : nullptr; }, before_handler);
    bridge_handler(svr, "POST", "/v1/completions",       [](LlamaServer* s) { return s->routes ? s->routes->post_completions : nullptr; }, before_handler);
    bridge_handler(svr, "POST", "/v1/embeddings",        [](LlamaServer* s) { return s->routes ? s->routes->post_embeddings : nullptr; }, before_handler);
    bridge_handler(svr, "POST", "/v1/tokenize",          [](LlamaServer* s) { return s->routes ? s->routes->post_tokenize : nullptr; }, before_handler);
    bridge_handler(svr, "POST", "/v1/detokenize",        [](LlamaServer* s) { return s->routes ? s->routes->post_detokenize : nullptr; }, before_handler);
}
```

### src/sd/api_endpoints.cpp
```cpp
#include "api_endpoints.hpp"
#include "api_utils.hpp"
#include "server_state.hpp"
#include "model_loader.hpp"
#include <sstream>
#include <iomanip>
#include <fstream>

namespace fs = std::filesystem;

// ... (omitted params handling for brevity, focus on handle_generate_image)

void handle_generate_image(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    reset_progress();
    try {
        if (req.body.empty()) {
            res.status = 400;
            res.set_content(R"({\"error\":\"empty body\"})", "application/json");
            return;
        }

        mysti::json j             = mysti::json::parse(req.body);
        std::string prompt        = j.value("prompt", "");
        // ... (parsing parameters) ...

        SDGenerationParams gen_params = ctx.default_gen_params;
        gen_params.prompt             = prompt;
        // ... (setting up gen_params) ...

        // ... (loading images if img2img) ...

        sd_img_gen_params_t img_gen_params = {
            // ... (populating struct)
        };

        sd_image_t* results = nullptr;
        int num_results     = 0;

        {
            // THE LOCK IS HERE
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            if (ctx.sd_ctx == nullptr) {
                res.status = 400;
                res.set_content(R"({\"error\":\"no model loaded\"})", "application/json");
                return;
            }
            set_progress_phase("Sampling...");
            results     = generate_image(ctx.sd_ctx, &img_gen_params);
            num_results = gen_params.batch_count;
            
            // ... (hires fix logic also inside lock if applicable or partially outside)
        }

        // ... (saving results) 
        
        res.set_content(out.dump(), "application/json");
        res.status = 200;

    } catch (const std::exception& e) {
        res.status = 500;
        mysti::json err;
        err["error"]   = "server_error";
        err["message"] = e.what();
        res.set_content(err.dump(), "application/json");
    }
}
```

What you’re trying to do (SD + LLM, same process, same GPU, both using ggml-cuda) is *possible in principle*, but in practice there are two big “gotchas” that very often explain **crashes**:

1. **You may still be executing ggml-cuda concurrently even though you think you serialized it** (because llama.cpp server streaming / loop-thread work can happen outside your mutex scope).
2. **VRAM spikes + per-context CUDA pooling** can cause “sudden” OOM / illegal access when both pipelines run close to the limit (your analysis about separate pools is on-point). ggml-cuda’s backend design uses streams and per-context resource management, including memory pooling, which means SD and LLM don’t automatically “negotiate” VRAM with each other. ([DeepWiki][1])

Below are the most practical ways to make this stable, ordered from “most likely to fix crashes” to “true parallel”.

---

## 1) First: verify your mutex actually covers *all* LLM GPU work (streaming is a trap)

### Why this matters

Your `bridge_handler()` only holds the lock while calling the *route handler* and building the `server_http_res`. But when the response is **streaming**, the real work often continues when `server_http_res::next()` is called repeatedly. llama.cpp’s server layer is explicitly built around a streaming generator model (“call `next()` until false”). ([Git Memory][2])

In your code, the lock is **released before** the chunk callback calls `llama_res->next(chunk)`.

That means this interleaving can happen:

* Chat request begins → handler returns a streaming response → **mutex released**
* SD generation starts → takes mutex → starts heavy CUDA work
* HTTP server keeps calling `next()` → which may drive more CUDA work → **boom** (concurrent ggml-cuda)

### Quick hardening change (minimal diff)

Lock around `next()` inside the chunk provider:

```cpp
if (llama_res->is_stream()) {
    auto * mtx = ggml_mutex; // capture the pointer
    res.set_chunked_content_provider(
        llama_res->content_type,
        [mtx, llama_res = std::shared_ptr<server_http_res>(std::move(llama_res))]
        (size_t /*offset*/, httplib::DataSink &sink) mutable {
            std::string chunk;

            if (mtx) {
                std::lock_guard<std::mutex> lock(*mtx);
                if (!llama_res->next(chunk)) {
                    sink.done();
                    return false;
                }
            } else {
                if (!llama_res->next(chunk)) {
                    sink.done();
                    return false;
                }
            }

            sink.write(chunk.c_str(), chunk.size());
            return true;
        }
    );
}
```

**Important caveat:** this only fully solves it if the GPU work happens during `next()`. If llama.cpp’s internal server loop does GPU work on a separate thread, you still need to move the lock *into* that loop (see next section).

---

## 2) If llama.cpp server loop runs inference on its own thread: your current lock is *structurally insufficient*

You spawn:

```cpp
loop_thread = std::thread([this]() {
    server_ctx->start_loop();
});
```

If `start_loop()` performs decoding / embeddings on that thread (common pattern in server designs), then **no lock in your HTTP wrapper can prevent concurrency**.

### What to do

You have 3 realistic options:

#### Option A: Add an “external GPU mutex” hook inside llama.cpp server loop (best if you want to keep llama server code)

Patch llama.cpp (tools/server) so that *every place it calls into decode / graph compute / kv cache clear* is wrapped by your mutex. This is consistent with how streaming works (generator model) and how multi-request crashes often show up as CUDA graph/stream capture errors. ([GitHub][3])

This turns your mutex into a **true “only one ggml-cuda user at a time” gate**.

#### Option B: Stop using the in-process llama “server_context” and call libllama directly

If you directly own the `llama_model` / `llama_context` and run decode in your request handler (or in *your* single GPU worker thread), you control locking precisely. This is usually the cleanest approach when embedding llama.cpp into another engine.

#### Option C: Put *both* SD and LLM behind your own single “GPU worker” thread (very robust)

Don’t let HTTP threads touch ggml at all. Instead:

* HTTP thread enqueues jobs (LLM token step, SD diffusion step, embeddings…)
* A single GPU worker thread executes jobs and publishes results (token stream, progress updates)

This avoids most “CUDA backend thread-safety” surprises outright and gives you knobs for fairness.

---

## 3) If your goal is “Chat while Generating”: time-slicing beats true parallel (and is much easier to make stable)

True parallel kernels on one GPU sounds nice, but on consumer GPUs the win is often small and the stability cost is high.

A much more controllable design is **cooperative scheduling**:

* SD generation is ~N diffusion steps (each step is a big forward).
* LLM generation is ~M token steps (each step is smaller but frequent).

You schedule like:

* run **1 SD step**
* run **k LLM tokens** (k=1..4)
* repeat

Result: the UI stays responsive and you never concurrently hit ggml-cuda.

### Catch

Your current SD call is a monolith:

```cpp
results = generate_image(ctx.sd_ctx, &img_gen_params);
```

To time-slice SD, you need either:

* stable-diffusion.cpp API support for step-wise sampling, or
* you patch stable-diffusion.cpp to expose a “run_one_step()” loop, or
* you accept that SD holds the GPU lock for the full 5–10s (no chat responsiveness).

If you can patch SD, do it: step-level yielding is the biggest “UX win per engineering hour” here.

---

## 4) Crashes that look like “threading bugs” are often CUDA Graph / stream-capture issues: test with graphs disabled

There are documented cases where multi-threaded/multi-request CUDA usage in llama.cpp leads to errors like:

* “operation not permitted when stream is capturing”
* “operation failed due to a previous error during capture” ([GitHub][3])

A common diagnostic/workaround is to disable CUDA graphs:

* `GGML_CUDA_DISABLE_GRAPHS=1` ([GitHub][4])
  and there are also known CUDA-graph-related bugs/workarounds in llama.cpp history. ([GitHub][5])

**Recommendation:**
Even if you ultimately want graphs for performance, first get the system stable with graphs off. Then re-enable and retest.

---

## 5) VRAM management: make it *impossible* for both models to hit a peak at the same time

Your pool-hoarding concern is valid: ggml-cuda uses pooling and per-context management. ([DeepWiki][1])
Even if “weights fit”, *peaks* during attention / convolutions / temporary buffers can push you over.

### Stable Diffusion side: use built-in SD memory controls aggressively

stable-diffusion.cpp has multiple memory strategies you can combine:

* `--offload-to-cpu` (move params to RAM, keep compute buffer on GPU)
* `--clip-on-cpu`, `--vae-on-cpu`, `--control-net-cpu`
* `--vae-tiling` (reduces peak VRAM during VAE)
* flash attention options can also reduce VRAM depending on model/component ([DeepWiki][6])

If you want SDXL + a 7B-ish LLM on a 12GB card, **parameter offloading is often the difference between “works” and “random crash.”** ([DeepWiki][6])

### LLM side: shrink KV and scratch first, not just weights

In llama.cpp server you have knobs that directly impact VRAM:

* `--ctx-size`
* `--batch-size` / `--ubatch-size`
* `--cache-type-k` / `--cache-type-v` (KV cache dtype, including quantized types) ([GitHub][7])

KV cache is usually the biggest “surprise VRAM eater” when you increase context.

### Add a *hard* headroom policy

Your `fit_params_target = 4GB` is a good idea, but SDXL at higher resolutions, hires fix, or controlnets can easily want more. Consider:

* make the “leave free VRAM” margin dynamic based on SD settings (resolution, batch, hires)
* or just reserve more (6–8GB margin) if you want stability over max LLM speed

---

## 6) Unified memory as an OOM safety net (won’t fix concurrency, but can stop hard crashes)

llama.cpp documents an env var to enable CUDA unified memory on Linux:

* `GGML_CUDA_ENABLE_UNIFIED_MEMORY=1` ([GitHub][8])

This can turn “hard OOM crash” into “slow but survives” if you occasionally exceed VRAM. It’s not free (it can get *much* slower), but for a UI product it can be a lifesaver.

---

## 7) True parallel execution on one GPU: what I’d do if you insist

If you *really* want both to execute concurrently (not time-sliced), the engineering path is:

1. **Disable CUDA graphs** during development to avoid stream capture crashes under concurrency. ([GitHub][4])
2. Patch ggml-cuda to support:

   * **shared per-device allocator / pool** (singleton pool per device, not per backend context), to reduce pool hoarding
   * optional **budget limits** per “client” (LLM vs SD)
3. Patch ggml-cuda stream creation to use **stream priorities** (`cudaStreamCreateWithPriority`) so LLM stays responsive while SD runs.
4. Add back graphs later (if stable).

This is doable, but it’s now a fork/patch maintenance burden.

Given the current ecosystem reality (and the existence of CUDA graph related bugs/workarounds), I’d only go this route if you’re comfortable carrying a ggml-cuda patch long-term. ([GitHub][5])

---

## 8) The lowest-risk “it just works” architecture: separate processes

If you want *true* parallelism and you don’t want to fight in-process ggml-cuda edge cases:

* Run **llama-server as a separate process**
* Run **your SD server as another process**
* Your UI talks to both (or your main server proxies)

This isolates global/static CUDA backend state per process and makes “crash containment” much better. You still share VRAM (so you still need budgets), but you avoid a whole class of in-process thread-safety pitfalls.

---

## A concrete “next steps” checklist I’d execute in your repo

1. **Fix streaming lock scope** (lock around `next()`), and verify you never run SD while a chat stream is active. ([Git Memory][2])
2. Set `GGML_CUDA_DISABLE_GRAPHS=1` and rerun your stress test. ([GitHub][4])
3. Turn on SD memory optimizations (`offload-to-cpu`, `vae-tiling`, etc.) and reduce LLM KV memory (`ctx-size`, `cache-type-k/v`). ([DeepWiki][6])
4. If you still want “chat while generating”, build a **single GPU worker** (time-slicing), and consider patching SD to be step-wise.

---

If you paste **one crash log** (even just the last ~30 lines) and tell me whether you’re using **streaming chat** (`"stream": true`) during the crash, I can usually pinpoint whether you’re hitting:

* stream-capture / CUDA graphs,
* concurrent ggml usage despite the mutex,
* or plain OOM/allocator fragmentation.

But even without that, the two biggest high-probability fixes are:

* **move the lock to cover actual streaming / loop inference**, and
* **disable CUDA graphs while you validate concurrency**.

[1]: https://deepwiki.com/ggml-org/llama.cpp/4.3-cuda-backend "CUDA Backend | ggml-org/llama.cpp | DeepWiki"
[2]: https://gitmemories.com/ggml-org/llama.cpp/issues/17216?utm_source=chatgpt.com "llama.cpp server: split HTTP into its own interface"
[3]: https://github.com/ggml-org/llama.cpp/issues/11804 "Misc. bug: CUDA errors with multi-threaded use · Issue #11804 · ggml-org/llama.cpp · GitHub"
[4]: https://github.com/ggml-org/llama.cpp/issues/11804?utm_source=chatgpt.com "Misc. bug: CUDA errors with multi-threaded use · Issue #11804 · ggml ..."
[5]: https://github.com/ggml-org/llama.cpp/issues/7492?utm_source=chatgpt.com "CUDA graphs break quantized K cache · Issue #7492 · ggml-org ... - GitHub"
[6]: https://deepwiki.com/leejet/stable-diffusion.cpp/6.4-memory-optimization "Memory Optimization | leejet/stable-diffusion.cpp | DeepWiki"
[7]: https://github.com/CodeBub/llama.cpp/blob/master/examples/server/README.md?utm_source=chatgpt.com "llama.cpp/examples/server/README.md at master - GitHub"
[8]: https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md?utm_source=chatgpt.com "llama.cpp/docs/build.md at master · ggml-org/llama.cpp · GitHub"


Okay I like the idea of two separate processes most. The idea however is, that the stable diffusion model and the (small) llm model both can be loaded in the GPU at the same time, if the GPU has enough VRAM. Is that possible? Because the application should allow the user to generate images AND craft better prompts with a small LLM at the same time. That should be possible if the VRAM is big enough no? And I would like to have everything inside one single application executable - that can launch two different sub-processes and handle everything - right?

Can you craft an instruction document for my Gemni AI Agent to do the implementation?

Yes — **two separate processes can both keep their models resident on the same GPU at the same time**, as long as:

* **Total VRAM usage + temporary peaks** (SD sampling buffers, VAE, LLM KV cache, scratch buffers) stays below physical VRAM, and
* you leave **headroom** (because “fits at load time” can still OOM during generation).

In fact, **separate processes is one of the cleanest ways** to avoid the in-process ggml-cuda concurrency hazards you’re seeing, because each process gets its own CUDA context, its own ggml backend state, its own threads, and you don’t have to coordinate a global mutex across two engines.

And yes, you can still ship “one app” as **a single executable** that launches two child processes: the classic pattern is a **multi-mode binary** that spawns itself with `--mode=sd-worker` and `--mode=llm-worker`, while the parent runs as an **orchestrator** (proxy + UI server + lifecycle manager).

Below is an instruction document you can hand to your Gemini agent to implement this.

---

# Implementation Instructions: Split SD + LLM into Two GPU Worker Processes with a Single Orchestrator Executable

## 0) Goal

Refactor DiffusionDesk into a **single distributed app** (one executable, three run modes):

1. **Orchestrator** (public HTTP server)

* Serves the WebUI and exposes the “public” API routes
* Spawns & monitors two child processes (SD Worker, LLM Worker)
* Proxies API calls to the correct worker
* Handles health checks, restarts, and graceful shutdown

2. **SD Worker**

* Hosts all Stable Diffusion endpoints and owns `stable-diffusion.cpp` context + GPU memory

3. **LLM Worker**

* Hosts all LLM endpoints and owns llama.cpp server context + GPU memory

**Key desired UX:** user can generate images and chat/prompt-craft simultaneously (subject to GPU contention). Models should both remain loaded on GPU if VRAM allows.

---

## 1) Success Criteria

### Functional

* Orchestrator starts, spawns both workers, and exposes **one** external port to the UI.
* `/v1/images/*` routes work even while `/v1/chat/completions` is streaming.
* Both workers can load GPU models concurrently if VRAM allows.
* If a worker crashes, orchestrator returns a helpful error and (optionally) restarts the worker.

### Robustness

* No shared in-process mutex between SD & LLM (they’re in different processes).
* Streaming responses (LLM stream and progress stream) do not deadlock or crash.
* Clean shutdown: Ctrl+C stops orchestrator and terminates both workers.

---

## 2) High-Level Architecture

```
                (external clients)
Browser/UI  --->  Orchestrator (public :8080)
                    |     |
                    |     +--> proxy /v1/chat/* to LLM Worker (127.0.0.1:PORT_LLM)
                    |
                    +--> proxy /v1/images/* to SD Worker  (127.0.0.1:PORT_SD)

Orchestrator also serves /public assets, and can serve /outputs from disk.
Workers bind only to 127.0.0.1 (local only).
```

### Design Choice: Single executable, multiple run modes

* Build one binary (e.g., `mysti_server`)
* Add CLI flag: `--mode=orchestrator|sd-worker|llm-worker`
* Orchestrator spawns **itself** with `--mode=sd-worker` and `--mode=llm-worker`

This satisfies “one executable” distribution without needing separate binaries.

---

## 3) Refactor Plan Overview

### Phase A — Add run modes & split server registration

* Add a small “mode switch” early in `main()`:

  * If `--mode sd-worker`: start SD-only server
  * If `--mode llm-worker`: start LLM-only server
  * Else: start orchestrator server

### Phase B — Orchestrator spawns workers and proxies endpoints

* Implement a `ProcessManager` (cross-platform spawn/kill)
* Allocate loopback ports for each worker (or accept fixed defaults)
* Add a proxy layer using `httplib::Client` to forward requests

### Phase C — Add health checks, graceful shutdown, restart policy

* Workers expose `/internal/health`
* Orchestrator polls health and can restart workers
* Orchestrator catches SIGINT and terminates children

### Phase D — VRAM co-existence policy + OOM fallback

* Enforce a load order policy and/or headroom
* If OOM detected in one worker, orchestrator can:

  * unload the other model, or
  * reconfigure LLM to CPU / fewer GPU layers, then retry once

---

## 4) Detailed Implementation Tasks

## 4.1 Add CLI Mode Flag

### Task

Extend argument parsing to accept a new `--mode` string:

* `orchestrator` (default)
* `sd-worker`
* `llm-worker`

### Notes

* Keep existing CLI args working.
* In worker mode, only parse the subset of args needed by that worker.

### Acceptance

Running:

* `mysti_server --mode sd-worker ...` starts SD endpoints only
* `mysti_server --mode llm-worker ...` starts LLM endpoints only
* `mysti_server` starts orchestrator

---

## 4.2 Implement SD Worker Mode

### Task

Create a function `run_sd_worker(...)` that:

* Builds `sd_ctx_t*`, `upscaler_ctx_t*` as today
* Registers only SD routes:

  * `/v1/models`, `/v1/models/load`, `/v1/images/generations`, `/v1/images/edits`, `/v1/progress`, `/v1/stream/progress`, etc.
* Does **not** initialize `LlamaServer`
* Binds to **127.0.0.1** and the passed worker port

### Important

* Remove any cross-engine mutex usage in worker mode (it is now unnecessary).
* Keep SD’s internal mutex if needed for SD context re-use within SD worker.

### Add internal endpoints

* `GET /internal/health` → `200 OK` with JSON like `{ "ok": true, "worker": "sd" }`
* `POST /internal/shutdown` (optional) → triggers clean shutdown

### Acceptance

* SD worker runs standalone and can generate images via localhost port.

---

## 4.3 Implement LLM Worker Mode

### Task

Create `run_llm_worker(...)` that:

* Initializes `LlamaServer` and registers LLM routes only:

  * `/v1/chat/completions`, `/v1/completions`, `/v1/embeddings`, etc.
  * `/v1/llm/load`, `/v1/llm/unload`
* Binds to **127.0.0.1** and passed worker port

### Critical Change

* Remove the `sd_ctx_mutex` bridging entirely in LLM worker mode.

  * LLM worker should have no knowledge of SD.

### Add internal endpoints

* `GET /internal/health` → `{ "ok": true, "worker": "llm", "loaded": true/false }`
* `POST /internal/shutdown` (optional)

### Acceptance

* LLM worker runs standalone and can chat via localhost port.

---

## 4.4 Orchestrator Mode: Process Spawning

### Task

Create `run_orchestrator(...)` that:

1. Chooses two free local ports:

   * `PORT_SD`
   * `PORT_LLM`

2. Spawns two child processes:

   * `mysti_server --mode sd-worker --listen-ip 127.0.0.1 --listen-port PORT_SD ...`
   * `mysti_server --mode llm-worker --listen-ip 127.0.0.1 --listen-port PORT_LLM ...`

3. Waits until both workers respond to `/internal/health` or times out.

### Cross-platform spawning

Implement a small `ProcessManager` abstraction:

* `spawn(args, env)` returns pid/handle
* `is_running()`
* `terminate(graceful_then_kill)`
* capture stdout/stderr if feasible (optional but useful)

Implement:

* Windows: `CreateProcessW`
* POSIX: `fork()` + `execvp()`

### Acceptance

* Starting orchestrator automatically starts both workers.

---

## 4.5 Orchestrator Mode: Reverse Proxy Routes

### Task

The orchestrator binds to the public port (e.g. 0.0.0.0:8080), serves:

* Static UI (mount `/public`)
* Proxies API calls to workers

### Routing rules

* Proxy to SD worker:

  * `/v1/images/*`
  * `/v1/models*` (SD model list/load)
  * `/v1/progress`
  * `/v1/stream/progress`
  * `/v1/upscale/*`
  * `/v1/history/images`

* Proxy to LLM worker:

  * `/v1/chat/completions`
  * `/v1/completions`
  * `/v1/embeddings`
  * `/v1/tokenize`
  * `/v1/detokenize`
  * `/v1/llm/*`

* Orchestrator-native:

  * `/health` can aggregate worker health
  * `/outputs/*` can be served directly from disk OR proxied to SD worker (choose one)

### Implementation detail: request forwarding

Using `httplib::Client`:

* Forward method (GET/POST)
* Forward headers (except hop-by-hop)
* Forward query params
* Forward body
* Return status + headers + body to the original client

### Acceptance

* UI can use a single base URL (orchestrator) for all API calls.
* SD + LLM calls work through orchestrator even if workers are on random ports.

---

## 4.6 Streaming Proxy Support (LLM stream + progress stream)

### Task

Support `Transfer-Encoding: chunked` / streaming responses through orchestrator.

Minimum required:

* LLM chat streaming (`"stream": true`) should stream tokens to browser via orchestrator endpoint.
* Progress stream endpoint should stream events.

### Implementation approach options

Pick one:

**Option 1 (Recommended): True streaming proxy**

* Use httplib client “content receiver” style callback to receive partial chunks from worker
* Write them to orchestrator response via `set_chunked_content_provider`

**Option 2 (Fallback if hard): Redirect streaming endpoints**

* For streaming routes only, orchestrator replies `307 Temporary Redirect` to worker URL
* Ensure CORS headers allow it
* UI must accept redirects and call loopback ports (may be undesirable for remote clients)

Prefer Option 1 for a clean “single origin” UX.

### Acceptance

* While SD is generating, a streaming LLM request still streams tokens (even if slower due to GPU contention).

---

## 4.7 Worker Authentication (Local-only hardening)

### Task (recommended)

To prevent random local processes from calling the workers:

* Orchestrator generates a random token at startup (e.g. 32 bytes).
* Pass token to each worker via env var or CLI arg.
* Orchestrator attaches header: `X-Mysti-Worker-Token: <token>` on every proxied request.
* Worker checks the header and rejects requests missing/incorrect token (`401`).

### Acceptance

* Workers accept only orchestrator requests by default.

---

## 4.8 VRAM Co-existence Policy

Even with huge VRAM, you still want guardrails.

### Key concept

Each worker sees “free VRAM” at load time. If you load SD first, then load LLM, llama can “fit into remaining” (if your llama build supports that feature) and you’re more likely to succeed. If you load LLM first using most VRAM, SD may fail later.

### Policy to implement in orchestrator

* Default load order:

  1. SD model (largest) loads first
  2. LLM model loads second, using “fit into remaining VRAM” settings (or smaller GPU layer count)

* If user explicitly loads LLM first:

  * Set conservative LLM GPU usage defaults (lower GPU layers, smaller ctx)
  * Or keep LLM on CPU until SD is loaded

### OOM fallback strategy

When orchestrator receives an error response that looks like OOM (match substrings like `out of memory`, `cudaMalloc`, `CUDA error`, etc.):

1. If the failing request is SD generation:

   * Ask LLM worker to unload or switch to CPU mode (or lower GPU layers)
   * Retry SD generation **once**
2. If the failing request is LLM load:

   * Fall back to CPU-only for LLM and return a warning

### Acceptance

* In “borderline VRAM” cases, app degrades gracefully instead of crashing.

---

## 4.9 Orchestrator Health + Restart Policy

### Task

* Orchestrator implements `/health` that reports:

  * orchestrator status
  * sd worker health
  * llm worker health

* Background watcher thread:

  * poll `/internal/health` every N seconds
  * if worker down, mark unhealthy and optionally restart it

### Restart policy

* Restart at most X times within a window
* If repeated crashes, stop restarting and return clear error

### Acceptance

* If LLM worker crashes, SD still works and orchestrator reports degraded mode.

---

## 4.10 Graceful Shutdown

### Task

On SIGINT / Ctrl+C:

1. Orchestrator stops accepting new requests
2. Orchestrator calls `/internal/shutdown` on each worker (if implemented)
3. If workers don’t exit quickly, kill them
4. Exit orchestrator

### Acceptance

* No orphan worker processes remain after shutdown.

---

## 5) CMake / Build Changes

### Task

* Add new source files:

  * `src/orchestrator/process_manager.(hpp/cpp)`
  * `src/orchestrator/proxy.(hpp/cpp)`
  * `src/orchestrator/orchestrator_main.(hpp/cpp)` (or similar)
* Update `src/main.cpp` to dispatch by mode and call the correct run function.

### Keep single binary

* Do not create separate targets.
* Keep linking stable-diffusion + llama to the same binary (workers will use only relevant parts at runtime).

---

## 6) Testing Plan

### Test 1 — Basic startup

* Start orchestrator, verify both workers started and `/health` returns healthy.

### Test 2 — Concurrent load

* Load SD model on SD worker.
* Load small LLM on LLM worker.
* Confirm both stay loaded (health can include “loaded”: true flags).

### Test 3 — Concurrency

* Start image generation loop (multiple requests)
* Simultaneously send streaming chat requests
* Confirm:

  * no crash
  * chat remains responsive (may slow under contention)

### Test 4 — OOM handling

* Deliberately push near VRAM limit (bigger SD model or higher res + bigger ctx)
* Confirm orchestrator returns a controlled error and doesn’t die
* Confirm fallback logic triggers (LLM unload/retry)

### Test 5 — Worker crash recovery

* Kill LLM worker process manually
* Confirm orchestrator returns a useful error for LLM routes and can restart worker (if enabled)

---

## 7) Notes / Tips to Avoid Common Pitfalls

* **Workers must bind only to `127.0.0.1`** by default.
* Don’t attempt to “share ggml state” across processes — you can’t. The point is isolation.
* Expect GPU contention: latency may increase. That’s OK; stability is priority.
* Leave VRAM headroom; “fits at load” ≠ “fits during sampling”.
* Prefer SD-first load order if SD is the bigger model.

---

## 8) Deliverables Checklist for the PR

* [x] `--mode` support with three modes
* [x] SD worker server functional standalone
* [x] LLM worker server functional standalone
* [x] Orchestrator spawns both and proxies requests
* [x] Streaming proxy works (or documented redirect fallback)
* [x] Health endpoints and `/health` aggregation
* [x] Graceful shutdown implemented
* [x] Basic restart policy
* [x] Minimal documentation for running in each mode

---

If you want, I can also provide a **skeleton code layout** (header/class outlines for `ProcessManager` and a `proxy_request()` helper using httplib) that your agent can copy-paste as a starting point.
