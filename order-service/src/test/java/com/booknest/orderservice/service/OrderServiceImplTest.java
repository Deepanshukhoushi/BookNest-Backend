package com.booknest.orderservice.service;

import com.booknest.orderservice.client.BookClient;
import com.booknest.orderservice.client.CartClient;
import com.booknest.orderservice.client.WalletClient;
import com.booknest.orderservice.dto.*;
import com.booknest.orderservice.entity.Address;
import com.booknest.orderservice.entity.Order;
import com.booknest.orderservice.enums.OrderStatus;
import com.booknest.orderservice.payload.ApiResponse;
import com.booknest.orderservice.repository.AddressRepository;
import com.booknest.orderservice.repository.OrderRepository;
import com.booknest.orderservice.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private BookClient bookClient;
    @Mock
    private CartClient cartClient;
    @Mock
    private WalletClient walletClient;
    @Mock
    private RazorpayService razorpayService;
    @Mock
    private com.booknest.orderservice.event.OrderEventPublisher eventPublisher;

    @InjectMocks
    private OrderServiceImpl orderService;

    private final Long userId = 1L;
    private final Long bookId = 101L;
    private Address testAddress;
    private BookDTO testBook;
    private CartDTO testCart;

    @BeforeEach
    void setUp() {
        testAddress = Address.builder()
                .addressId(1L)
                .customerId(userId)
                .fullName("John Doe")
                .city("New York")
                .updatedAt(LocalDateTime.now())
                .build();

        testBook = BookDTO.builder()
                .bookId(bookId)
                .title("Test Book")
                .price(100.0)
                .stock(10)
                .build();

        CartItemDTO item = CartItemDTO.builder().bookId(bookId).quantity(1).build();
        testCart = CartDTO.builder().items(Collections.singletonList(item)).build();
    }

    @Test
    void testCheckout_COD_Success() {
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", testCart));
        when(addressRepository.findByAddressIdAndCustomerIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(testAddress));
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(orderRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<Order> result = orderService.checkout(CheckoutRequest.builder()
                .userId(userId)
                .paymentMethod("COD")
                .addressId(1L)
                .build());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrderStatus()).isEqualTo(OrderStatus.PLACED);
        verify(bookClient).reduceStockBatch(anyList());
        verify(cartClient).clearCartByUserId(userId);
    }

    @Test
    void testCheckout_Wallet_Success() {
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", testCart));
        when(addressRepository.findByAddressIdAndCustomerIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(testAddress));
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(walletClient.getWalletByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", WalletDTO.builder().walletId(1L).build()));
        when(orderRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<Order> result = orderService.checkout(CheckoutRequest.builder()
                .userId(userId)
                .paymentMethod("WALLET")
                .addressId(1L)
                .build());

        assertThat(result.get(0).getOrderStatus()).isEqualTo(OrderStatus.PAID);
        verify(walletClient).payMoney(any(WalletRequest.class));
    }

    @Test
    void testCheckout_WithDiscount() {
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", testCart));
        when(addressRepository.findByAddressIdAndCustomerIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(testAddress));
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(orderRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<Order> result = orderService.checkout(CheckoutRequest.builder()
                .userId(userId)
                .paymentMethod("COD")
                .addressId(1L)
                .discountCode("SAVE20")
                .build());

        // Base price 100, 20% discount = 80
        assertThat(result.get(0).getAmountPaid()).isEqualTo(80.0);
    }

    @Test
    void testCancelOrder_Success() {
        Order order = Order.builder()
                .orderId(1L)
                .userId(userId)
                .bookId(bookId)
                .quantity(1)
                .amountPaid(100.0)
                .orderStatus(OrderStatus.PLACED)
                .paymentMethod("WALLET")
                .build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(walletClient.getWalletByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", WalletDTO.builder().walletId(1L).build()));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.cancelOrder(1L);

        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(walletClient).addMoney(any(WalletRequest.class));
        verify(bookClient).updateStock(eq(bookId), eq(1));
    }

    @Test
    void testInitiateRazorpayPayment() throws Exception {
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", testCart));
        when(addressRepository.findByCustomerIdAndIsActiveTrue(userId)).thenReturn(Collections.singletonList(testAddress));
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(razorpayService.createOrder(anyDouble())).thenReturn("rzp_order_123");

        String result = orderService.initiateRazorpayPayment(userId, null);

        assertThat(result).isEqualTo("rzp_order_123");
        verify(orderRepository).saveAll(anyList());
    }

    @Test
    void testVerifyRazorpayPayment_Success() throws Exception {
        Order order = Order.builder().userId(userId).bookId(bookId).quantity(1).amountPaid(100.0).build();
        when(razorpayService.verifySignature(any())).thenReturn(true);
        when(orderRepository.findByRazorpayOrderId("rzp_order_123")).thenReturn(Arrays.asList(order));

        PaymentVerifyRequest request = new PaymentVerifyRequest("rzp_order_123", "pay_123", "sig_123", null);
        List<Order> result = orderService.verifyRazorpayPayment(request, userId);

        assertThat(result.get(0).getOrderStatus()).isEqualTo(OrderStatus.PAID);
        verify(bookClient).reduceStockBatch(anyList());
    }

    @Test
    void testGetInvoice() throws Exception {
        Order order = Order.builder()
                .orderId(1L)
                .userId(userId)
                .bookId(bookId)
                .bookName("Test Book")
                .quantity(1)
                .amountPaid(100.0)
                .orderDate(LocalDateTime.now())
                .orderStatus(OrderStatus.PAID)
                .address(testAddress)
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        InvoiceResponse result = orderService.getInvoice(1L);

        assertThat(result.getInvoiceNumber()).isEqualTo("INV-1");
        assertThat(result.getBilledTo()).isEqualTo(testAddress.getFullName());
    }

    @Test
    void testGetOrdersByUserId() {
        Order order = Order.builder().userId(userId).build();
        when(orderRepository.findByUserId(userId)).thenReturn(Arrays.asList(order));

        List<Order> result = orderService.getOrderByUserId(userId);

        assertThat(result).hasSize(1);
    }

    @Test
    void testUpdateOrderStatus_Success() {
        Order order = Order.builder().orderId(1L).orderStatus(OrderStatus.PLACED).statusHistory(new ArrayList<>()).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.changeStatus(1L, OrderStatus.DELIVERED);

        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void testUpdateOrderStatus_NotFound() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.changeStatus(1L, OrderStatus.DELIVERED))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testCheckout_CartNotFound() {
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(false, "error", null));

        assertThatThrownBy(() -> orderService.checkout(CheckoutRequest.builder().userId(userId).build()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testCompensation_StockAndWallet() {
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", testCart));
        when(addressRepository.findByAddressIdAndCustomerIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(testAddress));
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(walletClient.getWalletByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", WalletDTO.builder().walletId(1L).build()));
        
        // Mock successful intermediate steps
        when(walletClient.payMoney(any())).thenReturn(new ApiResponse<>(true, "ok", null));
        when(bookClient.reduceStockBatch(any())).thenReturn(new ApiResponse<>(true, "ok", null));
        
        // Fail during order saving
        doThrow(new RuntimeException("DB Error")).when(orderRepository).saveAll(anyList());

        assertThatThrownBy(() -> orderService.checkout(CheckoutRequest.builder()
                .userId(userId)
                .paymentMethod("WALLET")
                .addressId(1L)
                .build()))
                .isInstanceOf(RuntimeException.class);

        // Verify compensation
        verify(walletClient).addMoney(any()); // Refund
        verify(bookClient).updateStock(eq(bookId), anyInt()); // Restore stock
    }

    @Test
    void testGetAllOrders() {
        when(orderRepository.findAll()).thenReturn(Collections.emptyList());
        List<Order> result = orderService.getAllOrders();
        assertThat(result).isEmpty();
    }

    @Test
    void testDeleteOrder() {
        doNothing().when(orderRepository).deleteById(1L);
        orderService.deleteOrder(1L);
        verify(orderRepository).deleteById(1L);
    }

    @Test
    void testTrackOrder() {
        Order order = Order.builder().orderId(1L).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        Order result = orderService.trackOrder(1L);
        assertThat(result.getOrderId()).isEqualTo(1L);
    }

    @Test
    void testGetOrderById() {
        Order order = Order.builder().orderId(1L).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        Order result = orderService.getOrderById(1L);
        assertThat(result.getOrderId()).isEqualTo(1L);
    }

    @Test
    void testStoreAddress() {
        when(addressRepository.save(any(Address.class))).thenReturn(testAddress);
        Address stored = orderService.storeAddress(testAddress);
        assertThat(stored.getFullName()).isEqualTo("John Doe");
        verify(addressRepository).save(any(Address.class));
    }

    @Test
    void testGetAddressById() {
        when(addressRepository.findByAddressId(1L)).thenReturn(Optional.of(testAddress));
        Address found = orderService.getAddressById(1L);
        assertThat(found.getAddressId()).isEqualTo(1L);
    }

    @Test
    void testUpdateAddress() {
        when(addressRepository.findByAddressId(1L)).thenReturn(Optional.of(testAddress));
        when(addressRepository.save(any(Address.class))).thenAnswer(i -> i.getArgument(0));
        Address update = Address.builder().fullName("Jane Doe").build();
        Address updated = orderService.updateAddress(1L, update);
        assertThat(updated.getFullName()).isEqualTo("Jane Doe");
        verify(addressRepository).save(any(Address.class));
    }

    @Test
    void testGetAllAddress() {
        when(addressRepository.findAll()).thenReturn(Arrays.asList(testAddress));
        assertThat(orderService.getAllAddress()).hasSize(1);
    }

    @Test
    void testGetAddressByCustomerId() {
        when(addressRepository.findByCustomerIdAndIsActiveTrue(userId)).thenReturn(Arrays.asList(testAddress));
        assertThat(orderService.getAddressByCustomerId(userId)).hasSize(1);
    }

    @Test
    void testDeleteAddress() {
        when(addressRepository.findByAddressId(1L)).thenReturn(Optional.of(testAddress));
        orderService.deleteAddress(1L);
        verify(addressRepository).save(argThat(a -> a.getIsActive() != null && !a.getIsActive()));
    }

    @Test
    void testHandlePaymentWebhook_Success() {
        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"rzp_order_1\",\"id\":\"pay_1\",\"amount\":10000}}}}";
        String signature = "valid_sig";
        Order order = Order.builder().amountPaid(100.0).orderStatus(OrderStatus.PLACED).build();
        
        when(razorpayService.verifyWebhookSignature(payload, signature)).thenReturn(true);
        when(orderRepository.findByRazorpayOrderId("rzp_order_1")).thenReturn(Arrays.asList(order));
        
        orderService.handlePaymentWebhook(payload, signature);
        
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).saveAll(anyList());
    }

    @Test
    void testCheckout_InsufficientStock() {
        testBook.setStock(0); // No stock available
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", testCart));
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(addressRepository.findByAddressIdAndCustomerIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(testAddress));

        assertThatThrownBy(() -> orderService.checkout(CheckoutRequest.builder()
                .userId(userId)
                .paymentMethod("COD")
                .addressId(1L)
                .build()))
                .isInstanceOf(com.booknest.orderservice.exception.InsufficientStockException.class);
    }

    @Test
    void testCheckout_InsufficientBalance() {
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", testCart));
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(addressRepository.findByAddressIdAndCustomerIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(testAddress));
        when(walletClient.getWalletByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", WalletDTO.builder().walletId(1L).build()));
        
        // Mock 400 Bad Request from wallet-service (insufficient funds)
        feign.Request request = feign.Request.create(feign.Request.HttpMethod.POST, "/pay", new HashMap<>(), null, new feign.RequestTemplate());
        when(walletClient.payMoney(any())).thenThrow(new feign.FeignException.BadRequest("Insufficient balance", request, null, null));

        assertThatThrownBy(() -> orderService.checkout(CheckoutRequest.builder()
                .userId(userId)
                .paymentMethod("WALLET")
                .addressId(1L)
                .build()))
                .isInstanceOf(com.booknest.orderservice.exception.InsufficientBalanceException.class);
    }

    @Test
    void testVerifyRazorpayPayment_OwnershipMismatch() {
        Order order = Order.builder().userId(999L).bookId(bookId).amountPaid(100.0).build(); // Belongs to different user
        when(razorpayService.verifySignature(any())).thenReturn(true);
        when(orderRepository.findByRazorpayOrderId("rzp_order_123")).thenReturn(Arrays.asList(order));

        PaymentVerifyRequest request = new PaymentVerifyRequest("rzp_order_123", "pay_123", "sig_123", null);
        
        assertThatThrownBy(() -> orderService.verifyRazorpayPayment(request, userId))
                .isInstanceOf(com.booknest.orderservice.exception.InvalidPaymentException.class)
                .hasMessageContaining("Access denied: Order ownership mismatch");
    }

    @Test
    void testCancelOrder_InvalidStatus() {
        Order order = Order.builder()
                .orderStatus(OrderStatus.DELIVERED) // Cannot cancel if already delivered
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(com.booknest.orderservice.exception.InvalidPaymentException.class)
                .hasMessageContaining("Order can no longer be cancelled");
    }

    @Test
    void testHandlePaymentWebhook_AmountMismatch() {
        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"rzp_order_1\",\"id\":\"pay_1\",\"amount\":5000}}}}"; // Received 50, but expected 100
        String signature = "valid_sig";
        Order order = Order.builder().amountPaid(100.0).build();
        
        when(razorpayService.verifyWebhookSignature(payload, signature)).thenReturn(true);
        when(orderRepository.findByRazorpayOrderId("rzp_order_1")).thenReturn(Arrays.asList(order));
        
        assertThatThrownBy(() -> orderService.handlePaymentWebhook(payload, signature))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Amount mismatch detected in webhook");
    }

    @Test
    void testInitiateRazorpayPayment_Simulation() throws Exception {
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", 
                CartDTO.builder().userId(userId).items(List.of(CartItemDTO.builder().bookId(bookId).quantity(1).price(20.0).build())).build()));
        when(addressRepository.findByCustomerIdAndIsActiveTrue(userId)).thenReturn(List.of(testAddress));
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(razorpayService.isSimulationMode()).thenReturn(true);
        when(razorpayService.createOrder(anyDouble())).thenReturn("rzp_sim");

        String result = orderService.initiateRazorpayPayment(userId, null);
        assertThat(result).isEqualTo("rzp_sim");
    }

    @Test
    void testAddressManagement() throws Exception {
        when(addressRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Address saved = orderService.storeAddress(testAddress);
        assertThat(saved.getFullName()).isEqualTo(testAddress.getFullName());

        when(addressRepository.findByAddressId(1L)).thenReturn(Optional.of(saved));
        orderService.updateAddress(1L, testAddress);
        verify(addressRepository, times(2)).save(any());
        
        orderService.deleteAddress(1L);
        assertThat(saved.getIsActive()).isFalse();
    }

    @Test
    void testDiscountLogic_Tiered() throws Exception {
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", 
                CartDTO.builder().userId(userId).items(List.of(CartItemDTO.builder().bookId(bookId).quantity(5).price(100.0).build())).build()));
        when(addressRepository.findByCustomerIdAndIsActiveTrue(userId)).thenReturn(List.of(testAddress));
        testBook.setPrice(100.0);
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(razorpayService.isSimulationMode()).thenReturn(true);
        when(razorpayService.createOrder(anyDouble())).thenReturn("rzp_sim");

        // Base 500, Qty 5 -> 15% discount -> 425.0
        orderService.initiateRazorpayPayment(userId, null);
        verify(razorpayService).createOrder(425.0);
    }

    // ──────────────────────────────────────────────────────
    //  Additional tests for ~85 % branch coverage
    // ──────────────────────────────────────────────────────

    @Test
    void testCheckout_NullRequest() {
        assertThatThrownBy(() -> orderService.checkout(null))
                .isInstanceOf(com.booknest.orderservice.exception.InvalidPaymentException.class)
                .hasMessageContaining("userId is required");
    }

    @Test
    void testCheckout_NullUserId() {
        assertThatThrownBy(() -> orderService.checkout(
                CheckoutRequest.builder().userId(null).paymentMethod("COD").build()))
                .isInstanceOf(com.booknest.orderservice.exception.InvalidPaymentException.class)
                .hasMessageContaining("userId is required");
    }

    @Test
    void testCheckout_EmptyCart() {
        com.booknest.orderservice.dto.CartDTO emptyCart =
                com.booknest.orderservice.dto.CartDTO.builder()
                        .items(Collections.emptyList()).build();
        when(cartClient.getCartByUserId(userId))
                .thenReturn(new ApiResponse<>(true, "ok", emptyCart));

        assertThatThrownBy(() -> orderService.checkout(
                CheckoutRequest.builder().userId(userId).paymentMethod("COD").build()))
                .isInstanceOf(com.booknest.orderservice.exception.InvalidPaymentException.class)
                .hasMessageContaining("empty cart");
    }

    @Test
    void testCheckout_InvalidPaymentMethod() {
        // normalizePaymentMethod() fires before fetchCart, so no client stubs needed
        assertThatThrownBy(() -> orderService.checkout(
                CheckoutRequest.builder().userId(userId).paymentMethod("BITCOIN").addressId(1L).build()))
                .isInstanceOf(com.booknest.orderservice.exception.InvalidPaymentException.class)
                .hasMessageContaining("Unsupported payment method");
    }

    @Test
    void testCheckout_OnlinePayment_WithVerifiedFlag() {
        when(cartClient.getCartByUserId(userId))
                .thenReturn(new ApiResponse<>(true, "ok", testCart));
        when(addressRepository.findByAddressIdAndCustomerIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(testAddress));
        when(bookClient.getBookById(bookId))
                .thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(orderRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<Order> result = orderService.checkout(CheckoutRequest.builder()
                .userId(userId)
                .paymentMethod("ONLINE")
                .addressId(1L)
                .paymentDetails(java.util.Map.of(
                        "verified", true, "paymentId", "pay_123", "signature", "sig_abc"))
                .build());

        assertThat(result.get(0).getOrderStatus()).isEqualTo(OrderStatus.PAID);
        verify(bookClient).reduceStockBatch(anyList());
        verify(cartClient).clearCartByUserId(userId);
    }

    @Test
    void testCheckout_OnlinePayment_MissingPaymentDetails() {
        when(cartClient.getCartByUserId(userId))
                .thenReturn(new ApiResponse<>(true, "ok", testCart));
        when(addressRepository.findByAddressIdAndCustomerIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(testAddress));
        when(bookClient.getBookById(bookId))
                .thenReturn(new ApiResponse<>(true, "ok", testBook));

        assertThatThrownBy(() -> orderService.checkout(CheckoutRequest.builder()
                .userId(userId)
                .paymentMethod("ONLINE")
                .addressId(1L)
                .build()))
                .isInstanceOf(com.booknest.orderservice.exception.InvalidPaymentException.class)
                .hasMessageContaining("paymentDetails are required");
    }

    @Test
    void testCheckout_QuantityDiscount_3Items() {
        com.booknest.orderservice.dto.CartItemDTO item3 =
                com.booknest.orderservice.dto.CartItemDTO.builder().bookId(bookId).quantity(3).build();
        com.booknest.orderservice.dto.CartDTO cart3 =
                com.booknest.orderservice.dto.CartDTO.builder()
                        .items(Collections.singletonList(item3)).build();
        testBook.setPrice(100.0);
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", cart3));
        when(addressRepository.findByAddressIdAndCustomerIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(testAddress));
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(orderRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<Order> result = orderService.checkout(CheckoutRequest.builder()
                .userId(userId).paymentMethod("COD").addressId(1L).build());

        // 100 * 3 * (1 - 0.10) = 270
        assertThat(result.get(0).getAmountPaid()).isEqualTo(270.0);
    }

    @Test
    void testCheckout_HighPriceDiscount() {
        testBook.setPrice(1000.0);
        com.booknest.orderservice.dto.CartItemDTO item1 =
                com.booknest.orderservice.dto.CartItemDTO.builder().bookId(bookId).quantity(1).build();
        com.booknest.orderservice.dto.CartDTO cart1 =
                com.booknest.orderservice.dto.CartDTO.builder()
                        .items(Collections.singletonList(item1)).build();
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", cart1));
        when(addressRepository.findByAddressIdAndCustomerIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(testAddress));
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(orderRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<Order> result = orderService.checkout(CheckoutRequest.builder()
                .userId(userId).paymentMethod("COD").addressId(1L).build());

        // 1000 * (1 - 0.05) = 950
        assertThat(result.get(0).getAmountPaid()).isEqualTo(950.0);
    }

    @Test
    void testCheckout_BookNest10Discount() {
        testBook.setPrice(100.0);
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", testCart));
        when(addressRepository.findByAddressIdAndCustomerIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(testAddress));
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(orderRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<Order> result = orderService.checkout(CheckoutRequest.builder()
                .userId(userId).paymentMethod("COD").addressId(1L).discountCode("BOOKNEST10").build());

        // 100 * (1 - 0.10) = 90
        assertThat(result.get(0).getAmountPaid()).isEqualTo(90.0);
    }

    @Test
    void testCheckout_UsesLatestAddress_WhenNoAddressId() {
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", testCart));
        when(addressRepository.findByCustomerIdAndIsActiveTrue(userId))
                .thenReturn(Collections.singletonList(testAddress));
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(orderRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<Order> result = orderService.checkout(CheckoutRequest.builder()
                .userId(userId).paymentMethod("COD").build());

        assertThat(result).hasSize(1);
        verify(addressRepository).findByCustomerIdAndIsActiveTrue(userId);
    }

    @Test
    void testCheckout_NoAddressFound() {
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", testCart));
        when(addressRepository.findByCustomerIdAndIsActiveTrue(userId)).thenReturn(Collections.emptyList());
        // bookClient stub omitted: address resolution throws before stock validation
        assertThatThrownBy(() -> orderService.checkout(
                CheckoutRequest.builder().userId(userId).paymentMethod("COD").build()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No shipping address found");
    }

    @Test
    void testCancelOrder_CodOrder_NoWalletRefund() {
        Order order = Order.builder()
                .orderId(1L).userId(userId).bookId(bookId)
                .quantity(1).amountPaid(100.0)
                .orderStatus(OrderStatus.PLACED)
                .paymentMethod("COD")
                .statusHistory(new java.util.ArrayList<>())
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.cancelOrder(1L);

        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(walletClient, never()).addMoney(any());
        verify(bookClient).updateStock(eq(bookId), eq(1));
    }

    @Test
    void testCancelOrder_PaidOrder_WalletRefund() {
        Order order = Order.builder()
                .orderId(2L).userId(userId).bookId(bookId)
                .quantity(2).amountPaid(200.0)
                .orderStatus(OrderStatus.PAID)
                .paymentMethod("WALLET")
                .statusHistory(new java.util.ArrayList<>())
                .build();
        when(orderRepository.findById(2L)).thenReturn(Optional.of(order));
        when(walletClient.getWalletByUserId(userId))
                .thenReturn(new ApiResponse<>(true, "ok",
                        com.booknest.orderservice.dto.WalletDTO.builder().walletId(1L).build()));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.cancelOrder(2L);

        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(walletClient).addMoney(any());
    }

    @Test
    void testCancelOrder_OrderNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void testGetOrderById_NotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void testGetAddressById_NotFound() {
        when(addressRepository.findByAddressId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getAddressById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Address not found");
    }

    @Test
    void testStoreAddress_SetsCreatedAt_WhenNull() {
        testAddress.setCreatedAt(null);
        when(addressRepository.save(any(Address.class))).thenAnswer(i -> i.getArgument(0));

        Address result = orderService.storeAddress(testAddress);

        assertThat(result.getCreatedAt()).isNotNull();
        verify(addressRepository).save(any(Address.class));
    }

    @Test
    void testGetInvoice_NullAddress() {
        Order order = Order.builder()
                .orderId(5L).userId(userId).bookId(bookId)
                .bookName("No Addr Book").quantity(1).amountPaid(50.0)
                .orderDate(LocalDateTime.now())
                .orderStatus(OrderStatus.PLACED)
                .address(null)
                .build();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        com.booknest.orderservice.dto.InvoiceResponse invoice = orderService.getInvoice(5L);

        assertThat(invoice.getBilledTo()).isEqualTo("Customer");
        assertThat(invoice.getShippingAddress()).isEqualTo("Address unavailable");
    }

    @Test
    void testVerifyRazorpayPayment_InvalidSignature() {
        when(razorpayService.verifySignature(any())).thenReturn(false);

        com.booknest.orderservice.dto.PaymentVerifyRequest req =
                new com.booknest.orderservice.dto.PaymentVerifyRequest("rzp_order_1", "pay_1", "bad", null);

        assertThatThrownBy(() -> orderService.verifyRazorpayPayment(req, userId))
                .isInstanceOf(com.booknest.orderservice.exception.InvalidPaymentException.class)
                .hasMessageContaining("Invalid signature");
    }

    @Test
    void testVerifyRazorpayPayment_OrdersNotFound() {
        when(razorpayService.verifySignature(any())).thenReturn(true);
        when(razorpayService.isSimulationMode()).thenReturn(false);
        when(orderRepository.findByRazorpayOrderId("rzp_order_empty"))
                .thenReturn(Collections.emptyList());

        com.booknest.orderservice.dto.PaymentVerifyRequest req =
                new com.booknest.orderservice.dto.PaymentVerifyRequest("rzp_order_empty", "pay_1", "sig", null);

        assertThatThrownBy(() -> orderService.verifyRazorpayPayment(req, userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Orders not found");
    }

    @Test
    void testVerifyRazorpayPayment_AlreadyPaid_Idempotent() {
        Order paidOrder = Order.builder()
                .userId(userId).bookId(bookId).amountPaid(100.0)
                .orderStatus(OrderStatus.PAID).build();
        when(razorpayService.verifySignature(any())).thenReturn(true);
        when(razorpayService.isSimulationMode()).thenReturn(false);
        when(orderRepository.findByRazorpayOrderId("rzp_paid"))
                .thenReturn(java.util.Arrays.asList(paidOrder));

        com.booknest.orderservice.dto.PaymentVerifyRequest req =
                new com.booknest.orderservice.dto.PaymentVerifyRequest("rzp_paid", "pay_1", "sig", null);
        List<Order> result = orderService.verifyRazorpayPayment(req, userId);

        assertThat(result.get(0).getOrderStatus()).isEqualTo(OrderStatus.PAID);
        verify(bookClient, never()).reduceStockBatch(anyList());
    }

    @Test
    void testVerifyRazorpayPayment_SimulationMode() {
        when(razorpayService.verifySignature(any())).thenReturn(true);
        when(razorpayService.isSimulationMode()).thenReturn(true);
        // Checkout mocks for the delegated call
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", testCart));
        when(addressRepository.findByAddressIdAndCustomerIdAndIsActiveTrue(1L, userId))
                .thenReturn(Optional.of(testAddress));
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(orderRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        com.booknest.orderservice.dto.PaymentVerifyRequest req =
                new com.booknest.orderservice.dto.PaymentVerifyRequest("rzp_sim_1", "pay_1", "sig", 1L);
        List<Order> result = orderService.verifyRazorpayPayment(req, userId);

        assertThat(result.get(0).getOrderStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void testHandlePaymentWebhook_InvalidSignature() {
        String payload = "{\"event\":\"payment.captured\"}";
        when(razorpayService.verifyWebhookSignature(payload, "bad_sig")).thenReturn(false);

        assertThatThrownBy(() -> orderService.handlePaymentWebhook(payload, "bad_sig"))
                .isInstanceOf(com.booknest.orderservice.exception.InvalidPaymentException.class)
                .hasMessageContaining("Invalid webhook signature");
    }

    @Test
    void testHandlePaymentWebhook_OrdersNotFound_ReturnsGracefully() {
        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{" +
                "\"order_id\":\"rzp_ghost\",\"id\":\"pay_1\",\"amount\":10000}}}}";
        when(razorpayService.verifyWebhookSignature(payload, "sig")).thenReturn(true);
        when(orderRepository.findByRazorpayOrderId("rzp_ghost")).thenReturn(Collections.emptyList());

        // Should NOT throw – just log and return
        orderService.handlePaymentWebhook(payload, "sig");

        verify(orderRepository, never()).saveAll(anyList());
    }

    @Test
    void testHandlePaymentWebhook_PaymentFailed() {
        String payload = "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{" +
                "\"order_id\":\"rzp_f1\",\"id\":\"pay_f\",\"amount\":10000}}}}";
        Order order = Order.builder().amountPaid(100.0).orderStatus(OrderStatus.PLACED).build();
        when(razorpayService.verifyWebhookSignature(payload, "sig")).thenReturn(true);
        when(orderRepository.findByRazorpayOrderId("rzp_f1"))
                .thenReturn(java.util.Arrays.asList(order));

        orderService.handlePaymentWebhook(payload, "sig");

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.FAILED);
        verify(orderRepository).saveAll(anyList());
    }

    @Test
    void testHandlePaymentWebhook_AlreadyPaid_NoDoubleUpdate() {
        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{" +
                "\"order_id\":\"rzp_done\",\"id\":\"pay_d\",\"amount\":10000}}}}";
        Order order = Order.builder().amountPaid(100.0).orderStatus(OrderStatus.PAID).build();
        when(razorpayService.verifyWebhookSignature(payload, "sig")).thenReturn(true);
        when(orderRepository.findByRazorpayOrderId("rzp_done"))
                .thenReturn(java.util.Arrays.asList(order));

        orderService.handlePaymentWebhook(payload, "sig");

        // saveAll should NOT be called because order is already PAID
        verify(orderRepository, never()).saveAll(anyList());
    }

    @Test
    void testInitiateRazorpayPayment_EmptyCart() {
        com.booknest.orderservice.dto.CartDTO emptyCart =
                com.booknest.orderservice.dto.CartDTO.builder()
                        .items(Collections.emptyList()).build();
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", emptyCart));

        assertThatThrownBy(() -> orderService.initiateRazorpayPayment(userId, null))
                .isInstanceOf(com.booknest.orderservice.exception.InvalidPaymentException.class)
                .hasMessageContaining("empty cart");
    }

    @Test
    void testInitiateRazorpayPayment_NoAddress() {
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", testCart));
        when(addressRepository.findByCustomerIdAndIsActiveTrue(userId)).thenReturn(Collections.emptyList());
        // bookClient stub omitted: getLatestAddress() throws before validateStockAndFetchBooks()
        assertThatThrownBy(() -> orderService.initiateRazorpayPayment(userId, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No shipping address found");
    }

    @Test
    void testInitiateRazorpayPayment_NonSimulationMode() throws Exception {
        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", testCart));
        when(addressRepository.findByCustomerIdAndIsActiveTrue(userId))
                .thenReturn(Collections.singletonList(testAddress));
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(razorpayService.isSimulationMode()).thenReturn(false);
        when(razorpayService.createOrder(anyDouble())).thenReturn("rzp_real_order_1");
        when(orderRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        String result = orderService.initiateRazorpayPayment(userId, null);

        assertThat(result).isEqualTo("rzp_real_order_1");
        verify(orderRepository).saveAll(anyList());
    }

    @Test
    void testCheckout_MultipleCartItems() {
        com.booknest.orderservice.dto.CartItemDTO item1 =
                com.booknest.orderservice.dto.CartItemDTO.builder().bookId(bookId).quantity(1).build();
        Long bookId2 = 202L;
        com.booknest.orderservice.dto.CartItemDTO item2 =
                com.booknest.orderservice.dto.CartItemDTO.builder().bookId(bookId2).quantity(1).build();
        com.booknest.orderservice.dto.CartDTO multiCart =
                com.booknest.orderservice.dto.CartDTO.builder()
                        .items(java.util.Arrays.asList(item1, item2)).build();

        com.booknest.orderservice.dto.BookDTO book2 = com.booknest.orderservice.dto.BookDTO.builder()
                .bookId(bookId2).title("Book Two").price(50.0).stock(5).build();

        when(cartClient.getCartByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", multiCart));
        when(addressRepository.findByAddressIdAndCustomerIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(testAddress));
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", testBook));
        when(bookClient.getBookById(bookId2)).thenReturn(new ApiResponse<>(true, "ok", book2));
        when(orderRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<Order> result = orderService.checkout(CheckoutRequest.builder()
                .userId(userId).paymentMethod("COD").addressId(1L).build());

        assertThat(result).hasSize(2);
        verify(cartClient).clearCartByUserId(userId);
    }
}
