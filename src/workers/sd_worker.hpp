#pragma once
#include "sd/api_endpoints.hpp"
#include "sd/api_utils.hpp"
#include "utils/common.hpp"

int run_sd_worker(SDSvrParams& svr_params, SDContextParams& ctx_params, SDGenerationParams& default_gen_params);
