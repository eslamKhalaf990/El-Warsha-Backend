package com.warsha.erp.services;

import com.warsha.erp.dtos.OrderRequest;
import com.warsha.erp.entities.Customer;
import com.warsha.erp.entities.Order;
import com.warsha.erp.entities.OrderItems;
import com.warsha.erp.entities.Product;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * HTML bodies for every email Warsha sends, built on one branded layout that follows the
 * store app identity (warsha_commerce AppColors + Cairo font + burgundy flower logo).
 * Inline styles and tables only, so Gmail/Outlook render it the same way.
 */
public final class EmailTemplates {

    // Brand palette (mirrors warsha_commerce/lib/core/theme/app_colors.dart)
    static final String BURGUNDY = "#6D1A2E";
    static final String DEEP_WINE = "#4A0F1E";
    static final String BLUSH = "#F7EBEC";
    static final String IVORY = "#FFFAF6";
    static final String CHAMPAGNE = "#C9A66B";
    static final String INK = "#2B1A1E";
    static final String MUTED = "#8A737A";
    static final String LINE = "#EADDD9";
    static final String WARNING_TEXT = "#7A5A1C";
    static final String WARNING_BG = "#F3E5C8";

    static final String FONT = "'Cairo', Tahoma, 'Segoe UI', Arial, sans-serif";
    static final String LOGO_CID = "warshaLogo";
    static final String STORE_URL = "https://el-warsha-accessories.web.app";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy - hh:mm a");

    private EmailTemplates() {
    }

    // ---------------------------------------------------------------- layout

    /** Wraps body content in the branded shell: logo header, ivory card, footer. */
    static String layout(String preheader, String body, String footerNote) {
        return """
                <!DOCTYPE html>
                <html lang="ar" dir="rtl">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <link href="https://fonts.googleapis.com/css2?family=Cairo:wght@400;600;700&display=swap" rel="stylesheet">
                </head>
                """
                + "<body style=\"margin:0;padding:0;background:" + BLUSH + ";\">"
                + "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">" + esc(preheader) + "</div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:" + BLUSH + ";\">"
                + "<tr><td align=\"center\" style=\"padding:24px 12px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" dir=\"rtl\" style=\"max-width:600px;background:" + IVORY + ";border:1px solid " + LINE + ";border-radius:16px;overflow:hidden;font-family:" + FONT + ";color:" + INK + ";text-align:right;\">"
                // header
                + "<tr><td align=\"center\" style=\"padding:28px 24px 18px;border-bottom:3px solid " + CHAMPAGNE + ";\">"
                + "<img src=\"cid:" + LOGO_CID + "\" width=\"64\" alt=\"ورشة\" style=\"display:block;width:64px;height:auto;border:0;\">"
                + "<div style=\"margin-top:8px;font-size:20px;font-weight:700;color:" + BURGUNDY + ";letter-spacing:1px;\">ورشة</div>"
                + "<div style=\"font-size:12px;color:" + MUTED + ";\">EL WARSHA ACCESSORIES</div>"
                + "</td></tr>"
                // body
                + "<tr><td style=\"padding:28px 28px 8px;font-size:15px;line-height:1.8;\">" + body + "</td></tr>"
                // footer
                + "<tr><td style=\"padding:20px 28px 26px;\">"
                + "<div style=\"border-top:1px solid " + LINE + ";padding-top:16px;font-size:12px;line-height:1.7;color:" + MUTED + ";text-align:center;\">"
                + (footerNote == null ? "" : footerNote + "<br>")
                + "<a href=\"" + STORE_URL + "\" style=\"color:" + BURGUNDY + ";text-decoration:none;font-weight:600;\">el-warsha-accessories.web.app</a>"
                + "<br>صُنعت في ورشة بكل حب"
                + "</div></td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    static String heading(String text) {
        return "<h1 style=\"margin:0 0 6px;font-size:22px;font-weight:700;color:" + BURGUNDY + ";\">" + text + "</h1>";
    }

    static String sectionTitle(String text) {
        return "<div style=\"margin:26px 0 10px;font-size:13px;font-weight:700;color:" + CHAMPAGNE + ";letter-spacing:0.5px;\">" + text + "</div>";
    }

