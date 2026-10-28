package com.warsha.erp.services;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import com.warsha.erp.dtos.PaymentDto;
import com.warsha.erp.entities.Invoice;
import com.warsha.erp.entities.Order;
import com.warsha.erp.entities.OrderItems;
import com.warsha.erp.repository.InvoiceRepository;
import com.warsha.erp.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class InvoiceService {
    final InvoiceRepository invoiceRepository;
    final OrderRepository orderRepository;
    final PaymentService paymentService;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          OrderRepository orderRepository, PaymentService paymentService) {
        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
    }

    public Invoice generateInvoice(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getInvoice() != null) {
            throw new RuntimeException("Invoice already exists for this order");
        }

        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setInvoiceNumber("INV-" + System.currentTimeMillis());

        order.setInvoice(invoice); // keep both sides in sync

        return invoiceRepository.save(invoice);
    }

    public Invoice getInvoiceById(Long orderId) {
        Invoice invoice = invoiceRepository.findByOrderId(orderId);
        if (invoice == null) {
            throw new RuntimeException("Invoice not found for Order ID: " + orderId);
        }
        return invoice;
    }

    public void deleteInvoiceByOrderId(Long orderId) {
        Invoice invoice = invoiceRepository.findByOrderId(orderId);
        if (invoice == null) {
            throw new RuntimeException("Invoice not found for Order ID: " + orderId);
        }
        invoiceRepository.delete(invoice);
    }

    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    public byte[] generateInvoicePdf(Invoice invoice) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = createDocument(baos);

            // Add sections
            document.add(createHeader(invoice));
            document.add(createItemsTable(invoice));
            document.add(createTotalTable(invoice));
            document.add(Chunk.NEWLINE);
            document.add(createFooter());

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF", e);
        }
    }

    // ====== Helpers ======
    private Document createDocument(ByteArrayOutputStream baos) throws DocumentException {
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, baos);
        document.open();
        return document;
    }

    private PdfPTable createHeader(Invoice invoice) throws Exception {
        // Load Arabic-supported font
        BaseFont bf = BaseFont.createFont("fonts/NotoKufiArabic-Regular.ttf",
                BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font arabicFont = new Font(bf, 12, Font.BOLD);

        PdfPTable headerTable = new PdfPTable(1);
        headerTable.setWidthPercentage(100);

        // Left Cell: Invoice Info
        PdfPCell textCell = new PdfPCell();
        textCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);


        textCell.setBorder(Rectangle.NO_BORDER);

        // Arabic text
        Paragraph invoiceNumber = new Paragraph(ArabicUtils.reshapeArabic("فاتورة رقم: " + invoice.getOrder().getId()), arabicFont);
        invoiceNumber.setAlignment(Element.ALIGN_CENTER);
        textCell.addElement(invoiceNumber);

        Paragraph issuedDate = new Paragraph(
                ArabicUtils.reshapeArabic("تاريخ الإصدار: " + invoice.getIssuedDate()), arabicFont);
        issuedDate.setAlignment(Element.ALIGN_CENTER);
        textCell.addElement(issuedDate);

        // Right Cell: Logo
        Image logo = Image.getInstance("https://drive.usercontent.google.com/download?id=10nH5QZg3EebwUmbXSB0P4A0xoNFmo2Lg&export=view&authuser=0");
        logo.scaleToFit(100, 100);
        PdfPCell logoCell = new PdfPCell(logo);
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        headerTable.addCell(logoCell);
        headerTable.setSpacingAfter(30f);
        headerTable.addCell(textCell);

        headerTable.setSpacingAfter(20f);
        return headerTable;
    }

    private PdfPTable createItemsTable(Invoice invoice) throws Exception {
        // Load Arabic font
        BaseFont bf = BaseFont.createFont("fonts/NotoKufiArabic-Regular.ttf",
                BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font boldFont = new Font(bf, 10, Font.BOLD);
        Font normalFont = new Font(bf, 10);

        // Adjust column count and widths to match the image: 5 columns
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(98);

        // Adjust widths based on visual weight (e.g., Description is wider)
        table.setWidths(new float[]{10, 10, 15, 35, 30});

        // Arabic headers, re-ordered to match the image (right to left)
        String[] headers = {
                ArabicUtils.reshapeArabic("المجموع"),  // Total
                ArabicUtils.reshapeArabic("الكمية"),   // Quantity
                ArabicUtils.reshapeArabic("سعر الوحدة"),// Unit Price
                ArabicUtils.reshapeArabic("الوصف"),    // Description
                ArabicUtils.reshapeArabic("البند"),    // Item
        };

        for (String col : headers) {

            PdfPCell headerCell = getPdfPCell(col, boldFont);
            table.addCell(headerCell);
        }

        // Data Rows
        List<OrderItems> items = invoice.getOrder().getItems();

        for (OrderItems item : items) {
            // Re-order and style cells
            // Item (right-aligned)
            PdfPCell itemCell = createArabicCell(item.displayName(), normalFont);

            // Description (right-aligned)
            PdfPCell descCell = createArabicCell(item.getProduct().getDescription(), normalFont);

            // Unit Price (right-aligned)
            //todo: - item.getProduct().getDiscount()
            PdfPCell priceCell = createArabicCell(String.valueOf(item.getProduct().getSellingPrice()), normalFont);

            // Quantity (right-aligned)
            PdfPCell qtyCell = createArabicCell(String.valueOf(item.getQuantity()), normalFont);

            // Total (right-aligned)
            //todo: - item.getProduct().getDiscount()
            double total = item.getQuantity() * (item.getProduct().getSellingPrice());
            PdfPCell totalCell = createArabicCell(String.valueOf(total), normalFont);

            // Add cells to the table in the correct order (left to right)
            table.addCell(totalCell);
            table.addCell(qtyCell);
            table.addCell(priceCell);
            table.addCell(descCell);
            table.addCell(itemCell);
        }

        return table;
    }

    private static PdfPCell getPdfPCell(String col, Font boldFont) {
        PdfPCell headerCell = new PdfPCell(new Phrase(col, boldFont));
        headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        headerCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        headerCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        headerCell.setBorder(Rectangle.BOX);
        headerCell.setBackgroundColor(Color.lightGray);
        headerCell.setBorderWidth(0.5f);
        headerCell.setPaddingTop(8f);
        headerCell.setPaddingBottom(8f);
        return headerCell;
    }

    // Helper for reshaped Arabic text
    private PdfPCell createArabicCell(String content, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(ArabicUtils.reshapeArabic(content), font));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        cell.setBorder(Rectangle.BOX);
        cell.setBorderWidth(0.5f);
        cell.setPadding(8f);
        return cell;
    }

    private PdfPTable createTotalTable(Invoice invoice) throws DocumentException, IOException {

        // Load Arabic font
        BaseFont bf = BaseFont.createFont("fonts/NotoKufiArabic-Regular.ttf",
                BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font smallBoldFont = new Font(bf, 12, Font.BOLD);

        // 2-column table
        PdfPTable totalTable = new PdfPTable(2);
        totalTable.setWidthPercentage(100);
        totalTable.setWidths(new float[]{1.5f, 2f});
        totalTable.setHorizontalAlignment(Element.ALIGN_LEFT);

        // ========== LEFT COLUMN ==========
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        leftCell.setBorderWidth(0.5f);
        leftCell.setPaddingLeft(5f);

        leftCell.setHorizontalAlignment(Element.ALIGN_LEFT);

        // Create an inner table to hold the two-column content
        PdfPTable innerTable = new PdfPTable(2);
        innerTable.setWidthPercentage(100);
        innerTable.setWidths(new float[]{1.5f, 2f}); // Adjust widths as needed

        // 1. Fetch the payment once and log it manually since we are using the variable now
        List<PaymentDto> payments = paymentService.getPaymentsByOrder(invoice.getOrder().getId());
        double downPayment = 0;

        if (payments != null && !payments.isEmpty()) {
            downPayment = payments.getFirst().getAmountPaid();
        }

        // 2. Pre-calculate values to keep the table logic clean
        double totalPrice = invoice.getOrder().getTotalPrice();
        double discount = invoice.getOrder().getDiscount();
        double delivery = invoice.getOrder().getDeliveryCharge();

        // Subtotal calculation: (Final + Discount - Delivery + Down payment)
        double subTotal = totalPrice + discount - delivery + downPayment;
        double totalWithDownPayment = totalPrice + downPayment;

        // 3. Add rows to the inner table using the stored variables
        addTotalRow(innerTable, "المجموع", String.valueOf(subTotal), smallBoldFont, smallBoldFont);
        addTotalRow(innerTable, "رسوم الشحن", String.valueOf(delivery), smallBoldFont, smallBoldFont);
        addTotalRow(innerTable, "الخصم", String.valueOf(discount), smallBoldFont, smallBoldFont);

        addTotalRow(innerTable, "الاجمالي", String.valueOf(totalWithDownPayment), smallBoldFont, smallBoldFont);
        addTotalRow(innerTable, "مدفوع مسبقا", String.valueOf(downPayment * -1), smallBoldFont, smallBoldFont);
        addTotalRow(innerTable, "المبلغ المتبقي", String.valueOf(totalPrice), smallBoldFont, smallBoldFont);

        // Add the inner table to the main left cell
        leftCell.addElement(innerTable);

        // ========== RIGHT COLUMN ==========
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        rightCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        rightCell.setPaddingTop(10f);

        Paragraph notes = new Paragraph(ArabicUtils.reshapeArabic("ملاحظات: " + invoice.getOrder().getNotes()), smallBoldFont);
        notes.setAlignment(Element.ALIGN_LEFT);
        rightCell.addElement(notes);

        Paragraph shippingTo = new Paragraph(ArabicUtils.reshapeArabic("اسم العميل: " + invoice.getOrder().getCustomer().getFullName()), smallBoldFont);
        shippingTo.setAlignment(Element.ALIGN_LEFT);
        shippingTo.setSpacingBefore(10f);

        rightCell.addElement(shippingTo);

        Paragraph primaryPhone = new Paragraph(ArabicUtils.reshapeArabic("الهاتف: " + invoice.getOrder().getCustomer().getPhone()), smallBoldFont);
        primaryPhone.setAlignment(Element.ALIGN_LEFT);
        rightCell.addElement(primaryPhone);

        String secondaryPhoneValue = (invoice.getOrder().getCustomer().getSecondaryPhone() == null)
                ? "لا يوجد"
                : invoice.getOrder().getCustomer().getSecondaryPhone();

        Paragraph secondaryPhone = new Paragraph(
                ArabicUtils.reshapeArabic("الهاتف الثانوي: " + secondaryPhoneValue),
                smallBoldFont
        );
        secondaryPhone.setAlignment(Element.ALIGN_LEFT);
        rightCell.addElement(secondaryPhone);

        Paragraph address = new Paragraph(ArabicUtils.reshapeArabic("عنوان التوصيل: " + invoice.getOrder().getCustomer().getAddress()), smallBoldFont);
        address.setAlignment(Element.ALIGN_LEFT);
        rightCell.addElement(address);

        // Add cells to table (one row, two columns)
        totalTable.addCell(leftCell);
        totalTable.addCell(rightCell);

        return totalTable;
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell valueCell = new PdfPCell(new Phrase(ArabicUtils.reshapeArabic(value + " جنيه"), valueFont));
        valueCell.setBorder(Rectangle.BOX); // No border
        valueCell.setPadding(8f);
        valueCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        valueCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(valueCell);

        PdfPCell labelCell = new PdfPCell(new Phrase(ArabicUtils.reshapeArabic(label), labelFont));
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setPadding(8f);
        labelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(labelCell);
    }

    private Element createFooter() throws DocumentException {

        // Fonts
        Font italicBoldFont = FontFactory.getFont(FontFactory.TIMES_BOLDITALIC, 12); // bold + italic
        Font italicFont = FontFactory.getFont(FontFactory.TIMES_ITALIC, 10); // italic

        // One column table (full width)
        PdfPTable footerTable = new PdfPTable(1);
        footerTable.setWidthPercentage(100);
        footerTable.setHorizontalAlignment(Element.ALIGN_CENTER);

        // Spacer (empty row above footer content)
        PdfPCell spacer = new PdfPCell(new Phrase(" "));
        spacer.setBorder(Rectangle.NO_BORDER);
        spacer.setFixedHeight(30f); // adjust for more/less space
        footerTable.addCell(spacer);

        // Thank you + Terms cell
        PdfPCell footerCell = getPdfPCell(italicBoldFont, italicFont);

        footerTable.addCell(footerCell);

        return footerTable;
    }

    private static PdfPCell getPdfPCell(Font italicBoldFont, Font italicFont) {
        PdfPCell footerCell = new PdfPCell();
        footerCell.setBorder(Rectangle.NO_BORDER);
        footerCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph thankYou = new Paragraph("All these accessories are made in\nELWARSHA with love by\nAhmed Nasser!", italicBoldFont);
        thankYou.setAlignment(Element.ALIGN_CENTER);
        footerCell.addElement(thankYou);

        Paragraph terms = new Paragraph("From ELWARSHA to the whole world", italicFont);
        terms.setAlignment(Element.ALIGN_CENTER);
        footerCell.addElement(terms);
        return footerCell;
    }

    @Transactional
    public void regenerateInvoice(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        Invoice existingInvoice = invoiceRepository.findByOrderId(orderId);

        if (existingInvoice != null) {
            existingInvoice.setIssuedDate(java.time.LocalDate.now());
            existingInvoice.setInvoiceNumber("INV-" + System.currentTimeMillis());
            existingInvoice.setOrder(order);
            invoiceRepository.save(existingInvoice);
        } else {
            // if missing, generate a new one
            generateInvoice(orderId);
        }
    }
}
