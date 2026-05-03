package com.booknest.orderservice.controller;

import com.booknest.orderservice.dto.CheckoutRequest;
import com.booknest.orderservice.dto.InvoiceResponse;
import com.booknest.orderservice.entity.Address;
import com.booknest.orderservice.entity.Order;
import com.booknest.orderservice.enums.OrderStatus;
import com.booknest.orderservice.payload.ApiResponse;
import com.booknest.orderservice.service.OrderService;
import com.booknest.orderservice.service.PdfInvoiceService;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for managing orders and addresses.
 * Handles order placement, payment processing (Razorpay),
 * order tracking, invoice generation, and address management.
 * Includes admin endpoints for order management and security
 * checks for user resource access.
 */
@RestController
@RequestMapping("/api/v1/orders")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class OrderResource {

    private final OrderService orderService;
    private final com.booknest.orderservice.service.RazorpayService razorpayService;
    private final PdfInvoiceService pdfInvoiceService;

    // Creates a Razorpay payment order for online transactions
    @PostMapping("/payment/create")
    public ResponseEntity<ApiResponse<String>> createRazorpayOrder(@RequestBody Map<String, Double> payload) {
        try {
            Double amount = payload.get("amount");
            String orderId = razorpayService.createOrder(amount);
            return ResponseEntity.ok(new ApiResponse<>(true, "Razorpay order created", orderId));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new ApiResponse<>(false, "Failed to create Razorpay order: " + e.getMessage(), null));
        }
    }

    // Verifies Razorpay payment signature after completion
    @PostMapping("/payment/verify")
    public ResponseEntity<ApiResponse<Boolean>> verifyRazorpayPayment(
            @RequestBody com.booknest.orderservice.dto.PaymentVerifyRequest request) {
        boolean isValid = razorpayService.verifySignature(request);
        return ResponseEntity
                .ok(new ApiResponse<>(isValid, isValid ? "Payment verified" : "Invalid signature", isValid));
    }

    /** ADMIN-only: retrieves all orders across all users. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Order>>> getAllOrders() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Orders fetched successfully", orderService.getAllOrders()));
    }

    // Retrieves all orders for a specific user
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<Order>>> getOrderByUserId(@PathVariable Long userId,
            HttpServletRequest request) {
        enforceUserAccess(userId, request);
        return ResponseEntity
                .ok(new ApiResponse<>(true, "User orders fetched successfully", orderService.getOrderByUserId(userId)));
    }

    // Retrieves a specific order by ID
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<Order>> getOrderById(@PathVariable Long orderId, HttpServletRequest request) {
        Order order = orderService.getOrderById(orderId);
        if (order != null) {
            enforceUserAccess(order.getUserId(), request);
        }
        return ResponseEntity.ok(new ApiResponse<>(true, "Order fetched successfully", order));
    }

    // Tracks order status and updates
    @GetMapping("/{orderId}/track")
    public ResponseEntity<ApiResponse<Order>> trackOrder(@PathVariable Long orderId, HttpServletRequest request) {
        Order order = orderService.trackOrder(orderId);
        enforceUserAccess(order.getUserId(), request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Order tracking fetched successfully", order));
    }

    // Cancels an existing order
    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Order>> cancelOrder(@PathVariable Long orderId, HttpServletRequest request) {
        Order order = orderService.getOrderById(orderId);
        enforceUserAccess(order.getUserId(), request);
        return ResponseEntity
                .ok(new ApiResponse<>(true, "Order cancelled successfully", orderService.cancelOrder(orderId)));
    }

    @GetMapping("/{orderId}/invoice")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoice(@PathVariable Long orderId,
            HttpServletRequest request) {
        Order order = orderService.getOrderById(orderId);
        enforceUserAccess(order.getUserId(), request);
        return ResponseEntity
                .ok(new ApiResponse<>(true, "Invoice fetched successfully", orderService.getInvoice(orderId)));
    }

    /**
     * Downloads a professional PDF invoice for the specified order.
     * <p>The PDF is streamed as an attachment so the browser triggers a file save dialog.</p>
     *
     * @param orderId the order to generate the invoice for
     * @param request the servlet request (used for ownership enforcement)
     * @return raw PDF bytes with {@code Content-Disposition: attachment; filename="INV-{orderId}.pdf"}
     */
    @GetMapping("/{orderId}/invoice/pdf")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable Long orderId, HttpServletRequest request) {
        Order order = orderService.getOrderById(orderId);
        enforceUserAccess(order.getUserId(), request);
        InvoiceResponse invoice = orderService.getInvoice(orderId);
        byte[] pdfBytes = pdfInvoiceService.generateInvoicePdf(invoice);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", invoice.getInvoiceNumber() + ".pdf");
        headers.setContentLength(pdfBytes.length);
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    @PostMapping("/place")
    @Deprecated(forRemoval = false)
    public ResponseEntity<ApiResponse<List<Order>>> placeOrder(@RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        Long userId = payload.get("userId") == null ? null : Long.valueOf(payload.get("userId").toString());
        enforceUserAccess(userId, request);
        String paymentMethod = payload.get("paymentMethod") == null ? "COD" : payload.get("paymentMethod").toString();
        CheckoutRequest checkoutRequest = CheckoutRequest.builder()
                .userId(userId)
                .paymentMethod(paymentMethod)
                .build();
        return ResponseEntity
                .ok(new ApiResponse<>(true, "Order placed successfully", orderService.checkout(checkoutRequest)));
    }

    @PostMapping("/online")
    @Deprecated(forRemoval = false)
    public ResponseEntity<ApiResponse<List<Order>>> onlinePayment(@RequestBody Map<String, Long> payload,
            HttpServletRequest request) {
        enforceUserAccess(payload.get("userId"), request);
        CheckoutRequest checkoutRequest = CheckoutRequest.builder()
                .userId(payload.get("userId"))
                .paymentMethod("ONLINE")
                .build();
        return ResponseEntity
                .ok(new ApiResponse<>(true, "Order placed successfully", orderService.checkout(checkoutRequest)));
    }

    // Main checkout endpoint: processes cart items into orders
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<List<Order>>> checkout(@Valid @RequestBody CheckoutRequest request,
            HttpServletRequest httpRequest) {
        enforceUserAccess(request.getUserId(), httpRequest);
        return ResponseEntity.ok(new ApiResponse<>(true, "Order placed successfully", orderService.checkout(request)));
    }

    // Admin endpoint: updates order status (processing, shipped, etc.)
    @PutMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Order>> changeStatus(@RequestBody Map<String, Object> payload) {
        Long orderId = Long.valueOf(payload.get("orderId").toString());
        OrderStatus status = OrderStatus.valueOf(payload.get("status").toString());
        return ResponseEntity.ok(new ApiResponse<>(true, "Order status updated successfully",
                orderService.changeStatus(orderId, status)));
    }

    // Admin endpoint: deletes an order
    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(@PathVariable Long orderId) {
        orderService.deleteOrder(orderId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Order deleted successfully", null));
    }

    // Saves a new delivery address for the user
    @PostMapping("/address")
    public ResponseEntity<ApiResponse<Address>> storeAddress(@Valid @RequestBody com.booknest.orderservice.dto.AddressRequest addressRequest, HttpServletRequest request) {
        enforceUserAccess(addressRequest.getCustomerId(), request);
        Address address = Address.builder()
                .customerId(addressRequest.getCustomerId())
                .fullName(addressRequest.getFullName())
                .mobileNumber(addressRequest.getMobileNumber())
                .flatNumber(addressRequest.getFlatNumber())
                .city(addressRequest.getCity())
                .state(addressRequest.getState())
                .pincode(addressRequest.getPincode())
                .build();
        return ResponseEntity
                .ok(new ApiResponse<>(true, "Address saved successfully", orderService.storeAddress(address)));
    }

    @GetMapping("/address")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Address>>> getAllAddress() {
        return ResponseEntity
                .ok(new ApiResponse<>(true, "Addresses fetched successfully", orderService.getAllAddress()));
    }

    @GetMapping("/address/{customerId}")
    public ResponseEntity<ApiResponse<List<Address>>> getAddressByCustomerId(@PathVariable Long customerId,
            HttpServletRequest request) {
        enforceUserAccess(customerId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Addresses fetched successfully",
                orderService.getAddressByCustomerId(customerId)));
    }

    @PutMapping("/address/{addressId}")
    public ResponseEntity<ApiResponse<Address>> updateAddress(
            @PathVariable Long addressId,
            @Valid @RequestBody com.booknest.orderservice.dto.AddressRequest addressRequest,
            HttpServletRequest request) {
        Address existing = orderService.getAddressById(addressId);
        enforceUserAccess(existing.getCustomerId(), request);
        Address address = Address.builder()
                .customerId(existing.getCustomerId())
                .fullName(addressRequest.getFullName())
                .mobileNumber(addressRequest.getMobileNumber())
                .flatNumber(addressRequest.getFlatNumber())
                .city(addressRequest.getCity())
                .state(addressRequest.getState())
                .pincode(addressRequest.getPincode())
                .build();
        return ResponseEntity.ok(new ApiResponse<>(true, "Address updated successfully",
                orderService.updateAddress(addressId, address)));
    }

    @DeleteMapping("/address/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(@PathVariable Long addressId, HttpServletRequest request) {
        Address existing = orderService.getAddressById(addressId);
        enforceUserAccess(existing.getCustomerId(), request);
        orderService.deleteAddress(addressId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Address deleted successfully", null));
    }

    // Security method: ensures users can only access their own resources (admins bypass)
    private void enforceUserAccess(Long targetUserId, HttpServletRequest request) {
        if (targetUserId == null) {
            return;
        }

        String roleHeader = resolveRole(request);
        if (isAdmin(roleHeader)) {
            return;
        }

        Long authenticatedUserId = resolveUserId(request);
        if (authenticatedUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user context is required");
        }

        if (!authenticatedUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied for requested user resource");
        }
    }

    private String resolveRole(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object attribute = request.getAttribute("authenticatedRole");
        if (attribute instanceof String role && !role.isBlank()) {
            return role;
        }
        return request.getHeader("X-Auth-Role");
    }

    private Long resolveUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object attribute = request.getAttribute("authenticatedUserId");
        if (attribute instanceof Long userId) {
            return userId;
        }
        if (attribute instanceof Integer userId) {
            return userId.longValue();
        }

        String userIdHeader = request.getHeader("X-Auth-UserId");
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(userIdHeader);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user context");
        }
    }

    private boolean isAdmin(String roleHeader) {
        if (roleHeader == null || roleHeader.isBlank()) {
            return false;
        }
        String normalized = roleHeader.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        return "ADMIN".equals(normalized);
    }
}
