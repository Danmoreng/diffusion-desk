#include "api_endpoints.hpp"
#include "api_utils.hpp"
#include "server_state.hpp"
#include "model_loader.hpp"
#include <sstream>
#include <iomanip>
#include <fstream>

using json = nlohmann::json;
namespace fs = std::filesystem;

// SDSvrParams Implementation

ArgOptions SDSvrParams::get_options() {
    ArgOptions options;

    options.string_options = {
        {" -l",
            "--listen-ip",
            "server listen ip (default: 127.0.0.1)",
            &listen_ip},
        {
            "",
            "--model-dir",
            "directory to scan for models (default: ./models)",
            &model_dir},
        {
            "",
            "--output-dir",
            "directory to save generated images (default: ./outputs)",
            &output_dir}};

    options.int_options = {
        {
            "",
            "--listen-port",
            "server listen port (default: 1234)",
            &listen_port},
    };

    options.bool_options = {
        {" -v",
            "--verbose",
            "print extra info",
            true, &verbose},
        {
            "",
            "--color",
            "colors the logging tags according to level",
            true, &color},
    };

    auto on_help_arg = [&](int argc, const char** argv, int index) {
        normal_exit = true;
        return -1;
    };

    options.manual_options = {
        {" -h",
            "--help",
            "show this help message and exit",
            on_help_arg},
    };
    return options;
};

bool SDSvrParams::process_and_check() {
    if (listen_ip.empty()) {
        LOG_ERROR("error: the following arguments are required: listen_ip");
        return false;
    }

    if (listen_port < 0 || listen_port > 65535) {
        LOG_ERROR("error: listen_port should be in the range [0, 65535]");
        return false;
    }
    return true;
}

std::string SDSvrParams::to_string() const {
    std::ostringstream oss;
    oss << "SDSvrParams {\n"
        << "  listen_ip: " << listen_ip << ",\n"
        << "  listen_port: \"" << listen_port << "\",\n"
        << "}";
    return oss.str();
}

// Handlers Placeholders - To be filled from main.cpp logic

void handle_health(const httplib::Request&, httplib::Response& res) {
    res.set_content(R"({\"ok\":true,\"service\":\"sd-cpp-http\"})", "application/json");
}

void handle_get_config(const httplib::Request&, httplib::Response& res, ServerContext& ctx) {
    json c;
    c["output_dir"] = ctx.svr_params.output_dir;
    c["model_dir"] = ctx.svr_params.model_dir;
    res.set_content(c.dump(), "application/json");
}

void handle_post_config(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    try {
        json body = json::parse(req.body);
        bool updated = false;
        if (body.contains("output_dir")) {
            ctx.svr_params.output_dir = body["output_dir"];
            LOG_INFO("Config updated: output_dir = %s", ctx.svr_params.output_dir.c_str());
            updated = true;
        }
        if (body.contains("model_dir")) {
            ctx.svr_params.model_dir = body["model_dir"];
            LOG_INFO("Config updated: model_dir = %s", ctx.svr_params.model_dir.c_str());
            updated = true;
        }
        res.set_content(R"({\"status\":\"success\"})", "application/json");
    } catch (const std::exception& e) {
        res.status = 400;
        res.set_content(R"({\"error\":\"invalid json\"})", "application/json");
    }
}

void handle_get_progress(const httplib::Request&, httplib::Response& res) {
    json r;
    {
        std::lock_guard<std::mutex> lock(progress_state.mutex);
        r["step"] = progress_state.step;
        r["steps"] = progress_state.steps;
        r["time"] = progress_state.time;
    }
    res.set_content(r.dump(), "application/json");
}

