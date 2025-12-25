#include "server_state.hpp"

ProgressState progress_state;

void on_progress(int step, int steps, float time, void* data) {
    std::lock_guard<std::mutex> lock(progress_state.mutex);
    progress_state.step = progress_state.base_step + step;
    progress_state.steps = progress_state.total_steps > 0 ? progress_state.total_steps : steps;
    progress_state.time = time;
    progress_state.version++;
    progress_state.cv.notify_all();
    
    if (progress_state.step % 5 == 0 || progress_state.step == progress_state.steps) {
        LOG_INFO("Progress: step %d/%d (phase: %s, time: %.2fs)", 
                 progress_state.step, progress_state.steps, 
                 progress_state.phase.c_str(), progress_state.time);
    }
}

void reset_progress() {
    std::lock_guard<std::mutex> lock(progress_state.mutex);
    progress_state.step = 0;
    progress_state.steps = 0;
    progress_state.total_steps = 0;
    progress_state.base_step = 0;
    progress_state.time = 0;
    progress_state.phase = "idle";
    progress_state.message = "";
    progress_state.version++;
    progress_state.cv.notify_all();
}

void set_progress_phase(const std::string& phase) {
    std::lock_guard<std::mutex> lock(progress_state.mutex);
    progress_state.phase = phase;
    progress_state.version++;
    progress_state.cv.notify_all();
}

void set_progress_message(const std::string& message) {
    std::lock_guard<std::mutex> lock(progress_state.mutex);
    progress_state.message = message;
    progress_state.version++;
    progress_state.cv.notify_all();
}
