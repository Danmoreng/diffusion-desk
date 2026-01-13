#include "thumbnail_service.hpp"
#include "../utils/common.hpp"
#include <iostream>
#include <filesystem>
#include <vector>
#include <algorithm>

// STB includes are handled carefully to avoid multiple definitions
#include "stb_image.h"
#include "stb_image_write.h"

namespace fs = std::filesystem;

namespace diffusion_desk {

ThumbnailService::ThumbnailService(std::shared_ptr<JobService> job_svc, std::shared_ptr<Database> db)
    : m_job_svc(job_svc), m_db(db) {
    
    ensure_preview_dir();
    
    if (m_job_svc) {
        m_job_svc->register_handler("generate_thumbnail", [this](const diffusion_desk::json& payload) {
            return this->handle_job(payload);
        });
    }
}

void ThumbnailService::ensure_preview_dir() {
    try {
        if (!fs::exists("outputs/previews")) {
            fs::create_directories("outputs/previews");
        }
    } catch (...) {}
}

bool ThumbnailService::handle_job(const diffusion_desk::json& payload) {
    try {
        if (!payload.contains("generation_id") || !payload.contains("image_path")) {
            DD_LOG_ERROR("Thumbnail Job missing required fields.");
            return false;
        }

        int id = payload["generation_id"];
        std::string rel_path = payload["image_path"];
        
        // Strip leading slash/backslash if present to force relative path behavior
        std::string clean_path = rel_path;
        if (!clean_path.empty() && (clean_path[0] == '/' || clean_path[0] == '\\')) {
            clean_path = clean_path.substr(1);
        }

        // Try as relative to CWD first (most likely for /outputs/...)
        fs::path img_path = fs::absolute(fs::current_path() / clean_path);
        
        if (!fs::exists(img_path)) {
            // Fallback: Check if original path was actually absolute and valid
            fs::path raw_path = fs::path(rel_path);
            if (fs::exists(raw_path)) {
                img_path = fs::absolute(raw_path);
            } else {
                 DD_LOG_ERROR("Thumbnail source image not found: %s (tried: %s)", rel_path.c_str(), img_path.string().c_str());
                 return false;
            }
        }

        int width, height, channels;
        unsigned char* img = stbi_load(img_path.string().c_str(), &width, &height, &channels, 3); // Force 3 channels (RGB)
        if (!img) {
            DD_LOG_ERROR("Failed to load image for thumbnail: %s", img_path.string().c_str());
            return false;
        }

        // Target size (max dimension 256)
        int target_w = width;
        int target_h = height;
        float scale = 1.0f;
        
        if (width > 256 || height > 256) {
            if (width > height) {
                scale = 256.0f / width;
            } else {
                scale = 256.0f / height;
            }
        }
        
        target_w = static_cast<int>(width * scale);
        target_h = static_cast<int>(height * scale);

        // Simple Nearest Neighbor / Boxish downsampling
        std::vector<unsigned char> thumb(target_w * target_h * 3);
        
        for (int y = 0; y < target_h; ++y) {
            for (int x = 0; x < target_w; ++x) {
                // Map target pixel (x,y) to source pixel (sx, sy)
                int sx = static_cast<int>(x / scale);
                int sy = static_cast<int>(y / scale);
                
                // Clamp
                sx = std::min(sx, width - 1);
                sy = std::min(sy, height - 1);
                
                int src_idx = (sy * width + sx) * 3;
                int dst_idx = (y * target_w + x) * 3;
                
                thumb[dst_idx] = img[src_idx];
                thumb[dst_idx + 1] = img[src_idx + 1];
                thumb[dst_idx + 2] = img[src_idx + 2];
            }
        }

        stbi_image_free(img);

        // Save
        std::string thumb_filename = "thumb_" + std::to_string(id) + ".jpg";
        fs::path thumb_path = fs::path("outputs") / "previews" / thumb_filename;
        
        if (!stbi_write_jpg(thumb_path.string().c_str(), target_w, target_h, 3, thumb.data(), 85)) {
             DD_LOG_ERROR("Failed to write thumbnail: %s", thumb_path.string().c_str());
             return false;
        }

        // Update DB
        // Use relative path for DB consistency
        std::string db_path = "/outputs/previews/" + thumb_filename;
        m_db->add_generation_file(id, "thumbnail", db_path);
        
        DD_LOG_INFO("Generated thumbnail for ID %d", id);
        return true;

    } catch (const std::exception& e) {
        DD_LOG_ERROR("Thumbnail exception: %s", e.what());
        return false;
    }
}

} // namespace diffusion_desk
