package com.warsha.erp.exceptions;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @RestController
    @RequestMapping("/test")
    static class TestController {
        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("Order", 12);
        }

        @GetMapping("/stock")
        void stock() {
            throw new InsufficientStockException("Not enough stock for 'Bracelet'.");
        }

        @GetMapping("/rule")
        void rule() {
            throw new BusinessRuleException("A cancelled order cannot be completed.");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("db password is hunter2");
        }

        @GetMapping("/typed/{id}")
        void typed(@PathVariable Long id) {
        }

        @PostMapping("/body")
        void body(@Valid @RequestBody Item item) {
        }

        @PostMapping("/status")
        void status(@RequestBody com.warsha.erp.dtos.UpdateOrderStatus body) {
        }
    }

    record Item(@NotBlank(message = "name is required") String name,
                @Positive(message = "quantity must be greater than 0") int quantity) {
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFoundUsesFriendlyMessage() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order #12 was not found."))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/test/not-found"));
    }

    @Test
    void insufficientStockIsConflict() throws Exception {
        mvc.perform(get("/test/stock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void businessRuleIsUnprocessable() throws Exception {
        mvc.perform(get("/test/rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("A cancelled order cannot be completed."));
    }

    @Test
    void unexpectedErrorsHideInternals() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }

    @Test
    void wrongPathVariableTypeIsValidationError() throws Exception {
        mvc.perform(get("/test/typed/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("id"));
    }

    @Test
    void invalidBodyListsEveryField() throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.length()").value(2));
    }

    @Test
    void invalidEnumValueKeepsFriendlyMessage() throws Exception {
        mvc.perform(post("/test/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"Delivered\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("status"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("'Delivered' is not a valid order status")));
    }

    @Test
    void malformedJsonIsReadable() throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
