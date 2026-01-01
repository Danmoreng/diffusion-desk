#include "api_endpoints.hpp"
#include "api_utils.hpp"
#include "server_state.hpp"
#include "model_loader.hpp"
#include <sstream>
#include <iomanip>
#include <fstream>

namespace fs = std::filesystem;

// Handlers Placeholders - To be filled from main.cpp logic

void log_vram_status(const std::string& phase) {
    float proc = get_current_process_vram_usage_gb();
    float free = get_free_vram_gb();
    float total = get_total_vram_gb();
    float other = total - free - proc;
    std::string msg = "[VRAM] " + phase + " | Process: " + std::to_string(proc).substr(0, 4) + " GB, Free: " + std::to_string(free).substr(0, 4) + " GB, Other: " + std::to_string(std::max(0.0f, other)).substr(0, 4) + " GB, Total: " + std::to_string(total).substr(0, 4) + " GB\n";
    log_print(SD_LOG_INFO, msg.c_str(), true, true);
}

void handle_health(const httplib::Request&, httplib::Response& res, ServerContext& ctx) {
    mysti::json j;
    j["ok"] = true;
    j["service"] = "sd";
    j["version"] = version_string();
    j["model_loaded"] = (ctx.sd_ctx != nullptr);
    std::string mp = ctx.ctx_params.diffusion_model_path;
    if (mp.empty()) mp = ctx.ctx_params.model_path;
    j["model_path"] = mp;
    j["vram_allocated_mb"] = (int)(get_current_process_vram_usage_gb() * 1024.0f);
    j["vram_free_mb"] = (int)(get_free_vram_gb() * 1024.0f);
    res.set_content(j.dump(), "application/json");
}

void handle_get_config(const httplib::Request&, httplib::Response& res, ServerContext& ctx) {
    mysti::json c;
    c["output_dir"] = ctx.svr_params.output_dir;
    c["model_dir"] = ctx.svr_params.model_dir;
    
    // Use standalone diffusion model if set, otherwise full model path
    std::string current_model = ctx.ctx_params.diffusion_model_path;
    if (current_model.empty()) current_model = ctx.ctx_params.model_path;
    c["model"] = current_model;
    
    res.set_content(c.dump(), "application/json");
}

void handle_post_config(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    try {
        mysti::json body = mysti::json::parse(req.body);
        if (body.contains("output_dir")) {
            ctx.svr_params.output_dir = body["output_dir"];
            LOG_INFO("Config updated: output_dir = %s", ctx.svr_params.output_dir.c_str());
        }
        if (body.contains("model_dir")) {
            ctx.svr_params.model_dir = body["model_dir"];
            LOG_INFO("Config updated: model_dir = %s", ctx.svr_params.model_dir.c_str());
        }
        res.set_content(R"({\"status\":\"success\"})", "application/json");
    } catch (const std::exception& e) {
        res.status = 400;
        res.set_content(make_error_json("invalid_json", e.what()), "application/json");
    }
}

void handle_get_progress(const httplib::Request&, httplib::Response& res) {
    mysti::json r;
    {
        std::lock_guard<std::mutex> lock(progress_state.mutex);
        r["step"] = progress_state.step;
        r["steps"] = progress_state.steps;
        r["time"] = progress_state.time;
        r["message"] = progress_state.message;
    }
    res.set_content(r.dump(), "application/json");
}

void handle_stream_progress(const httplib::Request& req, httplib::Response& res) {
    LOG_INFO("New progress stream subscription from %s", req.remote_addr.c_str());
    res.set_header("Cache-Control", "no-cache");
    res.set_header("Connection", "keep-alive");
    res.set_header("X-Accel-Buffering", "no"); // Disable proxy buffering

    res.set_chunked_content_provider("text/event-stream", [&](size_t offset, httplib::DataSink &sink) {
        uint64_t last_version = 0;
        int step = 0;
        int steps = 0;
        float time = 0;
        std::string phase = "";
        std::string message = "";

        // Send initial state or at least a comment to open the stream
        {
            std::lock_guard<std::mutex> lock(progress_state.mutex);
            last_version = progress_state.version;
            step = progress_state.step;
            steps = progress_state.steps;
            time = progress_state.time;
            phase = progress_state.phase;
            message = progress_state.message;
        }
        
        mysti::json initial_j;
        initial_j["step"] = step;
        initial_j["steps"] = steps;
        initial_j["time"] = time;
        initial_j["phase"] = phase;
        initial_j["message"] = message;
        std::string initial_s = "data: " + initial_j.dump() + "\n\n";
        if (!sink.write(initial_s.c_str(), initial_s.size())) return false;

        while (true) {
            std::unique_lock<std::mutex> lock(progress_state.mutex);
            // Wait for a new version, or a timeout to send a keep-alive
            if (!progress_state.cv.wait_for(lock, std::chrono::seconds(15), 
                [&]{ return progress_state.version > last_version; })) {
                // Timeout - send keep-alive comment (ping)
                lock.unlock();
                if (!sink.write(": ping\n\n", 9)) return false;
                continue;
            }
            
            step = progress_state.step;
            steps = progress_state.steps;
            time = progress_state.time;
            phase = progress_state.phase;
            message = progress_state.message;
            last_version = progress_state.version;
            lock.unlock();

            mysti::json j;
            j["step"] = step;
            j["steps"] = steps;
            j["time"] = time;
            j["phase"] = phase;
            j["message"] = message;
            std::string s = "data: " + j.dump() + "\n\n";
            if (!sink.write(s.c_str(), s.size())) {
                return false;
            }
        }
        return true;
    });
}

// We will move other handlers implementation in next steps to keep this manageable
void handle_get_outputs(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    std::string file_name = req.matches[1];
    fs::path file_path = fs::path(ctx.svr_params.output_dir) / file_name;

    if (fs::exists(file_path) && fs::is_regular_file(file_path)) {
        std::ifstream ifs(file_path, std::ios::binary);
        std::string content((std::istreambuf_iterator<char>(ifs)), std::istreambuf_iterator<char>());
        
        std::string ext = file_path.extension().string();
        std::string mime = "application/octet-stream";
        if (ext == ".png") mime = "image/png";
        else if (ext == ".jpg" || ext == ".jpeg") mime = "image/jpeg";
        else if (ext == ".json") mime = "application/json";
        
        res.set_content(content, mime.c_str());
    } else {
        res.status = 404;
    }
}

