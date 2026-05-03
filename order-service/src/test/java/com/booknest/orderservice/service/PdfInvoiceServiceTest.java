package com.booknest.orderservice.service;

import com.booknest.orderservice.dto.InvoiceResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PdfInvoiceServiceTest {

    private final PdfInvoiceService pdfInvoiceService = new PdfInvoiceService();

    @Test
    void generateInvoicePdf_ReturnsReadablePdf_WithExpectedStructure() throws IOException {
        InvoiceResponse invoice = InvoiceResponse.builder()
                .invoiceNumber("INV-42")
                .orderId(42L)
                .userId(7L)
                .bookName("A Very Long Book Title That Should Still Render Cleanly In The Invoice Output")
                .bookId(99L)
                .quantity(2)
                .amountPaid(499.99)
                .paymentMethod("ONLINE")
                .orderStatus("PAID")
                .orderDate("2026-05-02T10:15:30")
                .billedTo("Alice Reader")
                .mobileNumber("9999999999")
                .shippingAddress("221B Baker Street, London")
                .build();

        byte[] pdf = pdfInvoiceService.generateInvoicePdf(invoice);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void generateInvoicePdf_HandlesNullAndBlankValues() {
        InvoiceResponse invoice = InvoiceResponse.builder()
                .invoiceNumber(" ")
                .orderId(1L)
                .bookName(null)
                .quantity(0)
                .amountPaid(0.0)
                .paymentMethod(null)
                .orderStatus("")
                .orderDate("bad-date")
                .billedTo(null)
                .mobileNumber(" ")
                .shippingAddress(null)
                .build();

        byte[] pdf = pdfInvoiceService.generateInvoicePdf(invoice);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