    static String card(String inner) {
        return "<div style=\"background:#FFFFFF;border:1px solid " + LINE + ";border-radius:12px;padding:16px 18px;\">" + inner + "</div>";
    }

    static String button(String href, String label) {
        return "<div style=\"text-align:center;margin:28px 0 8px;\">"
                + "<a href=\"" + href + "\" style=\"display:inline-block;background:" + BURGUNDY + ";color:#FFFFFF;padding:12px 30px;border-radius:999px;text-decoration:none;font-weight:700;font-size:15px;\">" + label + "</a>"
                + "</div>";
    }

    static String badge(String text, String color, String background) {
        return "<span style=\"display:inline-block;background:" + background + ";color:" + color + ";border-radius:999px;padding:2px 12px;font-size:12px;font-weight:700;\">" + text + "</span>";
    }

    /** Label / value row for detail cards. Value is already-escaped HTML. */
    static String row(String label, String valueHtml) {
        return "<tr><td style=\"padding:5px 0;color:" + MUTED + ";font-size:13px;white-space:nowrap;vertical-align:top;width:110px;\">" + label + "</td>"
                + "<td style=\"padding:5px 0;font-size:14px;font-weight:600;color:" + INK + ";\">" + valueHtml + "</td></tr>";
    }

    static String rows(String rowsHtml) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">" + rowsHtml + "</table>";
    }

    // ---------------------------------------------------------------- emails

    public static String otp(String otp) {
        String body = heading("رمز التحقق")
                + "<p style=\"margin:0 0 18px;\">استخدم الرمز التالي لإكمال التحقق من حسابك في ورشة:</p>"
                + "<div style=\"background:" + BLUSH + ";border:1px dashed " + CHAMPAGNE + ";border-radius:12px;padding:18px;text-align:center;\">"
                + "<div style=\"font-size:34px;font-weight:700;letter-spacing:10px;color:" + BURGUNDY + ";direction:ltr;\">" + esc(otp) + "</div>"
                + "</div>"
                + "<p style=\"margin:18px 0 0;font-size:13px;color:" + MUTED + ";\">الرمز صالح لمدة 10 دقائق. لا تشارك هذا الرمز مع أي شخص.</p>";
        return layout("رمز التحقق الخاص بك: " + otp, body, "إذا لم تطلب هذا الرمز، يمكنك تجاهل هذا البريد.");
    }

    public static String orderProcessing(String customerName, String orderId, double orderTotal) {
        String body = heading("شكراً لطلبك، " + esc(customerName))
                + "<p style=\"margin:0;\">استلمنا طلبك رقم <strong>#" + esc(orderId) + "</strong> وفريقنا يعمل الآن على مراجعته وتجهيزه بكل عناية.</p>"
                + sectionTitle("تفاصيل الطلب")
                + card(rows(row("رقم الطلب", "#" + esc(orderId))
                        + row("الإجمالي", money(orderTotal))
                        + row("الحالة", badge("قيد التجهيز", WARNING_TEXT, WARNING_BG))))
                + "<div style=\"margin-top:18px;background:" + WARNING_BG + ";color:" + WARNING_TEXT + ";border-radius:12px;padding:14px 16px;font-size:14px;\">"
                + "<strong>ملاحظة هامة:</strong> يجب تأكيد الطلب بسداد العربون. إذا لم يصلنا العربون خلال المدة المحددة سيتم إلغاء الطلب تلقائياً."
                + "</div>"
                + "<p style=\"margin:18px 0 0;\">بمجرد تأكيد الدفع وشحن الطلب، سنرسل لكِ رسالة أخرى لتتبع الشحنة.</p>"
                + button(STORE_URL, "تسوقي مرة أخرى");
        return layout("طلبك #" + orderId + " قيد التجهيز", body,
                "لأي استفسار عن سداد العربون أو تفاصيل طلبك، يمكنك الرد مباشرة على هذا البريد.");
    }

    public static String orderCancelled(String customerName, String orderId) {
        String body = heading("تم إلغاء طلبك، " + esc(customerName))
                + "<p style=\"margin:0;\">نحيطك علماً بأنه تم إلغاء طلبك رقم <strong>#" + esc(orderId) + "</strong>.</p>"
                + sectionTitle("تفاصيل الطلب")
                + card(rows(row("رقم الطلب", "#" + esc(orderId))
                        + row("الحالة", badge("ملغي", "#777777", "#EEEEEE"))))
                + "<p style=\"margin:18px 0 0;\">غالباً ما يتم ذلك بسبب عدم استلام العربون خلال المدة المحددة، أو بناءً على طلبك.</p>"
                + "<p style=\"margin:8px 0 0;\">نأمل أن نراكِ مجدداً في <strong>ورشة</strong> قريباً، قطعنا المميزة دائماً بانتظارك!</p>"
                + button(STORE_URL, "تسوقي مرة أخرى");
        return layout("تحديث بخصوص طلبك #" + orderId, body,
                "إذا تم الإلغاء عن طريق الخطأ أو قمتِ بتحويل العربون بالفعل، تواصلي معنا فوراً بالرد على هذا البريد.");
    }

    /**
     * Internal notification to the shop owner for a new store order: everything needed to
     * contact the customer, prepare the items and collect the remaining amount.
     *
     * @param productImageCids product id -> inline image cid, for products whose image is on disk
     */
    public static String newOrderAdmin(Order order, OrderRequest request, Map<Long, String> productImageCids,
                                       int attachmentCount, LocalDateTime placedAt) {
        Customer c = order.getCustomer();
        String orderId = String.valueOf(order.getId());

        double subtotal = 0;
        int pieces = 0;
        StringBuilder items = new StringBuilder();
        for (OrderItems item : order.getItems()) {
            Product p = item.getProduct();
            double unit = nz(item.getUnitPrice());
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            subtotal += unit * qty;
            pieces += qty;
            items.append(itemRow(p, qty, unit, productImageCids.get(p.getProductID())));
        }

        double discount = nz(order.getDiscount());
        double delivery = nz(order.getDeliveryCharge());
        double downPayment = request.getDownPayment();
        double remaining = nz(order.getTotalPrice());

        // --- summary strip
        String summary = "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:" + BURGUNDY + ";border-radius:12px;color:#FFFFFF;\"><tr>"
                + stat("المطلوب تحصيله", money(remaining))
                + stat("عدد القطع", String.valueOf(pieces))
                + stat("المحافظة", esc(orEmpty(c.getGovernorate())))
                + "</tr></table>";

        // --- customer
        String phone = orEmpty(c.getPhone());
        StringBuilder customer = new StringBuilder()
                .append(row("الاسم", esc(c.getFullName())))
                .append(row("الهاتف", phoneHtml(phone)));
        if (notBlank(c.getSecondaryPhone())) customer.append(row("هاتف إضافي", phoneHtml(c.getSecondaryPhone())));
        if (notBlank(c.getEmail())) customer.append(row("البريد", "<a href=\"mailto:" + esc(c.getEmail()) + "\" style=\"color:" + BURGUNDY + ";text-decoration:none;direction:ltr;\">" + esc(c.getEmail()) + "</a>"));
        customer.append(row("العنوان", esc(joinAddress(c))));

        String contactButtons = "";
        String wa = whatsappNumber(phone);
        if (wa != null) {
            contactButtons = "<div style=\"margin-top:12px;\">"
                    + pill("https://wa.me/" + wa + "?text=" + urlEncode("أهلاً " + c.getFullName() + "، بخصوص طلبك رقم #" + orderId + " من ورشة"), "واتساب", BURGUNDY, "#FFFFFF")
                    + "&nbsp;"
                    + pill("tel:" + phone.replaceAll("[^0-9+]", ""), "اتصال", BLUSH, BURGUNDY)
                    + "</div>";
        }

        // --- totals
        StringBuilder totals = new StringBuilder()
                .append(totalRow("المجموع", money(subtotal), false));
        if (discount > 0) totals.append(totalRow("الخصم", "- " + money(discount), false));
        totals.append(totalRow("الشحن (" + esc(orEmpty(c.getGovernorate())) + ")", money(delivery), false));
        if (downPayment > 0) totals.append(totalRow("العربون المدفوع", "- " + money(downPayment), false));
        totals.append(totalRow("المتبقي على العميل", money(remaining), true));

        // --- order meta
        StringBuilder meta = new StringBuilder()
                .append(row("رقم الطلب", "#" + esc(orderId)))
                .append(row("التاريخ", "<span dir=\"ltr\" style=\"unicode-bidi:embed;\">" + esc(placedAt.format(DATE_TIME)) + "</span>"))
                .append(row("المصدر", esc(orEmpty(order.getOrderSource()))));
        if (notBlank(request.getPaymentMethod())) meta.append(row("طريقة الدفع", esc(request.getPaymentMethod())));
        if (notBlank(request.getPromoCode())) meta.append(row("كود الخصم", badge(esc(request.getPromoCode()), BURGUNDY, BLUSH)));
        meta.append(row("الحالة", badge("جديد - في انتظار المراجعة", WARNING_TEXT, WARNING_BG)));

        StringBuilder body = new StringBuilder()
                .append(heading("طلب جديد #" + esc(orderId)))
                .append("<p style=\"margin:0 0 18px;color:" + MUTED + ";\">وصل طلب جديد من المتجر الإلكتروني من <strong style=\"color:" + INK + ";\">" + esc(c.getFullName()) + "</strong>.</p>")
                .append(summary)
                .append(sectionTitle("بيانات العميل"))
                .append(card(rows(customer.toString()) + contactButtons))
                .append(sectionTitle("المنتجات (" + order.getItems().size() + ")"))
                .append(card("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">" + items + "</table>"
                        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-top:10px;border-top:1px solid " + LINE + ";\">" + totals + "</table>"));

        if (notBlank(order.getNotes())) {
            body.append(sectionTitle("ملاحظات العميل"))
                    .append("<div style=\"background:" + BLUSH + ";border-right:4px solid " + BURGUNDY + ";border-radius:8px;padding:12px 16px;white-space:pre-line;\">" + esc(order.getNotes()) + "</div>");
        }

        body.append(sectionTitle("تفاصيل الطلب"))
                .append(card(rows(meta.toString())));

        if (attachmentCount > 0) {
            body.append("<div style=\"margin-top:18px;background:" + WARNING_BG + ";color:" + WARNING_TEXT + ";border-radius:12px;padding:12px 16px;font-size:14px;\">"
                    + "📎 أرفق العميل <strong>" + attachmentCount + "</strong> " + (attachmentCount == 1 ? "صورة" : "صور") + " مع الطلب، تجدها في مرفقات هذا البريد."
                    + "</div>");
        }

        return layout("طلب جديد #" + orderId + " - " + c.getFullName() + " - " + plainMoney(remaining), body.toString(),
                "هذا إشعار داخلي من نظام ورشة لكل طلب جديد من المتجر.");
    }

    /** Plain-text fallback of the admin notification, for clients that block HTML. */
    public static String newOrderAdminText(Order order, OrderRequest request, int attachmentCount) {
        Customer c = order.getCustomer();
        StringBuilder sb = new StringBuilder()
                .append("طلب جديد #").append(order.getId()).append("\n\n")
                .append("العميل: ").append(c.getFullName()).append("\n")
                .append("الهاتف: ").append(orEmpty(c.getPhone())).append("\n");
        if (notBlank(c.getSecondaryPhone())) sb.append("هاتف إضافي: ").append(c.getSecondaryPhone()).append("\n");
        sb.append("العنوان: ").append(joinAddress(c)).append("\n\nالمنتجات:\n");
        for (OrderItems item : order.getItems()) {
            sb.append("- ").append(item.getProduct().getName()).append(" × ").append(item.getQuantity())
                    .append(" = ").append(plainMoney(nz(item.getUnitPrice()) * item.getQuantity())).append("\n");
        }
        sb.append("\nالشحن: ").append(plainMoney(nz(order.getDeliveryCharge())))
                .append("\nالخصم: ").append(plainMoney(nz(order.getDiscount())))
                .append("\nالعربون: ").append(plainMoney(request.getDownPayment()))
                .append("\nالمتبقي: ").append(plainMoney(nz(order.getTotalPrice())));
        if (notBlank(order.getNotes())) sb.append("\n\nملاحظات: ").append(order.getNotes());
        if (attachmentCount > 0) sb.append("\n\nمرفقات: ").append(attachmentCount);
        return sb.toString();
    }

    // ---------------------------------------------------------------- pieces

    private static String itemRow(Product p, int qty, double unit, String imageCid) {
        String thumb = imageCid != null
                ? "<img src=\"cid:" + imageCid + "\" width=\"56\" height=\"56\" alt=\"\" style=\"display:block;width:56px;height:56px;object-fit:cover;border-radius:10px;border:1px solid " + LINE + ";\">"
                : "<div style=\"width:56px;height:56px;line-height:56px;border-radius:10px;background:" + BLUSH + ";color:" + BURGUNDY + ";text-align:center;font-weight:700;font-size:20px;\">"
                + esc(initial(p.getName())) + "</div>";
        String sku = notBlank(p.getSku()) ? "<div style=\"font-size:12px;color:" + MUTED + ";direction:ltr;text-align:right;\">" + esc(p.getSku()) + "</div>" : "";
        return "<tr>"
                + "<td style=\"padding:8px 0;width:64px;vertical-align:middle;\">" + thumb + "</td>"
                + "<td style=\"padding:8px 10px;vertical-align:middle;\">"
                + "<div style=\"font-weight:700;font-size:14px;\">" + esc(p.getName()) + "</div>" + sku
                + "<div style=\"font-size:13px;color:" + MUTED + ";\">" + qty + " × " + money(unit) + "</div>"
                + "</td>"
                + "<td style=\"padding:8px 0;vertical-align:middle;text-align:left;font-weight:700;white-space:nowrap;\">" + money(unit * qty) + "</td>"
                + "</tr>";
    }

    private static String stat(String label, String value) {
        return "<td align=\"center\" style=\"padding:14px 6px;\">"
                + "<div style=\"font-size:12px;color:" + BLUSH + ";opacity:0.85;\">" + label + "</div>"
                + "<div style=\"font-size:17px;font-weight:700;color:#FFFFFF;\">" + value + "</div>"
                + "</td>";
    }

    private static String totalRow(String label, String value, boolean grand) {
        String style = grand
                ? "padding:10px 0 2px;font-size:16px;font-weight:700;color:" + BURGUNDY + ";border-top:1px dashed " + CHAMPAGNE + ";"
                : "padding:4px 0;font-size:14px;color:" + INK + ";";
        return "<tr><td style=\"" + style + "\">" + label + "</td><td style=\"" + style + "text-align:left;white-space:nowrap;\">" + value + "</td></tr>";
    }

    private static String pill(String href, String label, String bg, String fg) {
        return "<a href=\"" + href + "\" style=\"display:inline-block;background:" + bg + ";color:" + fg + ";border:1px solid " + BURGUNDY + ";padding:6px 18px;border-radius:999px;text-decoration:none;font-weight:700;font-size:13px;\">" + label + "</a>";
    }

    private static String phoneHtml(String phone) {
        if (!notBlank(phone)) return "-";
        return "<a href=\"tel:" + esc(phone.replaceAll("[^0-9+]", "")) + "\" style=\"color:" + INK + ";text-decoration:none;direction:ltr;unicode-bidi:embed;\">" + esc(phone) + "</a>";
    }

    /** Egyptian local number (01xxxxxxxxx) -> international digits for wa.me (201xxxxxxxxx). */
    static String whatsappNumber(String phone) {
        if (!notBlank(phone)) return null;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        if (digits.startsWith("0")) digits = "2" + digits;
        return digits.length() >= 11 ? digits : null;
    }

    private static String joinAddress(Customer c) {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]{c.getGovernorate(), c.getCity(), c.getAddress()}) {
            if (notBlank(part)) {
                if (sb.length() > 0) sb.append(" - ");
                sb.append(part.trim());
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    static String money(double amount) {
        return "<span style=\"white-space:nowrap;\">" + plainMoney(amount) + "</span>";
    }

    static String plainMoney(double amount) {
        String n = amount == Math.rint(amount) ? String.format("%,.0f", amount) : String.format("%,.2f", amount);
        return n + " ج.م";
    }

    private static String initial(String name) {
        return notBlank(name) ? name.trim().substring(0, 1) : "؟";
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static double nz(Double d) {
        return d == null ? 0 : d;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
