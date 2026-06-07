#include "api_endpoints.hpp"
#include "api_utils.hpp"
#include "server_state.hpp"
#include "model_loader.hpp"
#include <atomic>
#include <condition_variable>
#include <deque>
#include <sstream>
#include <iomanip>
#include <fstream>
#include <thread>
#include <unordered_map>
#include <regex>
#include <cctype>

namespace fs = std::filesystem;

// Handlers Placeholders - To be filled from main.cpp logic

namespace {

enum class GenerationJobStatus {
    Queued,
    Processing,
    Completed,
    Failed,
    Cancelled,
};

const char* generation_job_status_name(GenerationJobStatus status) {
    switch (status) {
        case GenerationJobStatus::Queued: return "queued";
        case GenerationJobStatus::Processing: return "processing";
        case GenerationJobStatus::Completed: return "completed";
        case GenerationJobStatus::Failed: return "failed";
        case GenerationJobStatus::Cancelled: return "cancelled";
        default: return "failed";
    }
}

bool generation_job_status_terminal(GenerationJobStatus status) {
    return status == GenerationJobStatus::Completed ||
           status == GenerationJobStatus::Failed ||
           status == GenerationJobStatus::Cancelled;
}

int64_t unix_ms_now() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
}

std::string resolve_model_path(const std::string& path, const std::string& model_dir) {
    if (path.empty()) return "";
    fs::path fp(path);
    if (fp.is_absolute()) return fp.string();
    return (fs::path(model_dir) / fp).string();
}

std::string lowercase_copy(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return (char)std::tolower(c);
    });
    return value;
}

void sanitize_context_params_for_backend(SDContextParams& params) {
    if (params.stream_layers && params.max_vram == 0.0f) {
        DD_LOG_WARN("stream_layers requested without max_vram; disabling stream_layers");
        params.stream_layers = false;
    }
    if (params.stream_layers && !params.offload_params_to_cpu) {
        DD_LOG_WARN("stream_layers requested without CPU parameter offload; disabling stream_layers");
        params.stream_layers = false;
    }
}

bool generation_needs_vae_encode(const diffusion_desk::json& request, const SDGenerationParams& params) {
    if (!params.init_image_path.empty() ||
        !params.end_image_path.empty() ||
        !params.ref_image_paths.empty() ||
        !params.control_video_path.empty() ||
        params.video_frames > 1) {
        return true;
    }

    for (const char* key : {"init_image", "image", "end_image", "ref_images"}) {
        if (request.contains(key) && !request[key].is_null()) return true;
    }

    return false;
}

sd_ctx_params_t make_sd_ctx_params(SDContextParams& params, bool vae_decode_only) {
    return params.to_sd_ctx_params_t(vae_decode_only, false, false);
}

float infer_pid_upscale_factor(const std::string& model_path) {
    std::string name = fs::path(model_path).filename().string();
    name = lowercase_copy(name);

    std::regex size_re(R"((\d+)_to_(\d+))");
    std::smatch match;
    if (std::regex_search(name, match, size_re) && match.size() == 3) {
        try {
            float src = std::stof(match[1].str());
            float dst = std::stof(match[2].str());
            if (src > 0.f && dst > src) return dst / src;
        } catch (...) {}
    }
    return 4.f;
}

struct GenerationJobEvent {
    uint64_t id = 0;
    std::string name;
    diffusion_desk::json data;
};

struct GenerationJob {
    std::string id;
    GenerationJobStatus status = GenerationJobStatus::Queued;
    std::string request_body;
    int64_t created_at = unix_ms_now();
    int64_t started_at = 0;
    int64_t completed_at = 0;
    diffusion_desk::json result;
    std::string error_code;
    std::string error_message;
    std::vector<GenerationJobEvent> events;
    uint64_t next_event_id = 1;
};

struct GenerationJobManager {
    std::mutex mutex;
    std::condition_variable cv;
    std::unordered_map<std::string, std::shared_ptr<GenerationJob>> jobs;
    std::deque<std::string> queue;
    std::thread worker_thread;
    uint64_t next_id = 1;
    bool stop = false;
    bool started = false;
    size_t max_pending_jobs = 64;
};

GenerationJobManager g_generation_jobs;

size_t count_pending_generation_jobs_locked() {
    size_t count = 0;
    for (const auto& entry : g_generation_jobs.jobs) {
        if (entry.second->status == GenerationJobStatus::Queued ||
            entry.second->status == GenerationJobStatus::Processing) {
            ++count;
        }
    }
    return count;
}

std::string make_generation_job_id_locked() {
    std::ostringstream oss;
    oss << "job_" << std::hex << unix_ms_now() << "_" << std::setw(8)
        << std::setfill('0') << g_generation_jobs.next_id++;
    return oss.str();
}

void append_generation_job_event_locked(GenerationJob& job, const std::string& name, diffusion_desk::json data) {
    data["job_id"] = job.id;
    job.events.push_back({job.next_event_id++, name, std::move(data)});
    g_generation_jobs.cv.notify_all();
}

diffusion_desk::json make_generation_job_json_locked(const GenerationJob& job) {
    diffusion_desk::json j;
    j["id"] = job.id;
    j["status"] = generation_job_status_name(job.status);
    j["created_at"] = job.created_at;
    j["started_at"] = job.started_at == 0 ? diffusion_desk::json(nullptr) : diffusion_desk::json(job.started_at);
    j["completed_at"] = job.completed_at == 0 ? diffusion_desk::json(nullptr) : diffusion_desk::json(job.completed_at);
    j["queue_position"] = 0;

    if (job.status == GenerationJobStatus::Queued) {
        size_t position = 1;
        for (const auto& queued_id : g_generation_jobs.queue) {
            if (queued_id == job.id) {
                j["queue_position"] = position;
                break;
            }
            ++position;
        }
    }

    if (job.status == GenerationJobStatus::Completed) {
        j["result"] = job.result;
        j["error"] = nullptr;
    } else if (job.status == GenerationJobStatus::Failed || job.status == GenerationJobStatus::Cancelled) {
        j["result"] = nullptr;
        j["error"] = {
            {"code", job.error_code.empty() ? "generation_failed" : job.error_code},
            {"message", job.error_message},
        };
    } else {
        j["result"] = nullptr;
        j["error"] = nullptr;
    }

    return j;
}

bool write_sse_event(httplib::DataSink& sink, const GenerationJobEvent& event) {
    std::string payload = "id: " + std::to_string(event.id) + "\n" +
                          "event: " + event.name + "\n" +
                          "data: " + event.data.dump() + "\n\n";
    return sink.write(payload.c_str(), payload.size());
}

void publish_progress_events_until_done(const std::shared_ptr<GenerationJob>& job, std::atomic<bool>& done) {
    uint64_t last_version = 0;
    while (!done.load()) {
        int step = 0;
        int steps = 0;
        float time = 0;
        std::string phase;
        std::string message;

        {
            std::unique_lock<std::mutex> lock(progress_state.mutex);
            progress_state.cv.wait_for(lock, std::chrono::milliseconds(250), [&] {
                return done.load() || progress_state.version > last_version;
            });
            if (progress_state.version <= last_version) {
                continue;
            }
            step = progress_state.step;
            steps = progress_state.steps;
            time = progress_state.time;
            phase = progress_state.phase;
            message = progress_state.message;
            last_version = progress_state.version;
        }

        diffusion_desk::json data;
        data["step"] = step;
        data["steps"] = steps;
        data["time"] = time;
        data["phase"] = phase;
        data["message"] = message;

        std::lock_guard<std::mutex> lock(g_generation_jobs.mutex);
        if (job->status == GenerationJobStatus::Processing) {
            append_generation_job_event_locked(*job, "progress", std::move(data));
        }
    }
}

void execute_generation_job(const std::shared_ptr<GenerationJob>& job, ServerContext& ctx) {
    {
        std::lock_guard<std::mutex> lock(g_generation_jobs.mutex);
        job->status = GenerationJobStatus::Processing;
        job->started_at = unix_ms_now();
        append_generation_job_event_locked(*job, "started", make_generation_job_json_locked(*job));
    }

    std::atomic<bool> progress_done{false};
    std::thread progress_thread([job, &progress_done]() {
        publish_progress_events_until_done(job, progress_done);
    });

    httplib::Request generation_req;
    generation_req.body = job->request_body;
    httplib::Response generation_res;

    try {
        handle_generate_image(generation_req, generation_res, ctx);
    } catch (const std::exception& e) {
        generation_res.status = 500;
        generation_res.set_content(make_error_json("server_error", e.what()), "application/json");
    } catch (...) {
        generation_res.status = 500;
        generation_res.set_content(make_error_json("server_error", "generation job failed"), "application/json");
    }

    progress_done = true;
    progress_state.cv.notify_all();
    if (progress_thread.joinable()) {
        progress_thread.join();
    }

    std::lock_guard<std::mutex> lock(g_generation_jobs.mutex);
    job->completed_at = unix_ms_now();
    if (generation_res.status >= 200 && generation_res.status < 300) {
        try {
            job->result = diffusion_desk::json::parse(generation_res.body);
            job->status = GenerationJobStatus::Completed;
            append_generation_job_event_locked(*job, "completed", make_generation_job_json_locked(*job));
        } catch (const std::exception& e) {
            job->status = GenerationJobStatus::Failed;
            job->error_code = "invalid_generation_response";
            job->error_message = e.what();
            append_generation_job_event_locked(*job, "failed", make_generation_job_json_locked(*job));
        }
    } else {
        job->status = GenerationJobStatus::Failed;
        job->error_code = "generation_failed";
        job->error_message = generation_res.body.empty()
            ? ("generation failed with status " + std::to_string(generation_res.status))
            : generation_res.body;
        append_generation_job_event_locked(*job, "failed", make_generation_job_json_locked(*job));
    }
}

void generation_job_worker(ServerContext* ctx) {
    while (true) {
        std::shared_ptr<GenerationJob> job;
        {
            std::unique_lock<std::mutex> lock(g_generation_jobs.mutex);
            g_generation_jobs.cv.wait(lock, [] {
                return g_generation_jobs.stop || !g_generation_jobs.queue.empty();
            });
            if (g_generation_jobs.stop && g_generation_jobs.queue.empty()) {
                return;
            }
            const std::string job_id = g_generation_jobs.queue.front();
            g_generation_jobs.queue.pop_front();
            auto it = g_generation_jobs.jobs.find(job_id);
            if (it == g_generation_jobs.jobs.end() || it->second->status != GenerationJobStatus::Queued) {
                continue;
            }
            job = it->second;
        }
        execute_generation_job(job, *ctx);
    }
}

void ensure_generation_job_worker_started(ServerContext& ctx) {
    if (g_generation_jobs.started) {
        return;
    }
    g_generation_jobs.stop = false;
    g_generation_jobs.started = true;
    ServerContext* ctx_ptr = &ctx;
    g_generation_jobs.worker_thread = std::thread([ctx_ptr]() {
        generation_job_worker(ctx_ptr);
    });
}

} // namespace

void log_vram_status(const std::string& phase) {
    float proc = get_current_process_vram_usage_gb();
    float free = get_free_vram_gb();
    float total = get_total_vram_gb();
    float other = total - free - proc;
    std::string msg = "[VRAM] " + phase + " | Process: " + std::to_string(proc).substr(0, 4) + " GB, Free: " + std::to_string(free).substr(0, 4) + " GB, Other: " + std::to_string(std::max(0.0f, other)).substr(0, 4) + " GB, Total: " + std::to_string(total).substr(0, 4) + " GB\n";
    log_print(DDLogLevel::DD_LEVEL_INFO, msg.c_str(), true, true);
}

