#pragma once

#include "sd/api_endpoints.hpp"
#include "sd/api_utils.hpp"
#include "utils/common.hpp"

// Main entry point for the orchestrator mode
int run_orchestrator(int argc, const char** argv, SDSvrParams& svr_params);
