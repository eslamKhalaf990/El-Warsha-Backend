package com.warsha.erp.controllers;

import com.warsha.erp.config.JwtUtil;
import com.warsha.erp.dtos.CustomerCountByGovernorate;
import com.warsha.erp.dtos.CustomerInfoDTO;
import com.warsha.erp.entities.Customer;
import com.warsha.erp.services.CustomerService;
import com.warsha.erp.services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
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

    @GetMapping
    public List<Customer> getAll() {
        System.out.println("[" + getTimestamp() + "] INFO: Request to fetch all customers");
        return customerService.getAllCustomers();
    }

    @GetMapping("/countsByGovernorate")
    public List<CustomerCountByGovernorate> getCountsByGovernorate() {
        System.out.println("[" + getTimestamp() + "] INFO: Request for customer counts by governorate");
        return customerService.getCustomerCountsByGovernorate();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getById(@PathVariable Long id) {
        System.out.println("[" + getTimestamp() + "] INFO: Request to fetch customer ID: " + id);
        return ResponseEntity.ok(customerService.getCustomerByID(id));
    }

    @GetMapping("/{id}/info")
    public ResponseEntity<CustomerInfoDTO> getCustomerInfo(@PathVariable Long id) {
        System.out.println("[" + getTimestamp() + "] INFO: Request to fetch customer info ID: " + id);
        return ResponseEntity.ok(customerService.getCustomerInfo(id));
    }

    @PostMapping
    public ResponseEntity<Customer> create(@RequestBody Customer customer) {
        System.out.println("[" + getTimestamp() + "] INFO: Request to create new customer (ERP Admin): " + customer.getFullName());
        return new ResponseEntity<>(customerService.createCustomer(customer), HttpStatus.CREATED);
    }

    @PostMapping("/customerSignUp")
    public ResponseEntity<?> customerSignUp(@RequestBody Customer customer) {
        System.out.println("[" + getTimestamp() + "] INFO: New customer self-signup attempt: " + customer.getEmail());

        // Messages here are plain text read by the storefronts; never include exception details
        if (customerService.emailExists(customer.getEmail())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Registration failed: This email is already registered.");
        }

        try {
            Customer savedCustomer = customerService.createCustomer(customer);
            System.out.println("[" + getTimestamp() + "] SUCCESS: Customer sign-up successful for " + savedCustomer.getEmail() + ". Pending verification.");

            // Standard Flow: Do NOT return a valid login token for a pending account.
            // Return a message so the frontend knows to route them to the OTP verification screen.
            return ResponseEntity.status(HttpStatus.CREATED).body("Registration successful. Please check your email for the verification code.");
        } catch (Exception e) {
            System.out.println("[" + getTimestamp() + "] ERROR: Customer sign-up failed: " + e.getMessage());
            String reason = e instanceof IllegalArgumentException && String.valueOf(e.getMessage()).contains("governorate")
                    ? "Invalid governorate."
                    : "Please check your details and try again.";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Registration failed: " + reason);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Customer> update(@PathVariable Long id, @RequestBody Customer customer) {
        System.out.println("[" + getTimestamp() + "] INFO: Request to update customer ID: " + id);
        return ResponseEntity.ok(customerService.updateCustomer(id, customer));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        System.out.println("[" + getTimestamp() + "] WARN: Request to delete customer ID: " + id);
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll() {
        System.out.println("[" + getTimestamp() + "] ALERT: Request to DELETE ALL customers received!");
        customerService.deleteAll();
        return ResponseEntity.noContent().build();
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