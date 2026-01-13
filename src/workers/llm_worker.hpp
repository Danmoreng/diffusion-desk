#pragma once
#include "utils/common.hpp"
#include "utils/llm_common.hpp"
#include "server/llama_server.hpp"

int run_llm_worker(SDSvrParams& svr_params, LLMContextParams& ctx_params);