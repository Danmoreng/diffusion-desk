#include "api_utils.hpp"
#include <ctime>
#include <iomanip>
#include <sstream>
#include <vector>
#include <regex>
#include <cctype> // For isalnum
#include "sd/api_endpoints.hpp" // For SDSvrParams definition
#include "stb_image_write.h"



// Time utilities
std::string iso_timestamp_now() {
    auto now = std::chrono::system_clock::now();
    std::time_t now_c = std::chrono::system_clock::to_time_t(now);
    std::tm now_tm;
    #ifdef _WIN32
        localtime_s(&now_tm, &now_c);
    #else
        localtime_r(&now_c, &now_tm);
    #endif
    std::stringstream ss;
    ss << std::put_time(&now_tm, "%Y-%m-%dT%H:%M:%S");
    return ss.str();
}

// Base64 utilities
static const std::string base64_chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

bool is_base64(unsigned char c) {
    return (isalnum(c) || (c == '+') || (c == '/'));
}

std::string base64_encode(const std::vector<uint8_t>& bytes) {
    std::string ret;
    int i = 0;
    int j = 0;
    unsigned char char_array_3[3];
    unsigned char char_array_4[4];

    for (uint8_t byte : bytes) {
        char_array_3[i++] = byte;
        if (i == 3) {
            char_array_4[0] = (char_array_3[0] & 0xfc) >> 2;
            char_array_4[1] = ((char_array_3[0] & 0x03) << 4) + ((char_array_3[1] & 0xf0) >> 4);
            char_array_4[2] = ((char_array_3[1] & 0x0f) << 2) + ((char_array_3[2] & 0xc0) >> 6);
            char_array_4[3] = char_array_3[2] & 0x3f;

            for (i = 0; i < 4; i++)
                ret += base64_chars[char_array_4[i]];
            i = 0;
        }
    }

    if (i) {
        for (j = i; j < 3; j++)
            char_array_3[j] = '\0';

        char_array_4[0] = (char_array_3[0] & 0xfc) >> 2;
        char_array_4[1] = ((char_array_3[0] & 0x03) << 4) + ((char_array_3[1] & 0xf0) >> 4);
        char_array_4[2] = ((char_array_3[1] & 0x0f) << 2) + ((char_array_3[2] & 0xc0) >> 6);
        char_array_4[3] = char_array_3[2] & 0x3f;

        for (j = 0; j < i + 1; j++)
            ret += base64_chars[char_array_4[j]];

        while (i++ < 3)
            ret += '=';
    }

    return ret;
}

std::vector<uint8_t> base64_decode(const std::string& encoded_string) {
    int in_len = (int)encoded_string.size();
    int i = 0;
    int j = 0;
    int in_ = 0;
    unsigned char char_array_4[4], char_array_3[3];
    std::vector<uint8_t> ret;

    while (in_len-- && (encoded_string[in_] != '=') && is_base64(encoded_string[in_])) {
        char_array_4[i++] = encoded_string[in_];
        in_++;
        if (i == 4) {
            for (i = 0; i < 4; i++)
                char_array_4[i] = (unsigned char)base64_chars.find(char_array_4[i]);

            char_array_3[0] = (char_array_4[0] << 2) + ((char_array_4[1] & 0x30) >> 4);
            char_array_3[1] = ((char_array_4[1] & 0xf) << 4) + ((char_array_4[2] & 0x3c) >> 2);
            char_array_3[2] = ((char_array_4[2] & 0x3) << 6) + char_array_4[3];

            for (i = 0; i < 3; i++)
                ret.push_back(char_array_3[i]);
            i = 0;
        }
    }

    if (i) {
        for (j = i; j < 4; j++)
            char_array_4[j] = 0;

        for (j = 0; j < 4; j++)
            char_array_4[j] = (unsigned char)base64_chars.find(char_array_4[j]);

        char_array_3[0] = (char_array_4[0] << 2) + ((char_array_4[1] & 0x30) >> 4);
        char_array_3[1] = ((char_array_4[1] & 0xf) << 4) + ((char_array_4[2] & 0x3c) >> 2);
        char_array_3[2] = ((char_array_4[2] & 0x3) << 6) + char_array_4[3];

        for (j = 0; j < i - 1; j++)
            ret.push_back(char_array_3[j]);
    }

    return ret;
}

