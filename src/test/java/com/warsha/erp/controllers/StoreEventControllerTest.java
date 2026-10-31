package com.warsha.erp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.warsha.erp.config.CustomerUserDetails;
import com.warsha.erp.dtos.TrackEventsRequest;
import com.warsha.erp.entities.Customer;
import com.warsha.erp.entities.StoreEventType;
import com.warsha.erp.exceptions.GlobalExceptionHandler;
import com.warsha.erp.services.StoreTrackingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of POST /events. */
class StoreEventControllerTest {

    private final StoreTrackingService service = mock(StoreTrackingService.class);
    private MockMvc mvc;

    private static final String VALID = """
            {"visitorId": "3f1c2b9e-1111-4a2b-9c3d-000000000001",
             "sessionId": "3f1c2b9e-2222-4a2b-9c3d-000000000002",
             "deviceType": "MOBILE",
             "events": [{"type": "PRODUCT_VIEW", "productId": 5, "occurredAt": "2026-10-31T09:59:30Z"}],
             "cart": {"items": [{"productId": 5, "quantity": 2}]}}
            """;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

        mvc = MockMvcBuilders.standaloneSetup(new StoreEventController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptsABatchWith204() throws Exception {
        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(VALID)
                        .header("User-Agent", "Mozilla/5.0").header("CF-Connecting-IP", "41.33.1.2"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<TrackEventsRequest> captor = ArgumentCaptor.forClass(TrackEventsRequest.class);
        verify(service).track(captor.capture(), isNull(), eq("Mozilla/5.0"), eq("41.33.1.2"));
        TrackEventsRequest r = captor.getValue();
        assertThat(r.getEvents().get(0).getType()).isEqualTo(StoreEventType.PRODUCT_VIEW);
        assertThat(r.getEvents().get(0).getOccurredAt()).isEqualTo(Instant.parse("2026-10-31T09:59:30Z"));
        assertThat(r.getCart().getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void linksTheLoggedInCustomer() throws Exception {
        Customer customer = new Customer();
        customer.setId(44L);
        CustomerUserDetails principal = new CustomerUserDetails(customer);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isNoContent());
        verify(service).track(any(), eq(44L), any(), any());
    }

    @Test
    void usesTheFirstForwardedIp() throws Exception {
        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(VALID)
                        .header("X-Forwarded-For", "41.33.1.2, 10.0.0.1"))
                .andExpect(status().isNoContent());
        verify(service).track(any(), any(), any(), eq("41.33.1.2"));
    }

    @Test
    void rejectsABadVisitorId() throws Exception {
        String body = VALID.replace("3f1c2b9e-1111-4a2b-9c3d-000000000001", "<script>");
        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("visitorId"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsTooManyEvents() throws Exception {
        String one = "{\"type\": \"PAGE_VIEW\"}";
        String events = String.join(",", java.util.Collections.nCopies(TrackEventsRequest.MAX_EVENTS + 1, one));
        String body = "{\"visitorId\": \"visitor-0001\", \"sessionId\": \"session-0001\", \"events\": [" + events + "]}";
        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].message").value("Send at most 50 events at a time."));
    }

    @Test
    void rejectsAnUnknownEventType() throws Exception {
        String body = "{\"visitorId\": \"visitor-0001\", \"sessionId\": \"session-0001\", \"events\": [{\"type\": \"HACK\"}]}";
        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
