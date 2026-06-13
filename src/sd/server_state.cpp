#include "server_state.hpp"

#include <algorithm>

ProgressState progress_state;

void on_progress(int step, int steps, float time, void* data) {
    std::lock_guard<std::mutex> lock(progress_state.mutex);

    const bool encoding_prompt = progress_state.phase.rfind("Encoding Prompt", 0) == 0;
    const bool sampling_progress = progress_state.sampling_steps <= 0 || steps == progress_state.sampling_steps;
    const int log_interval = std::max(steps / 20, 1);
    const bool log_callback = step <= 1 || step >= steps || step % log_interval == 0;

    if (log_callback) {
        DD_LOG_INFO(
            "Progress callback raw: step=%d/%d phase='%s' expected_sampling_steps=%d total_steps=%d base_step=%d encoding=%s sampling_match=%s",
            step,
            steps,
            progress_state.phase.c_str(),
            progress_state.sampling_steps,
            progress_state.total_steps,
            progress_state.base_step,
            encoding_prompt ? "true" : "false",
            sampling_progress ? "true" : "false");
    }

    if (encoding_prompt && !sampling_progress) {
        progress_state.step = step;
        progress_state.steps = steps;
        progress_state.time = time;
        progress_state.version++;
        progress_state.cv.notify_all();
        if (log_callback) {
            DD_LOG_INFO("Progress callback classified as prompt encoding: published=%d/%d", step, steps);
        }
        return;
    }

    // The first callback matching the configured diffusion step count marks the
    // transition from text encoding to sampling.
    if (encoding_prompt) {
        DD_LOG_INFO(
            "Progress phase transition: prompt encoding -> sampling on callback %d/%d (expected sampling steps: %d)",
            step,
            steps,
            progress_state.sampling_steps);
        progress_state.phase = "Sampling...";
    }

    // Ignore unrelated progress series once prompt encoding has completed.
    if (!sampling_progress) {
        if (log_callback) {
            DD_LOG_INFO("Progress callback ignored outside prompt encoding: step=%d/%d", step, steps);
        }
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
