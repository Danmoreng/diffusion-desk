#pragma once

#include <string>
#include <vector>
#include <cstdint>
#include <json.hpp>
#include "utils/common.hpp"

// Time utilities
std::string iso_timestamp_now();

// Base64 utilities
std::string base64_encode(const std::vector<uint8_t>& bytes);
std::vector<uint8_t> base64_decode(const std::string& encoded_string);
bool is_base64(unsigned char c);

// Image parameter handling
mysti::json parse_image_params(const std::string& txt);
std::string get_image_params(const SDContextParams& ctx_params, const SDGenerationParams& gen_params, int64_t seed);

// Image processing
enum class ImageFormat { 
    JPEG,
    PNG 
};

std::vector<uint8_t> write_image_to_vector(
    ImageFormat format,
    const uint8_t* image,
    int width,
    int height,
    int channels,
    int quality = 90
);

void sd_log_cb(enum sd_log_level_t level, const char* log, void* data);

