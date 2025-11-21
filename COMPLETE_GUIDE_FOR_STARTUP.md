<aside>
🍵

This is a ✨*SUPER GUIDE* ✨ for anyone who what to run this project.

</aside>

***ffs please work ffs please work ffs please work ffs please work ffs please work ffs please work ffs please work ffs please work ffs please work ffs please work ffs please work ffs please work.** Anyways let’s start :D*

---

# 01 | Running the Kubernetes

1. Go to root of the project directory, start PowerShell, and then run this command to turn on the policy to run automate setup `.sh`
    
    ```powershell
    # Set execution policy to allow local scripts (one-time)
    Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
    
    # Or run with bypass (temporary)
    Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process
    ```
    
2. Then run this command in the same terminal to use the automate setup

    ```powershell
    ./deploy-minikube.ps1
    ```

    you need to wait around like 5 minutes to setup all process.

3. **Access the application externally** - You have 2 options:

## Option A: Using Minikube Tunnel (Recommended) ⭐

This is the **standard way** to access Minikube applications from your local machine using `localhost`.

**Start the tunnel:**
```powershell
# Run in PowerShell as Administrator
minikube tunnel
```

> **Important:** Keep this terminal window open! The tunnel runs in the foreground and must stay active.

**Access the application:**
- Frontend: http://localhost or http://127.0.0.1
- Backend API: http://localhost/api
- Swagger UI: http://localhost/api/swagger-ui/index.html

**Why use Minikube tunnel?**
- ✅ Clean URLs without port numbers
- ✅ Production-like routing through ingress
- ✅ Properly handles API proxy configuration
- ✅ Most similar to real deployment

## Option B: Using Minikube IP Directly

If you can't run as Administrator or prefer not to use tunnel:

**Get Minikube IP:**
```powershell
minikube ip
# Returns: 192.168.49.2 (example)
```

**Access the application:**
- Frontend: http://192.168.49.2
- Backend API: http://192.168.49.2/api
- Swagger UI: http://192.168.49.2/api/swagger-ui/index.html

## Option C: Port-Forwarding (Debugging Only - NOT RECOMMENDED)

> ⚠️ **Warning:** Port-forwarding bypasses ingress and causes 405 errors with the current nginx configuration. Only use this for debugging individual services.

```powershell
# Only for debugging - not for normal use
.\scripts\start-port-forward.ps1
```

**Why port-forwarding causes issues:**
- The frontend nginx is configured to proxy API requests to `backend.smartsplit.svc.cluster.local:8081`
- This cluster-internal DNS only works when accessing through ingress
- When using port-forward, the frontend can't resolve the backend DNS, causing 405 errors
- **Solution:** Use Minikube tunnel or Minikube IP instead

## Summary

| Method | URL | Pros | Cons |
|--------|-----|------|------|
| **Minikube Tunnel** ⭐ | http://localhost | Clean URLs, production-like | Requires admin |
| **Minikube IP** | http://192.168.49.2 | No admin needed | IP in URL |
| Port-forwarding | http://localhost:3003 | Direct pod access | ❌ Causes 405 errors |

---

## Troubleshooting Common Issues

### Issue 1: 405 Method Not Allowed Errors on Login/Register

**Symptoms:**
- Login or register requests fail with 405 error
- API calls return "Method Not Allowed"

**Cause:**
Port-forwarding is active, which bypasses the ingress and causes DNS resolution issues with the nginx proxy configuration.

**Solution:**
1. Stop port-forwarding:
   ```powershell
   .\scripts\stop-port-forward.ps1
   ```

2. Use Minikube tunnel instead:
   ```powershell
   minikube tunnel
   ```

3. Access at http://localhost

### Issue 2: Minikube Tunnel Requires Admin Privileges

**Symptoms:**
- `minikube tunnel` fails with permission error
- Cannot bind to port 80

**Solution:**
Use Option B (Minikube IP directly) instead:
```powershell
minikube ip
# Then access at the returned IP, e.g., http://192.168.49.2
```

### Issue 3: Application Not Loading After Deployment

**Check these steps:**

1. Verify all pods are running:
   ```powershell
   kubectl get pods -n smartsplit
   # All should show STATUS: Running
   ```

2. Check ingress is configured:
   ```powershell
   kubectl get ingress -n smartsplit
   # Should show an ADDRESS
   ```

3. Verify tunnel is running (if using tunnel):
   ```powershell
   # Should see "Tunnel successfully started"
   minikube tunnel
   ```

4. Check pod logs for errors:
   ```powershell
   # Frontend logs
   kubectl logs -n smartsplit deployment/frontend --tail=50

   # Backend logs
   kubectl logs -n smartsplit deployment/backend --tail=50
   ```

### Issue 4: Backend API Returns 404

**Cause:** API endpoints might have changed or backend isn't fully started.

**Solution:**
1. Check backend health:
   ```powershell
   # Via tunnel:
   curl http://localhost/actuator/health

   # Via IP:
   curl http://192.168.49.2/actuator/health
   ```

2. Wait for backend to fully start (check logs for "Started SmartSplitBackApplication")

### Issue 5: Changes Not Reflected After Rebuild

**Cause:** Kubernetes is using cached Docker images.

**Solution:**
1. Delete the old pods to force pull new images:
   ```powershell
   kubectl delete pods -n smartsplit -l app=frontend
   kubectl delete pods -n smartsplit -l app=backend
   ```

2. Or restart deployments:
   ```powershell
   kubectl rollout restart deployment/frontend -n smartsplit
   kubectl rollout restart deployment/backend -n smartsplit
   ```

If you want more explanation, please check the [`KUBERNETES.md`](http://KUBERNETES.md)

---

# 02 | GitHub Actions Runner

Already setup and error: [Look this up](https://chat.deepseek.com/share/jhisce3gskewuktarm)

1. Go to you `C:\actions-runner>` then run `run ./run.cmd` and prey to god to will be fine.
2. Done, easy right :D

---

# 03 |