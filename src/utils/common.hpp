#pragma once

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#endif

#include <filesystem>
#include <iostream>
#include <map>
#include <random>
#include <regex>
#include <sstream>
#include <string>
#include <vector>
#include <cmath>
#include <ctime>
#include <iomanip>
#include <functional>
#include <memory>

#include <json.hpp>
namespace diffusion_desk {
    using json = nlohmann::json;
}
namespace fs = std::filesystem;

#if defined(_WIN32)
#define NOMINMAX
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#endif  // _WIN32



#include "stb_image.h"
#include "stb_image_write.h"
#include "stb_image_resize.h"

#define SAFE_STR(s) ((s) ? (s) : "")
#define BOOL_STR(b) ((b) ? "true" : "false")

extern const char* modes_str[];

extern thread_local std::string g_request_id;

struct RequestIdGuard {
    std::string prev;
    RequestIdGuard(const std::string& id) : prev(std::move(g_request_id)) { g_request_id = id; }
    ~RequestIdGuard() { g_request_id = std::move(prev); }
};

// Generic Utils
uint64_t get_file_size(const std::string& path);
std::string iso_timestamp_now();
std::string version_string();
std::string argv_to_utf8(int index, const char** argv);
float get_total_vram_gb();
float get_free_vram_gb();
float get_current_process_vram_usage_gb();
std::map<int, float> get_vram_usage_map();

// Logging
enum class DDLogLevel {
    DD_LEVEL_DEBUG,
    DD_LEVEL_INFO,
    DD_LEVEL_WARN,
    DD_LEVEL_ERROR
};

void log_print(DDLogLevel level, const char* log, bool verbose, bool color);
void set_log_verbose(bool verbose);
void set_log_color(bool color);

void dd_log_printf(DDLogLevel level, const char* format, ...);

#define DD_LOG_DEBUG(format, ...) dd_log_printf(DDLogLevel::DD_LEVEL_DEBUG, format, ##__VA_ARGS__)
#define DD_LOG_INFO(format, ...)  dd_log_printf(DDLogLevel::DD_LEVEL_INFO, format, ##__VA_ARGS__)
#define DD_LOG_WARN(format, ...)  dd_log_printf(DDLogLevel::DD_LEVEL_WARN, format, ##__VA_ARGS__)
#define DD_LOG_ERROR(format, ...) dd_log_printf(DDLogLevel::DD_LEVEL_ERROR, format, ##__VA_ARGS__)

// Error helpers
std::string make_error_json(const std::string& error, const std::string& message = "");

std::string generate_random_token(size_t length = 32);
std::string extract_json_block(const std::string& content);
std::vector<std::string> split(const std::string& s, char delimiter);
std::string base64_encode(const unsigned char* buf, unsigned int bufLen);

struct StringOption {
    std::string short_name;
    std::string long_name;
    std::string desc;
    std::string* target;
};

struct IntOption {
    std::string short_name;
    std::string long_name;
    std::string desc;
    int* target;
};

struct FloatOption {
    std::string short_name;
    std::string long_name;
    std::string desc;
    float* target;
};

struct BoolOption {
    std::string short_name;
    std::string long_name;
    std::string desc;
    bool keep_true;
    bool* target;
};

struct ManualOption {
    std::string short_name;
    std::string long_name;
    std::string desc;
    std::function<int(int argc, const char** argv, int index)> cb;
};

struct ArgOptions {
    std::vector<StringOption> string_options;
    std::vector<IntOption> int_options;
    std::vector<FloatOption> float_options;
    std::vector<BoolOption> bool_options;
    std::vector<ManualOption> manual_options;

    static std::string wrap_text(const std::string& text, size_t width, size_t indent);

    void print() const;
};

bool parse_options(int argc, const char** argv, const std::vector<ArgOptions>& options_list);

struct SDSvrParams {
    std::string listen_ip = "127.0.0.1";
    int listen_port       = 1234;
    std::string model_dir = "./models";
    std::string output_dir = "./outputs";
    std::string app_dir = "./public/app";
    std::string default_llm_model = "";
    std::string mode = "orchestrator"; // orchestrator, sd-worker, llm-worker
    int llm_threads = -1;
    int llm_idle_timeout = 300; 
    int sd_idle_timeout = 600; 
    int safe_mode_crashes = 2;
    std::string internal_token;
    std::string tagger_system_prompt = "You are a specialized image tagging engine. Output a JSON object with a 'tags' key containing an array of 5-8 descriptive tags (Subject, Style, Mood). Example: {\"tags\": [\"cat\", \"forest\", \"ethereal\"]}. Output ONLY valid JSON.";
    std::string assistant_system_prompt = "You are an integrated creative assistant for DiffusionDesk. You help users refine their artistic vision, improve prompts, and organize their library. You can control the application through tools. Be concise, professional, and inspiring.";
    std::string style_extractor_system_prompt = "You are an expert art style analyzer. Analyze the given image prompt and extract distinct art styles, artists, or aesthetic descriptors. Return a JSON object with a 'styles' key containing an array of objects. Each style object must have 'name' (concise style name), 'prompt' (keywords to append, MUST include '{prompt}' placeholder), and 'negative_prompt' (optional tags to avoid). Example: {\"styles\": [{\"name\": \"Cyberpunk\", \"prompt\": \"{prompt}, cyberpunk, neon lights\", \"negative_prompt\": \"organic\"}]}";
    bool normal_exit      = false;
    bool verbose          = false;
    bool color            = false;

    ArgOptions get_options();
    bool process_and_check();
    std::string to_string() const;
    bool load_from_file(const std::string& path);
};

uint8_t* load_image_from_file(const char* image_path,
                              int& width,
                              int& height,
                              int expected_width = 0,
                              int expected_height = 0,
                              int expected_channel = 3);

uint8_t* load_image_from_memory(const char* image_bytes,
                                int len,
                                int& width,
                                int& height,
                                int expected_width = 0,
                                int expected_height = 0,
                                int expected_channel = 3);