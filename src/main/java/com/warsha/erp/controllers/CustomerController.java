package com.warsha.erp.controllers;

import com.warsha.erp.config.JwtUtil;
import com.warsha.erp.dtos.CustomerCountByGovernorate;
import com.warsha.erp.dtos.CustomerInfoDTO;
import com.warsha.erp.dtos.CustomerRequest;
import com.warsha.erp.entities.Customer;
import com.warsha.erp.services.CustomerService;
import com.warsha.erp.services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @Autowired private JwtUtil jwtUtil;

    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private EmailService emailService;

    // Formatter for consistent log timestamps
    private final DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private String getTimestamp() {
        return LocalDateTime.now().format(logFormat);
    }

    /** Every customer, newest first. Passwords and verification codes are never included. */
    @GetMapping
    public List<CustomerInfoDTO> getAll() {
        return customerService.getAllCustomerDtos();
    }

    @GetMapping("/countsByGovernorate")
    public List<CustomerCountByGovernorate> getCountsByGovernorate() {
        return customerService.getCustomerCountsByGovernorate();
    }

    @GetMapping("/{id}")
    public CustomerInfoDTO getById(@PathVariable Long id) {
        return customerService.getCustomerInfo(id);
    }

    @GetMapping("/{id}/info")
    public CustomerInfoDTO getCustomerInfo(@PathVariable Long id) {
        return customerService.getCustomerInfo(id);
    }

    /** A customer entered by staff in the ERP. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerInfoDTO create(@Valid @RequestBody CustomerRequest request) {
        return customerService.createFromErp(request);
    }

    /**
     * Store sign-up with phone + password (email optional). The account is active at once, with no
     * OTP: the store logs in right after with the same phone and password.
     * Errors are the usual ApiError JSON: 400 VALIDATION_FAILED or 409 CONFLICT, with the field
     * ("phone", "email", "password", "governorate", ...) in fieldErrors.
     */
    @PostMapping("/customerSignUp")
    @ResponseStatus(HttpStatus.CREATED)
    public String customerSignUp(@RequestBody Customer customer) {
        Customer saved = customerService.registerFromStore(customer);
        System.out.println("[" + getTimestamp() + "] SUCCESS: Customer sign-up for #" + saved.getId());
        return "Registration successful.";
    }

    /** Staff set a customer's store password (customers without an email can't reset it themselves). */
    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPassword(@PathVariable Long id, @RequestBody SetPasswordRequest request) {
        customerService.setPasswordFromErp(id, request.password());
    }

    record SetPasswordRequest(String password) {
    }

    @PutMapping("/{id}")
    public CustomerInfoDTO update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.updateFromErp(id, request);
    }

    /** Refused (409) while the customer has orders, reviews or account history. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        customerService.deleteCustomer(id);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<String> resendOtp(@RequestParam String email) {
        System.out.println("[" + getTimestamp() + "] INFO: Request to resend OTP for: " + email);
        try {
            String otp = emailService.sendOtpEmail(email);
            if (otp != null) {
                // Use saveOtp because this is for the initial verification flow usually
                customerService.saveOtp(email, otp);
                return ResponseEntity.ok("OTP resent successfully.");
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send OTP.");
            }
        } catch (Exception e) {
            System.out.println("[" + getTimestamp() + "] ERROR: Resending OTP failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Could not send the verification code.");
        }
    }
}