#pragma once

#include "job_service.hpp"
#include "../database.hpp"
#include <memory>
#include <string>

namespace mysti {

class ThumbnailService {
public:
    ThumbnailService(std::shared_ptr<JobService> job_svc, std::shared_ptr<Database> db);
    ~ThumbnailService() = default;

private:
    bool handle_job(const mysti::json& payload);
    void ensure_preview_dir();

    std::shared_ptr<JobService> m_job_svc;
    std::shared_ptr<Database> m_db;
};

} // namespace mysti
