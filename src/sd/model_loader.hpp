#pragma once

#include <string>
#include "stable-diffusion.h"
#include "utils/common.hpp"

void load_model_config(SDContextParams& ctx_params, const std::string& model_path_str, const std::string& model_dir);
