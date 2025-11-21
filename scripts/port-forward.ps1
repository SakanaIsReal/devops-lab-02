# Function to check if a port is in use
function Test-PortInUse {
    param($port)
    
    $listener = $null
    try {
        $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $port)
        $listener.Start()
        return $false
    }
    catch {
        return $true
    }
    finally {
        if ($listener) {
            $listener.Stop()
        }
    }
}

# Function to check if Kubernetes services are ready
function Test-KubernetesServices {
    $services = @(
        @{Name = "frontend"; Namespace = "smartsplit"}
        @{Name = "backend"; Namespace = "smartsplit"}
        @{Name = "mysql"; Namespace = "smartsplit"}
    )
    
    foreach ($svc in $services) {
        Write-Host "Checking $($svc.Name) service..." -NoNewline
        try {
            $service = kubectl get svc $svc.Name -n $svc.Namespace -o json | ConvertFrom-Json
            Write-Host "OK" -ForegroundColor Green
        }
        catch {
            Write-Host "Failed" -ForegroundColor Red
            Write-Host "Error: Service $($svc.Name) not found in namespace $($svc.Namespace)" -ForegroundColor Red
            return $false
        }
    }
    return $true
}

# Check if ports are already in use
$portsToCheck = @(3003, 16048, 8082)
$portsInUse = $false

foreach ($port in $portsToCheck) {
    if (Test-PortInUse $port) {
        Write-Host "Port $port is already in use!" -ForegroundColor Red
        $portsInUse = $true
    }
}

if ($portsInUse) {
    Write-Host "`nPlease stop any applications using these ports and try again." -ForegroundColor Yellow
    Write-Host "You can use stop-port-forward.ps1 to stop previous port forwarding processes." -ForegroundColor Yellow
    exit 1
}

# Check Kubernetes services
Write-Host "`nChecking Kubernetes services..."
if (-not (Test-KubernetesServices)) {
    Write-Host "`nPlease ensure all services are deployed and try again." -ForegroundColor Red
    exit 1
}

# If all checks pass, start port forwarding
Write-Host "`nAll checks passed. Starting port forwarding..." -ForegroundColor Green
& "$PSScriptRoot\start-port-forward.ps1"