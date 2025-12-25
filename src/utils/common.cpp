#include "common.hpp"

#include <iostream>
#include <fstream>
#include <sstream>
#include <vector>
#include <map>
#include <regex>
#include <random>
#include <filesystem>
#include <cstdarg>
#include <cstring>
#include <cstdio>

#if defined(_WIN32)
#define NOMINMAX
#include <windows.h>
#include <shellapi.h>
#include <dxgi1_4.h>
#pragma comment(lib, "dxgi.lib")
#endif

#define STB_IMAGE_IMPLEMENTATION
#define STB_IMAGE_STATIC
#include "stb_image.h"



#define STB_IMAGE_RESIZE_IMPLEMENTATION
#define STB_IMAGE_RESIZE_STATIC
#include "stb_image_resize.h"

namespace fs = std::filesystem;

// Global variables for logging
static bool log_verbose = false;
static bool log_color   = false;

const char* modes_str[] = {
    "img_gen",
    "vid_gen",
    "convert",
    "upscale",
};

#if defined(_WIN32)
static std::string utf16_to_utf8(const std::wstring& wstr) {
    if (wstr.empty())
        return {};
    int size_needed = WideCharToMultiByte(CP_UTF8, 0, wstr.data(), (int)wstr.size(),
                                          nullptr, 0, nullptr, nullptr);
    if (size_needed <= 0)
        throw std::runtime_error("UTF-16 to UTF-8 conversion failed");

    std::string utf8(size_needed, 0);
    WideCharToMultiByte(CP_UTF8, 0, wstr.data(), (int)wstr.size(),
                        (char*)utf8.data(), size_needed, nullptr, nullptr);
    return utf8;
}

std::string argv_to_utf8(int index, const char** argv) {
    int argc;
    wchar_t** argv_w = CommandLineToArgvW(GetCommandLineW(), &argc);
    if (!argv_w)
        throw std::runtime_error("Failed to parse command line");

    std::string result;
    if (index < argc) {
        result = utf16_to_utf8(argv_w[index]);
    }
    LocalFree(argv_w);
    return result;
}

#else  // Linux / macOS
std::string argv_to_utf8(int index, const char** argv) {
    return std::string(argv[index]);
}
#endif

static void print_utf8(FILE* stream, const char* utf8) {
    if (!utf8)
        return;

#ifdef _WIN32
    HANDLE h = (stream == stderr)
                   ? GetStdHandle(STD_ERROR_HANDLE)
                   : GetStdHandle(STD_OUTPUT_HANDLE);

    int wlen = MultiByteToWideChar(CP_UTF8, 0, utf8, -1, NULL, 0);
    if (wlen <= 0)
        return;

    wchar_t* wbuf = (wchar_t*)malloc(wlen * sizeof(wchar_t));
    MultiByteToWideChar(CP_UTF8, 0, utf8, -1, wbuf, wlen);

    DWORD written;
    WriteConsoleW(h, wbuf, wlen - 1, &written, NULL);

    free(wbuf);
#else
    fputs(utf8, stream);
#endif
}

std::string sd_basename(const std::string& path) {
    size_t pos = path.find_last_of('/');
    if (pos != std::string::npos) {
        return path.substr(pos + 1);
    }
    pos = path.find_last_of('\\');
    if (pos != std::string::npos) {
        return path.substr(pos + 1);
    }
    return path;
}

void log_print(enum sd_log_level_t level, const char* log, bool verbose, bool color) {
    int tag_color;
    const char* level_str;
    FILE* out_stream = (level == SD_LOG_ERROR) ? stderr : stdout;

    if (!log || (!verbose && level <= SD_LOG_DEBUG)) {
        return;
    }

    switch (level) {
        case SD_LOG_DEBUG:
            tag_color = 37;
            level_str = "DEBUG";
            break;
        case SD_LOG_INFO:
            tag_color = 34;
            level_str = "INFO";
            break;
        case SD_LOG_WARN:
            tag_color = 35;
            level_str = "WARN";
            break;
        case SD_LOG_ERROR:
            tag_color = 31;
            level_str = "ERROR";
            break;
        default: /* Potential future-proofing */
            tag_color = 33;
            level_str = "?????";
            break;
    }

    if (color) {
        fprintf(out_stream, "\033[%d;1m[%-5s]\033[0m ", tag_color, level_str);
    } else {
        fprintf(out_stream, "[%-5s] ", level_str);
    }
    print_utf8(out_stream, log);
    fflush(out_stream);
}

