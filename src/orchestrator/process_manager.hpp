#pragma once

#include <string>
#include <vector>
#include <memory>
#include <functional>

#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <winsock2.h>
#include <windows.h>
#endif

class ProcessManager {
public:
    struct ProcessInfo {
#ifdef _WIN32
        PROCESS_INFORMATION pi;
#else
        pid_t pid;
#endif
        bool valid = false;
    };

    ProcessManager();
    ~ProcessManager();

    // Spawns a new process with the given command and arguments.
    // Returns true if successful.
    bool spawn(const std::string& command, const std::vector<std::string>& args, ProcessInfo& info);

    // Checks if a process is still running.
    bool is_running(const ProcessInfo& info);

    // Terminates a process.
    void terminate(ProcessInfo& info);

    // Waits for a process to exit.
    void wait(ProcessInfo& info);
};