void handle_health(const httplib::Request&, httplib::Response& res, ServerContext& ctx) {
    diffusion_desk::json j;
    j["ok"] = true;
    j["service"] = "sd";
    j["version"] = version_string();
    j["model_loaded"] = (ctx.sd_ctx != nullptr);
    std::string mp = ctx.ctx_params.diffusion_model_path;
    if (mp.empty()) mp = ctx.ctx_params.model_path;
    j["model_path"] = mp;
    j["vram_allocated_mb"] = (int)(get_current_process_vram_usage_gb() * 1024.0f);
    j["vram_free_mb"] = (int)(get_free_vram_gb() * 1024.0f);
    res.set_content(j.dump(), "application/json");
}

void handle_get_config(const httplib::Request&, httplib::Response& res, ServerContext& ctx) {
    diffusion_desk::json c;
    c["output_dir"] = ctx.svr_params.output_dir;
    c["model_dir"] = ctx.svr_params.model_dir;
    c["setup_completed"] = ctx.svr_params.setup_completed;
    
    // Use standalone diffusion model if set, otherwise full model path
    std::string current_model = ctx.ctx_params.diffusion_model_path;
    if (current_model.empty()) current_model = ctx.ctx_params.model_path;
    c["model"] = current_model;
    
    res.set_content(c.dump(), "application/json");
}

void handle_post_config(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    try {
        diffusion_desk::json body = diffusion_desk::json::parse(req.body);
        if (body.contains("output_dir")) {
            ctx.svr_params.output_dir = body["output_dir"];
            DD_LOG_INFO("Config updated: output_dir = %s", ctx.svr_params.output_dir.c_str());
        }
        if (body.contains("model_dir")) {
            ctx.svr_params.model_dir = body["model_dir"];
            DD_LOG_INFO("Config updated: model_dir = %s", ctx.svr_params.model_dir.c_str());
        }
        if (body.contains("setup_completed")) {
            ctx.svr_params.setup_completed = body["setup_completed"];
        }
        res.set_content(R"({\"status\":\"success\"})", "application/json");
    } catch (const std::exception& e) {
        res.status = 400;
        res.set_content(make_error_json("invalid_json", e.what()), "application/json");
    }
}

void handle_get_sampler_options(const httplib::Request&, httplib::Response& res) {
    diffusion_desk::json samplers = diffusion_desk::json::array();
    for (int i = 0; i < SAMPLE_METHOD_COUNT; ++i) {
        const char* name = sd_sample_method_name(static_cast<sample_method_t>(i));
        if (name != nullptr && name[0] != '\0') {
            samplers.push_back(name);
        }
    }

    diffusion_desk::json out;
    out["data"] = samplers;
    res.set_content(out.dump(), "application/json");
}

void handle_get_progress(const httplib::Request&, httplib::Response& res) {
    diffusion_desk::json r;
    {
        std::lock_guard<std::mutex> lock(progress_state.mutex);
        r["step"] = progress_state.step;
        r["steps"] = progress_state.steps;
        r["time"] = progress_state.time;
        r["message"] = progress_state.message;
    }
    res.set_content(r.dump(), "application/json");
}

void handle_stream_progress(const httplib::Request& req, httplib::Response& res) {
    DD_LOG_INFO("New progress stream subscription from %s", req.remote_addr.c_str());
    res.set_header("Cache-Control", "no-cache");
    res.set_header("Connection", "keep-alive");
    res.set_header("X-Accel-Buffering", "no"); // Disable proxy buffering

    res.set_chunked_content_provider("text/event-stream", [&](size_t offset, httplib::DataSink &sink) {
        uint64_t last_version = 0;
        int step = 0;
        int steps = 0;
        float time = 0;
        std::string phase = "";
        std::string message = "";

        // Send initial state or at least a comment to open the stream
        {
            std::lock_guard<std::mutex> lock(progress_state.mutex);
            last_version = progress_state.version;
            step = progress_state.step;
            steps = progress_state.steps;
            time = progress_state.time;
            phase = progress_state.phase;
            message = progress_state.message;
        }
        
        diffusion_desk::json initial_j;
        initial_j["step"] = step;
        initial_j["steps"] = steps;
        initial_j["time"] = time;
        initial_j["phase"] = phase;
        initial_j["message"] = message;
        std::string initial_s = "data: " + initial_j.dump() + "\n\n";
        if (!sink.write(initial_s.c_str(), initial_s.size())) return false;

        while (true) {
            std::unique_lock<std::mutex> lock(progress_state.mutex);
            // Wait for a new version, or a timeout to send a keep-alive
            if (!progress_state.cv.wait_for(lock, std::chrono::seconds(15), 
                [&]{ return progress_state.version > last_version; })) {
                // Timeout - send keep-alive comment (ping)
                lock.unlock();
                if (!sink.write(": ping\n\n", 9)) return false;
                continue;
            }
            
            step = progress_state.step;
            steps = progress_state.steps;
            time = progress_state.time;
            phase = progress_state.phase;
            message = progress_state.message;
            last_version = progress_state.version;
            lock.unlock();

            diffusion_desk::json j;
            j["step"] = step;
            j["steps"] = steps;
            j["time"] = time;
            j["phase"] = phase;
            j["message"] = message;
            std::string s = "data: " + j.dump() + "\n\n";
            if (!sink.write(s.c_str(), s.size())) {
                return false;
            }
        }
        return true;
    });
}

void handle_submit_generation_job(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    try {
        diffusion_desk::json::parse(req.body);
    } catch (const std::exception& e) {
        res.status = 400;
        res.set_content(make_error_json("invalid_json", e.what()), "application/json");
        return;
    }

    std::shared_ptr<GenerationJob> job = std::make_shared<GenerationJob>();
    diffusion_desk::json out;

    {
        std::lock_guard<std::mutex> lock(g_generation_jobs.mutex);
        if (count_pending_generation_jobs_locked() >= g_generation_jobs.max_pending_jobs) {
            res.status = 429;
            res.set_content(make_error_json("queue_full", "generation job queue is full"), "application/json");
            return;
        }

        ensure_generation_job_worker_started(ctx);
        job->id = make_generation_job_id_locked();
        job->request_body = req.body;
        g_generation_jobs.jobs[job->id] = job;
        g_generation_jobs.queue.push_back(job->id);
        append_generation_job_event_locked(*job, "queued", make_generation_job_json_locked(*job));

        out["id"] = job->id;
        out["status"] = generation_job_status_name(job->status);
        out["created_at"] = job->created_at;
        out["status_url"] = "/v1/generation-jobs/" + job->id;
        out["events_url"] = "/v1/generation-jobs/" + job->id + "/events";
    }

    g_generation_jobs.cv.notify_all();

    res.status = 202;
    res.set_content(out.dump(), "application/json");
}

void handle_get_generation_job(const httplib::Request& req, httplib::Response& res) {
    const std::string job_id = req.matches[1];
    std::lock_guard<std::mutex> lock(g_generation_jobs.mutex);
    auto it = g_generation_jobs.jobs.find(job_id);
    if (it == g_generation_jobs.jobs.end()) {
        res.status = 404;
        res.set_content(make_error_json("not_found", "generation job not found"), "application/json");
        return;
    }
    res.status = 200;
    res.set_content(make_generation_job_json_locked(*it->second).dump(), "application/json");
}

void handle_stream_generation_job_events(const httplib::Request& req, httplib::Response& res) {
    const std::string job_id = req.matches[1];
    {
        std::lock_guard<std::mutex> lock(g_generation_jobs.mutex);
        if (g_generation_jobs.jobs.find(job_id) == g_generation_jobs.jobs.end()) {
            res.status = 404;
            res.set_content(make_error_json("not_found", "generation job not found"), "application/json");
            return;
        }
    }

    DD_LOG_INFO("New generation job event stream subscription for %s", job_id.c_str());
    res.set_header("Cache-Control", "no-cache");
    res.set_header("Connection", "keep-alive");
    res.set_header("X-Accel-Buffering", "no");

    res.set_chunked_content_provider("text/event-stream", [job_id](size_t, httplib::DataSink& sink) {
        size_t next_event_index = 0;

        while (true) {
            std::vector<GenerationJobEvent> events;
            bool terminal = false;
            {
                std::unique_lock<std::mutex> lock(g_generation_jobs.mutex);
                auto it = g_generation_jobs.jobs.find(job_id);
                if (it == g_generation_jobs.jobs.end()) {
                    sink.done();
                    return false;
                }

                g_generation_jobs.cv.wait_for(lock, std::chrono::seconds(15), [&] {
                    auto current = g_generation_jobs.jobs.find(job_id);
                    return current == g_generation_jobs.jobs.end() ||
                           current->second->events.size() > next_event_index ||
                           generation_job_status_terminal(current->second->status);
                });

                it = g_generation_jobs.jobs.find(job_id);
                if (it == g_generation_jobs.jobs.end()) {
                    sink.done();
                    return false;
                }

                auto& job = *it->second;
                terminal = generation_job_status_terminal(job.status);
                if (job.events.size() > next_event_index) {
                    events.assign(job.events.begin() + static_cast<std::ptrdiff_t>(next_event_index), job.events.end());
                    next_event_index = job.events.size();
                }
            }

            if (events.empty()) {
                if (terminal) {
                    sink.done();
                    return false;
                }
                const char ping[] = ": ping\n\n";
                if (!sink.write(ping, sizeof(ping) - 1)) {
                    return false;
                }
                continue;
            }

            for (const auto& event : events) {
                if (!write_sse_event(sink, event)) {
                    return false;
                }
            }

            if (terminal) {
                sink.done();
                return false;
            }
        }
    });
}

void handle_cancel_generation_job(const httplib::Request& req, httplib::Response& res) {
    const std::string job_id = req.matches[1];
    std::lock_guard<std::mutex> lock(g_generation_jobs.mutex);
    auto it = g_generation_jobs.jobs.find(job_id);
    if (it == g_generation_jobs.jobs.end()) {
        res.status = 404;
        res.set_content(make_error_json("not_found", "generation job not found"), "application/json");
        return;
    }

    GenerationJob& job = *it->second;
    if (job.status == GenerationJobStatus::Queued) {
        auto new_end = std::remove(g_generation_jobs.queue.begin(), g_generation_jobs.queue.end(), job.id);
        if (new_end == g_generation_jobs.queue.end()) {
            res.status = 409;
            res.set_content(make_error_json("queue_changed", "generation job queue changed before cancellation"), "application/json");
            return;
        }
        g_generation_jobs.queue.erase(new_end, g_generation_jobs.queue.end());
        job.status = GenerationJobStatus::Cancelled;
        job.completed_at = unix_ms_now();
        job.error_code = "cancelled";
        job.error_message = "generation job cancelled by client";
        append_generation_job_event_locked(job, "cancelled", make_generation_job_json_locked(job));
        res.status = 200;
        res.set_content(make_generation_job_json_locked(job).dump(), "application/json");
        return;
    }

    if (job.status == GenerationJobStatus::Processing) {
        res.status = 409;
        res.set_content(make_error_json("already_processing", "generation job is already processing and cannot be interrupted"), "application/json");
        return;
    }

    res.status = 200;
    res.set_content(make_generation_job_json_locked(job).dump(), "application/json");
}

