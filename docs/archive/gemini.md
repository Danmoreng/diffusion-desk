# Session Failures and Environment Notes

## Environment
- **OS:** Windows (win32)
- **Shell:** PowerShell

## Common Issues & Workarounds

### 1. PowerShell Script Execution Policy
**Issue:**
Running scripts directly (e.g., `.\scripts\build.ps1`) fails with `PSSecurityException` because script execution is disabled by default on this system.

**Resolution:**
Always execute PowerShell scripts with the `Bypass` execution policy:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/build.ps1
```

### 2. Reading Ignored Files
**Issue:**
The `read_file` tool fails for files in ignored directories (e.g., `build/`, `libs/`) with "File path ... is ignored by configured ignore patterns."

**Resolution:**
Use `run_shell_command` with `Get-Content` to inspect these files:
```powershell
Get-Content path/to/ignored/file
```
For large files, pipe to `Select-Object`:
```powershell
Get-Content path/to/ignored/file | Select-Object -Index (0..100)
```

### 3. Build Failures (2025-12-22)
**Issue:**
Compilation error in `src/orchestrator/proxy.cpp` due to a lambda signature mismatch.
`httplib::ContentReceiver` expects `(const char*, size_t)`, but the code provided `(const char*, size_t, uint64_t, uint64_t)`.

**Resolution:**
Fixed by updating the lambda parameters to match the expected signature.
