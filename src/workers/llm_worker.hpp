#pragma once
#include "utils/common.hpp"
#include "server/llama_server.hpp"

int run_llm_worker(SDSvrParams& svr_params, SDContextParams& ctx_params);