void set_log_verbose(bool verbose) {
    log_verbose = verbose;
}

void set_log_color(bool color) {
    log_color = color;
}

std::string generate_random_token(size_t length) {
    static const char charset[] =
        "0123456789"
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        "abcdefghijklmnopqrstuvwxyz";
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, sizeof(charset) - 2);
    std::string token;
    for (size_t i = 0; i < length; ++i) {
        token += charset[dis(gen)];
    }
    return token;
}

// ArgOptions implementation
std::string ArgOptions::wrap_text(const std::string& text, size_t width, size_t indent) {
    std::ostringstream oss;
    size_t line_len = 0;
    size_t pos      = 0;

    while (pos < text.size()) {
        if (text[pos] == '\n') {
            oss << '\n'
                << std::string(indent, ' ');
            line_len = indent;
            ++pos;
            continue;
        }

        oss << text[pos];
        ++line_len;
        ++pos;

        if (line_len >= width) {
            std::string current = oss.str();
            size_t back         = current.size();

            while (back > 0 && current[back - 1] != ' ' && current[back - 1] != '\n')
                --back;

            if (back > 0 && current[back - 1] != '\n') {
                std::string before = current.substr(0, back - 1);
                std::string after  = current.substr(back);
                oss.str("");
                oss.clear();
                oss << before << "\n"
                    << std::string(indent, ' ') << after;
            } else {
                oss << "\n"
                    << std::string(indent, ' ');
            }
            line_len = indent;
        }
    }

    return oss.str();
}

void ArgOptions::print() const {
    constexpr size_t max_line_width = 120;

    struct Entry {
        std::string names;
        std::string desc;
    };
    std::vector<Entry> entries;

    auto add_entry = [&](const std::string& s, const std::string& l,
                         const std::string& desc, const std::string& hint = "") {
        std::ostringstream ss;
        if (!s.empty())
            ss << s;
        if (!s.empty() && !l.empty())
            ss << ", ";
        if (!l.empty())
            ss << l;
        if (!hint.empty())
            ss << " " << hint;
        entries.push_back({ss.str(), desc});
    };

    for (auto& o : string_options)
        add_entry(o.short_name, o.long_name, o.desc, "<string>");
    for (auto& o : int_options)
        add_entry(o.short_name, o.long_name, o.desc, "<int>");
    for (auto& o : float_options)
        add_entry(o.short_name, o.long_name, o.desc, "<float>");
    for (auto& o : bool_options)
        add_entry(o.short_name, o.long_name, o.desc, "");
    for (auto& o : manual_options)
        add_entry(o.short_name, o.long_name, o.desc);

    size_t max_name_width = 0;
    for (auto& e : entries)
        max_name_width = std::max(max_name_width, e.names.size());

    for (auto& e : entries) {
        size_t indent            = 2 + max_name_width + 4;
        size_t desc_width        = (max_line_width > indent ? max_line_width - indent : 40);
        std::string wrapped_desc = wrap_text(e.desc, max_line_width, indent);
        std::cout << "  " << std::left << std::setw(static_cast<int>(max_name_width) + 4)
                  << e.names << wrapped_desc << "\n";
    }
}

