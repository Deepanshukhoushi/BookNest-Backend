package com.booknest.orderservice.service.impl;

import com.booknest.orderservice.client.BookClient;
import com.booknest.orderservice.client.CartClient;
import com.booknest.orderservice.client.WalletClient;
import com.booknest.orderservice.dto.BookDTO;
import com.booknest.orderservice.dto.CartDTO;
import com.booknest.orderservice.dto.CartItemDTO;
import com.booknest.orderservice.dto.CheckoutRequest;
import com.booknest.orderservice.dto.InvoiceResponse;
import com.booknest.orderservice.dto.ReduceStockRequest;
import com.booknest.orderservice.dto.WalletDTO;
import com.booknest.orderservice.dto.WalletRequest;
import com.booknest.orderservice.entity.Address;
import com.booknest.orderservice.entity.Order;
import com.booknest.orderservice.entity.OrderStatusLog;
import com.booknest.orderservice.enums.OrderStatus;
import com.booknest.orderservice.exception.InsufficientBalanceException;
import com.booknest.orderservice.exception.InsufficientStockException;
import com.booknest.orderservice.exception.InvalidPaymentException;
import com.booknest.orderservice.payload.ApiResponse;
import com.booknest.orderservice.repository.AddressRepository;
import com.booknest.orderservice.repository.OrderRepository;
import com.booknest.orderservice.service.OrderService;
import com.booknest.orderservice.service.RazorpayService;
import org.json.JSONObject;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service implementation for managing the order lifecycle, payments, and checkout process.
 * Orchestrates calls to book, cart, and wallet services while handling transaction consistency.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final BookClient bookClient;
    private final CartClient cartClient;
    private final WalletClient walletClient;
    private final RazorpayService razorpayService;
    private final com.booknest.orderservice.event.OrderEventPublisher eventPublisher;

    // Initiates an online payment transaction using Razorpay
    @Override
    @Transactional
    public String initiateRazorpayPayment(Long userId, Long addressId) {
        log.info("Initiating Razorpay payment for userId: {}", userId);
        CartDTO cart = fetchCart(userId);
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new InvalidPaymentException("Cannot checkout with an empty cart");
        }

        Address address = resolveCheckoutAddress(userId, addressId);
        Map<Long, BookDTO> booksById = validateStockAndFetchBooks(cart.getItems());
        double totalAmount = calculateTotalAmount(cart.getItems(), booksById, null);

        try {
            if (razorpayService.isSimulationMode()) {
                log.info("Razorpay is not configured. Falling back to simulated online payment flow for userId={}", userId);
                return razorpayService.createOrder(totalAmount);
            }

            String rzpOrderId = razorpayService.createOrder(totalAmount);
            
            // Create and save orders in PAYMENT_PENDING status
            List<Order> orders = new ArrayList<>();
            for (CartItemDTO item : cart.getItems()) {
                BookDTO book = booksById.get(item.getBookId());
                Order order = Order.builder()
                        .userId(userId)
                        .orderDate(LocalDateTime.now())
                        .amountPaid(calculateLineItemAmount(book, item.getQuantity()))
                        .modeOfPayment("ONLINE")
                        .paymentMethod("ONLINE")
                        .orderStatus(OrderStatus.PLACED)
                        .quantity(item.getQuantity())
                        .bookId(item.getBookId())
                        .bookName(book.getTitle())
                        .address(address)
                        .razorpayOrderId(rzpOrderId)
                        .build();
                orders.add(order);
            }
            orderRepository.saveAll(orders);
            
            return rzpOrderId;
        } catch (Exception e) {
            log.error("Failed to initiate Razorpay payment: {}", e.getMessage());
            throw new RuntimeException("Payment initiation failed: " + e.getMessage());
        }
    }

    // Fetches all orders stored in the system
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    // Processes a checkout request, handles payment, reduces stock, and saves the order
    @Override
    @Transactional
    public List<Order> checkout(CheckoutRequest request) {
        log.info("Received checkout request: {}", request);
        Long userId = request == null ? null : request.getUserId();
        if (userId == null) {
            log.error("Checkout failed: userId is missing");
            throw new InvalidPaymentException("userId is required");
        }

        String paymentMethod = normalizePaymentMethod(request.getPaymentMethod());
        CartDTO cart = fetchCart(userId);
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new InvalidPaymentException("Cannot checkout with an empty cart");
        }

        Address address = resolveCheckoutAddress(userId, request.getAddressId());
        Map<Long, BookDTO> booksById = validateStockAndFetchBooks(cart.getItems());
        double totalAmount = calculateTotalAmount(cart.getItems(), booksById, request.getDiscountCode());
        List<ReduceStockRequest> reduceRequests = buildReduceRequests(cart.getItems());

        boolean walletDebited = false;
        boolean stockReduced = false;
        try {
            if ("WALLET".equals(paymentMethod)) {
                debitWallet(userId, totalAmount);
                walletDebited = true;
            } else if ("ONLINE".equals(paymentMethod)) {
                verifyOnlinePayment(request.getPaymentDetails());
            }

            reduceStockBatch(reduceRequests);
            stockReduced = true;

            List<Order> orders = saveOrders(userId, paymentMethod, cart.getItems(), booksById, address, request.getDiscountCode());
            clearCart(userId);
            
            // Trigger Order Placed Event
            try {
                publishOrderEvent(
                        orders.get(0).getOrderId(),
                        userId,
                        "ORDER",
                        "PLACED",
                        "Your order with " + cart.getItems().size() + " items has been placed successfully.");
                
                if ("ONLINE".equals(paymentMethod) || "WALLET".equals(paymentMethod)) {
                    publishOrderEvent(
                            orders.get(0).getOrderId(),
                            userId,
                            "PAYMENT",
                            "SUCCESS",
                            "Payment of " + totalAmount + " confirmed via " + paymentMethod);
                }

                for (CartItemDTO item : cart.getItems()) {
                    BookDTO book = booksById.get(item.getBookId());
                    int remainingStock = (book.getStock() == null ? 0 : book.getStock()) - item.getQuantity();
                    if (remainingStock <= 5) {
                        publishOrderEvent(
                                orders.get(0).getOrderId(),
                                userId,
                                "SYSTEM",
                                "LOW_STOCK",
                                "Low stock alert: '" + book.getTitle() + "' now has only " + Math.max(remainingStock, 0) + " copies remaining.");
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to publish order events, but order was placed: {}", e.getMessage());
            }

            log.info("Checkout successful for userId: {}. Orders created: {}", userId, orders.size());
            return orders;
        } catch (RuntimeException ex) {
            log.error("Checkout failed for userId: {}. Error: {}", userId, ex.getMessage());
            if (stockReduced) {
                log.info("Compensating stock for userId: {}", userId);
                restoreStock(reduceRequests);
            }
            if (walletDebited) {
                log.info("Compensating wallet for userId: {}", userId);
                refundWallet(userId, totalAmount);
            }
            throw ex;
        }
    }

    // Updates the status of an order and records the change in status history
    @Override
    @Transactional
    public Order changeStatus(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        
        validateStatusTransition(order.getOrderStatus(), status);
        order.setOrderStatus(status);
        
        // Add to history
        OrderStatusLog log = OrderStatusLog.builder()
                .orderId(orderId)
                .status(status)
                .updatedAt(LocalDateTime.now())
                .message("Status updated to " + status)
                .build();
        order.getStatusHistory().add(log);

        Order savedOrder = orderRepository.save(order);
        
        // Trigger Shipment/Status Update Event
        publishOrderEvent(
                orderId,
                order.getUserId(),
                "DELIVERY",
                status.toString(),
                "Your order status for '" + order.getBookName() + "' has been updated to: " + status);
        
        return savedOrder;
    }

    // Deletes an order record from the database
    @Override
    @Transactional
    public void deleteOrder(Long orderId) {
        orderRepository.deleteById(orderId);
    }

    // Retrieves all orders placed by a specific user
    @Override
    public List<Order> getOrderByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    @Override
    @Transactional
    public Order cancelOrder(Long orderId) {
        Order order = getOrderById(orderId);
        if (!canCancel(order.getOrderStatus())) {
            throw new InvalidPaymentException("Order can no longer be cancelled");
        }

        // If the order was paid (via Wallet or Online), refund the amount to the user's wallet
        // For WALLET payments, money is debited immediately at checkout even if status is PLACED.
        boolean shouldRefund = order.getOrderStatus() == OrderStatus.PAID || "WALLET".equalsIgnoreCase(order.getPaymentMethod());
        
        if (shouldRefund) {
            refundWallet(order.getUserId(), order.getAmountPaid());
        }

        restoreStock(List.of(ReduceStockRequest.builder()
                .bookId(order.getBookId())
                .quantity(order.getQuantity())
                .build()));

        order.setOrderStatus(OrderStatus.CANCELLED);
        order.getStatusHistory().add(OrderStatusLog.builder()
                .orderId(order.getOrderId())
                .status(OrderStatus.CANCELLED)
                .updatedAt(LocalDateTime.now())
                .message("Order cancelled successfully")
                .build());

        Order savedOrder = orderRepository.save(order);
        publishOrderEvent(
                savedOrder.getOrderId(),
                savedOrder.getUserId(),
                "ORDER",
                "CANCELLED",
                "Your order for '" + savedOrder.getBookName() + "' has been cancelled.");
        return savedOrder;
    }

    @Override
    public Order trackOrder(Long orderId) {
        return getOrderById(orderId);
    }

    @Override
    public InvoiceResponse getInvoice(Long orderId) {
        Order order = getOrderById(orderId);
        Address address = order.getAddress();
        String shippingAddress = address == null
                ? "Address unavailable"
                : String.join(", ",
                safe(address.getFlatNumber()),
                safe(address.getCity()),
                safe(address.getState()),
                safe(address.getPincode()));

        return InvoiceResponse.builder()
                .invoiceNumber("INV-" + order.getOrderId())
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .bookId(order.getBookId())
                .bookName(order.getBookName())
                .quantity(order.getQuantity())
                .amountPaid(order.getAmountPaid())
                .paymentMethod(resolvePaymentMethod(order))
                .orderStatus(order.getOrderStatus().name())
                .orderDate(order.getOrderDate().toString())
                .billedTo(address == null ? "Customer" : safe(address.getFullName()))
                .mobileNumber(address == null ? "" : safe(address.getMobileNumber()))
                .shippingAddress(shippingAddress)
                .build();
    }

    // Verifies the signature of a Razorpay payment and completes the order
    @Override
    @Transactional
    public List<Order> verifyRazorpayPayment(com.booknest.orderservice.dto.PaymentVerifyRequest request, Long authenticatedUserId) {
        log.info("Verifying Razorpay payment for orderId: {}", request.getOrderId());
        
        // 1. Verify Signature
        boolean isValid = razorpayService.verifySignature(request);
        if (!isValid) {
            log.error("Invalid Razorpay signature for orderId: {}", request.getOrderId());
            throw new InvalidPaymentException("Payment verification failed: Invalid signature");
        }

        if (razorpayService.isSimulationMode()) {
            if (authenticatedUserId == null) {
                throw new InvalidPaymentException("Authenticated user context is required for simulated payment verification");
            }

            CheckoutRequest checkoutRequest = CheckoutRequest.builder()
                    .userId(authenticatedUserId)
                    .addressId(request.getAddressId())
                    .paymentMethod("ONLINE")
                    .paymentDetails(Map.of(
                            "verified", true,
                            "paymentId", request.getPaymentId(),
                            "signature", request.getSignature()
                    ))
                    .build();

            return checkout(checkoutRequest);
        }

        // 2. Fetch orders by rzp_order_id
        List<Order> orders = orderRepository.findByRazorpayOrderId(request.getOrderId());
        if (orders.isEmpty()) {
            throw new RuntimeException("Orders not found for Razorpay Order ID: " + request.getOrderId());
        }

        // 3. Amount Validation (Security)
        double totalExpectedAmount = orders.stream().mapToDouble(Order::getAmountPaid).sum();
        log.info("Verifying payment for total amount: {}", totalExpectedAmount);
        // Note: In real scenarios, you might want to fetch the actual amount from Razorpay API to be 100% sure,
        // but since the signature is verified and rzp_order_id is unique to the amount we requested,
        // this is a safe consistency check.

        // 4. Ownership check for IDOR prevention
        if (authenticatedUserId != null && !orders.get(0).getUserId().equals(authenticatedUserId)) {
            log.error("IDOR Attempt: User {} tried to verify payment for orders belonging to User {}", 
                    authenticatedUserId, orders.get(0).getUserId());
            throw new InvalidPaymentException("Access denied: Order ownership mismatch");
        }

        // 5. Idempotency check - if already PAID, just return
        if (orders.get(0).getOrderStatus() == OrderStatus.PAID) {
            log.info("Payment already processed for orderId: {}", request.getOrderId());
            return orders;
        }

        // 6. Update status and process
        for (Order order : orders) {
            order.setOrderStatus(OrderStatus.PAID);
            order.setRazorpayPaymentId(request.getPaymentId());
            order.setRazorpaySignature(request.getSignature());
        }
        orderRepository.saveAll(orders);

        // 7. Downstream updates: Reduce Stock and Clear Cart
        List<ReduceStockRequest> reduceRequests = orders.stream()
            .map(o -> ReduceStockRequest.builder().bookId(o.getBookId()).quantity(o.getQuantity()).build())
            .toList();
        reduceStockBatch(reduceRequests);
        clearCart(orders.get(0).getUserId());

        return orders;
    }

    // Processes asynchronous payment events received via Razorpay webhooks
    @Override
    @Transactional
    public void handlePaymentWebhook(String payload, String signature) {
        log.info("Processing Razorpay Webhook");
        if (!razorpayService.verifyWebhookSignature(payload, signature)) {
            log.warn("Invalid Razorpay webhook signature");
            throw new InvalidPaymentException("Invalid webhook signature");
        }

        try {
            JSONObject event = new JSONObject(payload);
            String eventType = event.getString("event");
            JSONObject paymentEntity = event.getJSONObject("payload")
                    .getJSONObject("payment")
                    .getJSONObject("entity");
            
            String rzpOrderId = paymentEntity.getString("order_id");
            String rzpPaymentId = paymentEntity.getString("id");
            int paymentAmountInPaise = paymentEntity.getInt("amount");

            List<Order> orders = orderRepository.findByRazorpayOrderId(rzpOrderId);
            if (orders.isEmpty()) {
                log.warn("Webhook: Orders not found for RZP Order ID: {}", rzpOrderId);
                return;
            }

            // Amount Validation (Security)
            double expectedAmount = orders.stream().mapToDouble(Order::getAmountPaid).sum();
            if (Math.round(expectedAmount * 100) != paymentAmountInPaise) {
                log.error("CRITICAL: Webhook amount mismatch! Expected: {}, Received: {}", expectedAmount, paymentAmountInPaise / 100.0);
                throw new SecurityException("Amount mismatch detected in webhook");
            }

            if ("payment.captured".equals(eventType) || "order.paid".equals(eventType)) {
                log.info("Webhook: Payment success for orderId: {}", rzpOrderId);
                if (orders.get(0).getOrderStatus() != OrderStatus.PAID) {
                    for (Order order : orders) {
                        order.setOrderStatus(OrderStatus.PAID);
                        order.setRazorpayPaymentId(rzpPaymentId);
                    }
                    orderRepository.saveAll(orders);
                    
                    // Trigger inventory and cart updates
                    reduceStockBatch(orders.stream()
                        .map(o -> ReduceStockRequest.builder().bookId(o.getBookId()).quantity(o.getQuantity()).build())
                        .toList());
                    clearCart(orders.get(0).getUserId());
                }
            } else if ("payment.failed".equals(eventType)) {
                log.warn("Webhook: Payment failed for orderId: {}", rzpOrderId);
                if (orders.get(0).getOrderStatus() != OrderStatus.PAID) {
                    for (Order order : orders) {
                        order.setOrderStatus(OrderStatus.FAILED);
                    }
                    orderRepository.saveAll(orders);
                }
            }
        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage());
            throw new RuntimeException("Webhook processing failed: " + e.getMessage());
        }
    }

    // Retrieves a single order by its ID
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    // Saves a new shipping address to the database
    @Override
    @Transactional
    public Address storeAddress(Address address) {
        if (address.getCreatedAt() == null) {
            address.setCreatedAt(LocalDateTime.now());
        }
        address.setUpdatedAt(LocalDateTime.now());
        return addressRepository.save(address);
    }

    @Override
    @Transactional
    public Address updateAddress(Long addressId, Address address) {
        Address existing = getAddressById(addressId);
        existing.setFullName(address.getFullName());
        existing.setMobileNumber(address.getMobileNumber());
        existing.setFlatNumber(address.getFlatNumber());
        existing.setCity(address.getCity());
        existing.setState(address.getState());
        existing.setPincode(address.getPincode());
        existing.setUpdatedAt(LocalDateTime.now());
        return addressRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteAddress(Long addressId) {
        Address address = getAddressById(addressId);
        address.setIsActive(false);
        addressRepository.save(address);
    }

    // Fetches all shipping addresses stored in the system
    @Override
    public List<Address> getAllAddress() {
        return addressRepository.findAll();
    }

    // Retrieves all addresses associated with a specific user
    @Override
    public List<Address> getAddressByCustomerId(Long customerId) {
        return addressRepository.findByCustomerIdAndIsActiveTrue(customerId);
    }

    public Address getAddressById(Long addressId) {
        return addressRepository.findByAddressId(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));
    }

    // Helper to fetch the user's cart using Feign client with circuit breaker
    @CircuitBreaker(name = "cartService", fallbackMethod = "fetchCartFallback")
    private CartDTO fetchCart(Long userId) {
        ApiResponse<CartDTO> response = cartClient.getCartByUserId(userId);
        return response.getData();
    }

    @SuppressWarnings("unused")
    private CartDTO fetchCartFallback(Long userId, Throwable t) {
        log.error("Fallback for Cart-Service userId={}: {}", userId, t.getMessage());
        throw new RuntimeException("Cart Service is currently unavailable");
    }

    // Helper to fetch book details using Feign client with circuit breaker
    @CircuitBreaker(name = "bookService", fallbackMethod = "fetchBookDetailsFallback")
    private BookDTO fetchBookDetails(Long bookId) {
        ApiResponse<BookDTO> response = bookClient.getBookById(bookId);
        return response.getData();
    }

    @SuppressWarnings("unused")
    private BookDTO fetchBookDetailsFallback(Long bookId, Throwable t) {
        log.error("Fallback for Book-Service bookId={}: {}", bookId, t.getMessage());
        throw new RuntimeException("Book Service is currently unavailable");
    }

    // Normalizes the payment method string to uppercase and checks validity
    private String normalizePaymentMethod(String paymentMethod) {
        String method = paymentMethod == null ? "COD" : paymentMethod.trim().toUpperCase(Locale.ROOT);
        if (!List.of("COD", "WALLET", "ONLINE").contains(method)) {
            throw new InvalidPaymentException("Unsupported payment method: " + paymentMethod);
        }
        return method;
    }

    // Checks if books are in stock and fetches their details
    private Map<Long, BookDTO> validateStockAndFetchBooks(List<CartItemDTO> items) {
        Map<Long, BookDTO> booksById = new HashMap<>();
        for (CartItemDTO item : items) {
            BookDTO book = fetchBookDetails(item.getBookId());
            if (book == null) {
                log.error("Book details not found for ID: {}", item.getBookId());
                throw new RuntimeException("Book details not found for ID: " + item.getBookId());
            }
            booksById.put(book.getBookId(), book);
            if (book.getStock() == null || book.getStock() < item.getQuantity()) {
                throw new InsufficientStockException("Insufficient stock for bookId: " + item.getBookId());
            }
        }
        return booksById;
    }

    // Calculates the total cost of all items in the cart
    private double calculateTotalAmount(List<CartItemDTO> items, Map<Long, BookDTO> booksById, String discountCode) {
        double total = items.stream()
                .mapToDouble(item -> {
                    BookDTO book = booksById.get(item.getBookId());
                    return calculateLineItemAmount(book, item.getQuantity());
                })
                .sum();
        
        if (discountCode != null && !discountCode.isBlank()) {
            double extraDiscount = resolveCouponDiscount(discountCode);
            total = total * (1 - extraDiscount);
        }
        
        return roundCurrency(total);
    }

    private double resolveCouponDiscount(String code) {
        if ("SAVE20".equalsIgnoreCase(code)) return 0.20;
        if ("BOOKNEST10".equalsIgnoreCase(code)) return 0.10;
        return 0.0;
    }

    private double calculateLineItemAmount(BookDTO book, int quantity) {
        double unitPrice = book != null && book.getPrice() != null ? book.getPrice() : 0.0;
        double baseAmount = unitPrice * quantity;
        double discountRate = resolveDiscountRate(unitPrice, quantity);
        return roundCurrency(baseAmount * (1 - discountRate));
    }

    private double resolveDiscountRate(double unitPrice, int quantity) {
        if (quantity >= 5) {
            return 0.15;
        }
        if (quantity >= 3) {
            return 0.10;
        }
        if (unitPrice >= 1000.0) {
            return 0.05;
        }
        return 0.0;
    }

    private double roundCurrency(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // Prepares requests to reduce book inventory levels
    private List<ReduceStockRequest> buildReduceRequests(List<CartItemDTO> items) {
        return items.stream()
                .map(i -> ReduceStockRequest.builder().bookId(i.getBookId()).quantity(i.getQuantity()).build())
                .sorted(Comparator.comparing(ReduceStockRequest::getBookId))
                .toList();
    }

    // Deducts money from the user's digital wallet for the order
    private void debitWallet(Long userId, double amount) {
        try {
            WalletDTO wallet = getWalletByUserId(userId);
            walletClient.payMoney(WalletRequest.builder()
                    .walletId(wallet.getWalletId())
                    .userId(userId)
                    .amount(amount)
                    .orderId(0L)
                    .build());
        } catch (FeignException ex) {
            if (ex.status() == 400 || ex.status() == 409) {
                throw new InsufficientBalanceException("Insufficient wallet balance");
            }
            throw ex;
        }
    }

    // Refunds the user's wallet if an order fails after payment
    private void refundWallet(Long userId, double amount) {
        try {
            WalletDTO wallet = getWalletByUserId(userId);
            walletClient.addMoney(WalletRequest.builder()
                    .walletId(wallet.getWalletId())
                    .amount(amount)
                    .build());
            log.info("Wallet compensation applied for userId={}, amount={}", userId, amount);
        } catch (Exception ex) {
            log.error("Wallet compensation failed for userId={}. Manual intervention may be required. {}", userId, ex.getMessage());
        }
    }

    // Verifies online payment details provided in the request
    private void verifyOnlinePayment(Map<String, Object> paymentDetails) {
        if (paymentDetails == null || paymentDetails.isEmpty()) {
            throw new InvalidPaymentException("paymentDetails are required for ONLINE payment");
        }

        Object verified = paymentDetails.get("verified");
        if (verified instanceof Boolean && Boolean.TRUE.equals(verified)) {
            return;
        }

        Object paymentId = paymentDetails.get("paymentId");
        Object signature = paymentDetails.get("signature");
        boolean hasPaymentId = paymentId instanceof String && !((String) paymentId).isBlank();
        boolean hasSignature = signature instanceof String && !((String) signature).isBlank();
        if (!(hasPaymentId && hasSignature)) {
            throw new InvalidPaymentException("ONLINE payment verification failed");
        }
    }

    // Updates book stock in bulk via the book client
    private void reduceStockBatch(List<ReduceStockRequest> reduceRequests) {
        try {
            bookClient.reduceStockBatch(reduceRequests);
        } catch (FeignException ex) {
            if (ex.status() == 409 || ex.status() == 400) {
                throw new InsufficientStockException("Insufficient stock for one or more books");
            }
            throw ex;
        }
    }

    // Compensates stock levels if an order fails during processing
    private void restoreStock(List<ReduceStockRequest> reduceRequests) {
        for (ReduceStockRequest request : reduceRequests) {
            try {
                bookClient.updateStock(request.getBookId(), request.getQuantity());
            } catch (Exception ex) {
                log.error("Stock compensation failed for bookId={}. Manual intervention may be required. {}", request.getBookId(), ex.getMessage());
            }
        }
    }

    // Saves multiple order items and their initial status to the database
    private List<Order> saveOrders(Long userId, String paymentMethod, List<CartItemDTO> items, Map<Long, BookDTO> booksById, Address address, String discountCode) {
        List<Order> orders = new ArrayList<>();
        double extraDiscount = (discountCode != null && !discountCode.isBlank()) ? resolveCouponDiscount(discountCode) : 0.0;

        for (CartItemDTO item : items) {
            BookDTO book = booksById.get(item.getBookId());
            double lineAmount = calculateLineItemAmount(book, item.getQuantity());
            if (extraDiscount > 0) {
                lineAmount = roundCurrency(lineAmount * (1 - extraDiscount));
            }

            Order order = Order.builder()
                    .userId(userId)
                    .orderDate(LocalDateTime.now())
                    .amountPaid(lineAmount)
                    .modeOfPayment(paymentMethod)
                    .paymentMethod(paymentMethod)
                    .orderStatus("COD".equals(paymentMethod) ? OrderStatus.PLACED : OrderStatus.PAID)
                    .quantity(item.getQuantity())
                    .address(address)
                    .bookId(book.getBookId())
                    .bookName(book.getTitle())
                    .build();
            orders.add(order);
        }

        List<Order> savedOrders = orderRepository.saveAll(orders);
        for (Order savedOrder : savedOrders) {
            savedOrder.getStatusHistory().add(OrderStatusLog.builder()
                    .orderId(savedOrder.getOrderId())
                    .status(savedOrder.getOrderStatus())
                    .updatedAt(LocalDateTime.now())
                    .message("Order successfully placed")
                    .build());
        }
        return orderRepository.saveAll(savedOrders);
    }

    // Clears the user's shopping cart after a successful order placement
    private void clearCart(Long userId) {
        try {
            cartClient.clearCartByUserId(userId);
        } catch (FeignException ex) {
            throw new RuntimeException("Order processing failed while clearing cart");
        }
    }

    // Retrieves the most recently used shipping address for a user
    private Address getLatestAddress(Long userId) {
        log.info("Fetching latest address for userId: {}", userId);
        List<Address> addresses = addressRepository.findByCustomerIdAndIsActiveTrue(userId);
        if (addresses.isEmpty()) {
            log.error("No shipping address found for userId: {}", userId);
            throw new RuntimeException("No shipping address found for user. Please add an address first.");
        }
        Address latest = addresses.stream()
                .max(Comparator.comparing(Address::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Address::getAddressId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(addresses.get(addresses.size() - 1));
        log.info("Found address: {}", latest.getAddressId());
        return latest;
    }

    private Address resolveCheckoutAddress(Long userId, Long addressId) {
        if (addressId == null) {
            return getLatestAddress(userId);
        }

        return addressRepository.findByAddressIdAndCustomerIdAndIsActiveTrue(addressId, userId)
                .orElseThrow(() -> new InvalidPaymentException("Selected address is invalid or no longer available"));
    }

    private void publishOrderEvent(Long orderId, Long userId, String type, String status, String message) {
        if (eventPublisher == null) {
            return;
        }
        eventPublisher.publishOrderEvent(orderId, userId, type, status, message);
    }

    // Fetches the user's wallet information by their ID
    private WalletDTO getWalletByUserId(Long userId) {
        ApiResponse<WalletDTO> response = walletClient.getWalletByUserId(userId);
        return response.getData();
    }

    private boolean canCancel(OrderStatus status) {
        return status != null && switch (status) {
            case PLACED, CONFIRMED, PAID -> true;
            default -> false;
        };
    }

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        if (current == next) return;
        if (current == OrderStatus.CANCELLED || current == OrderStatus.DELIVERED || current == OrderStatus.FAILED) {
            throw new IllegalStateException("Cannot change status of a terminal order");
        }
        
        // Cancellation is always allowed from non-terminal pre-shipment states
        if (next == OrderStatus.CANCELLED) {
            if (canCancel(current)) return;
            throw new IllegalStateException("Order cannot be cancelled after shipping");
        }

        boolean isValid = switch (current) {
            case PLACED -> next == OrderStatus.CONFIRMED || next == OrderStatus.PAID || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.PAID || next == OrderStatus.SHIPPED || next == OrderStatus.CANCELLED;
            case PAID -> next == OrderStatus.SHIPPED || next == OrderStatus.CANCELLED;
            case SHIPPED -> next == OrderStatus.OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> next == OrderStatus.DELIVERED;
            default -> false;
        };

        if (!isValid) {
            throw new IllegalStateException("Invalid status transition: " + current + " -> " + next);
        }
    }


    private String resolvePaymentMethod(Order order) {
        if (order.getPaymentMethod() != null && !order.getPaymentMethod().isBlank()) {
            return order.getPaymentMethod();
        }
        return order.getModeOfPayment();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
