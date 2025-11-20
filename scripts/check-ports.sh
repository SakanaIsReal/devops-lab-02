#!/bin/bash

# Port Availability Checker for SmartSplit
# Checks which ports are in use and what services are running on them

# Colors
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
WHITE='\033[1;37m'
GRAY='\033[0;37m'
NC='\033[0m' # No Color

echo -e "\n${CYAN}=================================${NC}"
echo -e "${CYAN}SmartSplit Port Availability Check${NC}"
echo -e "${CYAN}=================================\n${NC}"

# Define all SmartSplit ports
declare -A ports
ports[3000]="Frontend Dev Server (npm start)"
ports[3003]="Frontend (Minikube Port Forward)"
ports[8080]="Frontend (Docker Compose)"
ports[8081]="Backend (Docker Compose)"
ports[16048]="Backend (Minikube/CI/CD)"
ports[8082]="MySQL (Docker/Minikube)"
ports[3306]="MySQL (Direct/System)"
ports[3307]="MySQL (CI/CD Test)"

# Counters
in_use_count=0
available_count=0

# Check if lsof is available
if ! command -v lsof &> /dev/null; then
    echo -e "${YELLOW}Warning: 'lsof' command not found. Install it for better results.${NC}"
    echo -e "${YELLOW}  Ubuntu/Debian: sudo apt-get install lsof${NC}"
    echo -e "${YELLOW}  macOS: (should be pre-installed)${NC}\n"
fi

# Check each port in order
for port in 3000 3003 8080 8081 16048 8082 3306 3307; do
    service="${ports[$port]}"

    # Check if port is in use
    if command -v lsof &> /dev/null; then
        # Using lsof
        result=$(lsof -i :$port -sTCP:LISTEN 2>/dev/null)

        if [ -n "$result" ]; then
            in_use_count=$((in_use_count + 1))

            # Extract process info
            process_info=$(echo "$result" | tail -n 1 | awk '{print $1 " (PID: " $2 ")"}')

            echo -e "[${YELLOW}IN USE${NC}] Port ${WHITE}$port${NC} - ${GRAY}$service${NC}"
            echo -e "        Process: ${YELLOW}$process_info${NC}"
        else
            available_count=$((available_count + 1))
            echo -e "[${GREEN}FREE   ${NC}] Port ${WHITE}$port${NC} - ${GRAY}$service${NC}"
        fi
    else
        # Fallback: Try to connect to port
        if nc -z localhost $port 2>/dev/null; then
            in_use_count=$((in_use_count + 1))
            echo -e "[${YELLOW}IN USE${NC}] Port ${WHITE}$port${NC} - ${GRAY}$service${NC}"
            echo -e "        Process: ${YELLOW}Unknown (install lsof for details)${NC}"
        else
            available_count=$((available_count + 1))
            echo -e "[${GREEN}FREE   ${NC}] Port ${WHITE}$port${NC} - ${GRAY}$service${NC}"
        fi
    fi
done

# Summary
echo -e "\n${CYAN}---------------------------------${NC}"
echo -e "${CYAN}Summary:${NC}"
if [ $in_use_count -gt 0 ]; then
    echo -e "  Ports in use: ${YELLOW}$in_use_count${NC}"
else
    echo -e "  Ports in use: ${GREEN}$in_use_count${NC}"
fi
echo -e "  Ports available: ${GREEN}$available_count${NC}"
echo -e "${CYAN}---------------------------------\n${NC}"

# Check for Docker containers
echo -e "${CYAN}Docker Containers:${NC}"
echo -e "${CYAN}---------------------------------${NC}"
if command -v docker &> /dev/null; then
    containers=$(docker ps --format "table {{.Names}}\t{{.Ports}}" 2>/dev/null)
    if [ -n "$containers" ]; then
        echo "$containers"
    else
        echo -e "${GRAY}No Docker containers running${NC}"
    fi
else
    echo -e "${GRAY}Docker is not running or not installed${NC}"
fi

# Check for kubectl port-forwards
echo -e "\n${CYAN}Kubectl Port Forwards:${NC}"
echo -e "${CYAN}---------------------------------${NC}"
if command -v kubectl &> /dev/null; then
    kubectl_pids=$(pgrep -f "kubectl.*port-forward" 2>/dev/null)
    if [ -n "$kubectl_pids" ]; then
        echo -e "${YELLOW}Active kubectl port-forward processes found:${NC}"
        echo "$kubectl_pids" | while read pid; do
            if [ -n "$pid" ]; then
                cmd=$(ps -p $pid -o args= 2>/dev/null)
                echo -e "  ${YELLOW}PID: $pid${NC}"
                echo -e "  ${GRAY}Command: $cmd${NC}"
            fi
        done
        echo -e "\n${GRAY}Note: Port forwards may be active. Check with:${NC}"
        echo -e "${GRAY}  kubectl get svc -n smartsplit${NC}"
    else
        echo -e "${GRAY}No kubectl port-forward processes found${NC}"
    fi
else
    echo -e "${GRAY}kubectl not found or not running${NC}"
fi

# Helpful commands
echo -e "\n${CYAN}Helpful Commands:${NC}"
echo -e "${CYAN}---------------------------------${NC}"
echo -e "${GRAY}Kill a process: ${WHITE}kill -9 <PID>${NC}"
echo -e "${GRAY}Start Docker Compose: ${WHITE}docker-compose up${NC}"
echo -e "${GRAY}Start Minikube port forwards: ${WHITE}./scripts/start-port-forward.sh${NC}"
echo -e "${GRAY}Stop Minikube port forwards: ${WHITE}./scripts/stop-port-forward.sh${NC}"
echo -e "${GRAY}Full port documentation: ${WHITE}docs/PORT_CONFIGURATION.md${NC}"

echo -e "\n${CYAN}=================================\n${NC}"
