<div align="center">
  <h1>📚 BookNest Backend</h1>
  <p><strong>A Modern, Robust, and Scalable E-Commerce Microservices Architecture</strong></p>
  
  ![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
  ![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
  ![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
  ![MySQL](https://img.shields.io/badge/MySQL-005C84?style=for-the-badge&logo=mysql&logoColor=white)
  ![RabbitMQ](https://img.shields.io/badge/RabbitMQ-FF6600?style=for-the-badge&logo=rabbitmq&logoColor=white)
  ![Docker](https://img.shields.io/badge/Docker-2CA5E0?style=for-the-badge&logo=docker&logoColor=white)
</div>

<br>

Welcome to the backend repository of **BookNest**. This backend is built from the ground up using a loosely coupled **Microservices Architecture**, ensuring high availability, fault tolerance, and seamless scalability for a premier online bookstore experience.

---

## 🏗️ Architecture Overview

The backend is composed of several independent microservices communicating synchronously via **OpenFeign (REST)** and asynchronously via **RabbitMQ** for event-driven workflows. All external traffic is routed securely through a unified API Gateway.

---

## 🧩 Microservices Breakdown

### 🌐 Infrastructure Services

| Service | Port | Description |
| :--- | :--- | :--- |
| **🚀 API Gateway** | `8080` | The single entry point for the frontend. Handles dynamic routing, CORS, and enforces JWT validation via a custom `AuthenticationFilter`. |
| **🧭 Eureka Server** | `8761` | Service Registry & Discovery. All microservices register here to find and communicate with each other dynamically without hardcoded IPs. |
| **📊 Admin Server** | `9090` | A centralized monitoring dashboard that aggregates health metrics, environment variables, and logs from all actuator-enabled services. |

### 💼 Core Business Services

#### 🔐 Auth Service (`auth-service`)
- **Database:** `booknest_auth`
- **Role:** Centralized Identity & Security Provider.
- **Key Features:** User registration, JWT-based login, role management (`USER`/`ADMIN`), profile management, and OTP-based password resets.
- **Inter-service:** Automatically provisions a user wallet via `WalletClient` during the registration process.

#### 📚 Book Service (`book-service`)
- **Database:** `booknest_book`
- **Role:** The core catalog manager.
- **Key Features:** Advanced book search (title, author, ISBN), genre filtering, pagination, and precise stock management.
- **Inter-service:** Exposes internal endpoints for the `order-service` to atomically reduce stock during checkout.

#### 🛒 Cart Service (`cart-service`)
- **Database:** `booknest_cart`
- **Role:** Shopping cart session management.
- **Key Features:** Add/remove items, update quantities, calculate totals, and clear carts post-purchase. Maintains a 1:1 mapped relationship with active users.

#### 📦 Order Service (`order-service`)
- **Database:** `booknest_order`
- **Role:** The central transaction engine.
- **Key Features:** Manages the entire order lifecycle (`INITIATED`, `CONFIRMED`, `SHIPPED`), handles payment verification (via Razorpay integration), and manages user delivery addresses.
- **Inter-service:** Orchestrates purchases by calling `book-service` to deduct stock, `wallet-service` to deduct funds (if paying via wallet), and emits `OrderEvent` messages to RabbitMQ.

#### 💳 Wallet Service (`wallet-service`)
- **Database:** `booknest_wallet`
- **Role:** Integrated digital payment system.
- **Key Features:** Maintains user wallet balances, tracks detailed transaction statements (Deposits/Withdrawals), and allows seamless, instant order checkouts.

#### 🔔 Notification Service (`notification-service`)
- **Database:** `booknest_notification`
- **Role:** Asynchronous event listener and dispatcher.
- **Key Features:** Listens to `order-events` on RabbitMQ and dispatches real-time in-app notifications and transactional emails without blocking the main order flow.

#### ⭐ Review & Wishlist Services
- **Databases:** Separate schemas for reviews and wishlists.
- **Role:** User engagement components.
- **Key Features:** Handles book ratings, moderation pipelines, and allows users to save favorite books for later.

---

## 🚀 Tech Stack

- **Core Framework:** Java 17+, Spring Boot 3.x
- **Microservices Routing:** Spring Cloud Netflix Eureka, Spring Cloud Gateway, OpenFeign
- **Database:** MySQL (Strictly isolated schema per microservice)
- **Message Broker:** RabbitMQ
- **Security:** Spring Security, JWT (JSON Web Tokens)
- **Containerization:** Docker & Docker Compose

---

## 🛠️ Getting Started

### Prerequisites
- **JDK 17** or higher
- **Maven 3.8+**
- **Docker** and **Docker Compose** (for infrastructure like MySQL and RabbitMQ)

### 🏃‍♂️ Running Locally

1. **Start Infrastructure Services**
   Fire up the required databases and messaging queues using the provided Compose file:
   ```bash
   docker-compose up -d
   ```

2. **Boot the Microservices**
   Run the services in the following order to ensure proper registration:
   1. `eureka-server`
   2. `api-gateway`
   3. All other core services (`auth-service`, `book-service`, etc.)

   To start an individual service:
   ```bash
   cd <service-name>
   mvn spring-boot:run
   ```
---

## 🐳 Docker Deployment

To build and run the entire microservices ecosystem locally using Docker in one shot:

```bash
# Build the application artifacts
mvn clean package -DskipTests

# Build Docker images and start all containers
docker-compose up --build -d
```

---