void handle_stream_progress(const httplib::Request&, httplib::Response& res) {
    res.set_header("Cache-Control", "no-cache");
    res.set_header("Connection", "keep-alive");
    res.set_header("X-Accel-Buffering", "no"); // Disable proxy buffering

    res.set_chunked_content_provider("text/event-stream", [&](size_t offset, httplib::DataSink &sink) {
        uint64_t last_version = 0;
        int step = 0;
        int steps = 0;
        float time = 0;
        std::string phase = "";

        // Send initial state or at least a comment to open the stream
        {
            std::lock_guard<std::mutex> lock(progress_state.mutex);
            last_version = progress_state.version;
            step = progress_state.step;
            steps = progress_state.steps;
            time = progress_state.time;
            phase = progress_state.phase;
        }
        
        json initial_j;
        initial_j["step"] = step;
        initial_j["steps"] = steps;
        initial_j["time"] = time;
        initial_j["phase"] = phase;
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
            last_version = progress_state.version;
            lock.unlock();

            json j;
            j["step"] = step;
            j["steps"] = steps;
            j["time"] = time;
            j["phase"] = phase;
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
    json r;
    r["data"] = json::array();
    
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
                        json model;
                        // Use relative path from model_dir as ID for easy loading
                        std::string rel_path = fs::relative(entry.path(), ctx.svr_params.model_dir).string();
                        std::replace(rel_path.begin(), rel_path.end(), '\\', '/');

                        model["id"] = rel_path;
                        model["name"] = entry.path().filename().string();
                        model["type"] = sub_dir;
                        model["object"] = "model";
                        model["owned_by"] = "local";
                        model["active"] = (model["id"] == current_model_name || rel_path == ctx.current_upscale_model_path);
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
        scan_dir("esrgan");
        
        // Also scan root of model_dir for convenience
        if (fs::exists(ctx.svr_params.model_dir) && fs::is_directory(ctx.svr_params.model_dir)) {
            for (const auto& entry : fs::directory_iterator(ctx.svr_params.model_dir)) {
                if (entry.is_regular_file()) {
                    auto ext = entry.path().extension().string();
                    if (ext == ".gguf" || ext == ".safetensors" || ext == ".ckpt") {
                        json model;
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
        json body = json::parse(req.body);
        if (!body.contains("model_id")) {
            res.status = 400;
            res.set_content(R"({\"error\":\"model_id (relative path) required\"})", "application/json");
            return;
        }
        std::string model_id = body["model_id"];
        fs::path model_path = fs::path(ctx.svr_params.model_dir) / model_id;
        
        if (!fs::exists(model_path)) {
            res.status = 404;
            res.set_content(R"({\"error\":\"model file not found at )" + model_path.string() + R"("})", "application/json");
            return;
        }

        LOG_INFO("Loading new model: %s", model_path.string().c_str());

        {
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            
            // Free old context
            if (ctx.sd_ctx) {
                free_sd_ctx(ctx.sd_ctx);
                ctx.sd_ctx = nullptr;
            }

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
            ctx.sd_ctx = new_sd_ctx(&sd_ctx_p);

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
        json body = json::parse(req.body);
        if (!body.contains("model_id")) {
            res.status = 400;
            res.set_content(R"({\"error\":\"model_id required\"})", "application/json");
            return;
        }
        std::string model_id = body["model_id"];
        fs::path model_path = fs::path(ctx.svr_params.model_dir) / model_id;
        
        if (!fs::exists(model_path)) {
            res.status = 404;
            res.set_content(R"({\"error\":\"upscale model not found\"})", "application/json");
            return;
        }

        LOG_INFO("Loading upscale model: %s", model_path.string().c_str());

        {
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            if (ctx.upscaler_ctx) {
                free_upscaler_ctx(ctx.upscaler_ctx);
                ctx.upscaler_ctx = nullptr;
            }

            ctx.upscaler_ctx = new_upscaler_ctx(model_path.string().c_str(), 
                                            ctx.ctx_params.offload_params_to_cpu,
                                            false, // direct
                                            ctx.ctx_params.n_threads,
                                            512); // tile_size

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
        json body = json::parse(req.body);
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
                res.set_content(R"({\"error\":\"image not found\"})", "application/json");
                return;
            }
            std::ifstream ifs(img_path, std::ios::binary);
            image_data = std::string((std::istreambuf_iterator<char>(ifs)), std::istreambuf_iterator<char>());
        } else {
            res.status = 400;
            res.set_content(R"({\"error\":\"image (base64) or image_name required\"})", "application/json");
            return;
        }

        uint32_t upscale_factor = body.value("upscale_factor", 0);

        sd_image_t input_image = {0, 0, 3, nullptr};
        std::vector<uint8_t> decoded_bytes;
        
        if (!image_name.empty()) {
            int w, h;
            input_image.data = load_image_from_memory(image_data.data(), image_data.size(), w, h, 0, 0, 3);
            input_image.width = w;
            input_image.height = h;
        } else {
            decoded_bytes = base64_decode(image_data);
            int w, h;
            input_image.data = load_image_from_memory((const char*)decoded_bytes.data(), decoded_bytes.size(), w, h, 0, 0, 3);
            input_image.width = w;
            input_image.height = h;
        }

        if (!input_image.data) {
            res.status = 400;
            res.set_content(R"({\"error\":\"failed to decode image\"})", "application/json");
            return;
        }

        sd_image_t upscaled_image = {0};
        {
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            if (!ctx.upscaler_ctx) {
                stbi_image_free(input_image.data);
                res.status = 400;
                res.set_content(R"({\"error\":\"no upscale model loaded\"})", "application/json");
                return;
            }
            
            if (upscale_factor == 0) {
                upscale_factor = get_upscale_factor(ctx.upscaler_ctx);
            }

            LOG_INFO("Upscaling image: %dx%d -> factor %d", input_image.width, input_image.height, upscale_factor);
            upscaled_image = upscale(ctx.upscaler_ctx, input_image, upscale_factor);
        }

        stbi_image_free(input_image.data);

        if (!upscaled_image.data) {
            res.status = 500;
            res.set_content(R"({\"error\":\"upscaling failed\"})", "application/json");
            return;
        }

        auto image_bytes = write_image_to_vector(ImageFormat::PNG,
                                                    upscaled_image.data,
                                                    upscaled_image.width,
                                                    upscaled_image.height,
                                                    upscaled_image.channel);
        
        free(upscaled_image.data);

        if (image_bytes.empty()) {
            res.status = 500;
            res.set_content(R"({\"error\":\"failed to encode upscaled image\"})", "application/json");
            return;
        }

        std::string b64 = base64_encode(image_bytes);
        json out;
        out["width"] = upscaled_image.width;
        out["height"] = upscaled_image.height;
        out["b64_json"] = b64;

        if (body.value("save_image", true)) {
            auto timestamp = std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
            std::string out_name = "upscale-" + std::to_string(timestamp) + ".png";
            fs::path out_path = fs::path(ctx.svr_params.output_dir) / out_name;
            std::ofstream ofs(out_path, std::ios::binary);
            ofs.write((const char*)image_bytes.data(), image_bytes.size());
            out["url"] = "/outputs/" + out_name;
            out["name"] = out_name;
            LOG_INFO("Saved upscaled image to %s", out_path.string().c_str());
        }

        res.set_content(out.dump(), "application/json");

    } catch (const std::exception& e) {
        LOG_ERROR("error during upscaling: %s", e.what());
        res.status = 500;
        res.set_content(R"({\"error\":\")" + std::string(e.what()) + R"("})", "application/json");
    }
}

void handle_get_history(const httplib::Request&, httplib::Response& res, ServerContext& ctx) {
    const std::string output_dir = ctx.svr_params.output_dir;
    json image_list = json::array();
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
                json item;
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
                            item["params"] = json::parse(json_file);
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
        res.set_content(R"({\"error\":\"failed to list image history\"})", "application/json");
        return;
    }
    res.set_content(image_list.dump(), "application/json");
}

void handle_generate_image(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    reset_progress();
    try {
        if (req.body.empty()) {
            res.status = 400;
            res.set_content(R"({\"error\":\"empty body\"})", "application/json");
            return;
        }

        json j                    = json::parse(req.body);
        std::string prompt        = j.value("prompt", "");
        int n                     = std::max(1, j.value("n", 1));
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
            res.set_content(R"({\"error\":\"prompt required\"})", "application/json");
            return;
        }

        if (output_format != "png" && output_format != "jpeg") {
            res.status = 400;
            res.set_content(R"({\"error\":\"invalid output_format, must be one of [png, jpeg]\"})", "application/json");
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

        json out;
        out["created"]       = iso_timestamp_now();
        out["data"]          = json::array();
        out["output_format"] = output_format;

        SDGenerationParams gen_params = ctx.default_gen_params;
        gen_params.prompt             = prompt;
        gen_params.width              = width;
        gen_params.height             = height;
        gen_params.batch_count        = n;

        if (!gen_params.from_json_str(req.body)) {
            res.status = 400;
            res.set_content(R"({\"error\":\"invalid params\"})", "application/json");
            return;
        }

        bool save_image = j.value("save_image", false);

        if (!gen_params.process_and_check(IMG_GEN, "")) {
            res.status = 400;
            res.set_content(R"({\"error\":\"invalid params\"})", "application/json");
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
                    init_bytes.size(),
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
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            if (ctx.sd_ctx == nullptr) {
                res.status = 400;
                res.set_content(R"({\"error\":\"no model loaded\"})", "application/json");
                return;
            }
            set_progress_phase("Sampling...");
            results     = generate_image(ctx.sd_ctx, &img_gen_params);
            num_results = gen_params.batch_count;

            LOG_INFO("Generation done, num_results: %d, hires_fix: %s", num_results, gen_params.hires_fix ? "true" : "false");

            if (gen_params.hires_fix && num_results > 0) {
                LOG_INFO("Performing highres-fix for %d images...", num_results);
                
                if (!gen_params.hires_upscale_model.empty() && 
                    ctx.current_upscale_model_path != gen_params.hires_upscale_model) {
                    
                    fs::path upscaler_path = fs::path(ctx.svr_params.model_dir) / gen_params.hires_upscale_model;
                    LOG_INFO("Attempting to load upscaler: %s", upscaler_path.string().c_str());
                    if (fs::exists(upscaler_path)) {
                        if (ctx.upscaler_ctx) free_upscaler_ctx(ctx.upscaler_ctx);
                        ctx.upscaler_ctx = new_upscaler_ctx(upscaler_path.string().c_str(), 
                                                        ctx.ctx_params.offload_params_to_cpu,
                                                        false, ctx.ctx_params.n_threads, 512);
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
                        upscaled_img = upscale(ctx.upscaler_ctx, base_img, (uint32_t)gen_params.hires_upscale_factor);
                    } else {
                        LOG_INFO("Resizing for highres-fix (factor %.2f) using simple resize...", gen_params.hires_upscale_factor);
                        upscaled_img.width = (uint32_t)(base_img.width * gen_params.hires_upscale_factor);
                        upscaled_img.height = (uint32_t)(base_img.height * gen_params.hires_upscale_factor);
                        upscaled_img.channel = base_img.channel;
                        upscaled_img.data = (uint8_t*)malloc(upscaled_img.width * upscaled_img.height * upscaled_img.channel);
                        stbir_resize_uint8(base_img.data, base_img.width, base_img.height, 0,
                                            upscaled_img.data, upscaled_img.width, upscaled_img.height, 0,
                                            upscaled_img.channel);
                    }

                    set_progress_phase("Highres-fix Pass...");
                    
                    uint32_t target_width = (uint32_t)(base_img.width * gen_params.hires_upscale_factor);
                    uint32_t target_height = (uint32_t)(base_img.height * gen_params.hires_upscale_factor);

                    if (upscaled_img.width != target_width || upscaled_img.height != target_height) {
                        LOG_INFO("Resizing upscaled image to target size: %dx%d", target_width, target_height);
                        sd_image_t resized_img = { target_width, target_height, upscaled_img.channel, nullptr };
                        resized_img.data = (uint8_t*)malloc(target_width * target_height * resized_img.channel);
                        stbir_resize_uint8(upscaled_img.data, upscaled_img.width, upscaled_img.height, 0,
                                            resized_img.data, target_width, target_height, 0,
                                            resized_img.channel);
                        free(upscaled_img.data);
                        upscaled_img = resized_img;
                    }

                    sd_img_gen_params_t hires_params = img_gen_params;
                    hires_params.init_image = upscaled_img;
                    hires_params.width = upscaled_img.width;
                    hires_params.height = upscaled_img.height;
                    hires_params.strength = gen_params.hires_denoising_strength;
                    hires_params.sample_params.sample_steps = gen_params.hires_steps;
                    hires_params.batch_count = 1;

                    hires_params.mask_image.width = target_width;
                    hires_params.mask_image.height = target_height;
                    hires_params.mask_image.data = (uint8_t*)malloc(target_width * target_height * hires_params.mask_image.channel);
                    if (img_gen_params.mask_image.data) {
                        stbir_resize_uint8(img_gen_params.mask_image.data, img_gen_params.mask_image.width, img_gen_params.mask_image.height, 0,
                                            hires_params.mask_image.data, target_width, target_height, 0,
                                            hires_params.mask_image.channel);
                    } else {
                        memset(hires_params.mask_image.data, 255, target_width * target_height * hires_params.mask_image.channel);
                    }

                    hires_params.control_image.width = target_width;
                    hires_params.control_image.height = target_height;
                    hires_params.control_image.data = (uint8_t*)calloc(1, target_width * target_height * hires_params.control_image.channel);
                    if (img_gen_params.control_image.data) {
                        stbir_resize_uint8(img_gen_params.control_image.data, img_gen_params.control_image.width, img_gen_params.control_image.height, 0,
                                            hires_params.control_image.data, target_width, target_height, 0,
                                            hires_params.control_image.channel);
                    }

                    sd_image_t* second_pass_result = generate_image(ctx.sd_ctx, &hires_params);
                    
                    if (second_pass_result && second_pass_result[0].data) {
                        hires_results[i] = second_pass_result[0];
                        free(second_pass_result); // Free the container, not the data
                    } else {
                        hires_results[i] = upscaled_img; // Fallback to upscaled if second pass fails
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
        }

        set_progress_phase("VAE Decoding...");
        for (int i = 0; i < num_results; i++) {
            if (results[i].data == nullptr) {
                continue;
            }
            auto image_bytes = write_image_to_vector(output_format == "jpeg" ? ImageFormat::JPEG : ImageFormat::PNG,
                                                        results[i].data,
                                                        results[i].width,
                                                        results[i].height,
                                                        results[i].channel,
                                                        output_compression);
            if (image_bytes.empty()) {
                LOG_ERROR("write image to mem failed");
                continue;
            }

            if (save_image) {
                try {
                    const std::string output_dir = ctx.svr_params.output_dir;
                    if (!fs::exists(output_dir)) {
                        fs::create_directories(output_dir);
                    }
                    auto timestamp = std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
                    std::string base_filename = "img-" + std::to_string(timestamp) + "-" + std::to_string(gen_params.seed);
                    std::string img_filename = output_dir + "/" + base_filename + ".png";
                    
                    std::ofstream file(img_filename, std::ios::binary);
                    file.write(reinterpret_cast<const char*>(image_bytes.data()), image_bytes.size());
                    LOG_INFO("saved image to %s", img_filename.c_str());

                    std::string txt_filename = output_dir + "/" + base_filename + ".txt";
                    std::string params_txt = get_image_params(ctx.ctx_params, gen_params, gen_params.seed);
                    
                    std::ofstream txt_file(txt_filename);
                    txt_file << params_txt;
                    LOG_INFO("saved parameters to %s", txt_filename.c_str());

                } catch (const std::exception& e) {
                    LOG_ERROR("failed to save image or metadata: %s", e.what());
                }
            }

            std::string b64 = base64_encode(image_bytes);
            json item;
            item["b64_json"] = b64;
            item["seed"] = gen_params.seed;
            out["data"].push_back(item);
        }

        res.set_content(out.dump(), "application/json");
        res.status = 200;

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
        json err;
        err["error"]   = "server_error";
        err["message"] = e.what();
        res.set_content(err.dump(), "application/json");
    }
}

void handle_edit_image(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    reset_progress();
    try {
        if (!req.is_multipart_form_data()) {
            res.status = 400;
            res.set_content(R"({\"error\":\"Content-Type must be multipart/form-data\"})", "application/json");
            return;
        }

        std::string prompt = req.form.get_field("prompt");
        if (prompt.empty()) {
            res.status = 400;
            res.set_content(R"({\"error\":\"prompt required\"})", "application/json");
            return;
        }

        std::string extra_args_str;
        if (req.form.has_field("extra_args")) {
            extra_args_str = req.form.get_field("extra_args");
        }

        size_t image_count = req.form.get_file_count("image[]");
        if (image_count == 0) {
            res.status = 400;
            res.set_content(R"({\"error\":\"at least one image[] required\"})", "application/json");
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
            res.set_content(R"({\"error\":\"invalid output_format, must be one of [png, jpeg]\"})", "application/json");
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

        if (!gen_params.process_and_check(IMG_GEN, "")) {
            res.status = 400;
            res.set_content(R"({\"error\":\"invalid params\"})", "application/json");
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
                bytes.size(),
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
                mask_bytes.size(),
                mask_w, mask_h,
                width, height, 1);
            mask_image = {(uint32_t)mask_w, (uint32_t)mask_h, 1, mask_raw};
        } else {
            mask_image.width   = width;
            mask_image.height  = height;
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
                res.set_content(R"({\"error\":\"no model loaded\"})", "application/json");
                return;
            }
            set_progress_phase("Sampling...");
            results     = generate_image(ctx.sd_ctx, &img_gen_params);
            num_results = gen_params.batch_count;
        }

        set_progress_phase("VAE Decoding...");
        json out;
        out["created"]       = iso_timestamp_now();
        out["data"]          = json::array();
        out["output_format"] = output_format;

        for (int i = 0; i < num_results; i++) {
            if (results[i].data == nullptr)
                continue;
            auto image_bytes = write_image_to_vector(output_format == "jpeg" ? ImageFormat::JPEG : ImageFormat::PNG,
                                                        results[i].data,
                                                        results[i].width,
                                                        results[i].height,
                                                        results[i].channel,
                                                        output_compression);
            std::string b64 = base64_encode(image_bytes);
            json item;
            item["b64_json"] = b64;
            item["seed"] = gen_params.seed;
            out["data"].push_back(item);
        }

        res.set_content(out.dump(), "application/json");
        res.status = 200;

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
        json err;
        err["error"]   = "server_error";
        err["message"] = e.what();
        res.set_content(err.dump(), "application/json");
    }
}