void handle_get_models(const httplib::Request&, httplib::Response& res, ServerContext& ctx) {
    mysti::json r;
    r["data"] = mysti::json::array();
    
    std::string current_model_name = fs::path(ctx.ctx_params.diffusion_model_path).filename().string();
    if (current_model_name.empty()) {
        current_model_name = fs::path(ctx.ctx_params.model_path).filename().string();
    }

    auto scan_dir = [&](const std::string& sub_dir) {
        fs::path base_path = fs::path(ctx.svr_params.model_dir) / sub_dir;
        if (fs::exists(base_path) && fs::is_directory(base_path)) {
            for (const auto& entry : fs::recursive_directory_iterator(base_path)) {
                if (entry.is_regular_file()) {
                    auto ext = entry.path().extension().string();
                    if (ext == ".gguf" || ext == ".safetensors" || ext == ".ckpt" || ext == ".pth") {
                        mysti::json model;
                        // Use relative path from model_dir as ID for easy loading
                        std::string rel_path = fs::relative(entry.path(), ctx.svr_params.model_dir).string();
                        std::replace(rel_path.begin(), rel_path.end(), '\\', '/');

                        model["id"] = rel_path;
                        model["name"] = entry.path().filename().string();
                        model["type"] = sub_dir;
                        model["object"] = "model";
                        model["owned_by"] = "local";
                        
                        bool isActive = false;
                        bool isLoaded = false;
                        if (sub_dir == "llm") {
                            isActive = (rel_path == ctx.active_llm_model_path);
                            isLoaded = isActive && ctx.active_llm_model_loaded;
                        } else if (sub_dir == "esrgan") {
                            isActive = (rel_path == ctx.current_upscale_model_path);
                            isLoaded = isActive; // Assuming for now
                        } else {
                            isActive = (model["name"] == current_model_name);
                            isLoaded = isActive; // SD models are loaded if active in this context
                        }

                        model["active"] = isActive;
                        model["loaded"] = isLoaded;
                        r["data"].push_back(model);
                    }
                }
            }
        }
    };

    try {
        scan_dir("stable-diffusion");
        scan_dir("lora");
        scan_dir("vae");
        scan_dir("text-encoder");
        scan_dir("llm");
        scan_dir("esrgan");
        
        // Also scan root of model_dir for convenience
        if (fs::exists(ctx.svr_params.model_dir) && fs::is_directory(ctx.svr_params.model_dir)) {
            for (const auto& entry : fs::directory_iterator(ctx.svr_params.model_dir)) {
                if (entry.is_regular_file()) {
                    auto ext = entry.path().extension().string();
                    if (ext == ".gguf" || ext == ".safetensors" || ext == ".ckpt") {
                        mysti::json model;
                        model["id"] = entry.path().filename().string();
                        model["name"] = entry.path().filename().string();
                        model["type"] = "root";
                        model["object"] = "model";
                        model["owned_by"] = "local";
                        model["active"] = (model["id"] == current_model_name);
                        r["data"].push_back(model);
                    }
                }
            }
        }
    } catch (const std::exception& e) {
        LOG_ERROR("failed to list models: %s", e.what());
    }

    res.set_content(r.dump(), "application/json");
}

void handle_load_model(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    try {
        mysti::json body = mysti::json::parse(req.body);
        if (!body.contains("model_id")) {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "model_id (relative path) required"), "application/json");
            return;
        }
        std::string model_id = body["model_id"];
        fs::path model_path = fs::path(ctx.svr_params.model_dir) / model_id;
        
        if (!fs::exists(model_path)) {
            res.status = 404;
            res.set_content(make_error_json("model_not_found", "model file not found at " + model_path.string()), "application/json");
            return;
        }

        LOG_INFO("Loading new model: %s", model_path.string().c_str());

        {
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            
            // Smart pointer reset handles freeing old context
            ctx.sd_ctx.reset();

            // Update params based on where it was found
            std::string rel_s = model_id;
            if (rel_s.find("vae/") == 0) {
                    ctx.ctx_params.vae_path = model_path.string();
            } else if (rel_s.find("esrgan/") == 0) {
                    ctx.ctx_params.esrgan_path = model_path.string();
            } else {
                    // Main model load - Reset optional paths and flags
                    ctx.ctx_params.diffusion_model_path = model_path.string();
                    ctx.ctx_params.model_path = "";
                    ctx.ctx_params.vae_path = "";
                    ctx.ctx_params.clip_l_path = "";
                    ctx.ctx_params.clip_g_path = "";
                    ctx.ctx_params.t5xxl_path = "";
                    ctx.ctx_params.llm_path = "";
                    ctx.ctx_params.diffusion_flash_attn = false;
                    ctx.ctx_params.clip_on_cpu = false;
                    ctx.ctx_params.vae_on_cpu = false;
                    ctx.ctx_params.offload_params_to_cpu = false;
                    ctx.ctx_params.vae_tiling_params.enabled = false;
                    ctx.ctx_params.prediction = PREDICTION_COUNT;
                    ctx.ctx_params.flow_shift = INFINITY;

                    // Use helper to load sidecar config
                    load_model_config(ctx.ctx_params, ctx.ctx_params.diffusion_model_path, ctx.svr_params.model_dir);
            }

            sd_ctx_params_t sd_ctx_p = ctx.ctx_params.to_sd_ctx_params_t(false, false, false);
            ctx.sd_ctx.reset(new_sd_ctx(&sd_ctx_p));

            if (!ctx.sd_ctx) {
                throw std::runtime_error("failed to create new context with selected model");
            }
        }

        res.set_content(R"({\"status\":\"success\",\"model\":\")" + model_id + R"("})", "application/json");

    } catch (const std::exception& e) {
        LOG_ERROR("error loading model: %s", e.what());
        res.status = 500;
        res.set_content(R"({\"error\":\")" + std::string(e.what()) + R"("})", "application/json");
    }
}

void handle_load_upscale_model(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    try {
        mysti::json body = mysti::json::parse(req.body);
        if (!body.contains("model_id")) {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "model_id required"), "application/json");
            return;
        }
        std::string model_id = body["model_id"];
        fs::path model_path = fs::path(ctx.svr_params.model_dir) / model_id;
        
        if (!fs::exists(model_path)) {
            res.status = 404;
            res.set_content(make_error_json("model_not_found", "upscale model not found"), "application/json");
            return;
        }

        LOG_INFO("Loading upscale model: %s", model_path.string().c_str());

        {
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            ctx.upscaler_ctx.reset(new_upscaler_ctx(model_path.string().c_str(), 
                                            ctx.ctx_params.offload_params_to_cpu,
                                            false, // direct
                                            ctx.ctx_params.n_threads,
                                            512)); // tile_size

            if (!ctx.upscaler_ctx) {
                throw std::runtime_error("failed to create upscaler context");
            }
            ctx.current_upscale_model_path = model_id;
        }

        res.set_content(R"({\"status\":\"success\",\"model\":\")" + model_id + R"("})", "application/json");
    } catch (const std::exception& e) {
        LOG_ERROR("error loading upscale model: %s", e.what());
        res.status = 500;
        res.set_content(R"({\"error\":\")" + std::string(e.what()) + R"("})", "application/json");
    }
}