bool parse_options(int argc, const char** argv, const std::vector<ArgOptions>& options_list) {

    bool invalid_arg = false;

    std::string arg;



    auto match_and_apply = [&](auto& opts, auto&& apply_fn) -> bool {

        for (auto& option : opts) {

            if ((option.short_name.size() > 0 && arg == option.short_name) ||

                (option.long_name.size() > 0 && arg == option.long_name)) {

                apply_fn(option);

                return true;

            }

        }

        return false;

    };



    for (int i = 1; i < argc; i++) {
        arg            = argv[i];
        bool found_arg = false;

        for (auto& options : options_list) {

            if (match_and_apply(options.string_options, [&](auto& option) {

                    if (++i >= argc) {

                        invalid_arg = true;

                        return;

                    }

                    *option.target = argv_to_utf8(i, argv);

                    found_arg      = true;

                }))

                break;



            if (match_and_apply(options.int_options, [&](auto& option) {

                    if (++i >= argc) {

                        invalid_arg = true;

                        return;

                    }

                    *option.target = std::stoi(argv[i]);

                    found_arg      = true;

                }))

                break;



            if (match_and_apply(options.float_options, [&](auto& option) {

                    if (++i >= argc) {

                        invalid_arg = true;

                        return;

                    }

                    *option.target = std::stof(argv[i]);

                    found_arg      = true;

                }))

                break;



            if (match_and_apply(options.bool_options, [&](auto& option) {

                    *option.target = option.keep_true ? true : false;

                    found_arg      = true;

                }))

                break;



            if (match_and_apply(options.manual_options, [&](auto& option) {

                    int ret = option.cb(argc, argv, i);

                    if (ret < 0) {

                        invalid_arg = true;

                        return;

                    }

                    i += ret;

                    found_arg = true;

                }))

                break;

        }



        if (invalid_arg) {
            LOG_ERROR("error: invalid parameter for argument: %s", arg.c_str());
            return false;
        }

        if (!found_arg) {
            LOG_ERROR("error: unknown argument: %s", arg.c_str());
            return false;
        }
    }



    return true;

}

std::string version_string() {
    return std::string("stable-diffusion.cpp version ") + sd_version() + ", commit " + sd_commit();
}

float get_total_vram_gb() {
    const char* cmd = "nvidia-smi --query-gpu=memory.total --format=csv,noheader,nounits";
#ifdef _WIN32
    FILE* pipe = _popen(cmd, "r");
#else
    FILE* pipe = popen(cmd, "r");
#endif
    if (!pipe) {
        return 8.0f; // Fallback
    }
    char buffer[128];
    float total_gb = 8.0f;
    if (fgets(buffer, 128, pipe) != NULL) {
        try {
            float total_mb = std::stof(buffer);
            total_gb = total_mb / 1024.0f;
        } catch (...) {}
    }
#ifdef _WIN32
    _pclose(pipe);
#else
    pclose(pipe);
#endif
    return total_gb;
}

float get_free_vram_gb() {
    const char* cmd = "nvidia-smi --query-gpu=memory.free --format=csv,noheader,nounits";
#ifdef _WIN32
    FILE* pipe = _popen(cmd, "r");
#else
    FILE* pipe = popen(cmd, "r");
#endif
    if (!pipe) {
        return 4.0f; // Fallback
    }
    char buffer[128];
    float free_gb = 4.0f;
    if (fgets(buffer, 128, pipe) != NULL) {
        try {
            float free_mb = std::stof(buffer);
            free_gb = free_mb / 1024.0f;
        } catch (...) {}
    }
#ifdef _WIN32
    _pclose(pipe);
#else
    pclose(pipe);
#endif
    return free_gb;
}

float get_current_process_vram_usage_gb() {
#ifdef _WIN32
    float usage_gb = 0.0f;
    IDXGIFactory4* pFactory = nullptr;
    if (SUCCEEDED(CreateDXGIFactory1(__uuidof(IDXGIFactory4), (void**)&pFactory))) {
        IDXGIAdapter* pAdapter = nullptr;
        if (SUCCEEDED(pFactory->EnumAdapters(0, &pAdapter))) {
            IDXGIAdapter3* pAdapter3 = nullptr;
            if (SUCCEEDED(pAdapter->QueryInterface(__uuidof(IDXGIAdapter3), (void**)&pAdapter3))) {
                DXGI_QUERY_VIDEO_MEMORY_INFO info;
                if (SUCCEEDED(pAdapter3->QueryVideoMemoryInfo(0, DXGI_MEMORY_SEGMENT_GROUP_LOCAL, &info))) {
                    usage_gb = (float)info.CurrentUsage / (1024.0f * 1024.0f * 1024.0f);
                }
                pAdapter3->Release();
            }
            pAdapter->Release();
        }
        pFactory->Release();
    }
    return usage_gb;
#else
    auto usage_map = get_vram_usage_map();
    int pid = getpid();
    if (usage_map.count(pid)) {
        return usage_map[pid];
    }
    return 0.0f;
#endif
}

