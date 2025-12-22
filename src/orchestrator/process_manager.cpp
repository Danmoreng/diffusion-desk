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
// POSIX implementation placeholder
bool ProcessManager::spawn(const std::string& command, const std::vector<std::string>& args, ProcessInfo& info) {
    return false;
}
bool ProcessManager::is_running(const ProcessInfo& info) { return false; }
void ProcessManager::terminate(ProcessInfo& info) {}
void ProcessManager::wait(ProcessInfo& info) {}
#endif
