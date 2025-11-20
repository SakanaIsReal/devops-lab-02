# Port Availability Checker for SmartSplit
# Checks which ports are in use and what services are running on them

Write-Host "`n=================================" -ForegroundColor Cyan
Write-Host "SmartSplit Port Availability Check" -ForegroundColor Cyan
Write-Host "=================================`n" -ForegroundColor Cyan

# Define all SmartSplit ports
$ports = @(
    @{Port = 3000; Service = "Frontend Dev Server (npm start)"; Category = "Frontend"},
    @{Port = 3003; Service = "Frontend (Minikube Port Forward)"; Category = "Frontend"},
    @{Port = 8080; Service = "Frontend (Docker Compose)"; Category = "Frontend"},
    @{Port = 8081; Service = "Backend (Docker Compose)"; Category = "Backend"},
    @{Port = 16048; Service = "Backend (Minikube/CI/CD)"; Category = "Backend"},
    @{Port = 8082; Service = "MySQL (Docker/Minikube)"; Category = "Database"},
    @{Port = 3306; Service = "MySQL (Direct/System)"; Category = "Database"},
    @{Port = 3307; Service = "MySQL (CI/CD Test)"; Category = "Database"}
)

# Check each port
$inUseCount = 0
$availableCount = 0

foreach ($portInfo in $ports) {
    $port = $portInfo.Port
    $service = $portInfo.Service
    $category = $portInfo.Category

    try {
        # Try to get TCP connection on this port
        $conn = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue

        if ($conn) {
            $inUseCount++

            # Get process information
            $process = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue

            if ($process) {
                $processName = $process.ProcessName
                $processId = $conn.OwningProcess

                Write-Host "[" -NoNewline
                Write-Host "IN USE" -ForegroundColor Yellow -NoNewline
                Write-Host "] Port " -NoNewline
                Write-Host $port -ForegroundColor White -NoNewline
                Write-Host " - $service" -ForegroundColor Gray
                Write-Host "        Process: $processName (PID: $processId)" -ForegroundColor Yellow
            } else {
                Write-Host "[" -NoNewline
                Write-Host "IN USE" -ForegroundColor Yellow -NoNewline
                Write-Host "] Port " -NoNewline
                Write-Host $port -ForegroundColor White -NoNewline
                Write-Host " - $service" -ForegroundColor Gray
                Write-Host "        Process: Unknown (PID: $($conn.OwningProcess))" -ForegroundColor Yellow
            }
        } else {
            $availableCount++
            Write-Host "[" -NoNewline
            Write-Host "FREE   " -ForegroundColor Green -NoNewline
            Write-Host "] Port " -NoNewline
            Write-Host $port -ForegroundColor White -NoNewline
            Write-Host " - $service" -ForegroundColor Gray
        }
    } catch {
        $availableCount++
        Write-Host "[" -NoNewline
        Write-Host "FREE   " -ForegroundColor Green -NoNewline
        Write-Host "] Port " -NoNewline
        Write-Host $port -ForegroundColor White -NoNewline
        Write-Host " - $service" -ForegroundColor Gray
    }
}

# Summary
Write-Host "`n---------------------------------" -ForegroundColor Cyan
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host "  Ports in use: " -NoNewline
Write-Host $inUseCount -ForegroundColor $(if ($inUseCount -gt 0) { "Yellow" } else { "Green" })
Write-Host "  Ports available: " -NoNewline
Write-Host $availableCount -ForegroundColor Green
Write-Host "---------------------------------`n" -ForegroundColor Cyan

# Check for Docker containers
Write-Host "Docker Containers:" -ForegroundColor Cyan
Write-Host "---------------------------------" -ForegroundColor Cyan
try {
    $containers = docker ps --format "table {{.Names}}\t{{.Ports}}" 2>$null
    if ($containers) {
        Write-Host $containers
    } else {
        Write-Host "No Docker containers running" -ForegroundColor Gray
    }
} catch {
    Write-Host "Docker is not running or not installed" -ForegroundColor Gray
}

# Check for kubectl port-forwards
Write-Host "`nKubectl Port Forwards:" -ForegroundColor Cyan
Write-Host "---------------------------------" -ForegroundColor Cyan
try {
    $kubectlProcesses = Get-Process kubectl -ErrorAction SilentlyContinue
    if ($kubectlProcesses) {
        Write-Host "Active kubectl processes found:" -ForegroundColor Yellow
        foreach ($proc in $kubectlProcesses) {
            Write-Host "  PID: $($proc.Id) - Started: $($proc.StartTime)" -ForegroundColor Yellow
        }
        Write-Host "`nNote: Port forwards may be active. Check with:" -ForegroundColor Gray
        Write-Host "  kubectl get svc -n smartsplit" -ForegroundColor Gray
    } else {
        Write-Host "No kubectl port-forward processes found" -ForegroundColor Gray
    }
} catch {
    Write-Host "kubectl not found or not running" -ForegroundColor Gray
}

# Helpful commands
Write-Host "`nHelpful Commands:" -ForegroundColor Cyan
Write-Host "---------------------------------" -ForegroundColor Cyan
Write-Host "Kill a process: " -NoNewline -ForegroundColor Gray
Write-Host "Stop-Process -Id <PID> -Force" -ForegroundColor White

Write-Host "Start Docker Compose: " -NoNewline -ForegroundColor Gray
Write-Host "docker-compose up" -ForegroundColor White

Write-Host "Start Minikube port forwards: " -NoNewline -ForegroundColor Gray
Write-Host ".\scripts\start-port-forward.ps1" -ForegroundColor White

Write-Host "Stop Minikube port forwards: " -NoNewline -ForegroundColor Gray
Write-Host ".\scripts\stop-port-forward.ps1" -ForegroundColor White

Write-Host "Full port documentation: " -NoNewline -ForegroundColor Gray
Write-Host "docs\PORT_CONFIGURATION.md" -ForegroundColor White

Write-Host "`n=================================`n" -ForegroundColor Cyan
