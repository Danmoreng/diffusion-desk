#include "process_manager.hpp"
#include <iostream>
#include <sstream>

ProcessManager::ProcessManager() {}

ProcessManager::~ProcessManager() {}

#ifdef _WIN32
#include <windows.h>
#include <strsafe.h>

bool ProcessManager::spawn(const std::string& command, const std::vector<std::string>& args, ProcessInfo& info) {
    std::stringstream cmd_ss;
    cmd_ss << "\"" << command << "\"";
    for (const auto& arg : args) {
        cmd_ss << " \"" << arg << "\"";
    }
    std::string cmd_line = cmd_ss.str();

    // CreateProcessW requires a mutable string
    std::vector<wchar_t> cmd_vec(cmd_line.length() + 1);
    MultiByteToWideChar(CP_UTF8, 0, cmd_line.c_str(), -1, cmd_vec.data(), (int)cmd_vec.size());

    STARTUPINFOW si;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    // Inherit std handles so output goes to the same console
    si.dwFlags |= STARTF_USESTDHANDLES;
    si.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
    si.hStdOutput = GetStdHandle(STD_OUTPUT_HANDLE);
    si.hStdError = GetStdHandle(STD_ERROR_HANDLE);

    ZeroMemory(&info.pi, sizeof(info.pi));

    if (!CreateProcessW(
        NULL,           // No module name (use command line)
        cmd_vec.data(), // Command line
        NULL,           // Process handle not inheritable
        NULL,           // Thread handle not inheritable
        TRUE,           // Set handle inheritance to TRUE
        0,              // No creation flags (removes CREATE_NEW_CONSOLE)
        NULL,           // Use parent's environment block
        NULL,           // Use parent's starting directory
        &si,            // Pointer to STARTUPINFO structure
        &info.pi)       // Pointer to PROCESS_INFORMATION structure
    ) {
        std::cerr << "CreateProcess failed (" << GetLastError() << ").\n";
        return false;
    }

    info.valid = true;
    return true;
}

bool ProcessManager::is_running(const ProcessInfo& info) {
    if (!info.valid) return false;
    DWORD exit_code;
    if (GetExitCodeProcess(info.pi.hProcess, &exit_code)) {
        return exit_code == STILL_ACTIVE;
    }
    return false;
}

void ProcessManager::terminate(ProcessInfo& info) {
    if (!info.valid) return;
    TerminateProcess(info.pi.hProcess, 1);
    CloseHandle(info.pi.hProcess);
    CloseHandle(info.pi.hThread);
    info.valid = false;
}

void ProcessManager::wait(ProcessInfo& info) {
    if (!info.valid) return;
    WaitForSingleObject(info.pi.hProcess, INFINITE);
    CloseHandle(info.pi.hProcess);
    CloseHandle(info.pi.hThread);
    info.valid = false;
}

#else
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <signal.h>
#include <vector>
#include <cstring>

// POSIX implementation
bool ProcessManager::spawn(const std::string& command, const std::vector<std::string>& args, ProcessInfo& info) {
    pid_t pid = fork();
    if (pid < 0) {
        perror("fork");
        return false;
    } else if (pid == 0) {
        // Child process
        std::vector<char*> c_args;
        c_args.push_back(strdup(command.c_str()));
        for (const auto& arg : args) {
            c_args.push_back(strdup(arg.c_str()));
        }
        c_args.push_back(nullptr);

        execvp(command.c_str(), c_args.data());
        perror("execvp");
        exit(1);
    } else {
        // Parent process
        info.pid = pid;
        info.valid = true;
        return true;
    }
}

bool ProcessManager::is_running(const ProcessInfo& info) {
    if (!info.valid) return false;
    int status;
    pid_t result = waitpid(info.pid, &status, WNOHANG);
    if (result == 0) {
        return true; // Still running
    } else if (result == -1) {
        // Error or process doesn't exist
        return false;
    } else {
        // Process exited
        return false;
    }
}

void ProcessManager::terminate(ProcessInfo& info) {
    if (info.valid) {
        kill(info.pid, SIGTERM);
        int status;
        waitpid(info.pid, &status, 0); // Wait for it to actually exit
        info.valid = false;
    }
}

void ProcessManager::wait(ProcessInfo& info) {
    if (info.valid) {
        int status;
        waitpid(info.pid, &status, 0);
        info.valid = false;
    }
}
#endif
