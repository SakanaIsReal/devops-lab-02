# GitHub Actions Self-Hosted Runner Setup (Windows)

## Port Binding Issues on Windows

### Problem
Windows with Hyper-V enabled reserves dynamic port ranges that can conflict with Docker port bindings. This causes the error:
```
Error response from daemon: ports are not available: exposing port TCP 0.0.0.0:3307 -> 127.0.0.1:0:
listen tcp 0.0.0.0:3307: bind: An attempt was made to access a socket in a way forbidden by its access permissions.
```

### Automatic Workaround (Implemented)
The CI/CD pipeline now includes:
- Automatic detection of Hyper-V port reservations
- WinNAT service restart to clear stale reservations
- Automatic fallback to port 50307 if 3307 is blocked
- Dynamic port configuration passed to backend

### Permanent Fix (Optional)

To permanently free port 3307 on your runner, run these commands as **Administrator**:

#### 1. Check Current Port Exclusions
```powershell
netsh int ipv4 show excludedportrange protocol=tcp
```

#### 2. Check Dynamic Port Range
```powershell
netsh int ipv4 show dynamicport tcp
```

#### 3. Modify Dynamic Port Range (Recommended)
Move the dynamic port range higher to avoid common application ports:

```powershell
# Start dynamic ports at 49152 (IANA ephemeral range)
netsh int ipv4 set dynamicport tcp start=49152 num=16384
netsh int ipv6 set dynamicport tcp start=49152 num=16384
```

#### 4. Restart WinNAT Service
```powershell
Restart-Service WinNat
```

#### 5. Restart Computer
A full restart ensures all port reservations are cleared:
```powershell
Restart-Computer
```

### Alternative: Disable Hyper-V (Not Recommended)
If you don't need Hyper-V, you can disable it (requires restart):

```powershell
# Disable Hyper-V
Disable-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V-All

# Or use DISM
dism.exe /Online /Disable-Feature:Microsoft-Hyper-V
```

**Warning:** This will break Docker Desktop if it's using Hyper-V backend. Only do this if you're using WSL2 backend for Docker.

### Verify Port is Free
After making changes, verify port 3307 is available:

```powershell
# Check netstat
netstat -ano | Select-String ":3307"

# Check exclusions
netsh int ipv4 show excludedportrange protocol=tcp | Select-String "3307"

# Try to bind to the port
Test-NetConnection -ComputerName localhost -Port 3307
```

## Docker Desktop Configuration

Ensure Docker Desktop is configured correctly:

1. **Use WSL2 Backend** (Recommended)
   - Settings → General → Use WSL 2 based engine

2. **Resource Limits**
   - Settings → Resources → Set appropriate CPU/Memory limits

3. **File Sharing**
   - Ensure workspace directory is accessible to Docker

## Runner Service Configuration

The GitHub Actions runner should run as a **Windows Service** with:

- **Account**: Network Service or dedicated service account with Docker access
- **Startup Type**: Automatic
- **Recovery**: Restart on failure

### Install Runner as Service
```powershell
cd C:\actions-runner
.\config.cmd --url https://github.com/YOUR_ORG/YOUR_REPO --token YOUR_TOKEN
.\svc.ps1 install
.\svc.ps1 start
```

## Troubleshooting

### Port Still Blocked After Changes
1. Check for processes using the port:
   ```powershell
   Get-NetTCPConnection -LocalPort 3307 -ErrorAction SilentlyContinue
   ```

2. Check Windows Firewall:
   ```powershell
   Get-NetFirewallRule | Where-Object {$_.LocalPort -eq 3307}
   ```

3. Restart Docker Desktop:
   ```powershell
   Restart-Service com.docker.service
   ```

### CI Pipeline Still Fails
The pipeline will automatically fall back to port 50307 if issues persist. Check the "Fix Windows Hyper-V Port Reservations" step output in the GitHub Actions logs for diagnostics.

### Docker Network Issues
If containers can't communicate:
```powershell
# Clean up Docker networks
docker network prune -f

# Restart Docker
Restart-Service com.docker.service
```

## Monitoring

Check runner health regularly:

```powershell
# Check runner service status
Get-Service actions.runner.*

# Check Docker status
docker info

# Check available ports
netsh int ipv4 show excludedportrange protocol=tcp
```

## References

- [Microsoft: Hyper-V dynamic port range](https://learn.microsoft.com/en-us/troubleshoot/windows-server/networking/service-overview-and-network-port-requirements)
- [Docker Desktop port binding issues](https://github.com/docker/for-win/issues/3171)
- [GitHub Actions self-hosted runners](https://docs.github.com/en/actions/hosting-your-own-runners)
