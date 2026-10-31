package com.warsha.erp.controllers;

import com.warsha.erp.dtos.CategoryDto;
import com.warsha.erp.exceptions.ConflictException;
import com.warsha.erp.exceptions.GlobalExceptionHandler;
import com.warsha.erp.services.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP contract of /category: the store reads it, the ERP edits it. */
class CategoryControllerTest {

    private final CategoryService service = mock(CategoryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new CategoryController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listKeepsTheFieldsTheStoreReads() throws Exception {
        when(service.getAllCategories()).thenReturn(List.of(new CategoryDto(1L, "Rings", "Silver", "/images/r.png", 4)));

        mvc.perform(get("/category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").value(1))
                .andExpect(jsonPath("$[0].name").value("Rings"))
                .andExpect(jsonPath("$[0].description").value("Silver"))
                .andExpect(jsonPath("$[0].imageUrl").value("/images/r.png"))
                .andExpect(jsonPath("$[0].productCount").value(4));
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create(any())).thenReturn(new CategoryDto(5L, "Rings", null, null, 0));

        mvc.perform(post("/category").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rings\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(5));
    }

    @Test
    void blankNameIsAFieldError() throws Exception {
        mvc.perform(post("/category").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").value("Enter the category name."));
        verifyNoInteractions(service);
    }

    @Test
    void duplicateNameIs409WithTheField() throws Exception {
        when(service.update(eq(2L), any())).thenThrow(new ConflictException("name", "A category named \"Rings\" already exists."));

        mvc.perform(put("/category/2").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rings\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details[0].field").value("name"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/category/3")).andExpect(status().isNoContent());
        verify(service).delete(3L);
    }
}