void shutdown_generation_jobs() {
    {
        std::lock_guard<std::mutex> lock(g_generation_jobs.mutex);
        g_generation_jobs.stop = true;
        while (!g_generation_jobs.queue.empty()) {
            const std::string job_id = g_generation_jobs.queue.front();
            g_generation_jobs.queue.pop_front();
            auto it = g_generation_jobs.jobs.find(job_id);
            if (it != g_generation_jobs.jobs.end() && it->second->status == GenerationJobStatus::Queued) {
                it->second->status = GenerationJobStatus::Cancelled;
                it->second->completed_at = unix_ms_now();
                it->second->error_code = "cancelled";
                it->second->error_message = "generation job cancelled by worker shutdown";
                append_generation_job_event_locked(*it->second, "cancelled", make_generation_job_json_locked(*it->second));
            }
        }
        g_generation_jobs.cv.notify_all();
    }
    if (g_generation_jobs.worker_thread.joinable()) {
        g_generation_jobs.worker_thread.join();
    }
    {
        std::lock_guard<std::mutex> lock(g_generation_jobs.mutex);
        g_generation_jobs.started = false;
    }
}

// We will move other handlers implementation in next steps to keep this manageable
void handle_get_outputs(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    std::string file_name = req.matches[1];
    fs::path file_path = fs::path(ctx.svr_params.output_dir) / file_name;

    if (fs::exists(file_path) && fs::is_regular_file(file_path)) {
        std::ifstream ifs(file_path, std::ios::binary);
        std::string content((std::istreambuf_iterator<char>(ifs)), std::istreambuf_iterator<char>());
        
        std::string ext = file_path.extension().string();
        std::string mime = "application/octet-stream";
        if (ext == ".png") mime = "image/png";
        else if (ext == ".jpg" || ext == ".jpeg") mime = "image/jpeg";
        else if (ext == ".json") mime = "application/json";
        
        res.set_content(content, mime.c_str());
    } else {
        res.status = 404;
    }
}

void handle_get_models(const httplib::Request&, httplib::Response& res, ServerContext& ctx) {
    diffusion_desk::json r;
    r["data"] = diffusion_desk::json::array();
    
    std::string current_model_name = fs::path(ctx.ctx_params.diffusion_model_path).filename().string();
    if (current_model_name.empty()) {
        current_model_name = fs::path(ctx.ctx_params.model_path).filename().string();
    }

    auto scan_dir = [&](const std::string& sub_dir) {
        fs::path base_path = fs::path(ctx.svr_params.model_dir) / sub_dir;
        if (fs::exists(base_path) && fs::is_directory(base_path)) {
            for (const auto& entry : fs::recursive_directory_iterator(base_path)) {
                if (entry.is_regular_file()) {
                    auto ext = entry.path().extension().string();
                    if (ext == ".gguf" || ext == ".safetensors" || ext == ".ckpt" || ext == ".pth") {
                        diffusion_desk::json model;
                        // Use relative path from model_dir as ID for easy loading
                        std::string rel_path = fs::relative(entry.path(), ctx.svr_params.model_dir).string();
                        std::replace(rel_path.begin(), rel_path.end(), '\\', '/');

                        model["id"] = rel_path;
                        model["name"] = entry.path().filename().string();
                        model["type"] = sub_dir;
                        model["object"] = "model";
                        model["owned_by"] = "local";
                        
                        bool isActive = false;
                        bool isLoaded = false;
                        if (sub_dir == "llm") {
                            isActive = (rel_path == ctx.active_llm_model_path);
                            isLoaded = isActive && ctx.active_llm_model_loaded;
                        } else if (sub_dir == "esrgan") {
                            isActive = (rel_path == ctx.current_upscale_model_path);
                            isLoaded = isActive; // Assuming for now
                        } else {
                            isActive = (model["name"] == current_model_name);
                            isLoaded = isActive; // SD models are loaded if active in this context
                        }

                        model["active"] = isActive;
                        model["loaded"] = isLoaded;
                        r["data"].push_back(model);
                    }
                }
            }
        }
    };

    try {
        scan_dir("stable-diffusion");
        scan_dir("diffusion_models");
        scan_dir("unet");
        scan_dir("lora");
        scan_dir("vae");
        scan_dir("text-encoder");
        scan_dir("text_encoders");
        scan_dir("clip");
        scan_dir("llm");
        scan_dir("esrgan");
        
        // Also scan root of model_dir for convenience
        if (fs::exists(ctx.svr_params.model_dir) && fs::is_directory(ctx.svr_params.model_dir)) {
            for (const auto& entry : fs::directory_iterator(ctx.svr_params.model_dir)) {
                if (entry.is_regular_file()) {
                    auto ext = entry.path().extension().string();
                    if (ext == ".gguf" || ext == ".safetensors" || ext == ".ckpt") {
                        diffusion_desk::json model;
                        model["id"] = entry.path().filename().string();
                        model["name"] = entry.path().filename().string();
                        model["type"] = "root";
                        model["object"] = "model";
                        model["owned_by"] = "local";
                        model["active"] = (model["id"] == current_model_name);
                        r["data"].push_back(model);
                    }
                }
            }
        }
    } catch (const std::exception& e) {
        DD_LOG_ERROR("failed to list models: %s", e.what());
    }

    res.set_content(r.dump(), "application/json");
}

void handle_load_model(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    try {
        diffusion_desk::json body = diffusion_desk::json::parse(req.body);
        if (!body.contains("model_id")) {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "model_id path required"), "application/json");
            return;
        }
        std::string model_id = body["model_id"];
        fs::path model_path = resolve_model_path(model_id, ctx.svr_params.model_dir);
        
        if (!fs::exists(model_path)) {
            res.status = 404;
            res.set_content(make_error_json("model_not_found", "model file not found at " + model_path.string()), "application/json");
            return;
        }

        DD_LOG_INFO("Loading new model: %s", model_path.string().c_str());

        {
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            
            // Smart pointer reset handles freeing old context
            ctx.sd_ctx.reset();

            // Update params based on where it was found
            std::string rel_s = model_id;
            if (rel_s.find("vae/") == 0) {
                    ctx.ctx_params.vae_path = model_path.string();
            } else if (rel_s.find("esrgan/") == 0) {
                    ctx.ctx_params.esrgan_path = model_path.string();
            } else {
                    // Main model load - Reset optional paths and flags
                    ctx.ctx_params.diffusion_model_path = model_path.string();
                    ctx.ctx_params.uncond_diffusion_model_path = "";
                    ctx.ctx_params.model_path = "";
                    ctx.ctx_params.vae_path = "";
                    ctx.ctx_params.clip_l_path = "";
                    ctx.ctx_params.clip_g_path = "";
                    ctx.ctx_params.t5xxl_path = "";
                    ctx.ctx_params.llm_path = "";
                    ctx.ctx_params.diffusion_flash_attn = false;
                    ctx.ctx_params.clip_on_cpu = false;
                    ctx.ctx_params.vae_on_cpu = false;
                    ctx.ctx_params.offload_params_to_cpu = false;
                    ctx.ctx_params.vae_tiling_params.enabled = false;
                    ctx.ctx_params.max_vram = 0.0f;
                    ctx.ctx_params.stream_layers = false;
                    ctx.ctx_params.prediction = PREDICTION_COUNT;
                    ctx.ctx_params.flow_shift = INFINITY;

                    auto resolve_path = [&](const std::string& p, const std::string& base) -> std::string {
                        if (p.empty()) return "";
                        fs::path fp(p);
                        if (fp.is_absolute()) return p;
                        return (fs::path(base) / p).string();
                    };

                    // Parse config from request body (replaces load_model_config)
                    if (body.contains("vae") && body["vae"].is_string()) ctx.ctx_params.vae_path = resolve_path(body["vae"].get<std::string>(), ctx.svr_params.model_dir);
                    if (body.contains("clip_l") && body["clip_l"].is_string()) ctx.ctx_params.clip_l_path = resolve_path(body["clip_l"].get<std::string>(), ctx.svr_params.model_dir);
                    if (body.contains("clip_g") && body["clip_g"].is_string()) ctx.ctx_params.clip_g_path = resolve_path(body["clip_g"].get<std::string>(), ctx.svr_params.model_dir);
                    if (body.contains("t5xxl") && body["t5xxl"].is_string()) ctx.ctx_params.t5xxl_path = resolve_path(body["t5xxl"].get<std::string>(), ctx.svr_params.model_dir);
                    if (body.contains("llm") && body["llm"].is_string()) ctx.ctx_params.llm_path = resolve_path(body["llm"].get<std::string>(), ctx.svr_params.model_dir);
                    if (body.contains("uncond_diffusion_model") && body["uncond_diffusion_model"].is_string()) ctx.ctx_params.uncond_diffusion_model_path = resolve_path(body["uncond_diffusion_model"].get<std::string>(), ctx.svr_params.model_dir);
                    if (body.contains("uncond_diffusion_model_path") && body["uncond_diffusion_model_path"].is_string()) ctx.ctx_params.uncond_diffusion_model_path = resolve_path(body["uncond_diffusion_model_path"].get<std::string>(), ctx.svr_params.model_dir);

                    if (body.contains("vae_tiling")) ctx.ctx_params.vae_tiling_params.enabled = body["vae_tiling"];
                    if (body.contains("clip_on_cpu")) ctx.ctx_params.clip_on_cpu = body["clip_on_cpu"];
                    if (body.contains("vae_on_cpu")) ctx.ctx_params.vae_on_cpu = body["vae_on_cpu"];
                    if (body.contains("offload_to_cpu")) ctx.ctx_params.offload_params_to_cpu = body["offload_to_cpu"];
                    if (body.contains("offload_params_to_cpu")) ctx.ctx_params.offload_params_to_cpu = body["offload_params_to_cpu"];
                    if (body.contains("flash_attn")) ctx.ctx_params.diffusion_flash_attn = body["flash_attn"];
                    if (body.contains("diffusion_flash_attn")) ctx.ctx_params.diffusion_flash_attn = body["diffusion_flash_attn"];
                    if (body.contains("max_vram") && body["max_vram"].is_number()) ctx.ctx_params.max_vram = body["max_vram"].get<float>();
                    if (body.contains("max_vram_gb") && body["max_vram_gb"].is_number()) ctx.ctx_params.max_vram = body["max_vram_gb"].get<float>();

                    if (body.contains("stream_layers")) {
                        ctx.ctx_params.stream_layers = body["stream_layers"];
                    } else {
                        ctx.ctx_params.stream_layers = ctx.ctx_params.offload_params_to_cpu && ctx.ctx_params.max_vram != 0.0f;
                    }
                    sanitize_context_params_for_backend(ctx.ctx_params);

                    auto validate_component_path = [&](const char* field_name, const std::string& path) {
                        if (path.empty() || fs::exists(path)) return true;
                        res.status = 404;
                        res.set_content(make_error_json("model_component_not_found", std::string(field_name) + " file not found at " + path), "application/json");
                        return false;
                    };

                    if (!validate_component_path("vae", ctx.ctx_params.vae_path)) return;
                    if (!validate_component_path("clip_l", ctx.ctx_params.clip_l_path)) return;
                    if (!validate_component_path("clip_g", ctx.ctx_params.clip_g_path)) return;
                    if (!validate_component_path("t5xxl", ctx.ctx_params.t5xxl_path)) return;
                    if (!validate_component_path("llm", ctx.ctx_params.llm_path)) return;
                    if (!validate_component_path("uncond_diffusion_model", ctx.ctx_params.uncond_diffusion_model_path)) return;
            }

            sd_ctx_params_t sd_ctx_p = make_sd_ctx_params(ctx.ctx_params, true);
            ctx.sd_ctx.reset(new_sd_ctx(&sd_ctx_p));
            ctx.sd_ctx_vae_decode_only = true;

            if (!ctx.sd_ctx) {
                throw std::runtime_error("failed to create new context with selected model");
            }
        }

        diffusion_desk::json response = {
            {"status", "success"},
            {"model", model_id},
            {"max_vram_gb", ctx.ctx_params.max_vram},
            {"stream_layers", ctx.ctx_params.stream_layers},
            {"offload_params_to_cpu", ctx.ctx_params.offload_params_to_cpu}
        };
        res.set_content(response.dump(), "application/json");

    } catch (const std::exception& e) {
        DD_LOG_ERROR("error loading model: %s", e.what());
        res.status = 500;
        res.set_content(R"({\"error\":\")" + std::string(e.what()) + R"("})", "application/json");
    }
}

