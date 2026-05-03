# BookNest Backend: The Master Blueprint

Welcome to the exhaustive technical documentation for the BookNest Backend. This system is built using a **Microservices Architecture** leveraging Spring Boot, Spring Cloud, RabbitMQ, and MySQL.

---

## 🏗️ 0. System Infrastructure

### API Gateway (Port 8080)
- **Role**: Entry point, Security Filter, Load Balancing.
- **Routes**:
  - `/api/v1/auth/**` -> AUTH-SERVICE (Public)
  - `/api/v1/users/**` -> AUTH-SERVICE (Secured)
  - `/api/v1/books/**` -> BOOK-SERVICE (Secured/Public)
  - `/api/v1/cart/**` -> CART-SERVICE (Secured)
  - `/api/v1/orders/**` -> ORDER-SERVICE (Secured)
  - `/api/v1/payments/**` -> ORDER-SERVICE (Secured)
  - `/api/v1/wallet/**` -> WALLET-SERVICE (Secured)
  - `/api/v1/reviews/**` -> REVIEW-SERVICE (Secured)
  - `/api/v1/wishlist/**` -> WISHLIST-SERVICE (Secured)
  - `/api/v1/notifications/**` -> NOTIFICATION-SERVICE (Secured)
- **Filters**: `AuthenticationFilter` (Validates JWT from Authorization Header).

### Eureka Server (Port 8761)
- **Role**: Service Registry and Discovery.

### Admin Server (Port 9090)
- **Role**: Monitoring Dashboard for all Actuator-enabled services.

---

## 🔐 1. Auth Service (`auth-service`)
**Database**: `booknest_auth`

### 📋 Detailed Entities
#### `User`
- `userId` (Long, PK, Auto-increment)
- `fullName` (String, 3-50 chars, NotBlank)
- `email` (String, Unique, NotBlank, Email pattern)
- `passwordHash` (String, Nullable for OAuth)
- `role` (Enum: `USER`, `ADMIN`)
- `provider` (Enum: `LOCAL`, `GOOGLE`, `GITHUB`)
- `mobile` (String, 10 digits, Pattern: `^[0-9]{10}$`)
- `profileImage` (LongText / LongBlob)
- `suspended` (Boolean, default: false)
- `createdAt` (LocalDateTime, Updatable=false)

#### `PasswordResetToken`
- `id` (Long, PK)
- `token` (String, NotBlank): Stores 6-digit OTP.
- `email` (String, NotBlank)
- `expiryDate` (LocalDateTime)

### 📦 DTOs & Payloads
- `LoginRequest`: `email`, `password`.
- `RegisterRequest`: `fullName`, `email`, `password`, `mobile`, `role`.
- `UserResponse`: `userId`, `fullName`, `email`, `role`, `mobile`, `profileImage`, `suspended`.
- `ResetPasswordRequest`: `email`, `token`, `newPassword`.
- `ChangePasswordRequest`: `currentPassword`, `newPassword`.
- `UpdateProfileRequest`: `fullName`, `mobile`, `profileImage`.

### 🚀 API Endpoints (`/api/v1/auth` & `/api/v1/users`)
- `POST /register`: Register user.
- `POST /login`: Login user (Returns JWT).
- `POST /refresh`: Refresh expired JWT.
- `POST /logout`: Blacklist current token.
- `GET /validate`: Internal token validation.
- `GET /profile/{userId}`: Fetch profile.
- `GET /user/{userId}`: Alias for profile.
- `GET /all`: (ADMIN) List all users.
- `PUT /users/{userId}/role`: (ADMIN) Change role.
- `PUT /users/{userId}/suspend`: (ADMIN) Suspend user.
- `PUT /users/{userId}/reactivate`: (ADMIN) Reactivate user.
- `DELETE /users/{userId}`: (ADMIN) Delete user.
- `POST /change-password`: Update password.
- `POST /forgot-password`: Send OTP.
- `POST /reset-password`: Reset via OTP.

### 🔗 Inter-service communication
- **WalletClient**: Calls `POST /api/v1/wallet/create` on `wallet-service` during registration.

---

## 📚 2. Book Service (`book-service`)
**Database**: `booknest_book`

### 📋 Detailed Entities
#### `Book`
- `bookId` (Long, PK)
- `title` (String, NotBlank)
- `author` (String, NotBlank)
- `isbn` (String, Unique, NotBlank)
- `genre` (String)
- `publisher` (String)
- `price` (Double, Min=0, NotNull)
- `stock` (Integer, Min=0, NotNull)
- `rating` (Double)
- `description` (String, max=1000)
- `coverImageUrl` (String)
- `publishedDate` (LocalDate)
- `isFeatured` (Boolean, default: false)

### 📦 DTOs
- `BookRequest`: All fields for create/update.
- `ReduceStockRequest`: `bookId`, `quantity`.
- `ReduceStockResponse`: `bookId`, `success`, `remainingStock`.

