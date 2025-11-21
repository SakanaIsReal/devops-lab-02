# Create a directory to store process IDs
$pidDir = "$PSScriptRoot\pids"
if (-not (Test-Path $pidDir)) {
    New-Item -ItemType Directory -Path $pidDir | Out-Null
}

Write-Host "Starting port forwarding for frontend, backend, and MySQL services..." -ForegroundColor Green

# Start frontend port forwarding
$frontendProcess = Start-Process powershell -ArgumentList "kubectl port-forward svc/frontend 3003:80 -n smartsplit" -WindowStyle Normal -PassThru
$frontendProcess.Id | Out-File "$pidDir\frontend.pid"
Write-Host "Frontend port forwarding started. Access at http://localhost:3003" -ForegroundColor Cyan

# Start backend port forwarding
$backendProcess = Start-Process powershell -ArgumentList "kubectl port-forward svc/backend 16048:8081 -n smartsplit" -WindowStyle Normal -PassThru
$backendProcess.Id | Out-File "$pidDir\backend.pid"
Write-Host "Backend port forwarding started. Access at http://localhost:16048" -ForegroundColor Cyan

# Start MySQL port forwarding
$mysqlProcess = Start-Process powershell -ArgumentList "kubectl port-forward svc/mysql 8082:3306 -n smartsplit" -WindowStyle Normal -PassThru
$mysqlProcess.Id | Out-File "$pidDir\mysql.pid"
Write-Host "MySQL port forwarding started. Access at localhost:8082" -ForegroundColor Cyan

Write-Host "`nPort forwarding is running. To stop, run stop-port-forward.ps1" -ForegroundColor Yellow
Write-Host "Process IDs are stored in $pidDir" -ForegroundColor Gray