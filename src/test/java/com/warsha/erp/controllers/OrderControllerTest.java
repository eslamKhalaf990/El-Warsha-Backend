package com.warsha.erp.controllers;

import com.warsha.erp.config.CustomerUserDetails;
import com.warsha.erp.config.OrderStatusParamConverter;
import com.warsha.erp.dtos.OrderResponse;
import com.warsha.erp.entities.Customer;
import com.warsha.erp.entities.Order;
import com.warsha.erp.entities.OrderStatus;
import com.warsha.erp.exceptions.GlobalExceptionHandler;
import com.warsha.erp.services.OrderService;
import com.warsha.erp.services.StoreTrackingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP contract of the orders API: paths, status codes and validation messages. */
class OrderControllerTest {

    private final OrderService service = mock(OrderService.class);
    private final StoreTrackingService tracking = mock(StoreTrackingService.class);
    private MockMvc mvc;

    private static final String VALID_ORDER = """
            {"customerId": 7, "paymentMethod": "Cash", "orderSource": "Instagram",
             "items": [{"productId": 1, "quantity": 2}]}
            """;

    @BeforeEach
    void setUp() {
        DefaultFormattingConversionService conversion = new DefaultFormattingConversionService();
        conversion.addConverter(new OrderStatusParamConverter());
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mvc = MockMvcBuilders.standaloneSetup(new OrderController(service, tracking))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setConversionService(conversion)
                .setValidator(validator)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        Order saved = new Order();
        saved.setId(12L);
        OrderResponse response = new OrderResponse();
        response.setOrderId(12L);
        when(service.createOrder(any())).thenReturn(saved);
        when(service.placeOrder(any(), anyLong(), any())).thenReturn(saved);
        when(service.toResponse(any())).thenReturn(response);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/orders/12")))
                .andExpect(jsonPath("$.orderId").value(12));
    }

    @Test
    void createListsEveryInvalidField() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"downPayment\": -5, \"items\": [{\"quantity\": 0}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[*].field", containsInAnyOrder(
                        "paymentMethod", "orderSource", "downPayment",
                        "items[0].productId", "items[0].quantity")))
                .andExpect(jsonPath("$.details[?(@.field == 'items[0].quantity')].message")
                        .value("Quantity must be at least 1."));
        verifyNoInteractions(service);
    }

    @Test
    void listAcceptsStatusInAnyCase() throws Exception {
        mvc.perform(get("/orders").param("from", "2026-10-01").param("to", "2026-10-31").param("status", "shipped"))
                .andExpect(status().isOk());
        verify(service).listOrders(java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 10, 31), OrderStatus.SHIPPED);
    }

    @Test
    void listRejectsUnknownStatus() throws Exception {
        mvc.perform(get("/orders").param("status", "Delivered"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("status"));
    }

    @Test
    void changeStatusPassesTheBankAccount() throws Exception {
        mvc.perform(patch("/orders/12/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"Completed\", \"bankAccountId\": 2}"))
                .andExpect(status().isOk());
        verify(service).updateOrderStatus(12L, OrderStatus.COMPLETED, 2L);
    }

    @Test
    void changeStatusRequiresAStatus() throws Exception {
        mvc.perform(patch("/orders/12/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].message").value("Choose the new status for the order."));
    }

    @Test
    void cancelAndDelete() throws Exception {
        mvc.perform(post("/orders/12/cancel")).andExpect(status().isOk());
        verify(service).cancelOrder(12L);

        mvc.perform(delete("/orders/12")).andExpect(status().isNoContent());
        verify(service).deleteOrder(12L);
    }

    private void loginAsCustomer(long id) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setEmail("shopper@mail.com");
        CustomerUserDetails principal = new CustomerUserDetails(customer);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void checkoutUsesTheLoggedInCustomer() throws Exception {
        loginAsCustomer(44);
        MockMultipartFile order = new MockMultipartFile("order", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"paymentMethod\": \"InstaPay\", \"orderSource\": \"Website\", \"items\": [{\"productId\": 1, \"quantity\": 1}]}".getBytes());

        for (String path : List.of("/orders/checkout", "/orders/placeOrder")) {
            mvc.perform(multipart(path).file(order)).andExpect(status().isCreated());
        }
        verify(service, times(2)).placeOrder(any(), eq(44L), any());
    }

    @Test
    void checkoutPassesTheTrackingHeadersAndGrandTotal() throws Exception {
        loginAsCustomer(44);
        OrderResponse response = new OrderResponse();
        response.setOrderId(12L);
        response.setGrandTotal(new java.math.BigDecimal("350.00"));
        when(service.toResponse(any())).thenReturn(response);
        MockMultipartFile order = new MockMultipartFile("order", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"paymentMethod\": \"InstaPay\", \"orderSource\": \"Website\", \"items\": [{\"productId\": 1, \"quantity\": 1}]}".getBytes());

        mvc.perform(multipart("/orders/checkout").file(order)
                        .header("X-Visitor-Id", "visitor-0001").header("X-Session-Id", "session-0001"))
                .andExpect(status().isCreated());
        verify(tracking).recordOrder("visitor-0001", "session-0001", 44L, 12L, new java.math.BigDecimal("350.00"));
    }

    @Test
    void checkoutSucceedsWhenTrackingFails() throws Exception {
        loginAsCustomer(44);
        doThrow(new RuntimeException("db down")).when(tracking).recordOrder(any(), any(), any(), any(), any());
        MockMultipartFile order = new MockMultipartFile("order", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"paymentMethod\": \"InstaPay\", \"orderSource\": \"Website\", \"items\": [{\"productId\": 1, \"quantity\": 1}]}".getBytes());

        mvc.perform(multipart("/orders/checkout").file(order))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(12));
    }

    @Test
    void checkoutValidatesTheOrderPart() throws Exception {
        loginAsCustomer(44);
        MockMultipartFile order = new MockMultipartFile("order", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"paymentMethod\": \"InstaPay\", \"orderSource\": \"Website\", \"items\": []}".getBytes());

        mvc.perform(multipart("/orders/checkout").file(order))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].message").value("Add at least one product to the order."));
        verify(service, never()).placeOrder(any(), anyLong(), any());
    }

    @Test
    void mineAndItsOldPath() throws Exception {
        loginAsCustomer(44);
        mvc.perform(get("/orders/mine")).andExpect(status().isOk());
        mvc.perform(get("/orders/customer")).andExpect(status().isOk());
        verify(service, times(2)).getOrdersByCustomer(44L);
    }
}
