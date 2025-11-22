# SmartSplit

A full-stack expense splitting application for managing and sharing group expenses

Project by **devops-lab-02**

---

## **Application**

Expense Splitting & Group Bill Management

---

## **Course / Context**

**Course:** ITDS323 Practical DevOps and Applications
**Institution:** Faculty of ICT, Mahidol University

---

## **URLs**

* **Live Demo / Application:** `<link>`
* **Presentation Slide:** `<link>`
* **GitHub Repository:** `<link>` (Private)

---

## **Team Members**

* Krittanon Chongklahan
* Saisawat
* Jutichot Phenpan
* Navadol Somboonkul
* Wattanachai Boonchai

---

## **Overview**

SmartSplit is a web-based expense splitting application designed to help groups manage shared expenses efficiently. Users can create groups, add expenses with flexible split options, track payments, and settle balances among members.

This project is part of the course ITDS323 Practical DevOps and Applications, demonstrating industry-standard practices:

* Web application development (React + Spring Boot)
* Automated testing (Unit + Integration + E2E)
* Infrastructure as code (Docker, Kubernetes)
* Containerization and orchestration
* CI/CD pipelines with GitHub Actions
* JWT-based authentication
* Multi-currency support with exchange rates

---

## **Core Features**

### **User Management**

* User registration and authentication with JWT
* Profile management with avatar upload
* QR code generation for payment information
* Password management

### **Group Management**

* Create and manage expense groups
* Add/remove group members
* Group cover image customization
* Member balance tracking

### **Expense Management**

* Add expenses with multiple split types:
  * Equal split: Divide evenly among participants
  * Manual split: Custom percentage or amount per person
* Multi-line item support per expense
* Multi-currency support with exchange rates
* Expense status tracking (Open, Settled, Canceled)

### **Payment & Settlement**

* Record payments against expenses
* Payment verification workflow
* Receipt upload and storage
* Real-time balance calculations
* Settlement suggestions

### **Dashboard / UI**

* Responsive mobile-first design
* Real-time expense and balance overview
* Group expense history and summaries

---

## **Core Requirements**

### **Phase 1**

- Login/Register system (Multiple users)
- Create a **group** (e.g., for a trip, dinner, project team)
- Add **expenses** with details:
    - Amount
    - Payer
    - Description
    - Participants
- Support **custom splits** for expenses:
    - Equal split
    - Manual split by amount or percentage
    - Tag-based split (e.g., "Alcohol" only charges people who drink)
- Show **who owes whom** and how much
- Mark payments as settled

### **Phase 2 Requirements**

- Add participants by both **group** and **individual** (e.g., selecting "Group A" adds all its members, with the option to add more individuals)
- **Recurring groups** with history (e.g., "Friday Night Gang")
- Receipt attachment or voice notes
- Export summary to PDF
- Multi-currency support with real-time exchange rates and configurable rates (must store original data)
    - If configurable, it must accept batches of data (e.g., CSV)
- Generate a PromptPay QR code for receiving payment and record it (verification not required)
- Smart Settlement Suggestions
    - The system suggests the minimum number of transactions needed to settle all debts.

### **Bonus Features**

- Smart search for participant names with fuzzy matching (e.g., "krant" finds "kant")

---

## **Tech Stack**

### **Frontend**

* React 19 with TypeScript
* TailwindCSS for styling
* React Router v7 for navigation
* Axios for HTTP requests
* React Context API for state management

### **Backend**

* Java 17
* Spring Boot 3.5.5
* Spring Data JPA with Hibernate
* MySQL 8
* Flyway for database migrations
* Spring Security with JWT authentication

### **Infrastructure**

* Docker & Docker Compose
* Kubernetes (Minikube)
* Nginx reverse proxy
* GitHub Actions CI/CD

### **Monitoring / Observability**

* Spring Boot Actuator (health checks, metrics)
* Grafana (optional dashboards)
* Tempo (optional distributed tracing)

---

## **Installation & Run**

### **Backend**

```bash
cd backend
mvn spring-boot:run
```

### **Frontend**

```bash
cd frontend
npm install
npm start
```

### **Full Stack (Docker Compose)**

```bash
docker-compose up -d
```

---

## **Service Ports**

| Component | Port | Description |
| --------- | ---- | ----------- |
| Frontend (Docker) | 8080 | React app served via Nginx |
| Frontend (Dev) | 3000 | React development server |
| Backend API | 8081 | Spring Boot REST API |
| MySQL | 8082 | Database server |
| Minikube Frontend | 3003 | Port-forwarded frontend |
| Minikube Backend | 16048 | Port-forwarded backend |

---

## **Testing**

* **Unit Tests (Backend):** JUnit 5 with Maven (`mvn test`)
* **Integration Tests:** Testcontainers with MySQL (`mvn verify`)
* **Unit Tests (Frontend):** Jest + React Testing Library (`npm test`)
* **E2E Tests:** Cypress (`npm run test:e2e`)

---

## **Project Structure**

```
devops-lab-02/
│── backend/              # Spring Boot REST API
│── frontend/             # React TypeScript application
│── k8s/                  # Kubernetes manifests
│── cypress/              # E2E test suite
│── scripts/              # Utility scripts
│── docs/                 # Documentation
│── .github/workflows/    # CI/CD pipelines
│── docker-compose.yml    # Docker Compose configuration
│── README.md
```

---

## **License**

2026 – devops-lab-02

---

## **Contributors — devops-lab-02**

* Krittanon Chongklahan
* Saisawat
* Jutichot Phenpan
* Navadol Somboonkul
* Wattanachai Boonchai

---

## **ITDS323**

Part of
**Practical DevOps and Applications**
Faculty of ICT, Mahidol University
