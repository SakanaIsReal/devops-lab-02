# Directory containing process IDs
$pidDir = "$PSScriptRoot\pids"

if (Test-Path $pidDir) {
    Write-Host "Stopping port forwarding processes..." -ForegroundColor Yellow
    
    # Stop frontend process
    $frontendPidFile = "$pidDir\frontend.pid"
    if (Test-Path $frontendPidFile) {
        $frontendPid = Get-Content $frontendPidFile
        try {
            Stop-Process -Id $frontendPid -Force -ErrorAction SilentlyContinue
            Remove-Item $frontendPidFile -Force
            Write-Host "Frontend port forwarding stopped." -ForegroundColor Green
        }
        catch {
            Write-Host "Frontend process was not running." -ForegroundColor Gray
        }
    }
    
    # Stop backend process
    $backendPidFile = "$pidDir\backend.pid"
    if (Test-Path $backendPidFile) {
        $backendPid = Get-Content $backendPidFile
        try {
            Stop-Process -Id $backendPid -Force -ErrorAction SilentlyContinue
            Remove-Item $backendPidFile -Force
            Write-Host "Backend port forwarding stopped." -ForegroundColor Green
        }
        catch {
            Write-Host "Backend process was not running." -ForegroundColor Gray
        }
    }
    
    # Clean up PID directory if empty
    if ((Get-ChildItem $pidDir).Count -eq 0) {
        Remove-Item $pidDir -Force
    }
}
else {
    Write-Host "No port forwarding processes found." -ForegroundColor Yellow
}

Write-Host "`nAll port forwarding processes have been stopped." -ForegroundColor Cyan