### 🚀 API Endpoints (`/api/v1/books`)
- `GET /`: Get all books (Pageable).
- `GET /{id}`: Get by ID.
- `GET /search?keyword=...`: Search across title/author/isbn.
- `GET /genre/{genre}`: Filter by genre.
- `GET /filter`: Advanced filter (minPrice, maxPrice, rating, etc.).
- `POST /`: (ADMIN) Add book.
- `PUT /{id}`: (ADMIN) Update book.
- `DELETE /{id}`: (ADMIN) Delete book.
- `PUT /{id}/stock`: (INTERNAL) Update stock.
- `POST /reduce-stock`: (INTERNAL) Atomically reduce stock for order.

---

## 🛒 3. Cart Service (`cart-service`)
**Database**: `booknest_cart`

### 📋 Detailed Entities
#### `Cart`
- `cartId` (Long, PK)
- `userId` (Long, Unique)
- `totalPrice` (Double)
- **Relationships**: `@OneToMany` with `CartItem`.

#### `CartItem`
- `itemId` (Long, PK)
- `bookId` (Long)
- `bookTitle` (String)
- `price` (Double)
- `quantity` (Integer, Min=1)
- `bookImageUrl` (String)
- **Relationships**: `@ManyToOne` with `Cart`.

### 🚀 API Endpoints (`/api/v1/cart`)
- `GET /{userId}`: Get cart.
- `POST /add`: Add item to cart (Body: `userId`, `bookId`, `quantity`).
- `PUT /update`: Update quantity (Body: `userId`, `itemId`, `quantity`).
- `DELETE /remove/{itemId}`: Remove item.
- `DELETE /clear/{userId}`: Empty cart.

---

## 📦 4. Order Service (`order-service`)
**Database**: `booknest_order`

### 📋 Detailed Entities
#### `Order`
- `orderId` (Long, PK)
- `userId` (Long)
- `orderDate` (LocalDateTime)
- `amountPaid` (Double)
- `paymentMethod` (String: `COD`, `WALLET`, `ONLINE`)
- `orderStatus` (Enum: `INITIATED`, `PLACED`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`)
- `quantity` (Integer)
- `bookId` (Long)
- `bookName` (String)
- `razorpayOrderId` (String), `razorpayPaymentId` (String), `razorpaySignature` (String)
- **Relationships**:
  - `@ManyToOne` with `Address`.
  - `@OneToMany` with `OrderStatusLog`.

#### `Address`
- `addressId` (Long, PK)
- `customerId` (Long)
- `fullName` (String, 2-100 chars)
- `mobileNumber` (String, 10 digits)
- `flatNumber` (String, 200 chars max)
- `city` (String, 100 chars max)
- `state` (String)
- `pincode` (String, 6 digits)
- `isActive` (Boolean)

### 🚀 API Endpoints (`/api/v1/orders`)
- `POST /place`: Create order (Handles stock check and wallet deduction).
- `GET /user/{userId}`: User history.
- `PUT /{orderId}/status`: Update status (Admin).
- `POST /payments/verify`: Verify payment & finalize order.

---

## 💳 5. Wallet Service (`wallet-service`)
**Database**: `booknest_wallet`

### 📋 Detailed Entities
#### `Wallet`
- `walletId` (Long, PK)
- `userId` (Long, Unique)
- `currentBalance` (Double, Min=0)
- **Relationships**: `@OneToMany` with `Statement`.

#### `Statement`
- `id` (Long, PK)
- `amount` (Double)
- `type` (Enum: `DEPOSIT`, `WITHDRAW`)
- `transactionDate` (LocalDateTime)
- `remarks` (String)

---

## 🔔 6. Notification Service (`notification-service`)
**Database**: `booknest_notification`

### 📋 Entities
#### `Notification`
- `notificationId` (Long, PK)
- `userId` (Long)
- `type` (Enum: `ORDER`, `PAYMENT`, `DELIVERY`, `SYSTEM`)
- `message` (String)
- `isRead` (Boolean, default: false)
- `createdAt` (LocalDateTime)

### 🚀 Event Processing (RabbitMQ)
- **Binding**: `orderProcessor` -> `order-events` destination.
- **Logic**: Listens for `OrderEvent` payload: `orderId`, `userId`, `type`, `status`, `message`, `timestamp`.
- **Outputs**: In-App notification + Email dispatch.

---

## ⭐ 7. Review & Wishlist Services
**Purpose**: User engagement and personalization.
- **Review**: `rating`, `comment`, `status` (PENDING/APPROVED).
- **Wishlist**: `userId`, `bookId` collection.

---

## 📡 8. Cross-Service Communication Map

### Asynchronous (RabbitMQ)
- `order-service` -> `order-events` -> `notification-service`.

### Synchronous (Feign Clients)
- `AuthClient` -> `auth-service`.
- `BookClient` -> `book-service`.
- `CartClient` -> `cart-service`.
- `OrderClient` -> `order-service`.
- `WalletClient` -> `wallet-service`.

---

## 🛠️ 9. Global Technical Details
- **Security**: JWT-based auth via API Gateway.
- **Exception Handling**: Global `@RestControllerAdvice` returning `ApiResponse`.
- **Isolation**: Each service has its own MySQL schema and dedicated configuration.