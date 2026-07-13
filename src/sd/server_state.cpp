#include "server_state.hpp"

#include <algorithm>

ProgressState progress_state;

void on_progress(int step, int steps, float time, void* data) {
    std::lock_guard<std::mutex> lock(progress_state.mutex);

    const bool encoding_prompt = progress_state.phase.rfind("Encoding", 0) == 0;
    const bool sampling_progress = progress_state.sampling_steps <= 0 || steps == progress_state.sampling_steps;

    // Text encoders, VAE preparation, tensor loading, and diffusion sampling all
    // share the same upstream callback. Their step totals can collide (for
    // example Ideogram's VAE encode and an 8-step sampling pass both report
    // 0/8..8/8), so counters cannot reliably identify the active phase.
    // mark_sampling_started() performs the semantic transition from the
    // upstream "generating image" marker; preparation counters stay hidden.
    if (encoding_prompt) {
        return;
    }

    // Ignore unrelated progress series such as tensor loading and tiled VAE.
    if (!sampling_progress) {
        return;
    }

    progress_state.step = progress_state.base_step + step;
    progress_state.steps = progress_state.total_steps > 0 ? progress_state.total_steps : steps;
    progress_state.time = time;
    progress_state.version++;
    progress_state.cv.notify_all();
    
    if (progress_state.step % 5 == 0 || progress_state.step >= progress_state.steps) {
        DD_LOG_INFO("Progress: step %d/%d (phase: %s, time: %.2fs)", 
                 progress_state.step, progress_state.steps, 
                 progress_state.phase.c_str(), progress_state.time);
    }
}

void mark_sampling_started() {
    std::lock_guard<std::mutex> lock(progress_state.mutex);
    if (progress_state.phase.rfind("Encoding", 0) != 0) {
        return;
    }

    DD_LOG_INFO("Progress phase transition: encoding -> sampling");
    progress_state.phase = "Sampling...";
    progress_state.step = progress_state.base_step;
    progress_state.steps = progress_state.total_steps;
    progress_state.time = 0;
    progress_state.version++;
    progress_state.cv.notify_all();
}

void reset_progress() {
    std::lock_guard<std::mutex> lock(progress_state.mutex);
    progress_state.step = 0;
    progress_state.steps = 0;
    progress_state.total_steps = 0;
    progress_state.sampling_steps = 0;
    progress_state.base_step = 0;
    progress_state.time = 0;
    progress_state.phase = "idle";
    progress_state.message = "";
    progress_state.version++;
    progress_state.cv.notify_all();
}

void set_progress_phase(const std::string& phase) {
    std::lock_guard<std::mutex> lock(progress_state.mutex);
    DD_LOG_INFO("Progress phase set: '%s' -> '%s'", progress_state.phase.c_str(), phase.c_str());
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
