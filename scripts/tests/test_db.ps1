$baseUrl = "http://127.0.0.1:1234"

Write-Host "--- DiffusionDesk Database Diagnostic ---" -ForegroundColor Cyan

# 1. Check DB File
$dbPath = "mysti.db"
if (Test-Path $dbPath) {
    $size = (Get-Item $dbPath).Length / 1KB
    Write-Host "[OK] Database file found: $dbPath ($($size.ToString('F2')) KB)" -ForegroundColor Green
} else {
    Write-Host "[ERROR] Database file 'mysti.db' not found in current directory!" -ForegroundColor Red
}

# 2. Check API
Write-Host "`nTesting API: $baseUrl/v1/history/images ..." -ForegroundColor Cyan
try {
    $json = Invoke-RestMethod -Uri "$baseUrl/v1/history/images" -Method Get -TimeoutSec 5
    $response = $json.data
    Write-Host "[OK] API responded with $($response.Count) items." -ForegroundColor Green
    
    if ($json.next_cursor) {
        Write-Host "     Next Cursor: $($json.next_cursor)" -ForegroundColor Gray
    }
    
    if ($response.Count -gt 0) {
        Write-Host "`nLast 3 images in DB:" -ForegroundColor Yellow
        $response | Select-Object -First 3 | ForEach-Object {
            Write-Host " - ID: $($_.id)"
            Write-Host "   Path: $($_.file_path)"
            Write-Host "   Prompt: $($_.params.prompt.Substring(0, [Math]::Min(50, $_.params.prompt.Length)))..."
        }
    } else {
        Write-Host "[WARN] Database is empty. No history items returned." -ForegroundColor Yellow
    }
} catch {
    Write-Host "[ERROR] Failed to connect to Orchestrator. Is it running?" -ForegroundColor Red
    Write-Host $_.Exception.Message
}

Write-Host "`nTesting Tags API: $baseUrl/v1/history/tags ..." -ForegroundColor Cyan
try {
    $tags = Invoke-RestMethod -Uri "$baseUrl/v1/history/tags" -Method Get
    Write-Host "[OK] Found $($tags.Count) tags in DB." -ForegroundColor Green
    if ($tags.Count -gt 0) {
        $tagNames = $tags.name -join ", "
        Write-Host " Tags: $tagNames"
    }
} catch {
    Write-Host "[ERROR] Tags API failed." -ForegroundColor Red
}