void handle_upscale_image(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    try {
        mysti::json body = mysti::json::parse(req.body);
        std::string image_data;
        std::string image_name;

        if (body.contains("image")) {
            image_data = body["image"];
            if (image_data.find("base64,") != std::string::npos) {
                image_data = image_data.substr(image_data.find("base64,") + 7);
            }
        } else if (body.contains("image_name")) {
            image_name = body["image_name"];
            fs::path img_path = fs::path(ctx.svr_params.output_dir) / image_name;
            if (!fs::exists(img_path)) {
                res.status = 404;
                res.set_content(make_error_json("image_not_found"), "application/json");
                return;
            }
            std::ifstream ifs(img_path, std::ios::binary);
            image_data = std::string((std::istreambuf_iterator<char>(ifs)), std::istreambuf_iterator<char>());
        } else {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "image (base64) or image_name required"), "application/json");
            return;
        }

        uint32_t upscale_factor = body.value("upscale_factor", 0);

        sd_image_t input_image = {0, 0, 3, nullptr};
        std::vector<uint8_t> decoded_bytes;
        
        if (!image_name.empty()) {
            int w, h;
            input_image.data = load_image_from_memory(image_data.data(), (int)image_data.size(), w, h, 0, 0, 3);
            input_image.width = (uint32_t)w;
            input_image.height = (uint32_t)h;
        } else {
            decoded_bytes = base64_decode(image_data);
            int w, h;
            input_image.data = load_image_from_memory((const char*)decoded_bytes.data(), (int)decoded_bytes.size(), w, h, 0, 0, 3);
            input_image.width = (uint32_t)w;
            input_image.height = (uint32_t)h;
        }

        if (!input_image.data) {
            res.status = 400;
            res.set_content(make_error_json("decode_failed", "failed to decode image"), "application/json");
            return;
        }

        sd_image_t upscaled_image = {0};
        {
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            if (!ctx.upscaler_ctx) {
                stbi_image_free(input_image.data);
                res.status = 400;
                res.set_content(make_error_json("no_model", "no upscale model loaded"), "application/json");
                return;
            }
            
            if (upscale_factor == 0) {
                upscale_factor = get_upscale_factor(ctx.upscaler_ctx.get());
            }

            LOG_INFO("Upscaling image: %dx%d -> factor %d", input_image.width, input_image.height, upscale_factor);
            upscaled_image = upscale(ctx.upscaler_ctx.get(), input_image, upscale_factor);
        }

        stbi_image_free(input_image.data);

        if (!upscaled_image.data) {
            res.status = 500;
            res.set_content(make_error_json("upscale_failed"), "application/json");
            return;
        }

        auto image_bytes = write_image_to_vector(ImageFormat::PNG,
                                                    upscaled_image.data,
                                                    (int)upscaled_image.width,
                                                    (int)upscaled_image.height,
                                                    (int)upscaled_image.channel);
        
        free(upscaled_image.data);

        if (image_bytes.empty()) {
            res.status = 500;
            res.set_content(make_error_json("encode_failed", "failed to encode upscaled image"), "application/json");
            return;
        }

        mysti::json out;
        out["width"] = upscaled_image.width;
        out["height"] = upscaled_image.height;

        bool save_image = body.value("save_image", true);
        
        // Always save to disk now (either persistent or temp) to serve via URL
        std::string final_output_dir = ctx.svr_params.output_dir;
        std::string url_prefix = "/outputs/";
        
        if (!save_image) {
            final_output_dir = (fs::path(ctx.svr_params.output_dir) / "temp").string();
            url_prefix = "/outputs/temp/";
            if (!fs::exists(final_output_dir)) {
                fs::create_directories(final_output_dir);
            }
        }

        auto timestamp = std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
        std::string out_name = "upscale-" + std::to_string(timestamp) + ".png";
        fs::path out_path = fs::path(final_output_dir) / out_name;
        
        std::ofstream ofs(out_path, std::ios::binary);
        ofs.write((const char*)image_bytes.data(), image_bytes.size());
        
        out["url"] = url_prefix + out_name;
        out["name"] = out_name;
        LOG_INFO("Saved upscaled image to %s", out_path.string().c_str());

        res.set_content(out.dump(), "application/json");

    } catch (const std::exception& e) {
        LOG_ERROR("error during upscaling: %s", e.what());
        res.status = 500;
        res.set_content(R"({\"error\":\")" + std::string(e.what()) + R"("})", "application/json");
    }
}

void handle_get_history(const httplib::Request&, httplib::Response& res, ServerContext& ctx) {
    const std::string output_dir = ctx.svr_params.output_dir;
    mysti::json image_list = mysti::json::array();
    try {
        if (fs::exists(output_dir) && fs::is_directory(output_dir)) {
            std::vector<fs::path> image_paths;
            for (const auto& entry : fs::directory_iterator(output_dir)) {
                if (entry.is_regular_file()) {
                    auto ext = entry.path().extension().string();
                    if (ext == ".png" || ext == ".jpg" || ext == ".jpeg") {
                        image_paths.push_back(entry.path());
                    }
                }
            }
            std::sort(image_paths.begin(), image_paths.end(), [](const fs::path& a, const fs::path& b) {
                return a.filename().string() > b.filename().string();
            });

            for(const auto& img_path : image_paths) {
                mysti::json item;
                item["name"] = img_path.filename().string();
                
                auto txt_path = img_path;
                txt_path.replace_extension(".txt");
                if (fs::exists(txt_path)) {
                    try {
                        std::ifstream txt_file(txt_path);
                        std::string content((std::istreambuf_iterator<char>(txt_file)),
                                            (std::istreambuf_iterator<char>()));
                        item["params"] = parse_image_params(content);
                    } catch (...) {
                        LOG_WARN("failed to parse txt metadata: %s", txt_path.string().c_str());
                    }
                } else {
                    auto json_path = img_path;
                    json_path.replace_extension(".json");
                    if (fs::exists(json_path)) {
                        try {
                            std::ifstream json_file(json_path);
                            item["params"] = mysti::json::parse(json_file);
                        } catch (...) {
                            LOG_WARN("failed to parse json metadata: %s", json_path.string().c_str());
                        }
                    }
                }
                image_list.push_back(item);
            }
        }
    } catch (const std::exception& e) {
        LOG_ERROR("failed to list image history: %s", e.what());
        res.status = 500;
        res.set_content(make_error_json("server_error", "failed to list image history"), "application/json");
        return;
    }
    res.set_content(image_list.dump(), "application/json");
}

