package com.booknest.orderservice.service;

import com.booknest.orderservice.dto.InvoiceResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Service that generates professional PDF invoices for BookNest orders using Apache PDFBox.
 *
 * <p>The generated PDF follows a clean, branded layout:</p>
 * <ul>
 *   <li>Header band with BookNest branding and invoice number.</li>
 *   <li>Customer details section (billed to, mobile, shipping address).</li>
 *   <li>Itemised table with book title, quantity, unit price, and line total.</li>
 *   <li>Discount row (if applicable) and grand total.</li>
 *   <li>Payment method and order status footer.</li>
 * </ul>
 *
 * <p>Returns a {@code byte[]} so the controller can stream it as a PDF attachment.</p>
 */
@Service
@Slf4j
public class PdfInvoiceService {

    // ── Brand colours (dark-gold theme) ─────────────────────────────────────
    private static final Color BRAND_PRIMARY   = new Color(0xAD, 0x14, 0x57); // deep rose / maroon
    private static final Color BRAND_GOLD      = new Color(0xD4, 0xA0, 0x17); // warm gold accent
    private static final Color TEXT_DARK       = new Color(0x1A, 0x1A, 0x2E);
    private static final Color TEXT_SECONDARY  = new Color(0x6B, 0x72, 0x80);
    private static final Color DIVIDER         = new Color(0xE5, 0xE7, 0xEB);
    private static final Color TABLE_HEADER_BG = new Color(0xF9, 0xF0, 0xF5);

    // ── Page geometry ────────────────────────────────────────────────────────
    private static final float PAGE_WIDTH   = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT  = PDRectangle.A4.getHeight();
    private static final float MARGIN_LEFT  = 50;
    private static final float MARGIN_RIGHT = PAGE_WIDTH - 50;
    private static final float CONTENT_W    = MARGIN_RIGHT - MARGIN_LEFT;

    /**
     * Generates a PDF invoice for the given {@link InvoiceResponse} and returns the raw bytes.
     *
     * @param invoice the order invoice data
     * @return raw PDF bytes suitable for streaming as {@code application/pdf}
     */
    public byte[] generateInvoicePdf(InvoiceResponse invoice) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PAGE_HEIGHT - 50;

                // ── Header Band ────────────────────────────────────────────
                y = drawHeaderBand(cs, y, invoice);

                // ── Invoice Meta ───────────────────────────────────────────
                y = drawMetaSection(cs, y, invoice);

                // ── Divider ────────────────────────────────────────────────
                y = drawDivider(cs, y);

                // ── Bill To ────────────────────────────────────────────────
                y = drawBillTo(cs, y, invoice);

                // ── Item Table ─────────────────────────────────────────────
                y = drawItemTable(cs, y, invoice);

                // ── Totals ─────────────────────────────────────────────────
                y = drawTotals(cs, y, invoice);

