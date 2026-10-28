package com.warsha.erp.controllers;

import com.warsha.erp.entities.Vendor;
import com.warsha.erp.exceptions.GlobalExceptionHandler;
import com.warsha.erp.services.VendorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VendorControllerTest {

    private final VendorService service = mock(VendorService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new VendorController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listHidesArchivedByDefault() throws Exception {
        when(service.getAllVendors(false)).thenReturn(List.of());
        mvc.perform(get("/vendors")).andExpect(status().isOk());
        verify(service).getAllVendors(false);

        mvc.perform(get("/vendors?includeArchived=true")).andExpect(status().isOk());
        verify(service).getAllVendors(true);
    }

    @Test
    void createReturns201() throws Exception {
        Vendor v = new Vendor();
        v.setVendorId(4L);
        v.setName("Silver Co");
        when(service.create(any())).thenReturn(v);

        mvc.perform(post("/vendors").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Silver Co\",\"phone\":\"+20 100 123-4567\",\"email\":\"\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vendorId").value(4));
    }

    @Test
    void invalidFieldsAreReportedTogether() throws Exception {
        mvc.perform(post("/vendors").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"phone\":\"call me\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[*].field", containsInAnyOrder("name", "phone", "email")));
        verifyNoInteractions(service);
    }

    @Test
    void deleteArchivesAndReturns204() throws Exception {
        mvc.perform(delete("/vendors/4")).andExpect(status().isNoContent());
        verify(service).archive(4L);
    }
}