std::map<int, float> get_vram_usage_map() {
    std::map<int, float> usage;
    const char* cmd = "nvidia-smi --query-compute-apps=pid,used_memory --format=csv,noheader,nounits";
#ifdef _WIN32
    FILE* pipe = _popen(cmd, "r");
#else
    FILE* pipe = popen(cmd, "r");
#endif
    if (pipe) {
        char buffer[256];
        while (fgets(buffer, sizeof(buffer), pipe) != NULL) {
            int pid;
            float mem_mb;
            if (sscanf(buffer, "%d, %f", &pid, &mem_mb) == 2) {
                usage[pid] = mem_mb / 1024.0f; // Convert to GB
            }
        }
#ifdef _WIN32
        _pclose(pipe);
#else
        pclose(pipe);
#endif
    }
    return usage;
}

uint8_t* load_image_common(bool from_memory,
                           const char* image_path_or_bytes,
                           int len,
                           int& width,
                           int& height,
                           int expected_width,
                           int expected_height,
                           int expected_channel) {
    int c = 0;
    const char* image_path;
    uint8_t* image_buffer = nullptr;
    if (from_memory) {
        image_path   = "memory";
        image_buffer = (uint8_t*)stbi_load_from_memory((const stbi_uc*)image_path_or_bytes, len, &width, &height, &c, expected_channel);
    } else {
        image_path   = image_path_or_bytes;
        image_buffer = (uint8_t*)stbi_load(image_path_or_bytes, &width, &height, &c, expected_channel);
    }
    if (image_buffer == nullptr) {
        LOG_ERROR("load image from '%s' failed", image_path);
        return nullptr;
    }
    if (c < expected_channel) {
        fprintf(stderr,
                "the number of channels for the input image must be >= %d,விற்கு",
                expected_channel);
        free(image_buffer);
        return nullptr;
    }
    if (width <= 0) {
        LOG_ERROR("error: the width of image must be greater than 0, image_path = %s", image_path);
        free(image_buffer);
        return nullptr;
    }
    if (height <= 0) {
        LOG_ERROR("error: the height of image must be greater than 0, image_path = %s", image_path);
        free(image_buffer);
        return nullptr;
    }

    // Resize input image ...
    if ((expected_width > 0 && expected_height > 0) && (height != expected_height || width != expected_width)) {
        float dst_aspect = (float)expected_width / (float)expected_height;
        float src_aspect = (float)width / (float)height;

        int crop_x = 0, crop_y = 0;
        int crop_w = width, crop_h = height;

        if (src_aspect > dst_aspect) {
            crop_w = (int)(height * dst_aspect);
            crop_x = (width - crop_w) / 2;
        } else if (src_aspect < dst_aspect) {
            crop_h = (int)(width / dst_aspect);
            crop_y = (height - crop_h) / 2;
        }

        if (crop_x != 0 || crop_y != 0) {
            LOG_INFO("crop input image from %dx%d to %dx%d, image_path = %s", width, height, crop_w, crop_h, image_path);
            uint8_t* cropped_image_buffer = (uint8_t*)malloc(crop_w * crop_h * expected_channel);
            if (cropped_image_buffer == nullptr) {
                LOG_ERROR("error: allocate memory for crop\n");
                free(image_buffer);
                return nullptr;
            }
            for (int row = 0; row < crop_h; row++) {
                uint8_t* src = image_buffer + ((crop_y + row) * width + crop_x) * expected_channel;
                uint8_t* dst = cropped_image_buffer + (row * crop_w) * expected_channel;
                memcpy(dst, src, crop_w * expected_channel);
            }

            width  = crop_w;
            height = crop_h;
            free(image_buffer);
            image_buffer = cropped_image_buffer;
        }

        LOG_INFO("resize input image from %dx%d to %dx%d", width, height, expected_width, expected_height);
        int resized_height = expected_height;
        int resized_width  = expected_width;

        uint8_t* resized_image_buffer = (uint8_t*)malloc(resized_height * resized_width * expected_channel);
        if (resized_image_buffer == nullptr) {
            LOG_ERROR("error: allocate memory for resize input image\n");
            free(image_buffer);
            return nullptr;
        }
        stbir_resize(image_buffer, width, height, 0,
                     resized_image_buffer, resized_width, resized_height, 0, STBIR_TYPE_UINT8,
                     expected_channel, STBIR_ALPHA_CHANNEL_NONE, 0,
                     STBIR_EDGE_CLAMP, STBIR_EDGE_CLAMP,
                     STBIR_FILTER_BOX, STBIR_FILTER_BOX,
                     STBIR_COLORSPACE_SRGB, nullptr);
        width  = resized_width;
        height = resized_height;
        free(image_buffer);
        image_buffer = resized_image_buffer;
    }
    return image_buffer;
}

