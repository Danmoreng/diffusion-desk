#include "model_loader.hpp"
#include <filesystem>
#include <fstream>
#include <json.hpp>

using json = diffusion_desk::json;
namespace fs = std::filesystem;

void load_model_config(SDContextParams& ctx_params, const std::string& model_path_str, const std::string& model_dir) {
    // Deprecated: Model configuration is now passed fully via the API request (Presets).
    // This function is kept for linking compatibility but does nothing.
    // DD_LOG_WARN("load_model_config is deprecated and ignored. Use API presets instead.");
}
