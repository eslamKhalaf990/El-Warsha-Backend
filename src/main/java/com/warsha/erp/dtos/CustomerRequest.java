package com.warsha.erp.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Body of POST /customers and PUT /customers/{id} (ERP admin). Phones are
 * normalised and checked in {@code CustomerService}; the governorate must have
 * a shipping zone.
 */
@Getter
@Setter
public class CustomerRequest {

    @NotBlank(message = "Enter the customer's name.")
    @Size(max = 100, message = "Name can be at most 100 characters.")
    private String fullName;

    @NotBlank(message = "Enter the customer's mobile number.")
    @Size(max = 20, message = "Phone can be at most 20 characters.")
    private String phone;

    @Size(max = 20, message = "Second phone can be at most 20 characters.")
    private String secondaryPhone;

    @NotBlank(message = "Choose the governorate.")
    @Size(max = 100, message = "Governorate can be at most 100 characters.")
    private String governorate;

    @Size(max = 60, message = "City can be at most 60 characters.")
    private String city;

    @Size(max = 255, message = "Address can be at most 255 characters.")
    private String address;

    @Email(message = "Enter a valid email address.")
    @Size(max = 255, message = "Email can be at most 255 characters.")
    private String email;
}
