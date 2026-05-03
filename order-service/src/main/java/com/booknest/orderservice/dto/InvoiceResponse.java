package com.booknest.orderservice.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InvoiceResponse {
    String invoiceNumber;
    Long orderId;
    Long userId;
    String bookName;
    Long bookId;
    Integer quantity;
    Double amountPaid;
    String paymentMethod;
    String orderStatus;
    String orderDate;
    String billedTo;
    String mobileNumber;
    String shippingAddress;
}