void handle_unload_model(httplib::Response& res, ServerContext& ctx) {
    DD_LOG_INFO("Unloading Image model to free VRAM.");
    try {
        std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
        ctx.sd_ctx.reset();
        
        // Reset path state so it's clearly empty
        ctx.ctx_params.diffusion_model_path = "";
        ctx.ctx_params.model_path = "";
        ctx.ctx_params.vae_path = "";
        ctx.ctx_params.clip_l_path = "";
        ctx.ctx_params.clip_g_path = "";
        ctx.ctx_params.t5xxl_path = "";
        
        res.set_content(R"({\"status\":\"success\",\"message\":\"Model unloaded\"})", "application/json");
    } catch (const std::exception& e) {
        DD_LOG_ERROR("Failed to unload model: %s", e.what());
        res.status = 500;
        res.set_content(R"({\"error\":\")" + std::string(e.what()) + R"("})", "application/json");
    }
}

void handle_offload_model(httplib::Response& res, ServerContext& ctx) {
    DD_LOG_INFO("Offloading Image model to CPU/RAM.");
    try {
        std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
        if (!ctx.sd_ctx) {
            res.set_content(R"({\"status\":\"success\",\"message\":\"No model loaded\"})", "application/json");
            return;
        }

        // We reload the current model with offload_params_to_cpu = true
        auto params = ctx.ctx_params;
        params.offload_params_to_cpu = true;
        sanitize_context_params_for_backend(params);

        DD_LOG_INFO("Re-initializing SD context with CPU offloading...");
        auto sd_params = make_sd_ctx_params(params, ctx.sd_ctx_vae_decode_only);
        ctx.sd_ctx.reset(new_sd_ctx(&sd_params));

        if (ctx.sd_ctx) {
            ctx.ctx_params = params;
            res.set_content(R"({\"status\":\"success\",\"message\":\"Model offloaded to CPU\"})", "application/json");
        } else {
            res.status = 500;
            res.set_content(R"({\"error\":\"Failed to re-initialize model for offloading\"})", "application/json");
        }
    } catch (const std::exception& e) {
        DD_LOG_ERROR("Failed to offload model: %s", e.what());
        res.status = 500;
        res.set_content(R"({\"error\":\")" + std::string(e.what()) + R"("})", "application/json");
    }
}

void handle_load_upscale_model(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    try {
        diffusion_desk::json body = diffusion_desk::json::parse(req.body);
        if (!body.contains("model_id")) {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "model_id required"), "application/json");
            return;
        }
        std::string model_id = body["model_id"];
        fs::path model_path = fs::path(ctx.svr_params.model_dir) / model_id;
        
        if (!fs::exists(model_path)) {
            res.status = 404;
            res.set_content(make_error_json("model_not_found", "upscale model not found"), "application/json");
            return;
        }

        DD_LOG_INFO("Loading upscale model: %s", model_path.string().c_str());

        {
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            ctx.upscaler_ctx.reset(new_upscaler_ctx(model_path.string().c_str(),
                                            ctx.ctx_params.offload_params_to_cpu,
                                            false, // direct
                                            ctx.ctx_params.n_threads,
                                            512, // tile_size
                                            nullptr, // backend
                                            nullptr)); // params_backend

            if (!ctx.upscaler_ctx) {
                throw std::runtime_error("failed to create upscaler context");
            }
            ctx.current_upscale_model_path = model_id;
        }

        res.set_content(R"({\"status\":\"success\",\"model\":\")" + model_id + R"("})", "application/json");
    } catch (const std::exception& e) {
        DD_LOG_ERROR("error loading upscale model: %s", e.what());
        res.status = 500;
        res.set_content(R"({\"error\":\")" + std::string(e.what()) + R"("})", "application/json");
    }
}

void handle_upscale_image(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    try {
        diffusion_desk::json body = diffusion_desk::json::parse(req.body);
        std::string image_data;
        std::string image_name;

        if (body.contains("image")) {
            image_data = body["image"];
            if (image_data.find("base64,") != std::string::npos) {
                image_data = image_data.substr(image_data.find("base64,") + 7);
            }
        } else if (body.contains("image_name")) {
            image_name = body["image_name"];
            fs::path img_path = fs::path(ctx.svr_params.output_dir) / image_name;
            if (!fs::exists(img_path)) {
                res.status = 404;
                res.set_content(make_error_json("image_not_found"), "application/json");
                return;
            }
            std::ifstream ifs(img_path, std::ios::binary);
            image_data = std::string((std::istreambuf_iterator<char>(ifs)), std::istreambuf_iterator<char>());
        } else {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "image (base64) or image_name required"), "application/json");
            return;
        }

        uint32_t upscale_factor = body.value("upscale_factor", 0);

        sd_image_t input_image = {0, 0, 3, nullptr};
        std::vector<uint8_t> decoded_bytes;
        
        if (!image_name.empty()) {
            int w, h;
            input_image.data = load_image_from_memory(image_data.data(), (int)image_data.size(), w, h, 0, 0, 3);
            input_image.width = (uint32_t)w;
            input_image.height = (uint32_t)h;
        } else {
            decoded_bytes = base64_decode(image_data);
            int w, h;
            input_image.data = load_image_from_memory((const char*)decoded_bytes.data(), (int)decoded_bytes.size(), w, h, 0, 0, 3);
            input_image.width = (uint32_t)w;
            input_image.height = (uint32_t)h;
        }

        if (!input_image.data) {
            res.status = 400;
            res.set_content(make_error_json("decode_failed", "failed to decode image"), "application/json");
            return;
        }

        sd_image_t upscaled_image = {0};
        {
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            if (!ctx.upscaler_ctx) {
                stbi_image_free(input_image.data);
                res.status = 400;
                res.set_content(make_error_json("no_model", "no upscale model loaded"), "application/json");
                return;
            }
            
            if (upscale_factor == 0) {
                upscale_factor = get_upscale_factor(ctx.upscaler_ctx.get());
            }

            DD_LOG_INFO("Upscaling image: %dx%d -> factor %d", input_image.width, input_image.height, upscale_factor);
            upscaled_image = upscale(ctx.upscaler_ctx.get(), input_image, upscale_factor);
        }

        stbi_image_free(input_image.data);

        if (!upscaled_image.data) {
            res.status = 500;
            res.set_content(make_error_json("upscale_failed"), "application/json");
            return;
        }

        auto image_bytes = write_image_to_vector(ImageFormat::PNG,
                                                    upscaled_image.data,
                                                    (int)upscaled_image.width,
                                                    (int)upscaled_image.height,
                                                    (int)upscaled_image.channel);
        
        free(upscaled_image.data);

        if (image_bytes.empty()) {
            res.status = 500;
            res.set_content(make_error_json("encode_failed", "failed to encode upscaled image"), "application/json");
            return;
        }

        diffusion_desk::json out;
        out["width"] = upscaled_image.width;
        out["height"] = upscaled_image.height;

        bool save_image = body.value("save_image", true);
        
        // Always save to disk now (either persistent or temp) to serve via URL
        std::string final_output_dir = ctx.svr_params.output_dir;
        std::string url_prefix = "/outputs/";
        
        if (!save_image) {
            final_output_dir = (fs::path(ctx.svr_params.output_dir) / "temp").string();
            url_prefix = "/outputs/temp/";
            if (!fs::exists(final_output_dir)) {
                fs::create_directories(final_output_dir);
            }
        }

        auto timestamp = std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
        std::string out_name = "upscale-" + std::to_string(timestamp) + ".png";
        fs::path out_path = fs::path(final_output_dir) / out_name;
        
        std::ofstream ofs(out_path, std::ios::binary);
        ofs.write((const char*)image_bytes.data(), image_bytes.size());
        
        out["url"] = url_prefix + out_name;
        out["name"] = out_name;
        DD_LOG_INFO("Saved upscaled image to %s", out_path.string().c_str());

        res.set_content(out.dump(), "application/json");

    } catch (const std::exception& e) {
        DD_LOG_ERROR("error during upscaling: %s", e.what());
        res.status = 500;
        res.set_content(R"({\"error\":\")" + std::string(e.what()) + R"("})", "application/json");
    }
}

void handle_get_history(const httplib::Request&, httplib::Response& res, ServerContext& ctx) {
    const std::string output_dir = ctx.svr_params.output_dir;
    diffusion_desk::json image_list = diffusion_desk::json::array();
    try {
        if (fs::exists(output_dir) && fs::is_directory(output_dir)) {
            std::vector<fs::path> image_paths;
            for (const auto& entry : fs::directory_iterator(output_dir)) {
                if (entry.is_regular_file()) {
                    auto ext = entry.path().extension().string();
                    if (ext == ".png" || ext == ".jpg" || ext == ".jpeg") {
                        image_paths.push_back(entry.path());
                    }
                }
            }
            std::sort(image_paths.begin(), image_paths.end(), [](const fs::path& a, const fs::path& b) {
                return a.filename().string() > b.filename().string();
            });

            for(const auto& img_path : image_paths) {
                diffusion_desk::json item;
                item["name"] = img_path.filename().string();
                
                auto txt_path = img_path;
                txt_path.replace_extension(".txt");
                if (fs::exists(txt_path)) {
                    try {
                        std::ifstream txt_file(txt_path);
                        std::string content((std::istreambuf_iterator<char>(txt_file)),
                                            (std::istreambuf_iterator<char>()));
                        item["params"] = parse_image_params(content);
                    } catch (...) {
                        DD_LOG_WARN("failed to parse txt metadata: %s", txt_path.string().c_str());
                    }
                } else {
                    auto json_path = img_path;
                    json_path.replace_extension(".json");
                    if (fs::exists(json_path)) {
                        try {
                            std::ifstream json_file(json_path);
                            item["params"] = diffusion_desk::json::parse(json_file);
                        } catch (...) {
                            DD_LOG_WARN("failed to parse json metadata: %s", json_path.string().c_str());
                        }
                    }
                }
                image_list.push_back(item);
            }
        }
    } catch (const std::exception& e) {
        DD_LOG_ERROR("failed to list image history: %s", e.what());
        res.status = 500;
        res.set_content(make_error_json("server_error", "failed to list image history"), "application/json");
        return;
    }
    res.set_content(image_list.dump(), "application/json");
}

