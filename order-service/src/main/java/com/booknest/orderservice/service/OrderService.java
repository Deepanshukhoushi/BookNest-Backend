package com.booknest.orderservice.service;

import com.booknest.orderservice.dto.CheckoutRequest;
import com.booknest.orderservice.dto.InvoiceResponse;
import com.booknest.orderservice.entity.Address;
import com.booknest.orderservice.entity.Order;
import com.booknest.orderservice.enums.OrderStatus;

import java.util.List;

public interface OrderService {
    List<Order> getAllOrders();
    List<Order> checkout(CheckoutRequest request);
    Order changeStatus(Long orderId, OrderStatus status);
    void deleteOrder(Long orderId);
    List<Order> getOrderByUserId(Long userId);
    Order getOrderById(Long orderId);
    Order cancelOrder(Long orderId);
    Order trackOrder(Long orderId);
    InvoiceResponse getInvoice(Long orderId);
    
    Address storeAddress(Address address);
    Address updateAddress(Long addressId, Address address);
    void deleteAddress(Long addressId);
    Address getAddressById(Long addressId);
    List<Address> getAllAddress();
    List<Address> getAddressByCustomerId(Long customerId);
    
    String initiateRazorpayPayment(Long userId, Long addressId);
    List<Order> verifyRazorpayPayment(com.booknest.orderservice.dto.PaymentVerifyRequest request, Long authenticatedUserId);
    void handlePaymentWebhook(String payload, String signature);
}
