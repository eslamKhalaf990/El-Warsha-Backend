package com.warsha.erp.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Body of POST /vendors and PUT /vendors/{id}. Only the name is required. */
@Getter
@Setter
public class VendorRequest {

    @NotBlank(message = "Enter the vendor name.")
    @Size(max = 200, message = "Vendor name can be at most 200 characters.")
    private String name;

    /** Mobile, landline or international: digits with optional +, spaces, dashes and brackets. */
    @Pattern(regexp = "^$|^\\+?[0-9(][0-9 ()\\-]{5,19}$", message = "Enter a valid phone number.")
    @Size(max = 50, message = "Phone can be at most 50 characters.")
    private String phone;

    @Email(message = "Enter a valid email address.")
    @Size(max = 255, message = "Email can be at most 255 characters.")
    private String email;

    @Size(max = 300, message = "Address can be at most 300 characters.")
    private String address;

    @Size(max = 50, message = "Tax number can be at most 50 characters.")
    private String taxNumber;

    @Size(max = 100, message = "Contact person can be at most 100 characters.")
    private String contactPerson;

    @Size(max = 2000, message = "Notes can be at most 2000 characters.")
    private String notes;
}
