#pragma once

#include <string>
#include <vector>
#include "utils/common.hpp"

struct LLMContextParams {
    int n_threads = -1;
    std::string model_path;
    std::string mmproj_path; // Vision adapter
    int n_gpu_layers = -1;
    int n_ctx = 2048;
    
    ArgOptions get_options() {
        ArgOptions options;
        options.string_options = {
            {"-m", "--model", "path to model file", &model_path},
            {"-lm", "--llm-model", "alias for model", &model_path},
            {"", "--mmproj", "path to multimodal projector", &mmproj_path},
        };
        options.int_options = {
            {"-t", "--threads", "number of threads", &n_threads},
            {"-ngl", "--n-gpu-layers", "number of GPU layers", &n_gpu_layers},
            {"-c", "--ctx-size", "context size", &n_ctx},
        };
        return options;
    }
};
