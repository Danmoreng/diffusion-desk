#pragma once

#include <mutex>
#include <condition_variable>
#include <string>
#include "utils/common.hpp"

struct ProgressState {
    int step = 0;
    int steps = 0;
    float time = 0;
    std::string phase = "";
    uint64_t version = 0;
    std::mutex mutex;
    std::condition_variable cv;
};

extern ProgressState progress_state;

void on_progress(int step, int steps, float time, void* data);
void reset_progress();
void set_progress_phase(const std::string& phase);