uint8_t* load_image_from_file(const char* image_path,
                              int& width,
                              int& height,
                              int expected_width,
                              int expected_height,
                              int expected_channel) {
    return load_image_common(false, image_path, 0, width, height, expected_width, expected_height, expected_channel);
}

uint8_t* load_image_from_memory(const char* image_bytes,
                                int len,
                                int& width,
                                int& height,
                                int expected_width,
                                int expected_height,
                                int expected_channel) {
    return load_image_common(true, image_bytes, len, width, height, expected_width, expected_height, expected_channel);
}

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
            "--mode",
            "server run mode: orchestrator, sd-worker, llm-worker (default: orchestrator)",
            &mode},
        {
            "",
            "--model-dir",
            "directory to scan for models (default: ./models)",
            &model_dir},
        {
            "",
            "--output-dir",
            "directory to save generated images (default: ./outputs)",
            &output_dir},
        {
            "",
            "--app-dir",
            "directory for WebUI assets (default: ./public/app)",
            &app_dir},
        {
            "",
            "--default-llm",
            "default LLM model to load automatically",
            &default_llm_model},
        {
            "",
            "--internal-token",
            "transient API token for internal communication",
            &internal_token}};

    options.int_options = {
        {
            "",
            "--listen-port",
            "server listen port (default: 1234)",
            &listen_port},
        {
            "",
            "--llm-threads",
            "number of threads for LLM",
            &llm_threads},
        {
            "",
            "--llm-idle-timeout",
            "seconds of inactivity before unloading LLM (default: 300)",
            &llm_idle_timeout},
        {
            "",
            "--safe-mode-crashes",
            "number of crashes before enabling safe mode (default: 2)",
            &safe_mode_crashes},
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

static std::string resolve_path(const std::string& p) {
    if (p.empty()) return p;
#ifdef _WIN32
    char expanded[MAX_PATH];
    DWORD ret = ExpandEnvironmentStringsA(p.c_str(), expanded, MAX_PATH);
    if (ret > 0 && ret <= MAX_PATH) {
        return std::string(expanded);
    }
#endif
    return p;
}

bool SDSvrParams::load_from_file(const std::string& path) {
    std::ifstream f(path);
    if (!f.is_open()) {
        return false;
    }

    try {
        mysti::json j = mysti::json::parse(f);
        
        if (j.contains("server")) {
            auto& s = j["server"];
            if (s.contains("listen_ip")) listen_ip = s["listen_ip"];
            if (s.contains("listen_port")) listen_port = s["listen_port"];
            if (s.contains("verbose")) verbose = s["verbose"];
            if (s.contains("color")) color = s["color"];
        }

        if (j.contains("paths")) {
            auto& p = j["paths"];
            if (p.contains("model_dir")) model_dir = resolve_path(p["model_dir"]);
            if (p.contains("output_dir")) output_dir = resolve_path(p["output_dir"]);
            if (p.contains("app_dir")) app_dir = resolve_path(p["app_dir"]);
        }

        if (j.contains("llm")) {
            auto& l = j["llm"];
            if (l.contains("default_model")) default_llm_model = l["default_model"];
            if (l.contains("threads")) llm_threads = l["threads"];
            if (l.contains("idle_timeout")) llm_idle_timeout = l["idle_timeout"];
        }

        if (j.contains("sd")) {
            auto& sd = j["sd"];
            if (sd.contains("safe_mode_crashes")) safe_mode_crashes = sd["safe_mode_crashes"];
        }

        return true;
    } catch (const std::exception& e) {
        LOG_ERROR("Failed to parse config file: %s", e.what());
        return false;
    }
}

std::string SDSvrParams::to_string() const {
    std::ostringstream oss;
    oss << "SDSvrParams {\n"
        << "  listen_ip: " << listen_ip << ",\n"
        << "  listen_port: " << listen_port << ",\n"
        << "  model_dir: " << model_dir << ",\n"
        << "  output_dir: " << output_dir << ",\n"
        << "  app_dir: " << app_dir << ",\n"
        << "  mode: " << mode << ",\n"
        << "  verbose: " << (verbose ? "true" : "false") << ",\n"
        << "  color: " << (color ? "true" : "false") << "\n"
        << "}";
    return oss.str();
}