void handle_generate_image(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    reset_progress();
    LOG_INFO("New generation request received");
    try {
        if (req.body.empty()) {
            res.status = 400;
            res.set_content(make_error_json("empty_body"), "application/json");
            return;
        }

        mysti::json j             = mysti::json::parse(req.body);
        std::string prompt        = j.value("prompt", "");
        int n                     = std::max(1, (int)j.value("n", 1));
        std::string size          = j.value("size", "");
        std::string output_format = j.value("output_format", "png");
        int output_compression    = j.value("output_compression", 100);
        int width                 = 512;
        int height                = 512;
        if (!size.empty()) {
            auto pos = size.find('x');
            if (pos != std::string::npos) {
                try {
                    width  = std::stoi(size.substr(0, pos));
                    height = std::stoi(size.substr(pos + 1));
                } catch (...) {
                }
            }
        }

        if (prompt.empty()) {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "prompt required"), "application/json");
            return;
        }

        if (output_format != "png" && output_format != "jpeg") {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "invalid output_format, must be one of [png, jpeg]"), "application/json");
            return;
        }
        if (n <= 0)
            n = 1;
        if (n > 8)
            n = 8;  // safety
        if (output_compression > 100) {
            output_compression = 100;
        }
        if (output_compression < 0) {
            output_compression = 0;
        }

        double total_generation_time = 0;
        mysti::json out;
        out["created"]       = iso_timestamp_now();
        out["data"]          = mysti::json::array();
        out["output_format"] = output_format;
        out["generation_time"] = total_generation_time;

        SDGenerationParams gen_params = ctx.default_gen_params;
        gen_params.prompt             = prompt;
        gen_params.width              = width;
        gen_params.height             = height;
        gen_params.batch_count        = n;

        if (!gen_params.from_json_str(req.body)) {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "invalid params"), "application/json");
            return;
        }

        // B2.5: Dynamic Text Encoder Offloading
        if (gen_params.clip_on_cpu != ctx.ctx_params.clip_on_cpu) {
            LOG_INFO("Switching CLIP to %s for this generation...", gen_params.clip_on_cpu ? "CPU" : "GPU");
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            ctx.sd_ctx.reset();
            ctx.ctx_params.clip_on_cpu = gen_params.clip_on_cpu;
            sd_ctx_params_t sd_ctx_p = ctx.ctx_params.to_sd_ctx_params_t(false, false, false);
            ctx.sd_ctx.reset(new_sd_ctx(&sd_ctx_p));
        }

        bool save_image = j.value("save_image", false);

        std::string lora_dir = ctx.ctx_params.lora_model_dir;
        if (lora_dir.empty()) {
            lora_dir = (fs::path(ctx.svr_params.model_dir) / "lora").string();
        }

        if (!gen_params.process_and_check(IMG_GEN, lora_dir)) {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "invalid params"), "application/json");
            return;
        }

        LOG_DEBUG("%s\n", gen_params.to_string().c_str());

        sd_image_t init_image    = {(uint32_t)gen_params.width, (uint32_t)gen_params.height, 3, nullptr};
        
        if (j.contains("init_image") && j["init_image"].is_string()) {
            std::string b64_init = j["init_image"];
            if (b64_init.find("base64,") != std::string::npos) {
                b64_init = b64_init.substr(b64_init.find("base64,") + 7);
            }
            auto init_bytes = base64_decode(b64_init);
            if (!init_bytes.empty()) {
                int img_w = gen_params.width;
                int img_h = gen_params.height;
                init_image.data = load_image_from_memory(
                    reinterpret_cast<const char*>(init_bytes.data()),
                    (int)init_bytes.size(),
                    img_w, img_h,
                    gen_params.width, gen_params.height, 3);
                init_image.width = (uint32_t)img_w;
                init_image.height = (uint32_t)img_h;
                if (!init_image.data) {
                    LOG_ERROR("failed to load init_image from base64");
                } else {
                    LOG_INFO("loaded init_image for img2img: %dx%d", img_w, img_h);
                }
            }
        }

        sd_image_t control_image = {(uint32_t)gen_params.width, (uint32_t)gen_params.height, 3, nullptr};
        sd_image_t mask_image    = {(uint32_t)gen_params.width, (uint32_t)gen_params.height, 1, nullptr};

        if (j.contains("mask_image") && j["mask_image"].is_string()) {
            std::string b64_mask = j["mask_image"];
            if (b64_mask.find("base64,") != std::string::npos) {
                b64_mask = b64_mask.substr(b64_mask.find("base64,") + 7);
            }
            auto mask_bytes_v = base64_decode(b64_mask);
            if (!mask_bytes_v.empty()) {
                int mask_w = gen_params.width;
                int mask_h = gen_params.height;
                mask_image.data = load_image_from_memory(
                    reinterpret_cast<const char*>(mask_bytes_v.data()),
                    (int)mask_bytes_v.size(),
                    mask_w, mask_h,
                    gen_params.width, gen_params.height, 1);
                mask_image.width = (uint32_t)mask_w;
                mask_image.height = (uint32_t)mask_h;
                if (!mask_image.data) {
                    LOG_ERROR("failed to load mask_image from base64");
                } else {
                    LOG_INFO("loaded mask_image for inpainting: %dx%d", mask_w, mask_h);
                }
            }
        }

        if (mask_image.data == nullptr) {
            mask_image.data = (uint8_t*)malloc(mask_image.width * mask_image.height * mask_image.channel);
            memset(mask_image.data, 255, mask_image.width * mask_image.height * mask_image.channel);
        }
        if (control_image.data == nullptr) {
            control_image.data = (uint8_t*)calloc(1, control_image.width * control_image.height * control_image.channel);
        }

        std::vector<sd_image_t> pmid_images;

        sd_img_gen_params_t img_gen_params = {
            gen_params.lora_vec.data(),
            static_cast<uint32_t>(gen_params.lora_vec.size()),
            gen_params.prompt.c_str(),
            gen_params.negative_prompt.c_str(),
            gen_params.clip_skip,
            init_image,
            nullptr,
            0,
            gen_params.auto_resize_ref_image,
            gen_params.increase_ref_index,
            mask_image,
            gen_params.width,
            gen_params.height,
            gen_params.sample_params,
            gen_params.strength,
            gen_params.seed,
            gen_params.batch_count,
            control_image,
            gen_params.control_strength,
            {
                pmid_images.data(),
                (int)pmid_images.size(),
                gen_params.pm_id_embed_path.c_str(),
                gen_params.pm_style_strength,
            },  // pm_params
            ctx.ctx_params.vae_tiling_params,
            gen_params.easycache_params,
        };

        sd_image_t* results = nullptr;
        int num_results     = 0;

        {
            auto start_time = std::chrono::high_resolution_clock::now();
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            if (ctx.sd_ctx == nullptr) {
                res.status = 400;
                res.set_content(make_error_json("no_model", "no model loaded"), "application/json");
                return;
            }

            // Dynamic VRAM management for VAE
            float free_vram = get_free_vram_gb();
            // Estimate VAE VRAM: 1.6GB for 512x512, scales with area
            float estimated_vae_vram = (float(gen_params.width) * gen_params.height) / (512.0f * 512.0f) * 1.6f;
            
            LOG_INFO("VAE VRAM Check: Free=%.2fGB, Estimated Needed=%.2fGB", free_vram, estimated_vae_vram);
            
            bool request_vae_tiling = j.value("vae_tiling", false);
            if (request_vae_tiling) {
                LOG_INFO("VAE tiling enabled by request.");
                img_gen_params.vae_tiling_params.enabled = true;
            } else if (estimated_vae_vram > free_vram * 0.7f && !img_gen_params.vae_tiling_params.enabled) {
                LOG_WARN("High VRAM usage predicted. Automatically enabling VAE tiling.");
                img_gen_params.vae_tiling_params.enabled = true;
                set_progress_message("VRAM low: VAE tiling enabled");
            } else {
                img_gen_params.vae_tiling_params.enabled = false;
            }

            if (img_gen_params.vae_tiling_params.enabled) {
                // Use default tile size if not set
                if (img_gen_params.vae_tiling_params.tile_size_x <= 0) {
                    img_gen_params.vae_tiling_params.tile_size_x = 512;
                    img_gen_params.vae_tiling_params.tile_size_y = 512;
                }
            }

            {
                std::lock_guard<std::mutex> lock_prog(progress_state.mutex);
                int sampling_steps = gen_params.sample_params.sample_steps;
                if (gen_params.hires_fix) {
                    // One sampling pass for all batch + One hires pass for EACH image in batch
                    progress_state.total_steps = sampling_steps + (gen_params.hires_steps * gen_params.batch_count);
                } else {
                    progress_state.total_steps = sampling_steps;
                }
                progress_state.sampling_steps = sampling_steps;
                progress_state.base_step = 0;
                LOG_INFO("Total expected steps: %d", progress_state.total_steps);
            }

            set_progress_phase("Sampling...");
            log_vram_status("Sampling Start");
            float vram_before = get_current_process_vram_usage_gb();
            results     = generate_image(ctx.sd_ctx.get(), &img_gen_params);
            float vram_after = get_current_process_vram_usage_gb();
            float vram_delta = vram_after - vram_before;
            log_vram_status("Sampling End");
            LOG_INFO("Generation Sampling finished. Delta: %+.2f GB", vram_delta);
            num_results = gen_params.batch_count;

            // B0.1 & B0.2: Fail Loudly + Conservative Retry
            bool first_pass_success = (results != nullptr);
            if (first_pass_success) {
                first_pass_success = false;
                for (int i = 0; i < num_results; i++) {
                    if (results[i].data != nullptr) {
                        // B0.1: Check for "blank" (all zeros) output which indicates a silent failure in SD
                        bool all_zeros = true;
                        size_t total_bytes = (size_t)results[i].width * results[i].height * results[i].channel;
                        for (size_t b = 0; b < total_bytes; b++) {
                            if (results[i].data[b] != 0) {
                                all_zeros = false;
                                break;
                            }
                        }
                        if (!all_zeros) {
                            first_pass_success = true;
                            break;
                        }
                    }
                }
            }

            if (!first_pass_success) {
                LOG_WARN("First pass generation failed (empty results). Retrying with conservative settings (VAE tiling)...");
                set_progress_message("Retrying with VAE tiling...");

                free_sd_images(results, num_results);

                img_gen_params.vae_tiling_params.enabled = true;
                if (img_gen_params.vae_tiling_params.tile_size_x <= 0) {
                    img_gen_params.vae_tiling_params.tile_size_x = 512;
                    img_gen_params.vae_tiling_params.tile_size_y = 512;
                }

                results = generate_image(ctx.sd_ctx.get(), &img_gen_params);

                first_pass_success = (results != nullptr);
                if (first_pass_success) {
                    first_pass_success = false;
                    for (int i = 0; i < num_results; i++) {
                        if (results[i].data != nullptr) {
                            bool all_zeros = true;
                            size_t total_bytes = (size_t)results[i].width * results[i].height * results[i].channel;
                            for (size_t b = 0; b < total_bytes; b++) {
                                if (results[i].data[b] != 0) {
                                    all_zeros = false;
                                    break;
                                }
                            }
                            if (!all_zeros) {
                                first_pass_success = true;
                                break;
                            }
                        }
                    }
                }

                if (!first_pass_success) {
                    LOG_ERROR("Generation failed after retry.");
                    res.status = 500;
                    res.set_content(make_error_json("generation_failed", "Stable diffusion generation failed even after retry with conservative settings."), "application/json");
                    free_sd_images(results, num_results);
                    return;
                }
            }

            {
                std::lock_guard<std::mutex> lock_prog(progress_state.mutex);
                progress_state.base_step = gen_params.sample_params.sample_steps;
            }

            LOG_INFO("Generation done, num_results: %d, hires_fix: %s", num_results, gen_params.hires_fix ? "true" : "false");

            if (gen_params.hires_fix && num_results > 0) {
                LOG_INFO("Performing highres-fix for %d images...", num_results);
                
                if (!gen_params.hires_upscale_model.empty() && 
                    ctx.current_upscale_model_path != gen_params.hires_upscale_model) {
                    
                    fs::path upscaler_path = fs::path(ctx.svr_params.model_dir) / gen_params.hires_upscale_model;
                    LOG_INFO("Attempting to load upscaler: %s", upscaler_path.string().c_str());
                    if (fs::exists(upscaler_path)) {
                        ctx.upscaler_ctx.reset(new_upscaler_ctx(upscaler_path.string().c_str(), 
                                                        ctx.ctx_params.offload_params_to_cpu,
                                                        false, ctx.ctx_params.n_threads, 512));
                        ctx.current_upscale_model_path = gen_params.hires_upscale_model;
                        LOG_INFO("Upscaler loaded successfully.");
                    } else {
                        LOG_WARN("Upscaler model not found: %s", upscaler_path.string().c_str());
                    }
                }

                sd_image_t* hires_results = (sd_image_t*)malloc(sizeof(sd_image_t) * num_results);
                
                for (int i = 0; i < num_results; i++) {
                    sd_image_t base_img = results[i];
                    sd_image_t upscaled_img = {0};

                    if (ctx.upscaler_ctx) {
                        LOG_INFO("Upscaling for highres-fix (factor %.2f)...", gen_params.hires_upscale_factor);
                        upscaled_img = upscale(ctx.upscaler_ctx.get(), base_img, (uint32_t)gen_params.hires_upscale_factor);
                    } else {
                        LOG_INFO("Resizing for highres-fix (factor %.2f) using simple resize...", gen_params.hires_upscale_factor);
                        upscaled_img.width = (uint32_t)(base_img.width * gen_params.hires_upscale_factor);
                        upscaled_img.height = (uint32_t)(base_img.height * gen_params.hires_upscale_factor);
                        upscaled_img.channel = base_img.channel;
                        upscaled_img.data = (uint8_t*)malloc(upscaled_img.width * upscaled_img.height * upscaled_img.channel);
                        stbir_resize_uint8(base_img.data, (int)base_img.width, (int)base_img.height, 0,
                                            upscaled_img.data, (int)upscaled_img.width, (int)upscaled_img.height, 0,
                                            (int)upscaled_img.channel);
                    }

                    set_progress_phase("Highres-fix Pass...");
                    
                    uint32_t target_width = (uint32_t)(base_img.width * gen_params.hires_upscale_factor);
                    uint32_t target_height = (uint32_t)(base_img.height * gen_params.hires_upscale_factor);

                    if (upscaled_img.width != target_width || upscaled_img.height != target_height) {
                        LOG_INFO("Resizing upscaled image to target size: %dx%d", target_width, target_height);
                        sd_image_t resized_img = { target_width, target_height, upscaled_img.channel, nullptr };
                        resized_img.data = (uint8_t*)malloc(target_width * target_height * resized_img.channel);
                        stbir_resize_uint8(upscaled_img.data, (int)upscaled_img.width, (int)upscaled_img.height, 0,
                                            resized_img.data, (int)target_width, (int)target_height, 0,
                                            (int)resized_img.channel);
                        free(upscaled_img.data);
                        upscaled_img = resized_img;
                    }

                    sd_img_gen_params_t hires_params = img_gen_params;
                    hires_params.init_image = upscaled_img;
                    hires_params.width = (int)upscaled_img.width;
                    hires_params.height = (int)upscaled_img.height;
                    hires_params.strength = gen_params.hires_denoising_strength;
                    hires_params.sample_params.sample_steps = gen_params.hires_steps;
                    hires_params.batch_count = 1;

                    hires_params.mask_image.width = target_width;
                    hires_params.mask_image.height = target_height;
                    hires_params.mask_image.data = (uint8_t*)malloc(target_width * target_height * hires_params.mask_image.channel);
                    if (img_gen_params.mask_image.data) {
                        stbir_resize_uint8(img_gen_params.mask_image.data, (int)img_gen_params.mask_image.width, (int)img_gen_params.mask_image.height, 0,
                                            hires_params.mask_image.data, (int)target_width, (int)target_height, 0,
                                            (int)hires_params.mask_image.channel);
                    } else {
                        memset(hires_params.mask_image.data, 255, target_width * target_height * hires_params.mask_image.channel);
                    }

                    hires_params.control_image.width = target_width;
                    hires_params.control_image.height = target_height;
                    hires_params.control_image.data = (uint8_t*)calloc(1, target_width * target_height * hires_params.control_image.channel);
                    if (img_gen_params.control_image.data) {
                        stbir_resize_uint8(img_gen_params.control_image.data, (int)img_gen_params.control_image.width, (int)img_gen_params.control_image.height, 0,
                                            hires_params.control_image.data, (int)target_width, (int)target_height, 0,
                                            (int)hires_params.control_image.channel);
                    }

                    {
                        std::lock_guard<std::mutex> lock_prog(progress_state.mutex);
                        progress_state.sampling_steps = gen_params.hires_steps;
                    }

                    sd_image_t* second_pass_result = generate_image(ctx.sd_ctx.get(), &hires_params);
                    
                    if (second_pass_result && second_pass_result[0].data) {
                        hires_results[i] = second_pass_result[0];
                        free(second_pass_result); // Free the container, not the data
                    } else {
                        hires_results[i] = upscaled_img; // Fallback to upscaled if second pass fails
                    }

                    {
                        std::lock_guard<std::mutex> lock_prog(progress_state.mutex);
                        progress_state.base_step += gen_params.hires_steps;
                    }

                    stbi_image_free(base_img.data);
                    if (upscaled_img.data && upscaled_img.data != hires_results[i].data) {
                        free(upscaled_img.data);
                    }
                    free(hires_params.mask_image.data);
                    free(hires_params.control_image.data);
                }
                
                free(results);
                results = hires_results;
            }
            auto end_time = std::chrono::high_resolution_clock::now();
            total_generation_time = std::chrono::duration<double>(end_time - start_time).count();
            
            out["vram_peak_gb"] = vram_after;
            out["vram_delta_gb"] = vram_delta;
        }
        set_progress_phase("VAE Decoding...");
        
        bool no_base64 = j.value("no_base64", false);
        
        // If batch > 1, force JSON to keep it simple unless we implement multipart
        // Note: We now always return JSON with URLs as primary method.

        int successful_generations = 0;
        for (int i = 0; i < num_results; i++) {
            if (results[i].data == nullptr) {
                continue;
            }
            successful_generations++;
            auto image_bytes = write_image_to_vector(output_format == "jpeg" ? ImageFormat::JPEG : ImageFormat::PNG,
                                                        results[i].data,
                                                        (int)results[i].width,
                                                        (int)results[i].height,
                                                        (int)results[i].channel,
                                                        output_compression);
            if (image_bytes.empty()) {
                LOG_ERROR("write image to mem failed");
                continue;
            }

            mysti::json item;
            item["seed"] = gen_params.seed;

            // Always save to disk (temp or permanent) to serve via URL
            try {
                std::string final_output_dir = ctx.svr_params.output_dir;
                std::string url_prefix = "/outputs/";

                if (!save_image) {
                    final_output_dir = (fs::path(ctx.svr_params.output_dir) / "temp").string();
                    url_prefix = "/outputs/temp/";
                    if (!fs::exists(final_output_dir)) {
                        fs::create_directories(final_output_dir);
                    }
                } else {
                    if (!fs::exists(final_output_dir)) {
                        fs::create_directories(final_output_dir);
                    }
                }

                auto timestamp = std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
                std::string base_filename = "img-" + std::to_string(timestamp) + "-" + std::to_string(gen_params.seed);
                std::string img_filename = (fs::path(final_output_dir) / (base_filename + ".png")).string();
                
                std::ofstream file(img_filename, std::ios::binary);
                file.write(reinterpret_cast<const char*>(image_bytes.data()), image_bytes.size());
                LOG_INFO("saved image to %s", img_filename.c_str());

                // Only save metadata txt if saving permanently
                if (save_image) {
                    std::string txt_filename = (fs::path(final_output_dir) / (base_filename + ".txt")).string();
                    std::string params_txt = get_image_params(ctx.ctx_params, gen_params, gen_params.seed, total_generation_time);
                    std::ofstream txt_file(txt_filename);
                    txt_file << params_txt;
                }

                // Add file info to response
                item["url"] = url_prefix + base_filename + ".png";
                item["name"] = base_filename + ".png";

            } catch (const std::exception& e) {
                LOG_ERROR("failed to save image or metadata: %s", e.what());
            }

            out["data"].push_back(item);
        }

        if (successful_generations == 0) {
            LOG_ERROR("All generated images were null (VAE decoding pass).");
            res.status = 500;
            res.set_content(make_error_json("generation_failed", "Stable diffusion returned only null images. This can happen if the VAE failed or the model is corrupted."), "application/json");
            free_sd_images(results, num_results);
            return;
        }

        out["generation_time"] = total_generation_time;
        res.set_content(out.dump(), "application/json");
        res.status = 200;

        free_sd_images(results, num_results);

        if (init_image.data) {
            stbi_image_free(init_image.data);
        }
        if (mask_image.data) {
            free(mask_image.data);
        }
        if (control_image.data) {
            free(control_image.data);
        }

    } catch (const std::exception& e) {
        res.status = 500;
        res.set_content(make_error_json("server_error", e.what()), "application/json");
    }
}

