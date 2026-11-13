package com.warsha.erp.services;

import java.util.Locale;

/**
 * Turns the free-text values stored on orders and customers into one label per
 * real-world thing, so "E-Commerce", "ecommerce" and "Website" count as one channel
 * and "القاهرة" / "القاهره " as one governorate.
 */
public final class AnalyticsLabels {

    public static final String UNKNOWN = "Unknown";

    private AnalyticsLabels() {
    }

    /** The sales channel an order source belongs to. */
    public static String channel(String source) {
        String s = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("-")) return UNKNOWN;
        if (s.contains("commerce") || s.contains("website") || s.contains("store") || s.equals("web")) return "Online store";
        if (s.contains("tiktok") || s.contains("tik tok")) return "TikTok";
        if (s.contains("insta")) return "Instagram";
        if (s.contains("facebook") || s.equals("fb")) return "Facebook";
        if (s.contains("whatsapp")) return "WhatsApp";
        if (s.replace(" ", "").replace("-", "").equals("walkin")) return "Walk-in";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** How a down payment was made. */
    public static String paymentMethod(String method) {
        String m = method == null ? "" : method.trim().toLowerCase(Locale.ROOT);
        return switch (m) {
            case "", "-" -> "Not recorded";
            case "cash" -> "Cash";
            case "instapay" -> "InstaPay";
            case "vodafone cash", "vodafone" -> "Vodafone Cash";
            case "card" -> "Card";
            case "bank transfer" -> "Bank transfer";
            default -> Character.toUpperCase(m.charAt(0)) + m.substring(1);
        };
    }

    /** A governorate as written, trimmed; blank becomes {@link #UNKNOWN}. */
    public static String governorate(String name) {
        String g = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        return g.isEmpty() ? UNKNOWN : g;
    }

    /**
     * The key two spellings of one governorate share: alef forms, taa marbuta / haa,
     * alef maqsura / yaa and spacing are folded together.
     */
    public static String governorateKey(String name) {
        return governorate(name)
                .replaceAll("[أإآ]", "ا")
                .replace('ة', 'ه')
                .replace('ى', 'ي')
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
    }
}