void handle_generate_image(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    reset_progress();
    DD_LOG_INFO("New generation request received");
    try {
        if (req.body.empty()) {
            res.status = 400;
            res.set_content(make_error_json("empty_body"), "application/json");
            return;
        }

        diffusion_desk::json j             = diffusion_desk::json::parse(req.body);
        std::string prompt        = j.value("prompt", "");
        int n                     = std::max(1, (int)j.value("n", 1));
        std::string size          = j.value("size", "");
        std::string output_format = j.value("output_format", "png");
        int output_compression    = j.value("output_compression", 100);
        int width                 = 512;
        int height                = 512;
        if (!size.empty()) {
            auto pos = size.find('x');
            if (pos != std::string::npos) {
                try {
                    width  = std::stoi(size.substr(0, pos));
                    height = std::stoi(size.substr(pos + 1));
                } catch (...) {
                }
            }
        }

        if (prompt.empty()) {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "prompt required"), "application/json");
            return;
        }

        if (output_format != "png" && output_format != "jpeg") {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "invalid output_format, must be one of [png, jpeg]"), "application/json");
            return;
        }
        if (n <= 0)
            n = 1;
        if (n > 8)
            n = 8;  // safety
        if (output_compression > 100) {
            output_compression = 100;
        }
        if (output_compression < 0) {
            output_compression = 0;
        }

        double total_generation_time = 0;
        diffusion_desk::json out;
        out["created"]       = iso_timestamp_now();
        out["data"]          = diffusion_desk::json::array();
        out["output_format"] = output_format;
        out["generation_time"] = total_generation_time;

        SDGenerationParams gen_params = ctx.default_gen_params;
        gen_params.prompt             = prompt;
        gen_params.width              = width;
        gen_params.height             = height;
        gen_params.batch_count        = n;

        if (!gen_params.from_json_str(req.body)) {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "invalid params"), "application/json");
            return;
        }

        const bool request_vae_decode_only = !generation_needs_vae_encode(j, gen_params);
        if (ctx.sd_ctx && request_vae_decode_only != ctx.sd_ctx_vae_decode_only) {
            DD_LOG_INFO("Switching VAE load mode to %s for this generation...",
                        request_vae_decode_only ? "decode-only" : "full encode/decode");
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            ctx.sd_ctx.reset();
            sd_ctx_params_t sd_ctx_p = make_sd_ctx_params(ctx.ctx_params, request_vae_decode_only);
            ctx.sd_ctx.reset(new_sd_ctx(&sd_ctx_p));
            if (!ctx.sd_ctx) {
                res.status = 500;
                res.set_content(make_error_json("model_reload_failed", "failed to reload model after VAE load-mode change"), "application/json");
                return;
            }
            ctx.sd_ctx_vae_decode_only = request_vae_decode_only;
        }

        // B2.5: Dynamic Text Encoder Offloading
        // Omitted placement fields mean "keep the loaded context". The desktop app
        // loads presets separately, then sends lean generation payloads.
        bool request_clip_on_cpu = j.value("clip_on_cpu", ctx.ctx_params.clip_on_cpu);
        if (request_clip_on_cpu != ctx.ctx_params.clip_on_cpu) {
            DD_LOG_INFO("Switching CLIP to %s for this generation...", request_clip_on_cpu ? "CPU" : "GPU");
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            ctx.sd_ctx.reset();
            ctx.ctx_params.clip_on_cpu = request_clip_on_cpu;
            sanitize_context_params_for_backend(ctx.ctx_params);
            sd_ctx_params_t sd_ctx_p = make_sd_ctx_params(ctx.ctx_params, ctx.sd_ctx_vae_decode_only);
            ctx.sd_ctx.reset(new_sd_ctx(&sd_ctx_p));
            if (!ctx.sd_ctx) {
                res.status = 500;
                res.set_content(make_error_json("model_reload_failed", "failed to reload model after CLIP placement change"), "application/json");
                return;
            }
        }
        gen_params.clip_on_cpu = request_clip_on_cpu;

        // B2.6: Dynamic VAE Offloading (Force CPU/FP32 for high res to prevent NaNs)
        bool request_vae_on_cpu = j.value("vae_on_cpu", ctx.ctx_params.vae_on_cpu);
        float mp_check = (float)(gen_params.width * gen_params.height) / (1024.0f * 1024.0f);
        if (mp_check >= 3.0f && !request_vae_on_cpu) {
             DD_LOG_INFO("High resolution detected (%.2f MP). Forcing VAE to CPU to avoid NaN/static artifacts.", mp_check);
             request_vae_on_cpu = true;
        }

        // B2.7: Dynamic Model Weight Offloading (Crucial for ultra-high res)
        bool request_offload = j.value("offload_params_to_cpu",
                               j.value("offload_to_cpu", ctx.ctx_params.offload_params_to_cpu));
        if (mp_check >= 4.0f && !request_offload) {
             DD_LOG_INFO("Ultra-high resolution detected (%.2f MP). Enabling model weight offloading to prevent OOM.", mp_check);
             request_offload = true;
        }
        if (request_vae_on_cpu != ctx.ctx_params.vae_on_cpu || request_offload != ctx.ctx_params.offload_params_to_cpu) {
            DD_LOG_INFO("Context update required: VAE on CPU: %s, Offload: %s", request_vae_on_cpu ? "Yes" : "No", request_offload ? "Yes" : "No");
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            ctx.sd_ctx.reset();
            ctx.ctx_params.vae_on_cpu = request_vae_on_cpu;
            ctx.ctx_params.offload_params_to_cpu = request_offload;
            sanitize_context_params_for_backend(ctx.ctx_params);
            sd_ctx_params_t sd_ctx_p = make_sd_ctx_params(ctx.ctx_params, ctx.sd_ctx_vae_decode_only);
            ctx.sd_ctx.reset(new_sd_ctx(&sd_ctx_p));
            if (!ctx.sd_ctx) {
                res.status = 500;
                res.set_content(make_error_json("model_reload_failed", "failed to reload model after placement change"), "application/json");
                return;
            }
        }

        bool save_image = j.value("save_image", false);

        std::string lora_dir = ctx.ctx_params.lora_model_dir;
        if (lora_dir.empty()) {
            lora_dir = (fs::path(ctx.svr_params.model_dir) / "lora").string();
        }

        if (!gen_params.process_and_check(IMG_GEN, lora_dir)) {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "invalid params"), "application/json");
            return;
        }

        if (gen_params.highres_pid_fix) {
            gen_params.highres_pid_model = resolve_model_path(gen_params.highres_pid_model, ctx.svr_params.model_dir);
            gen_params.highres_pid_llm = resolve_model_path(gen_params.highres_pid_llm, ctx.svr_params.model_dir);
            if (gen_params.highres_pid_vae.empty()) {
                gen_params.highres_pid_vae = ctx.ctx_params.vae_path;
            } else {
                gen_params.highres_pid_vae = resolve_model_path(gen_params.highres_pid_vae, ctx.svr_params.model_dir);
            }

            if (!fs::exists(gen_params.highres_pid_model) ||
                !fs::exists(gen_params.highres_pid_llm) ||
                !fs::exists(gen_params.highres_pid_vae)) {
                res.status = 400;
                res.set_content(make_error_json("invalid_request", "Highres PiD is enabled, but one or more PiD model paths do not exist."), "application/json");
                return;
            }

            if (sd_vae_format_from_string(gen_params.highres_pid_vae_format) == SD_VAE_FORMAT_COUNT) {
                res.status = 400;
                res.set_content(make_error_json("invalid_request", "highres_pid_vae_format must be one of auto, flux, sd3, or flux2."), "application/json");
                return;
            }
        }

        DD_LOG_DEBUG("%s\n", gen_params.to_string().c_str());

        sd_image_t init_image    = {(uint32_t)gen_params.width, (uint32_t)gen_params.height, 3, nullptr};
        
        if (j.contains("init_image") && j["init_image"].is_string()) {
            std::string b64_init = j["init_image"];
            if (b64_init.find("base64,") != std::string::npos) {
                b64_init = b64_init.substr(b64_init.find("base64,") + 7);
            }
            auto init_bytes = base64_decode(b64_init);
            if (!init_bytes.empty()) {
                int img_w = gen_params.width;
                int img_h = gen_params.height;
                init_image.data = load_image_from_memory(
                    reinterpret_cast<const char*>(init_bytes.data()),
                    (int)init_bytes.size(),
                    img_w, img_h,
                    gen_params.width, gen_params.height, 3);
                init_image.width = (uint32_t)img_w;
                init_image.height = (uint32_t)img_h;
                if (!init_image.data) {
                    DD_LOG_ERROR("failed to load init_image from base64");
                } else {
                    DD_LOG_INFO("loaded init_image for img2img: %dx%d", img_w, img_h);
                }
            }
        }

        sd_image_t control_image = {(uint32_t)gen_params.width, (uint32_t)gen_params.height, 3, nullptr};
        sd_image_t mask_image    = {(uint32_t)gen_params.width, (uint32_t)gen_params.height, 1, nullptr};

        if (j.contains("mask_image") && j["mask_image"].is_string()) {
            std::string b64_mask = j["mask_image"];
            if (b64_mask.find("base64,") != std::string::npos) {
                b64_mask = b64_mask.substr(b64_mask.find("base64,") + 7);
            }
            auto mask_bytes_v = base64_decode(b64_mask);
            if (!mask_bytes_v.empty()) {
                int mask_w = gen_params.width;
                int mask_h = gen_params.height;
                mask_image.data = load_image_from_memory(
                    reinterpret_cast<const char*>(mask_bytes_v.data()),
                    (int)mask_bytes_v.size(),
                    mask_w, mask_h,
                    gen_params.width, gen_params.height, 1);
                mask_image.width = (uint32_t)mask_w;
                mask_image.height = (uint32_t)mask_h;
                if (!mask_image.data) {
                    DD_LOG_ERROR("failed to load mask_image from base64");
                } else {
                    DD_LOG_INFO("loaded mask_image for inpainting: %dx%d", mask_w, mask_h);
                }
            }
        }

        if (mask_image.data == nullptr) {
            mask_image.data = (uint8_t*)malloc(mask_image.width * mask_image.height * mask_image.channel);
            memset(mask_image.data, 255, mask_image.width * mask_image.height * mask_image.channel);
        }
        if (control_image.data == nullptr) {
            control_image.data = (uint8_t*)calloc(1, control_image.width * control_image.height * control_image.channel);
        }

        std::vector<sd_image_t> pmid_images;

        sd_img_gen_params_t img_gen_params;
        sd_img_gen_params_init(&img_gen_params);

        img_gen_params.loras = gen_params.lora_vec.empty() ? nullptr : gen_params.lora_vec.data();
        img_gen_params.lora_count = (uint32_t)gen_params.lora_vec.size();
        img_gen_params.prompt = gen_params.prompt.c_str();
        img_gen_params.negative_prompt = gen_params.negative_prompt.c_str();
        img_gen_params.clip_skip = gen_params.clip_skip;
        img_gen_params.init_image = init_image;
        img_gen_params.mask_image = mask_image;
        img_gen_params.width = gen_params.width;
        img_gen_params.height = gen_params.height;
        img_gen_params.sample_params = gen_params.sample_params;
        img_gen_params.strength = gen_params.strength;
        img_gen_params.seed = gen_params.seed;
        img_gen_params.batch_count = gen_params.batch_count;
        img_gen_params.control_image = control_image;
        img_gen_params.control_strength = gen_params.control_strength;
        
        img_gen_params.pm_params.id_images = pmid_images.empty() ? nullptr : pmid_images.data();
        img_gen_params.pm_params.id_images_count = (int)pmid_images.size();
        img_gen_params.pm_params.id_embed_path = gen_params.pm_id_embed_path.c_str();
        img_gen_params.pm_params.style_strength = gen_params.pm_style_strength;

        img_gen_params.vae_tiling_params = ctx.ctx_params.vae_tiling_params;
        img_gen_params.cache = gen_params.cache_params;

        sd_image_t* results = nullptr;
        int num_results     = 0;

        {
            auto start_time = std::chrono::high_resolution_clock::now();
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            if (ctx.sd_ctx == nullptr) {
                res.status = 400;
                res.set_content(make_error_json("no_model", "no model loaded"), "application/json");
                return;
            }

            // Dynamic VRAM management for VAE
            float free_vram = get_free_vram_gb();
            // Estimate VAE VRAM: 1.5GB base per 512x512 tile (more conservative than 0.8)
            float estimated_vae_vram = (float(gen_params.width) * gen_params.height) / (512.0f * 512.0f) * 1.5f;
            float mp = (float)(gen_params.width * gen_params.height) / (1024.0f * 1024.0f);
            
            DD_LOG_INFO("VAE VRAM Check: Free=%.2fGB, Estimated Needed=%.2fGB", free_vram, estimated_vae_vram);
            
            bool request_vae_tiling = j.value("vae_tiling", false);
            
            if (request_vae_tiling) {
                DD_LOG_INFO("VAE tiling enabled by request.");
                img_gen_params.vae_tiling_params.enabled = true;
            } 
            else if (img_gen_params.vae_tiling_params.enabled) {
                 // Already enabled by model config or global default - keep it
            }
            else if (mp > 1.25f) { 
                // > 1.25 MP (e.g. above 1024x1024/1152x1152) -> Auto-tile to prevent grey images/NaNs
                DD_LOG_INFO("High resolution (%.2f MP) detected. Auto-enabling VAE tiling.", mp);
                img_gen_params.vae_tiling_params.enabled = true;
                set_progress_message("High-res: VAE tiling enabled");
            }
            else if (estimated_vae_vram > free_vram * 0.6f) {
                DD_LOG_WARN("High VRAM usage predicted. Auto-enabling VAE tiling.");
                img_gen_params.vae_tiling_params.enabled = true;
            } else {
                img_gen_params.vae_tiling_params.enabled = false;
            }

            if (img_gen_params.vae_tiling_params.enabled) {
                // Use default tile size if not set
                if (img_gen_params.vae_tiling_params.tile_size_x <= 0) {
                    img_gen_params.vae_tiling_params.tile_size_x = 32;
                    img_gen_params.vae_tiling_params.tile_size_y = 32;
                }
            }

            {
                std::lock_guard<std::mutex> lock_prog(progress_state.mutex);
                int sampling_steps = gen_params.sample_params.sample_steps;
                if (gen_params.highres_pid_fix) {
                    progress_state.total_steps = sampling_steps + (4 * gen_params.batch_count);
                } else if (gen_params.hires_fix) {
                    // One sampling pass for all batch + One hires pass for EACH image in batch
                    progress_state.total_steps = sampling_steps + (gen_params.hires_steps * gen_params.batch_count);
                } else {
                    progress_state.total_steps = sampling_steps;
                }
                progress_state.sampling_steps = sampling_steps;
                progress_state.base_step = 0;
                DD_LOG_INFO("Total expected steps: %d", progress_state.total_steps);
            }

            if (gen_params.clip_on_cpu) {
                set_progress_phase("Encoding Prompt (CLIP CPU)...");
            } else {
                set_progress_phase("Sampling...");
            }
            log_vram_status("Sampling Start");
            float vram_before = get_current_process_vram_usage_gb();
            results     = generate_image(ctx.sd_ctx.get(), &img_gen_params);
            float vram_after = get_current_process_vram_usage_gb();
            float vram_delta = vram_after - vram_before;
            log_vram_status("Sampling End");
            DD_LOG_INFO("Generation Sampling finished. Delta: %+.2f GB", vram_delta);
            num_results = gen_params.batch_count;

            // B0.1 & B0.2: Fail Loudly + Conservative Retry
            bool first_pass_success = (results != nullptr);
            if (first_pass_success) {
                first_pass_success = false;
                for (int i = 0; i < num_results; i++) {
                    if (results[i].data != nullptr && is_image_valid(results[i])) {
                        first_pass_success = true;
                        break;
                    }
                }
            }

            if (!first_pass_success) {
                DD_LOG_WARN("First pass generation failed (empty results or invalid/grey image). Retrying with conservative settings (VAE tiling)...");
                set_progress_message("Retrying with VAE tiling...");

                free_sd_images(results, num_results);

                img_gen_params.vae_tiling_params.enabled = true;
                if (img_gen_params.vae_tiling_params.tile_size_x <= 0) {
                    img_gen_params.vae_tiling_params.tile_size_x = 32;
                    img_gen_params.vae_tiling_params.tile_size_y = 32;
                }

                results = generate_image(ctx.sd_ctx.get(), &img_gen_params);

                first_pass_success = (results != nullptr);
                if (first_pass_success) {
                    first_pass_success = false;
                                    for (int i = 0; i < num_results; i++) {
                                        if (results[i].data != nullptr && is_image_valid(results[i])) {
                                            first_pass_success = true;
                                            break;
                                        }
                                    }
                                }
                    
                                if (!first_pass_success) {
                    
                    DD_LOG_ERROR("Generation failed after retry.");
                    res.status = 500;
                    res.set_content(make_error_json("generation_failed", "Stable diffusion generation failed even after retry with conservative settings."), "application/json");
                    free_sd_images(results, num_results);
                    if (init_image.data) stbi_image_free(init_image.data);
                    if (mask_image.data) stbi_image_free(mask_image.data);
                    if (control_image.data) stbi_image_free(control_image.data);
                    return;
                }
            }

            {
                std::lock_guard<std::mutex> lock_prog(progress_state.mutex);
                progress_state.base_step = gen_params.sample_params.sample_steps;
            }

            DD_LOG_INFO("Generation done, num_results: %d, hires_fix: %s", num_results, gen_params.hires_fix ? "true" : "false");

            if (gen_params.highres_pid_fix && num_results > 0) {
                DD_LOG_INFO("Performing NVIDIA PiD highres upscale for %d images...", num_results);
                set_progress_phase("Highres PiD Upscale...");

                SDContextParams base_ctx_params = ctx.ctx_params;
                const bool base_vae_decode_only = ctx.sd_ctx_vae_decode_only;
                SDContextParams pid_ctx_params = ctx.ctx_params;
                pid_ctx_params.model_path = "";
                pid_ctx_params.diffusion_model_path = gen_params.highres_pid_model;
                pid_ctx_params.high_noise_diffusion_model_path = "";
                pid_ctx_params.clip_l_path = "";
                pid_ctx_params.clip_g_path = "";
                pid_ctx_params.clip_vision_path = "";
                pid_ctx_params.t5xxl_path = "";
                pid_ctx_params.llm_path = gen_params.highres_pid_llm;
                pid_ctx_params.llm_vision_path = "";
                pid_ctx_params.vae_path = gen_params.highres_pid_vae;
                pid_ctx_params.vae_format = sd_vae_format_from_string(gen_params.highres_pid_vae_format);
                pid_ctx_params.diffusion_flash_attn = true;
                pid_ctx_params.offload_params_to_cpu = true;
                pid_ctx_params.clip_on_cpu = true;
                pid_ctx_params.rng_type = CPU_RNG;
                pid_ctx_params.sampler_rng_type = RNG_TYPE_COUNT;
                pid_ctx_params.vae_tiling_params.enabled = true;
                if (pid_ctx_params.vae_tiling_params.tile_size_x <= 0) {
                    pid_ctx_params.vae_tiling_params.tile_size_x = 32;
                    pid_ctx_params.vae_tiling_params.tile_size_y = 32;
                }

                ctx.sd_ctx.reset();
                sd_ctx_params_t pid_ctx_raw = pid_ctx_params.to_sd_ctx_params_t(false, false, false);
                SdCtxPtr pid_ctx(new_sd_ctx(&pid_ctx_raw));
                if (!pid_ctx) {
                    DD_LOG_ERROR("Failed to create PiD context.");
                    sd_ctx_params_t base_ctx_raw = make_sd_ctx_params(base_ctx_params, base_vae_decode_only);
                    ctx.sd_ctx.reset(new_sd_ctx(&base_ctx_raw));
                    ctx.sd_ctx_vae_decode_only = base_vae_decode_only;
                    res.status = 500;
                    res.set_content(make_error_json("generation_failed", "Failed to load NVIDIA PiD upscale context."), "application/json");
                    free_sd_images(results, num_results);
                    if (init_image.data) stbi_image_free(init_image.data);
                    if (mask_image.data) stbi_image_free(mask_image.data);
                    if (control_image.data) stbi_image_free(control_image.data);
                    return;
                }

                sd_image_t* pid_results = (sd_image_t*)calloc(num_results, sizeof(sd_image_t));
                bool pid_success = pid_results != nullptr;
                float pid_factor = infer_pid_upscale_factor(gen_params.highres_pid_model);

                for (int i = 0; i < num_results && pid_success; i++) {
                    sd_image_t base_img = results[i];
                    if (!base_img.data) {
                        pid_success = false;
                        break;
                    }
                    int pid_width = (int)std::round((float)base_img.width * pid_factor);
                    int pid_height = (int)std::round((float)base_img.height * pid_factor);
                    DD_LOG_INFO("PiD highres upscale target for image %d: %ux%u -> %dx%d",
                                i + 1, base_img.width, base_img.height, pid_width, pid_height);

                    sd_img_gen_params_t pid_params;
                    sd_img_gen_params_init(&pid_params);
                    pid_params.prompt = gen_params.prompt.c_str();
                    pid_params.negative_prompt = "";
                    pid_params.clip_skip = gen_params.clip_skip;
                    pid_params.ref_images = &base_img;
                    pid_params.ref_images_count = 1;
                    pid_params.auto_resize_ref_image = true;
                    pid_params.width = pid_width;
                    pid_params.height = pid_height;
                    pid_params.sample_params = gen_params.sample_params;
                    pid_params.sample_params.sample_steps = 4;
                    pid_params.sample_params.guidance.txt_cfg = 1.0f;
                    pid_params.strength = 1.0f;
                    pid_params.seed = gen_params.seed + i;
                    pid_params.batch_count = 1;
                    pid_params.vae_tiling_params = pid_ctx_params.vae_tiling_params;

                    {
                        std::lock_guard<std::mutex> lock_prog(progress_state.mutex);
                        progress_state.sampling_steps = 4;
                    }

                    sd_image_t* pid_pass = generate_image(pid_ctx.get(), &pid_params);
                    if (pid_pass && pid_pass[0].data && is_image_valid(pid_pass[0])) {
                        pid_results[i] = pid_pass[0];
                        free(pid_pass);
                    } else {
                        DD_LOG_ERROR("PiD highres upscale failed for image %d.", i + 1);
                        if (pid_pass) free_sd_images(pid_pass, 1);
                        pid_success = false;
                    }

                    {
                        std::lock_guard<std::mutex> lock_prog(progress_state.mutex);
                        progress_state.base_step += 4;
                    }
                }

                pid_ctx.reset();
                sd_ctx_params_t base_ctx_raw = make_sd_ctx_params(base_ctx_params, base_vae_decode_only);
                ctx.sd_ctx.reset(new_sd_ctx(&base_ctx_raw));
                ctx.sd_ctx_vae_decode_only = base_vae_decode_only;
                if (!ctx.sd_ctx) {
                    DD_LOG_WARN("Failed to restore base SD context after PiD stage. The next request may need to reload the preset.");
                }

                if (!pid_success) {
                    free_sd_images(pid_results, num_results);
                    res.status = 500;
                    res.set_content(make_error_json("generation_failed", "NVIDIA PiD highres upscale failed."), "application/json");
                    free_sd_images(results, num_results);
                    if (init_image.data) stbi_image_free(init_image.data);
                    if (mask_image.data) stbi_image_free(mask_image.data);
                    if (control_image.data) stbi_image_free(control_image.data);
                    return;
                }

                free_sd_images(results, num_results);
                results = pid_results;
            } else if (gen_params.hires_fix && num_results > 0) {
                DD_LOG_INFO("Performing highres-fix for %d images...", num_results);
                
                if (!gen_params.hires_upscale_model.empty() && 
                    ctx.current_upscale_model_path != gen_params.hires_upscale_model) {
                    
                    fs::path upscaler_path = fs::path(ctx.svr_params.model_dir) / gen_params.hires_upscale_model;
                    DD_LOG_INFO("Attempting to load upscaler: %s", upscaler_path.string().c_str());
                    if (fs::exists(upscaler_path)) {
                        ctx.upscaler_ctx.reset(new_upscaler_ctx(upscaler_path.string().c_str(),
                                                        ctx.ctx_params.offload_params_to_cpu,
                                                        false,
                                                        ctx.ctx_params.n_threads,
                                                        512,
                                                        nullptr,
                                                        nullptr));
                        ctx.current_upscale_model_path = gen_params.hires_upscale_model;
                        DD_LOG_INFO("Upscaler loaded successfully.");
                    } else {
                        DD_LOG_WARN("Upscaler model not found: %s", upscaler_path.string().c_str());
                    }
                }

                sd_image_t* hires_results = (sd_image_t*)malloc(sizeof(sd_image_t) * num_results);
                
                for (int i = 0; i < num_results; i++) {
                    sd_image_t base_img = results[i];
                    sd_image_t upscaled_img = {0};

                    if (ctx.upscaler_ctx) {
                        DD_LOG_INFO("Upscaling for highres-fix (factor %.2f)...", gen_params.hires_upscale_factor);
                        upscaled_img = upscale(ctx.upscaler_ctx.get(), base_img, (uint32_t)gen_params.hires_upscale_factor);
                    } else {
                        DD_LOG_INFO("Resizing for highres-fix (factor %.2f) using simple resize...", gen_params.hires_upscale_factor);
                        upscaled_img.width = (uint32_t)(base_img.width * gen_params.hires_upscale_factor);
                        upscaled_img.height = (uint32_t)(base_img.height * gen_params.hires_upscale_factor);
                        upscaled_img.channel = base_img.channel;
                        upscaled_img.data = (uint8_t*)malloc(upscaled_img.width * upscaled_img.height * upscaled_img.channel);
                        stbir_resize_uint8(base_img.data, (int)base_img.width, (int)base_img.height, 0,
                                            upscaled_img.data, (int)upscaled_img.width, (int)upscaled_img.height, 0,
                                            (int)upscaled_img.channel);
                    }

                    set_progress_phase("Highres-fix Pass...");
                    
                    uint32_t target_width = (uint32_t)(base_img.width * gen_params.hires_upscale_factor);
                    uint32_t target_height = (uint32_t)(base_img.height * gen_params.hires_upscale_factor);

                    if (upscaled_img.width != target_width || upscaled_img.height != target_height) {
                        DD_LOG_INFO("Resizing upscaled image to target size: %dx%d", target_width, target_height);
                        sd_image_t resized_img = { target_width, target_height, upscaled_img.channel, nullptr };
                        resized_img.data = (uint8_t*)malloc(target_width * target_height * resized_img.channel);
                        stbir_resize_uint8(upscaled_img.data, (int)upscaled_img.width, (int)upscaled_img.height, 0,
                                            resized_img.data, (int)target_width, (int)target_height, 0,
                                            (int)resized_img.channel);
                        free(upscaled_img.data);
                        upscaled_img = resized_img;
                    }

                    sd_img_gen_params_t hires_params = img_gen_params;
                    hires_params.init_image = upscaled_img;
                    hires_params.width = (int)upscaled_img.width;
                    hires_params.height = (int)upscaled_img.height;
                    hires_params.strength = gen_params.hires_denoising_strength;
                    hires_params.sample_params.sample_steps = gen_params.hires_steps;
                    hires_params.batch_count = 1;

                    hires_params.mask_image.width = target_width;
                    hires_params.mask_image.height = target_height;
                    hires_params.mask_image.data = (uint8_t*)malloc(target_width * target_height * hires_params.mask_image.channel);
                    if (img_gen_params.mask_image.data) {
                        stbir_resize_uint8(img_gen_params.mask_image.data, (int)img_gen_params.mask_image.width, (int)img_gen_params.mask_image.height, 0,
                                            hires_params.mask_image.data, (int)target_width, (int)target_height, 0,
                                            (int)hires_params.mask_image.channel);
                    } else {
                        memset(hires_params.mask_image.data, 255, target_width * target_height * hires_params.mask_image.channel);
                    }

                    hires_params.control_image.width = target_width;
                    hires_params.control_image.height = target_height;
                    hires_params.control_image.data = (uint8_t*)calloc(1, target_width * target_height * hires_params.control_image.channel);
                    if (img_gen_params.control_image.data) {
                        stbir_resize_uint8(img_gen_params.control_image.data, (int)img_gen_params.control_image.width, (int)img_gen_params.control_image.height, 0,
                                            hires_params.control_image.data, (int)target_width, (int)target_height, 0,
                                            (int)hires_params.control_image.channel);
                    }

                    {
                        std::lock_guard<std::mutex> lock_prog(progress_state.mutex);
                        progress_state.sampling_steps = gen_params.hires_steps;
                    }

                    sd_image_t* second_pass_result = generate_image(ctx.sd_ctx.get(), &hires_params);
                    
                    if (second_pass_result && second_pass_result[0].data) {
                        hires_results[i] = second_pass_result[0];
                        free(second_pass_result); // Free the container, not the data
                    } else {
                        hires_results[i] = upscaled_img; // Fallback to upscaled if second pass fails
                    }

                    {
                        std::lock_guard<std::mutex> lock_prog(progress_state.mutex);
                        progress_state.base_step += gen_params.hires_steps;
                    }

                    stbi_image_free(base_img.data);
                    if (upscaled_img.data && upscaled_img.data != hires_results[i].data) {
                        free(upscaled_img.data);
                    }
                    free(hires_params.mask_image.data);
                    free(hires_params.control_image.data);
                }
                
                free(results);
                results = hires_results;
            }
            auto end_time = std::chrono::high_resolution_clock::now();
            total_generation_time = std::chrono::duration<double>(end_time - start_time).count();
            
            out["vram_peak_gb"] = vram_after;
            out["vram_delta_gb"] = vram_delta;
        }
        set_progress_phase("VAE Decoding...");
        
        bool no_base64 = j.value("no_base64", false);
        
        // If batch > 1, force JSON to keep it simple unless we implement multipart
        // Note: We now always return JSON with URLs as primary method.

        int successful_generations = 0;
        for (int i = 0; i < num_results; i++) {
            if (results[i].data == nullptr) {
                continue;
            }
            successful_generations++;
            auto image_bytes = write_image_to_vector(output_format == "jpeg" ? ImageFormat::JPEG : ImageFormat::PNG,
                                                        results[i].data,
                                                        (int)results[i].width,
                                                        (int)results[i].height,
                                                        (int)results[i].channel,
                                                        output_compression);
            if (image_bytes.empty()) {
                DD_LOG_ERROR("write image to mem failed");
                continue;
            }

            int64_t current_seed = gen_params.seed + i;
            diffusion_desk::json item;
            item["seed"] = current_seed;

            // Always save to disk (temp or permanent) to serve via URL
            try {
                std::string final_output_dir = ctx.svr_params.output_dir;
                std::string url_prefix = "/outputs/";

                if (!save_image) {
                    final_output_dir = (fs::path(ctx.svr_params.output_dir) / "temp").string();
                    url_prefix = "/outputs/temp/";
                    if (!fs::exists(final_output_dir)) {
                        fs::create_directories(final_output_dir);
                    }
                } else {
                    if (!fs::exists(final_output_dir)) {
                        fs::create_directories(final_output_dir);
                    }
                }

                auto timestamp = std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
                std::string base_filename = "img-" + std::to_string(timestamp) + "-" + std::to_string(current_seed);
                std::string img_filename = (fs::path(final_output_dir) / (base_filename + ".png")).string();
                
                std::ofstream file(img_filename, std::ios::binary);
                file.write(reinterpret_cast<const char*>(image_bytes.data()), image_bytes.size());
                DD_LOG_INFO("saved image to %s", img_filename.c_str());

                // Only save metadata txt if saving permanently
                if (save_image) {
                    std::string txt_filename = (fs::path(final_output_dir) / (base_filename + ".txt")).string();
                    std::string params_txt = get_image_params(ctx.ctx_params, gen_params, current_seed, total_generation_time);
                    std::ofstream txt_file(txt_filename);
                    txt_file << params_txt;
                }

                // Add file info to response
                item["url"] = url_prefix + base_filename + ".png";
                item["name"] = base_filename + ".png";

            } catch (const std::exception& e) {
                DD_LOG_ERROR("failed to save image or metadata: %s", e.what());
            }

            out["data"].push_back(item);
        }

        if (successful_generations == 0) {
            DD_LOG_ERROR("All generated images were null (VAE decoding pass).");
            res.status = 500;
            res.set_content(make_error_json("generation_failed", "Stable diffusion returned only null images. This can happen if the VAE failed or the model is corrupted."), "application/json");
            free_sd_images(results, num_results);
            if (init_image.data) stbi_image_free(init_image.data);
            if (mask_image.data) stbi_image_free(mask_image.data);
            if (control_image.data) stbi_image_free(control_image.data);
            return;
        }

        out["generation_time"] = total_generation_time;
        res.set_content(out.dump(), "application/json");
        res.status = 200;

        free_sd_images(results, num_results);

        if (init_image.data) {
            stbi_image_free(init_image.data);
        }
        if (mask_image.data) {
            stbi_image_free(mask_image.data);
        }
        if (control_image.data) {
            stbi_image_free(control_image.data);
        }

    } catch (const std::exception& e) {
        res.status = 500;
        res.set_content(make_error_json("server_error", e.what()), "application/json");
    }
}

