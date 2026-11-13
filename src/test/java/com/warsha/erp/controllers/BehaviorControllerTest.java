package com.warsha.erp.controllers;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.warsha.erp.dtos.BehaviorDtos.*;
import com.warsha.erp.exceptions.GlobalExceptionHandler;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.services.BehaviorReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP contract of /analytics/behavior. */
class BehaviorControllerTest {

    private final BehaviorReportService service = mock(BehaviorReportService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new BehaviorController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @Test
    void passesTheRangeAndIdleTime() throws Exception {
        Range range = new Range(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 31);
        AbandonedCart cart = new AbandonedCart("visitor-0001", 44L, "Mona", "010", null, 2, 200,
                LocalDateTime.of(2026, 10, 31, 9, 15), "Facebook", "MOBILE",
                List.of(new CartItem(1, null, "Mug", null, 2)));
        when(service.abandonedCarts(any(), any(), any()))
                .thenReturn(new AbandonedCarts(range, 120, 1, 200, 3, 0.25, List.of(cart)));

        mvc.perform(get("/analytics/behavior/abandoned-carts")
                        .param("from", "2026-10-01").param("to", "2026-10-31").param("idleMinutes", "120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range.from").value("2026-10-01"))
                .andExpect(jsonPath("$.carts[0].lastActivity").value("2026-10-31T09:15:00"))
                .andExpect(jsonPath("$.carts[0].items[0].name").value("Mug"));
        verify(service).abandonedCarts(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 120);
    }

    @Test
    void badRangeIsA400() throws Exception {
        when(service.overview(any(), any())).thenThrow(new ValidationException("from", "The start date must be on or before the end date."));
        mvc.perform(get("/analytics/behavior/overview").param("from", "2026-11-02").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("from"));
    }

    @Test
    void badDateIsA400() throws Exception {
        mvc.perform(get("/analytics/behavior/products").param("from", "yesterday"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
