package com.warsha.erp.config;

import com.warsha.erp.entities.Customer;
import com.warsha.erp.exceptions.ApiErrorController;
import com.warsha.erp.exceptions.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.StubController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class, RestAuthErrorHandlers.class,
        RequestIdFilter.class, GlobalExceptionHandler.class, ApiErrorController.class,
        SecurityConfigTest.StubController.class})
@TestPropertySource(properties = "jwt.secret=test-secret-test-secret-test-secret-0123456789")
class SecurityConfigTest {

    /** Stand-ins for real endpoints so only the access rules are under test. */
    @RestController
    static class StubController {
        @GetMapping("/products") String products() { return "ok"; }
        @DeleteMapping("/products") String deleteProducts() { return "ok"; }
        @GetMapping("/customers") String customers() { return "ok"; }
        @GetMapping("/orders") String orders() { return "ok"; }
        @PostMapping("/orders/placeOrder") String placeOrder() { return "ok"; }
        @GetMapping("/orders/customer") String customerOrders() { return "ok"; }
        @PostMapping("/auth/register") String register() { return "ok"; }
        @DeleteMapping("/api/bank/resetTransactions") String reset() { return "ok"; }
        @GetMapping("/invoice/pdf/{id}") String invoice(@PathVariable Long id) { return "ok"; }
        @PostMapping("/feedbacks") String feedback() { return "ok"; }
    }

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtil jwtUtil;

    @MockBean private CustomUserDetailsService adminService;
    @MockBean private CustomerDetailsService customerService;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        when(adminService.loadUserByUsername("admin")).thenReturn(
                new User("admin", "x", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(adminService.loadUserByUsername("shopper@mail.com"))
                .thenThrow(new UsernameNotFoundException("not staff"));

        Customer customer = new Customer();
        customer.setEmail("shopper@mail.com");
        when(customerService.loadUserByUsername(anyString())).thenReturn(new CustomerUserDetails(customer));

        adminToken = "Bearer " + jwtUtil.generateToken("admin", JwtUtil.ROLE_ADMIN);
        customerToken = "Bearer " + jwtUtil.generateToken("shopper@mail.com", JwtUtil.ROLE_CUSTOMER);
    }

    @Test
    void catalogIsPublicToRead() throws Exception {
        mvc.perform(get("/products")).andExpect(status().isOk());
    }

    @Test
    void anonymousCannotWipeProducts() throws Exception {
        mvc.perform(delete("/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void customerListIsNoLongerPublic() throws Exception {
        mvc.perform(get("/customers")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotRegisterAdmins() throws Exception {
        mvc.perform(post("/auth/register")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerTokenCannotReachErp() throws Exception {
        mvc.perform(get("/orders").header("Authorization", customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(delete("/api/bank/resetTransactions").header("Authorization", customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCanUseStorefrontOrderEndpoints() throws Exception {
        mvc.perform(post("/orders/placeOrder").header("Authorization", customerToken)).andExpect(status().isOk());
        mvc.perform(get("/orders/customer").header("Authorization", customerToken)).andExpect(status().isOk());
    }

    @Test
    void adminCanReachErpButNotCustomerOnlyRoutes() throws Exception {
        mvc.perform(get("/orders").header("Authorization", adminToken)).andExpect(status().isOk());
        mvc.perform(get("/invoice/pdf/1").header("Authorization", adminToken)).andExpect(status().isOk());
        mvc.perform(get("/orders/customer").header("Authorization", adminToken)).andExpect(status().isForbidden());
    }

    @Test
    void invoicesNeedAToken() throws Exception {
        mvc.perform(get("/invoice/pdf/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIsTreatedAsAnonymous() throws Exception {
        mvc.perform(get("/orders").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestsMayStillLeaveFeedback() throws Exception {
        mvc.perform(post("/feedbacks")).andExpect(status().isOk());
    }
}
