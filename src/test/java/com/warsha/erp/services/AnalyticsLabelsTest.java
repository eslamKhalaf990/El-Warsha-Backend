package com.warsha.erp.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsLabelsTest {

    @Test
    void channelsFoldTheSpellingsFoundInTheData() {
        assertThat(AnalyticsLabels.channel("E-Commerce")).isEqualTo("Online store");
        assertThat(AnalyticsLabels.channel("ecommerce")).isEqualTo("Online store");
        assertThat(AnalyticsLabels.channel("Website")).isEqualTo("Online store");
        assertThat(AnalyticsLabels.channel(" tiktok ")).isEqualTo("TikTok");
        assertThat(AnalyticsLabels.channel("instagram")).isEqualTo("Instagram");
        assertThat(AnalyticsLabels.channel("walk-in")).isEqualTo("Walk-in");
        assertThat(AnalyticsLabels.channel("")).isEqualTo("Unknown");
        assertThat(AnalyticsLabels.channel(null)).isEqualTo("Unknown");
        assertThat(AnalyticsLabels.channel("referral")).isEqualTo("Referral");
    }

    @Test
    void paymentMethodsGetReadableNames() {
        assertThat(AnalyticsLabels.paymentMethod("vodafone cash")).isEqualTo("Vodafone Cash");
        assertThat(AnalyticsLabels.paymentMethod("Cash")).isEqualTo("Cash");
        assertThat(AnalyticsLabels.paymentMethod("")).isEqualTo("Not recorded");
    }

    @Test
    void governorateSpellingsShareAKey() {
        assertThat(AnalyticsLabels.governorateKey("القاهرة")).isEqualTo(AnalyticsLabels.governorateKey("القاهره "));
        assertThat(AnalyticsLabels.governorateKey("الإسكندرية ")).isEqualTo(AnalyticsLabels.governorateKey("الاسكندريه"));
        assertThat(AnalyticsLabels.governorateKey("الجيزة")).isNotEqualTo(AnalyticsLabels.governorateKey("القاهرة"));
        assertThat(AnalyticsLabels.governorate("  ")).isEqualTo("Unknown");
    }
}
