#include "import_service.hpp"
#include "utils/common.hpp"
#include <iostream>
#include <fstream>
#include <filesystem>
#include <regex>

namespace fs = std::filesystem;

namespace diffusion_desk {

ImportService::ImportService(std::shared_ptr<Database> db) : m_db(db) {}

void ImportService::auto_import_outputs(const std::string& output_dir) {
    if (!m_db) return;
    DD_LOG_INFO("Scanning %s for images to import to DB...", output_dir.c_str());
    try {
        fs::path out_path_abs = fs::absolute(output_dir);
        if (!fs::exists(out_path_abs) || !fs::is_directory(out_path_abs)) {
            DD_LOG_WARN("Output directory %s does not exist or is not a directory.", out_path_abs.string().c_str());
            return;
        }

        int imported = 0;
        int checked = 0;
        for (const auto& entry : fs::directory_iterator(out_path_abs)) {
            if (entry.is_regular_file()) {
                auto path = entry.path();
                auto ext = path.extension().string();
                if (ext == ".png" || ext == ".jpg" || ext == ".jpeg") {
                    checked++;
                    std::string filename = path.filename().string();
                    std::string file_url = "/outputs/" + filename;

                    // Check if already in DB
                    if (!m_db->generation_exists(file_url)) {
                        std::string prompt = "";
                        std::string neg_prompt = "";
                        int width = 512, height = 512, steps = 20;
                        float cfg = 7.0f;
                        long long seed = 0;
                        double gen_time = 0.0;
                        std::string params_json = "";

                        auto json_path = path;
                        json_path.replace_extension(".json");
                        if (fs::exists(json_path)) {
                            try {
                                std::ifstream f(json_path);
                                std::string raw_json((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
                                auto j = diffusion_desk::json::parse(raw_json);
                                prompt = j.value("prompt", "");
                                neg_prompt = j.value("negative_prompt", "");
                                seed = j.value("seed", 0LL);
                                width = j.value("width", 512);
                                height = j.value("height", 512);
                                steps = j.value("steps", 20);
                                cfg = j.value("cfg_scale", 7.0f);
                                gen_time = j.value("generation_time", 0.0);
                                params_json = raw_json;
                            } catch(...) {}
                        } else {
                            auto txt_path = path;
                            txt_path.replace_extension(".txt");
                            if (fs::exists(txt_path)) {
                                try {
                                    std::ifstream f(txt_path);
                                    std::string content((std::istreambuf_iterator<char>(f)), (std::istreambuf_iterator<char>()));
                                    // Basic regex extraction for legacy .txt
                                    std::regex time_re(R"(Time:\s*([\d\.]+))");
                                    std::smatch match;
                                    if (std::regex_search(content, match, time_re)) {
                                        gen_time = std::stod(match[1]);
                                    }
                                    // For simplicity, we just take the first line as prompt if it doesn't start with "Negative"
                                    std::stringstream ss(content);
                                    std::string line;
                                    if (std::getline(ss, line) && line.find("Negative prompt:") != 0) {
                                        prompt = line;
                                    }
                                } catch(...) {}
                            }
                        }

                        // Generate a UUID if missing (use filename as seed for deterministic UUID or just random)
                        std::string uuid = "legacy-" + filename;

                        diffusion_desk::Generation gen;
                        gen.uuid = uuid;
                        gen.file_path = file_url;
                        gen.prompt = prompt;
                        gen.negative_prompt = neg_prompt;
                        gen.seed = seed;
                        gen.width = width;
                        gen.height = height;
                        gen.steps = steps;
                        gen.cfg_scale = cfg;
                        gen.generation_time = gen_time;
                        gen.params_json = params_json;
                        
                        m_db->insert_generation(gen);
                        imported++;
                    }
                }
            }
        }
        DD_LOG_INFO("Migration: Checked %d files, imported %d new records.", checked, imported);
    } catch (const std::exception& e) {
        DD_LOG_ERROR("Auto-import failed: %s", e.what());
    }
}

} // namespace diffusion_desk