void handle_edit_image(const httplib::Request& req, httplib::Response& res, ServerContext& ctx) {
    reset_progress();
    DD_LOG_INFO("New edit request received (multipart)");
    try {
        if (!req.is_multipart_form_data()) {
            DD_LOG_ERROR("Edit request failed: Not multipart form data");
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "Content-Type must be multipart/form-data"), "application/json");
            return;
        }

        std::string prompt = req.form.get_field("prompt");
        if (prompt.empty()) {
            DD_LOG_ERROR("Edit request failed: Prompt is empty");
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "prompt required"), "application/json");
            return;
        }

        std::string extra_args_str;
        if (req.form.has_field("extra_args")) {
            extra_args_str = req.form.get_field("extra_args");
        }

        size_t image_count = req.form.get_file_count("image[]");
        if (image_count == 0) {
            DD_LOG_ERROR("Edit request failed: No images provided");
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "at least one image[] required"), "application/json");
            return;
        }

        std::vector<std::vector<uint8_t>> images_bytes;
        for (size_t i = 0; i < image_count; i++) {
            auto file = req.form.get_file("image[]", i);
            images_bytes.emplace_back(file.content.begin(), file.content.end());
        }

        std::vector<uint8_t> mask_bytes;
        if (req.form.has_field("mask")) {
            auto file = req.form.get_file("mask");
            mask_bytes.assign(file.content.begin(), file.content.end());
        }

        int n = 1;
        if (req.form.has_field("n")) {
            try {
                n = std::stoi(req.form.get_field("n"));
            } catch (...) {
            }
        }
        n = std::clamp(n, 1, 8);

        std::string size = req.form.get_field("size");
        int width = 512, height = 512;
        if (!size.empty()) {
            auto pos = size.find('x');
            if (pos != std::string::npos) {
                try {
                    width  = std::stoi(size.substr(0, pos));
                    height = std::stoi(size.substr(pos + 1));
                } catch (...) {
                }
            }
        }

        std::string output_format = "png";
        if (req.form.has_field("output_format"))
            output_format = req.form.get_field("output_format");
        if (output_format != "png" && output_format != "jpeg") {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "invalid output_format, must be one of [png, jpeg]"), "application/json");
            return;
        }

        std::string output_compression_str = req.form.get_field("output_compression");
        int output_compression             = 100;
        try {
            output_compression = std::stoi(output_compression_str);
        } catch (...) {
        }
        if (output_compression > 100) {
            output_compression = 100;
        }
        if (output_compression < 0) {
            output_compression = 0;
        }

        SDGenerationParams gen_params = ctx.default_gen_params;
        gen_params.prompt             = prompt;
        gen_params.width              = width;
        gen_params.height             = height;
        gen_params.batch_count        = n;

        if (!extra_args_str.empty() && !gen_params.from_json_str(extra_args_str)) {
            DD_LOG_ERROR("Edit request failed: Invalid extra_args JSON");
            res.status = 400;
            res.set_content(R"({\"error\":\"invalid extra_args\"})", "application/json");
            return;
        }

        std::string lora_dir = ctx.ctx_params.lora_model_dir;
        if (lora_dir.empty()) {
            lora_dir = (fs::path(ctx.svr_params.model_dir) / "lora").string();
        }

        if (!gen_params.process_and_check(IMG_GEN, lora_dir)) {
            DD_LOG_ERROR("Edit request failed: Invalid generation params (process_and_check)");
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "invalid params"), "application/json");
            return;
        }

        DD_LOG_DEBUG("%s\n", gen_params.to_string().c_str());

        sd_image_t init_image    = {(uint32_t)gen_params.width, (uint32_t)gen_params.height, 3, nullptr};
        sd_image_t control_image = {(uint32_t)gen_params.width, (uint32_t)gen_params.height, 3, nullptr};
        std::vector<sd_image_t> pmid_images;

        std::vector<sd_image_t> ref_images;
        ref_images.reserve(images_bytes.size());
        for (auto& bytes : images_bytes) {
            int img_w           = width;
            int img_h           = height;
            uint8_t* raw_pixels = load_image_from_memory(
                reinterpret_cast<const char*>(bytes.data()),
                (int)bytes.size(),
                img_w, img_h,
                width, height, 3);

            if (!raw_pixels) {
                continue;
            }

            sd_image_t img{(uint32_t)img_w, (uint32_t)img_h, 3, raw_pixels};
            ref_images.push_back(img);
        }

        sd_image_t mask_image = {0};
        if (!mask_bytes.empty()) {
            int mask_w        = width;
            int mask_h        = height;
            uint8_t* mask_raw = load_image_from_memory(
                reinterpret_cast<const char*>(mask_bytes.data()),
                (int)mask_bytes.size(),
                mask_w, mask_h,
                width, height, 1);
            mask_image = {(uint32_t)mask_w, (uint32_t)mask_h, 1, mask_raw};
        } else {
            mask_image.width   = (uint32_t)width;
            mask_image.height  = (uint32_t)height;
            mask_image.channel = 1;
            mask_image.data    = nullptr;
        }

        sd_img_gen_params_t img_gen_params;
        sd_img_gen_params_init(&img_gen_params);

        img_gen_params.loras = gen_params.lora_vec.empty() ? nullptr : gen_params.lora_vec.data();
        img_gen_params.lora_count = (uint32_t)gen_params.lora_vec.size();
        img_gen_params.prompt = gen_params.prompt.c_str();
        img_gen_params.negative_prompt = gen_params.negative_prompt.c_str();
        img_gen_params.clip_skip = gen_params.clip_skip;
        img_gen_params.init_image = init_image;
        img_gen_params.ref_images = ref_images.empty() ? nullptr : ref_images.data();
        img_gen_params.ref_images_count = (int)ref_images.size();
        img_gen_params.auto_resize_ref_image = gen_params.auto_resize_ref_image;
        img_gen_params.increase_ref_index = gen_params.increase_ref_index;
        img_gen_params.mask_image = mask_image;
        img_gen_params.width = gen_params.width;
        img_gen_params.height = gen_params.height;
        img_gen_params.sample_params = gen_params.sample_params;
        img_gen_params.strength = gen_params.strength;
        img_gen_params.seed = gen_params.seed;
        img_gen_params.batch_count = gen_params.batch_count;
        img_gen_params.control_image = control_image;
        img_gen_params.control_strength = gen_params.control_strength;

        img_gen_params.pm_params.id_images = pmid_images.empty() ? nullptr : pmid_images.data();
        img_gen_params.pm_params.id_images_count = (int)pmid_images.size();
        img_gen_params.pm_params.id_embed_path = gen_params.pm_id_embed_path.c_str();
        img_gen_params.pm_params.style_strength = gen_params.pm_style_strength;

        img_gen_params.vae_tiling_params = ctx.ctx_params.vae_tiling_params;
        img_gen_params.cache = gen_params.cache_params;

        sd_image_t* results = nullptr;
        int num_results     = 0;

        {
            std::lock_guard<std::mutex> lock(ctx.sd_ctx_mutex);
            if (ctx.sd_ctx == nullptr) {
                res.status = 400;
                res.set_content(make_error_json("no_model", "no model loaded"), "application/json");
                if (init_image.data) stbi_image_free(init_image.data);
                if (mask_image.data) stbi_image_free(mask_image.data);
                for (auto& ref_image : ref_images) stbi_image_free(ref_image.data);
                return;
            }

            {
                std::lock_guard<std::mutex> lock_prog(progress_state.mutex);
                progress_state.total_steps = gen_params.sample_params.sample_steps;
                progress_state.sampling_steps = progress_state.total_steps;
                progress_state.base_step = 0;
            }

        set_progress_phase("Sampling...");
        log_vram_status("Edit Start");
        float vram_before = get_current_process_vram_usage_gb();
        results     = generate_image(ctx.sd_ctx.get(), &img_gen_params);
        float vram_after = get_current_process_vram_usage_gb();
        float vram_delta = vram_after - vram_before;
        log_vram_status("Edit End");
        DD_LOG_INFO("Edit Sampling finished. Delta: %+.2f GB", vram_delta);
        num_results = gen_params.batch_count;

        // B0.1 & B0.2: Fail Loudly + Conservative Retry
        bool success = (results != nullptr);
        if (success) {
            success = false;
            for (int i = 0; i < num_results; i++) {
                if (results[i].data != nullptr) {
                    bool all_zeros = true;
                    size_t total_bytes = (size_t)results[i].width * results[i].height * results[i].channel;
                    for (size_t b = 0; b < total_bytes; b++) {
                        if (results[i].data[b] != 0) {
                            all_zeros = false;
                            break;
                        }
                    }
                    if (!all_zeros) {
                        success = true;
                        break;
                    }
                }
            }
        }

        if (!success) {
            DD_LOG_WARN("Edit generation failed (empty results). Retrying with conservative settings (VAE tiling)...");
            set_progress_message("Retrying with VAE tiling...");
            
            free_sd_images(results, num_results);
            
            img_gen_params.vae_tiling_params.enabled = true;
            if (img_gen_params.vae_tiling_params.tile_size_x <= 0) {
                img_gen_params.vae_tiling_params.tile_size_x = 512;
                img_gen_params.vae_tiling_params.tile_size_y = 512;
            }
            
            results = generate_image(ctx.sd_ctx.get(), &img_gen_params);
            
            success = (results != nullptr);
            if (success) {
                success = false;
                for (int i = 0; i < num_results; i++) {
                    if (results[i].data != nullptr) {
                        bool all_zeros = true;
                        size_t total_bytes = (size_t)results[i].width * results[i].height * results[i].channel;
                        for (size_t b = 0; b < total_bytes; b++) {
                            if (results[i].data[b] != 0) {
                                all_zeros = false;
                                break;
                            }
                        }
                        if (!all_zeros) {
                            success = true;
                            break;
                        }
                    }
                }
            }
            
            if (!success) {
                DD_LOG_ERROR("Edit generation failed after retry.");
                res.status = 500;
                res.set_content(make_error_json("generation_failed", "Stable diffusion generation failed even after retry with conservative settings."), "application/json");
                free_sd_images(results, num_results);
                
                // Still need to free other resources
                if (init_image.data) stbi_image_free(init_image.data);
                if (mask_image.data) stbi_image_free(mask_image.data);
                for (auto& ref_image : ref_images) stbi_image_free(ref_image.data);
                return;
            }
        }
    }

    set_progress_phase("VAE Decoding...");
    diffusion_desk::json out;
    out["created"]       = iso_timestamp_now();
    out["data"]          = diffusion_desk::json::array();
    out["output_format"] = output_format;

    int successful_generations = 0;
    for (int i = 0; i < num_results; i++) {
        if (results[i].data == nullptr)
            continue;
        successful_generations++;
        auto image_bytes = write_image_to_vector(output_format == "jpeg" ? ImageFormat::JPEG : ImageFormat::PNG,
                                                    results[i].data,
                                                    (int)results[i].width,
                                                    (int)results[i].height,
                                                    (int)results[i].channel,
                                                    output_compression);
        
        int64_t current_seed = gen_params.seed + i;
        diffusion_desk::json item;
        item["seed"] = current_seed;

        // Always save edits to temp to serve via URL (Edits are usually transient unless saved by user)
        try {
            std::string temp_dir = (fs::path(ctx.svr_params.output_dir) / "temp").string();
            if (!fs::exists(temp_dir)) {
                fs::create_directories(temp_dir);
            }

            auto timestamp = std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
            std::string base_filename = "edit-" + std::to_string(timestamp) + "-" + std::to_string(current_seed);
            std::string img_filename = (fs::path(temp_dir) / (base_filename + ".png")).string();
            
            std::ofstream file(img_filename, std::ios::binary);
            file.write(reinterpret_cast<const char*>(image_bytes.data()), image_bytes.size());
            
            item["url"] = "/outputs/temp/" + base_filename + ".png";
            item["name"] = base_filename + ".png";
        } catch (...) {
            DD_LOG_ERROR("failed to save edit image to temp");
        }

        out["data"].push_back(item);
    }

    if (successful_generations == 0) {
        DD_LOG_ERROR("All generated images were null (VAE decoding pass).");
        res.status = 500;
        res.set_content(make_error_json("generation_failed", "Stable diffusion returned only null images."), "application/json");
        free_sd_images(results, num_results);
        if (init_image.data) stbi_image_free(init_image.data);
        if (mask_image.data) stbi_image_free(mask_image.data);
        for (auto& ref_image : ref_images) stbi_image_free(ref_image.data);
        return;
    }

    res.set_content(out.dump(), "application/json");
    res.status = 200;

    free_sd_images(results, num_results);

    if (init_image.data) {
        stbi_image_free(init_image.data);
    }
    if (mask_image.data) {
        stbi_image_free(mask_image.data);
    }
    for (auto& ref_image : ref_images) {
        stbi_image_free(ref_image.data);
    }
    } catch (const std::exception& e) {
        res.status = 500;
        res.set_content(make_error_json("server_error", e.what()), "application/json");
    }
}
