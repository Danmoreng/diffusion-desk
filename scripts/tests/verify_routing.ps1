# Verify Routing & Security Script for DiffusionDesk
$ScriptDir = $PSScriptRoot
$ProjectRoot = Split-Path (Split-Path $ScriptDir)
$ServerExe = Join-Path (Join-Path $ProjectRoot "build") "bin\diffusion_desk_server.exe"
if (!(Test-Path $ServerExe)) { $ServerExe = Join-Path (Join-Path $ProjectRoot "build") "diffusion_desk_server.exe" }

Write-Host "Starting server for routing & security test..."
# Generate a known token for testing
$TestToken = "test-security-token-123"
$Process = Start-Process -FilePath $ServerExe -ArgumentList "--listen-port 1234 --internal-token $TestToken --model-dir . --diffusion-model dummy.gguf --llm-model dummy.gguf" -PassThru -NoNewWindow
Start-Sleep -Seconds 5 # Wait for startup

$tests = @(
    @{ Path = "/"; ExpectedStatus = 200; Description = "Root Redirect" },
    @{ Path = "/app/"; ExpectedStatus = 200; Description = "Vue Index" },
    @{ Path = "/app/settings"; ExpectedStatus = 200; Description = "SPA Fallback" },
    @{ Path = "/health"; ExpectedStatus = 200; Description = "API Health (Proxied)" },
    # Security Tests
    @{ Path = ":1235/internal/health"; ExpectedStatus = 200; Token = $TestToken; Description = "Worker Direct (Valid Token)" },
    @{ Path = ":1235/internal/health"; ExpectedStatus = 401; Token = "wrong-token"; Description = "Worker Direct (Invalid Token)" },
    @{ Path = ":1235/internal/health"; ExpectedStatus = 401; Token = $null; Description = "Worker Direct (No Token)" }
)

$allPassed = $true

foreach ($test in $tests) {
    if ($test.Path -match "^:") {
        $url = "http://localhost" + $test.Path
    } else {
        $url = "http://localhost:1234" + $test.Path
    }
    
    try {
        $headers = @{}
        if ($null -ne $test.Token) {
            $headers["X-Internal-Token"] = $test.Token
        }
        
        $response = Invoke-WebRequest -Uri $url -UseBasicParsing -MaximumRedirection 5 -Headers $headers
        $status = [int]$response.StatusCode
        $content = $response.Content
        
        Write-Host "Test: $($test.Description) ($url)"
        Write-Host "  Expected: $($test.ExpectedStatus), Got: $status"
        
        if ($status -ne $test.ExpectedStatus) {
            Write-Host "  FAILED: Status mismatch" -ForegroundColor Red
            $allPassed = $false
        } else {
            Write-Host "  Success" -ForegroundColor Green
        }

    } catch {
        $status = [int]$_.Exception.Response.StatusCode
        Write-Host "Test: $($test.Description) ($url)"
        Write-Host "  Expected: $($test.ExpectedStatus), Got: $status"
        
        if ($status -eq $test.ExpectedStatus) {
            Write-Host "  Success (Caught expected error)" -ForegroundColor Green
        } else {
            Write-Host "  FAILED: Status mismatch or connection error" -ForegroundColor Red
            Write-Host "  Error: $_"
            $allPassed = $false
        }
    }
}

Write-Host "Cleaning up..."
# Kill all related processes just to be sure
taskkill /F /IM diffusion_desk_server.exe /IM diffusion_desk_sd_worker.exe /IM diffusion_desk_llm_worker.exe /T

if ($allPassed) {
    Write-Host "`nAll routing & security tests passed!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`nSome tests failed." -ForegroundColor Red
    exit 1
}
