#pragma once

#include "orchestrator/database.hpp"
#include <string>
#include <memory>
#include <vector>

namespace diffusion_desk {

class ImportService {
public:
    explicit ImportService(std::shared_ptr<Database> db);
    ~ImportService() = default;

    void auto_import_outputs(const std::string& output_dir);

private:
    std::shared_ptr<Database> m_db;
};

} // namespace diffusion_desk