void handle_edit_image(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    reset_progress();
    try {
        if (!req.is_multipart_form_data()) {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "Content-Type must be multipart/form-data"), "application/json");
            return;
        }

        std::string prompt = req.form.get_field("prompt");
        if (prompt.empty()) {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "prompt required"), "application/json");
            return;
        }

        std::string extra_args_str;
        if (req.form.has_field("extra_args")) {
            extra_args_str = req.form.get_field("extra_args");
        }

        size_t image_count = req.form.get_file_count("image[]");
        if (image_count == 0) {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "at least one image[] required"), "application/json");
            return;
        }

        std::vector<std::vector<uint8_t>> images_bytes;
        for (size_t i = 0; i < image_count; i++) {
            auto file = req.form.get_file("image[]", i);
            images_bytes.emplace_back(file.content.begin(), file.content.end());
        }

        std::vector<uint8_t> mask_bytes;
        if (req.form.has_field("mask")) {
            auto file = req.form.get_file("mask");
            mask_bytes.assign(file.content.begin(), file.content.end());
        }

        int n = 1;
        if (req.form.has_field("n")) {
            try {
                n = std::stoi(req.form.get_field("n"));
            } catch (...) {
            }
        }
        n = std::clamp(n, 1, 8);

        std::string size = req.form.get_field("size");
        int width = 512, height = 512;
        if (!size.empty()) {
            auto pos = size.find('x');
            if (pos != std::string::npos) {
                try {
                    width  = std::stoi(size.substr(0, pos));
                    height = std::stoi(size.substr(pos + 1));
                } catch (...) {
                }
            }
        }

        std::string output_format = "png";
        if (req.form.has_field("output_format"))
            output_format = req.form.get_field("output_format");
        if (output_format != "png" && output_format != "jpeg") {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "invalid output_format, must be one of [png, jpeg]"), "application/json");
            return;
        }

        std::string output_compression_str = req.form.get_field("output_compression");
        int output_compression             = 100;
        try {
            output_compression = std::stoi(output_compression_str);
        } catch (...) {
        }
        if (output_compression > 100) {
            output_compression = 100;
        }
        if (output_compression < 0) {
            output_compression = 0;
        }

        SDGenerationParams gen_params = ctx.default_gen_params;
        gen_params.prompt             = prompt;
        gen_params.width              = width;
        gen_params.height             = height;
        gen_params.batch_count        = n;

        if (!extra_args_str.empty() && !gen_params.from_json_str(extra_args_str)) {
            res.status = 400;
            res.set_content(R"({\"error\":\"invalid extra_args\"})", "application/json");
            return;
        }

        std::string lora_dir = ctx.ctx_params.lora_model_dir;
        if (lora_dir.empty()) {
            lora_dir = (fs::path(ctx.svr_params.model_dir) / "lora").string();
        }

        if (!gen_params.process_and_check(IMG_GEN, lora_dir)) {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "invalid params"), "application/json");
            return;
        }

        LOG_DEBUG("%s\n", gen_params.to_string().c_str());

        sd_image_t init_image    = {(uint32_t)gen_params.width, (uint32_t)gen_params.height, 3, nullptr};
        sd_image_t control_image = {(uint32_t)gen_params.width, (uint32_t)gen_params.height, 3, nullptr};
        std::vector<sd_image_t> pmid_images;

        std::vector<sd_image_t> ref_images;
        ref_images.reserve(images_bytes.size());
        for (auto& bytes : images_bytes) {
            int img_w           = width;
            int img_h           = height;
            uint8_t* raw_pixels = load_image_from_memory(
                reinterpret_cast<const char*>(bytes.data()),
                (int)bytes.size(),
                img_w, img_h,
                width, height, 3);

            if (!raw_pixels) {
                continue;
            }

            sd_image_t img{(uint32_t)img_w, (uint32_t)img_h, 3, raw_pixels};
            ref_images.push_back(img);
        }

        sd_image_t mask_image = {0};
        if (!mask_bytes.empty()) {
            int mask_w        = width;
            int mask_h        = height;
            uint8_t* mask_raw = load_image_from_memory(
                reinterpret_cast<const char*>(mask_bytes.data()),
                (int)mask_bytes.size(),
                mask_w, mask_h,
                width, height, 1);
            mask_image = {(uint32_t)mask_w, (uint32_t)mask_h, 1, mask_raw};
        } else {
            mask_image.width   = (uint32_t)width;
            mask_image.height  = (uint32_t)height;
            mask_image.channel = 1;
            mask_image.data    = nullptr;
        }

        sd_img_gen_params_t img_gen_params = {
            gen_params.lora_vec.data(),
            static_cast<uint32_t>(gen_params.lora_vec.size()),
            gen_params.prompt.c_str(),
            gen_params.negative_prompt.c_str(),
            gen_params.clip_skip,
            init_image,
            ref_images.data(),
            (int)ref_images.size(),
            gen_params.auto_resize_ref_image,
            gen_params.increase_ref_index,
            mask_image,
            gen_params.width,
            gen_params.height,
            gen_params.sample_params,
            gen_params.strength,
            gen_params.seed,
            gen_params.batch_count,
            control_image,
            gen_params.control_strength,
            {
                pmid_images.data(),
                (int)pmid_images.size(),
                gen_params.pm_id_embed_path.c_str(),
                gen_params.pm_style_strength,
            },  // pm_params
            ctx.ctx_params.vae_tiling_params,
            gen_params.easycache_params,
        };

        sd_image_t* results = nullptr;
        int num_results     = 0;

        {
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            if (ctx.sd_ctx == nullptr) {
                res.status = 400;
                res.set_content(make_error_json("no_model", "no model loaded"), "application/json");
                return;
            }

            {
                std::lock_guard<std::mutex> lock_prog(progress_state.mutex);
                progress_state.total_steps = gen_params.sample_params.sample_steps;
                progress_state.sampling_steps = progress_state.total_steps;
                progress_state.base_step = 0;
            }

        set_progress_phase("Sampling...");
        log_vram_status("Edit Start");
        float vram_before = get_current_process_vram_usage_gb();
        results     = generate_image(ctx.sd_ctx.get(), &img_gen_params);
        float vram_after = get_current_process_vram_usage_gb();
        float vram_delta = vram_after - vram_before;
        log_vram_status("Edit End");
        LOG_INFO("Edit Sampling finished. Delta: %+.2f GB", vram_delta);
        num_results = gen_params.batch_count;

        // B0.1 & B0.2: Fail Loudly + Conservative Retry
        bool success = (results != nullptr);
        if (success) {
            success = false;
            for (int i = 0; i < num_results; i++) {
                if (results[i].data != nullptr) {
                    bool all_zeros = true;
                    size_t total_bytes = (size_t)results[i].width * results[i].height * results[i].channel;
                    for (size_t b = 0; b < total_bytes; b++) {
                        if (results[i].data[b] != 0) {
                            all_zeros = false;
                            break;
                        }
                    }
                    if (!all_zeros) {
                        success = true;
                        break;
                    }
                }
            }
        }

        if (!success) {
            LOG_WARN("Edit generation failed (empty results). Retrying with conservative settings (VAE tiling)...");
            set_progress_message("Retrying with VAE tiling...");
            
            free_sd_images(results, num_results);
            
            img_gen_params.vae_tiling_params.enabled = true;
            if (img_gen_params.vae_tiling_params.tile_size_x <= 0) {
                img_gen_params.vae_tiling_params.tile_size_x = 512;
                img_gen_params.vae_tiling_params.tile_size_y = 512;
            }
            
            results = generate_image(ctx.sd_ctx.get(), &img_gen_params);
            
            success = (results != nullptr);
            if (success) {
                success = false;
                for (int i = 0; i < num_results; i++) {
                    if (results[i].data != nullptr) {
                        bool all_zeros = true;
                        size_t total_bytes = (size_t)results[i].width * results[i].height * results[i].channel;
                        for (size_t b = 0; b < total_bytes; b++) {
                            if (results[i].data[b] != 0) {
                                all_zeros = false;
                                break;
                            }
                        }
                        if (!all_zeros) {
                            success = true;
                            break;
                        }
                    }
                }
            }
            
            if (!success) {
                LOG_ERROR("Edit generation failed after retry.");
                res.status = 500;
                res.set_content(make_error_json("generation_failed", "Stable diffusion generation failed even after retry with conservative settings."), "application/json");
                free_sd_images(results, num_results);
                
                // Still need to free other resources
                if (init_image.data) stbi_image_free(init_image.data);
                if (mask_image.data) stbi_image_free(mask_image.data);
                for (auto ref_image : ref_images) stbi_image_free(ref_image.data);
                return;
            }
        }
    }

    set_progress_phase("VAE Decoding...");
    mysti::json out;
    out["created"]       = iso_timestamp_now();
    out["data"]          = mysti::json::array();
    out["output_format"] = output_format;

    int successful_generations = 0;
    for (int i = 0; i < num_results; i++) {
        if (results[i].data == nullptr)
            continue;
        successful_generations++;
        auto image_bytes = write_image_to_vector(output_format == "jpeg" ? ImageFormat::JPEG : ImageFormat::PNG,
                                                    results[i].data,
                                                    (int)results[i].width,
                                                    (int)results[i].height,
                                                    (int)results[i].channel,
                                                    output_compression);
        
        mysti::json item;
        item["seed"] = gen_params.seed;

        // Always save edits to temp to serve via URL (Edits are usually transient unless saved by user)
        try {
            std::string temp_dir = (fs::path(ctx.svr_params.output_dir) / "temp").string();
            if (!fs::exists(temp_dir)) {
                fs::create_directories(temp_dir);
            }

            auto timestamp = std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
            std::string base_filename = "edit-" + std::to_string(timestamp) + "-" + std::to_string(gen_params.seed);
            std::string img_filename = (fs::path(temp_dir) / (base_filename + ".png")).string();
            
            std::ofstream file(img_filename, std::ios::binary);
            file.write(reinterpret_cast<const char*>(image_bytes.data()), image_bytes.size());
            
            item["url"] = "/outputs/temp/" + base_filename + ".png";
            item["name"] = base_filename + ".png";
        } catch (...) {
            LOG_ERROR("failed to save edit image to temp");
        }

        out["data"].push_back(item);
    }

    if (successful_generations == 0) {
        LOG_ERROR("All generated images were null (VAE decoding pass).");
        res.status = 500;
        res.set_content(make_error_json("generation_failed", "Stable diffusion returned only null images."), "application/json");
        free_sd_images(results, num_results);
        if (init_image.data) stbi_image_free(init_image.data);
        if (mask_image.data) stbi_image_free(mask_image.data);
        for (auto ref_image : ref_images) stbi_image_free(ref_image.data);
        return;
    }

    res.set_content(out.dump(), "application/json");
    res.status = 200;

    free_sd_images(results, num_results);

    if (init_image.data) {
        stbi_image_free(init_image.data);
    }
    if (mask_image.data) {
        stbi_image_free(mask_image.data);
    }
    for (auto ref_image : ref_images) {
        stbi_image_free(ref_image.data);
    }
    } catch (const std::exception& e) {
        res.status = 500;
        res.set_content(make_error_json("server_error", e.what()), "application/json");
    }
}