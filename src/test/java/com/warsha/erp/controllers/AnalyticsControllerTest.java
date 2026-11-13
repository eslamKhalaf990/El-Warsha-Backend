package com.warsha.erp.controllers;

import com.warsha.erp.exceptions.GlobalExceptionHandler;
import com.warsha.erp.repository.AnalyticsRepository;
import com.warsha.erp.services.AnalyticsService;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AnalyticsControllerTest {

    private final AnalyticsRepository repository = mock(AnalyticsRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AnalyticsController(new AnalyticsService(repository)))
                .setControllerAdvice(new GlobalExceptionHandler())
                // Dates as yyyy-MM-dd, the way Spring Boot's mapper writes them
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @Test
    void overviewQueriesTheRangeAndThePreviousOneAtOnce() throws Exception {
        when(repository.orderFacts(any(), any())).thenReturn(List.of());

        mvc.perform(get("/analytics/overview?from=2026-01-01&to=2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.days").value(31))
                .andExpect(jsonPath("$.period.previousFrom").value("2025-12-01"))
                .andExpect(jsonPath("$.current.orders").value(0))
                .andExpect(jsonPath("$.previous.orders").value(0));

        verify(repository).orderFacts(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 1));
    }

    @Test
    void allTimeStartsAtTheFirstOrderWithNoComparison() throws Exception {
        when(repository.firstOrderDate()).thenReturn(java.sql.Date.valueOf("2025-10-02"));
        when(repository.orderFacts(any(), any())).thenReturn(List.of());

        mvc.perform(get("/analytics/overview?to=2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.from").value("2025-10-02"))
                .andExpect(jsonPath("$.previous").isEmpty());
    }

    @Test
    void startAfterEndIsAFieldError() throws Exception {
        mvc.perform(get("/analytics/channels?from=2026-02-01&to=2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("from"));
        verifyNoInteractions(repository);
    }

    @Test
    void unknownGranularityIsRejected() throws Exception {
        mvc.perform(get("/analytics/trend?from=2026-01-01&to=2026-01-31&granularity=hour"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("granularity"));
    }

    @Test
    void unreadableDateIsABadRequest() throws Exception {
        mvc.perform(get("/analytics/cash?from=yesterday")).andExpect(status().isBadRequest());
    }
}