                // ── Footer ─────────────────────────────────────────────────
                drawFooter(cs, invoice);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            log.info("PDF invoice generated for order #{}", invoice.getOrderId());
            return out.toByteArray();
        } catch (IOException ex) {
            log.error("Failed to generate PDF invoice for order #{}: {}", invoice.getOrderId(), ex.getMessage());
            throw new RuntimeException("PDF generation failed: " + ex.getMessage(), ex);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private drawing helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Draws the coloured header band with the BookNest logo text and invoice title. */
    private float drawHeaderBand(PDPageContentStream cs, float y, InvoiceResponse invoice) throws IOException {
        float bandHeight = 70;
        float bandBot    = y - bandHeight;

        // Coloured rectangle
        cs.setNonStrokingColor(BRAND_PRIMARY);
        cs.addRect(0, bandBot, PAGE_WIDTH, bandHeight);
        cs.fill();

        // Gold accent strip (thin bottom border)
        cs.setNonStrokingColor(BRAND_GOLD);
        cs.addRect(0, bandBot, PAGE_WIDTH, 3);
        cs.fill();

        // Brand name (left)
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 22);
        cs.setNonStrokingColor(Color.WHITE);
        cs.newLineAtOffset(MARGIN_LEFT, bandBot + 26);
        cs.showText("BOOKNEST");
        cs.endText();

        // Tagline (left, smaller)
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
        cs.setNonStrokingColor(new Color(0xFF, 0xC0, 0xCB));
        cs.newLineAtOffset(MARGIN_LEFT, bandBot + 12);
        cs.showText("Your Premier Digital Bookstore");
        cs.endText();

        // "TAX INVOICE" (right)
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
        cs.setNonStrokingColor(Color.WHITE);
        cs.newLineAtOffset(MARGIN_RIGHT - 110, bandBot + 30);
        cs.showText("TAX INVOICE");
        cs.endText();

        // Invoice number
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
        cs.setNonStrokingColor(new Color(0xFF, 0xC0, 0xCB));
        cs.newLineAtOffset(MARGIN_RIGHT - 110, bandBot + 14);
        cs.showText(safe(invoice.getInvoiceNumber()));
        cs.endText();

        return bandBot - 20;
    }

    /** Invoice date and order ID summary row. */
    private float drawMetaSection(PDPageContentStream cs, float y, InvoiceResponse invoice) throws IOException {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        cs.setNonStrokingColor(TEXT_SECONDARY);
        cs.newLineAtOffset(MARGIN_LEFT, y);
        cs.showText("Date Issued: " + formatDate(invoice.getOrderDate()));
        cs.endText();

        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        cs.setNonStrokingColor(TEXT_SECONDARY);
        cs.newLineAtOffset(MARGIN_RIGHT - 180, y);
        cs.showText("Order ID: #BN-" + invoice.getOrderId());
        cs.endText();

        return y - 30;
    }

    /** Horizontal divider line. */
    private float drawDivider(PDPageContentStream cs, float y) throws IOException {
        cs.setStrokingColor(DIVIDER);
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN_LEFT, y);
        cs.lineTo(MARGIN_RIGHT, y);
        cs.stroke();
        return y - 20;
    }

    /** Customer billing and shipping section. */
    private float drawBillTo(PDPageContentStream cs, float y, InvoiceResponse invoice) throws IOException {
        // Section label
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
        cs.setNonStrokingColor(BRAND_PRIMARY);
        cs.newLineAtOffset(MARGIN_LEFT, y);
        cs.showText("BILLED TO");
        cs.endText();
        y -= 16;

        String[] billingLines = {
            safe(invoice.getBilledTo()),
            "Mobile: " + safe(invoice.getMobileNumber()),
            "Address: " + safe(invoice.getShippingAddress()),
            "Payment: " + safe(invoice.getPaymentMethod()) + "  |  Status: " + safe(invoice.getOrderStatus())
        };

        for (String line : billingLines) {
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
            cs.setNonStrokingColor(TEXT_DARK);
            cs.newLineAtOffset(MARGIN_LEFT, y);
            cs.showText(truncate(line, 90));
            cs.endText();
            y -= 15;
        }

        return y - 15;
    }

    /** Itemized order table. */
    private float drawItemTable(PDPageContentStream cs, float y, InvoiceResponse invoice) throws IOException {
        float[] colX = { MARGIN_LEFT, MARGIN_LEFT + 260, MARGIN_LEFT + 330, MARGIN_LEFT + 400 };
        String[] headers = { "Description", "Qty", "Unit Price", "Total" };

        // Table header background
        cs.setNonStrokingColor(TABLE_HEADER_BG);
        cs.addRect(MARGIN_LEFT, y - 18, CONTENT_W, 22);
        cs.fill();

        // Table header text
        for (int i = 0; i < headers.length; i++) {
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 9);
            cs.setNonStrokingColor(BRAND_PRIMARY);
            cs.newLineAtOffset(colX[i], y - 12);
            cs.showText(headers[i]);
            cs.endText();
        }
        y -= 25;

        // Row divider
        drawDivider(cs, y + 2);

        // Item row
        double unitPrice = invoice.getQuantity() > 0
                ? invoice.getAmountPaid() / invoice.getQuantity()
                : invoice.getAmountPaid();

        String[] rowData = {
            truncate(safe(invoice.getBookName()), 50),
            String.valueOf(invoice.getQuantity()),
            "INR " + String.format("%.2f", unitPrice),
            "INR " + String.format("%.2f", invoice.getAmountPaid())
        };

        for (int i = 0; i < rowData.length; i++) {
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
            cs.setNonStrokingColor(TEXT_DARK);
            cs.newLineAtOffset(colX[i], y - 10);
            cs.showText(rowData[i]);
            cs.endText();
        }
        y -= 22;

        drawDivider(cs, y);
        return y - 15;
    }

    /** Grand total and payment status summary. */
    private float drawTotals(PDPageContentStream cs, float y, InvoiceResponse invoice) throws IOException {
        float labelX = MARGIN_RIGHT - 200;
        float valueX = MARGIN_RIGHT - 10;

        // Total row
        String[][] rows = {
            { "Subtotal:",       "INR " + String.format("%.2f", invoice.getSubtotal()) },
            { "Tax:",            "INR " + String.format("%.2f", invoice.getTaxAmount()) },
            { "Shipping:",       "INR " + String.format("%.2f", invoice.getShippingAmount()) },
            { "Discount:",       "-INR " + String.format("%.2f", invoice.getDiscountAmount()) },
            { "Grand Total:",    "INR " + String.format("%.2f", invoice.getAmountPaid()) }
        };

        for (int i = 0; i < rows.length; i++) {
            boolean isGrand = i == rows.length - 1;

            cs.beginText();
            cs.setFont(isGrand
                    ? new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
                    : new PDType1Font(Standard14Fonts.FontName.HELVETICA), isGrand ? 11 : 10);
            cs.setNonStrokingColor(isGrand ? BRAND_PRIMARY : TEXT_SECONDARY);
            cs.newLineAtOffset(labelX, y);
            cs.showText(rows[i][0]);
            cs.endText();

            // Right-align value
            float valueWidth = (rows[i][1].length() * (isGrand ? 6.6f : 6f));
            cs.beginText();
            cs.setFont(isGrand
                    ? new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
                    : new PDType1Font(Standard14Fonts.FontName.HELVETICA), isGrand ? 11 : 10);
            cs.setNonStrokingColor(isGrand ? BRAND_PRIMARY : TEXT_DARK);
            cs.newLineAtOffset(valueX - valueWidth, y);
            cs.showText(rows[i][1]);
            cs.endText();

            y -= (isGrand ? 5 : 18);
            if (isGrand) {
                // Grand total underline
                cs.setStrokingColor(BRAND_GOLD);
                cs.setLineWidth(1f);
                cs.moveTo(labelX, y + 3);
                cs.lineTo(MARGIN_RIGHT, y + 3);
                cs.stroke();
            }
        }

        return y - 30;
    }

    /** Footer with legal text and thank-you message. */
    private void drawFooter(PDPageContentStream cs, InvoiceResponse invoice) throws IOException {
        float footerY = 45;

        // Thin top border
        cs.setStrokingColor(DIVIDER);
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN_LEFT, footerY + 30);
        cs.lineTo(MARGIN_RIGHT, footerY + 30);
        cs.stroke();

        String footerText = "Thank you for your purchase! | BookNest Platform | support@booknest.in";
        String footerRight = "Generated: " + LocalDate.now().format(DateTimeFormatter.ISO_DATE);

        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
        cs.setNonStrokingColor(TEXT_SECONDARY);
        cs.newLineAtOffset(MARGIN_LEFT, footerY + 14);
        cs.showText(footerText);
        cs.endText();

        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
        cs.setNonStrokingColor(TEXT_SECONDARY);
        cs.newLineAtOffset(MARGIN_RIGHT - 130, footerY + 14);
        cs.showText(footerRight);
        cs.endText();
    }

    // ── Utility helpers ──────────────────────────────────────────────────────

    private String safe(String value) {
        if (value == null || value.isBlank()) return "N/A";
        // PDFBox Helvetica (Standard14) only supports WinAnsiEncoding.
        // We strip non-printable ASCII and replace common problematic symbols like Rupee (₹).
        return value.replace("₹", "INR")
                   .replaceAll("[^\\x20-\\x7E\\x80-\\xFF]", " ") // Keep ASCII + Latin-1
                   .trim();
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() > maxChars ? text.substring(0, maxChars - 3) + "..." : text;
    }

    private String formatDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) return "N/A";
        try {
            return rawDate.substring(0, 10); // ISO-8601 date part
        } catch (Exception e) {
            return rawDate;
        }
    }
}