// JSON utilities
mysti::json redact_json_impl(const mysti::json& j, int depth) {
    if (depth > 10) return "[MAX DEPTH]"; // Safety break

    if (j.is_object()) {
        mysti::json redacted = j;
        const std::vector<std::string> keys_to_redact = {"b64_json", "image", "init_image", "mask_image", "extra_args"};
        for (auto& element : redacted.items()) {
            bool should_redact = false;
            for (const auto& key : keys_to_redact) {
                if (element.key() == key) {
                    should_redact = true;
                    break;
                }
            }
            if (should_redact) {
                if (element.value().is_string()) {
                    if (element.value().get<std::string>().size() > 128) {
                        element.value() = "[REDACTED BASE64 (" + std::to_string(element.value().get<std::string>().size()) + " chars)]";
                    }
                } else {
                    element.value() = "[REDACTED NON-STRING DATA]";
                }
            } else if (element.value().is_structured()) {
                element.value() = redact_json_impl(element.value(), depth + 1);
            }
        }
        return redacted;
    } else if (j.is_array()) {
        mysti::json redacted = mysti::json::array();
        for (const auto& item : j) {
            redacted.push_back(redact_json_impl(item, depth + 1));
        }
        return redacted;
    }
    return j;
}

mysti::json redact_json(const mysti::json& j) {
    return redact_json_impl(j, 0);
}

// Image params

mysti::json parse_image_params(const std::string& txt) {
    mysti::json j;
    std::regex param_re(R"(([^:]+):\s*(.*))");
    std::stringstream ss(txt);
    std::string line;
    bool in_params = false;
    std::string prompt = "";
    std::string neg_prompt = "";
    
    while(std::getline(ss, line)) {
        if (line.find("Negative prompt:") == 0) {
            neg_prompt = line.substr(16);
            in_params = false;
            continue;
        }
        if (line.find("Steps:") == 0) {
            in_params = true;
            std::regex kv_re(R"(([^:,]+):\s*([^,]+))");
            std::sregex_iterator begin(line.begin(), line.end(), kv_re);
            std::sregex_iterator end;
            for (std::sregex_iterator i = begin; i != end; ++i) {
                std::smatch match = *i;
                std::string key = match[1];
                std::string val = match[2];
                // Trim leading/trailing whitespace
                key = std::regex_replace(key, std::regex("^\\s+|\\s+$"), "");
                val = std::regex_replace(val, std::regex("^\\s+|\\s+$"), "");
                j[key] = val;
            }
            continue;
        }
        if (!in_params) {
            if (!prompt.empty()) prompt += "\n";
            prompt += line;
        }
    }
    j["prompt"] = prompt;
    j["negative_prompt"] = neg_prompt;
    return j;
}

std::string get_image_params(const SDContextParams& ctx_params, const SDGenerationParams& gen_params, int64_t seed, double generation_time) {
    std::stringstream ss;
    ss << gen_params.prompt << "\n";
    if (!gen_params.negative_prompt.empty()) {
        ss << "Negative prompt: " << gen_params.negative_prompt << "\n";
    }
    ss << "Steps: " << gen_params.sample_params.sample_steps << ", ";
    ss << "Sampler: " << sd_sample_method_name(gen_params.sample_params.sample_method) << ", ";
    ss << "CFG scale: " << gen_params.sample_params.guidance.txt_cfg << ", ";
    ss << "Seed: " << seed << ", ";
    ss << "Size: " << gen_params.width << "x" << gen_params.height << ", ";
    ss << "Model: " << fs::path(ctx_params.diffusion_model_path.empty() ? ctx_params.model_path : ctx_params.diffusion_model_path).filename().string();
    if (generation_time > 0) {
        ss << ", Time: " << std::fixed << std::setprecision(2) << generation_time << "s";
    }
    return ss.str();
}

void write_func(void *context, void *data, int size) {
    std::vector<uint8_t> *vec = (std::vector<uint8_t> *)context;
    vec->insert(vec->end(), (uint8_t *)data, (uint8_t *)data + size);
}

std::vector<uint8_t> write_image_to_vector(ImageFormat format, const uint8_t* image, int width, int height, int channels, int quality) {
    std::vector<uint8_t> buffer;
    int res = 0;
    if (format == ImageFormat::PNG) {
        res = stbi_write_png_to_func(write_func, &buffer, width, height, channels, image, width * channels);
    } else {
        res = stbi_write_jpg_to_func(write_func, &buffer, width, height, channels, image, quality);
    }
    if (res == 0) return {};
    return buffer;
}

void free_sd_images(sd_image_t* images, int n) {

    if (images) {

        for (int i = 0; i < n; i++) {

            if (images[i].data) {

                free(images[i].data);

            }

        }

        free(images);

    }

}
