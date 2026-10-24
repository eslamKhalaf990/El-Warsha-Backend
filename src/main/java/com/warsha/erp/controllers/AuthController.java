package com.warsha.erp.controllers;

import com.warsha.erp.config.JwtUtil;
import com.warsha.erp.entities.Customer;
import com.warsha.erp.entities.User;
import com.warsha.erp.exceptions.AppException;
import com.warsha.erp.exceptions.ErrorCode;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.services.CustomerService;
import com.warsha.erp.services.EmailService;
import com.warsha.erp.services.UserService;
import com.warsha.erp.services.UserActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired private AuthenticationManager authManager;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private EmailService emailService;
    @Autowired private UserService userService;
    @Autowired private CustomerService customerService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserActivityLogService userActivityLogService;

    // Formatter for consistent log timestamps
    private final DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private String getTimestamp() {
        return LocalDateTime.now().format(logFormat);
    }

    @PostMapping("/login")
    public ResponseEntity<ERPLogin> login(@RequestBody LoginRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ValidationException("Username and password are required.");
        }
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException | UsernameNotFoundException e) {
            log.info("ERP login failed for {}", request.getUsername());
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        log.info("ERP login succeeded for {}", request.getUsername());
        return ResponseEntity.ok(new ERPLogin(jwtUtil.generateToken(request.getUsername(), JwtUtil.ROLE_ADMIN)));
    }

    @PostMapping("/customerLogin")
    public ResponseEntity<?> customerLogin(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        System.out.println("[" + getTimestamp() + "] INFO: Customer Login attempt for: " + loginRequest.getUsername());

        try {
            Customer customer = customerService.findByEmail(loginRequest.getUsername());
            
            if (customer != null && passwordEncoder.matches(loginRequest.getPassword(), customer.getPassword())) {
                
                if ("Active".equals(customer.getStatus())) {
                    String token = jwtUtil.generateToken(customer.getEmail(), JwtUtil.ROLE_CUSTOMER);
                    System.out.println("[" + getTimestamp() + "] SUCCESS: Customer authenticated: " + customer.getEmail());

                    userActivityLogService.logCustomerLogin(customer.getId(), request);

                    return ResponseEntity.ok(new CustomerLogin(
                            token,
                            customer.getAddress(),
                            customer.getFullName(),
                            customer.getPhone(),
                            customer.getEmail(),
                            customer.getGovernorate())
                    );
                } else if ("Pending".equals(customer.getStatus())) {
                    System.out.println("[" + getTimestamp() + "] INFO: Customer account pending verification: " + customer.getEmail());
                    
                    String otp = emailService.sendOtpEmail(customer.getEmail());
                    customerService.saveOtp(customer.getEmail(), otp);
                    
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Account needs verification. OTP sent to email.");
                } else {
                     System.out.println("[" + getTimestamp() + "] WARN: Customer account status: " + customer.getStatus());
                     return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Account is not active.");
                }
            }

            System.out.println("[" + getTimestamp() + "] WARN: Customer Login failed - Wrong password or user null for: " + loginRequest.getUsername());
        } catch (Exception e) {
            System.out.println("[" + getTimestamp() + "] ERROR: Customer Login service error: " + e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid customer credentials");
    }

    @PostMapping("/verifyCode")
    public ResponseEntity<?> verifyCode(@RequestBody VerifyRequest request) {
        System.out.println("[" + getTimestamp() + "] INFO: Verifying code for: " + request.email());
        
        boolean isVerified = customerService.verifyOtp(request.email(), request.otp());
        
        if (isVerified) {
             return ResponseEntity.ok("Account verified successfully");
        } else {
             return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired OTP");
        }
    }

    @PostMapping("/changePassword")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        System.out.println("[" + getTimestamp() + "] INFO: Change password attempt for: " + request.email());

        boolean isChanged = customerService.changeCustomerPassword(request.email(), request.oldPassword(), request.newPassword());

        if (isChanged) {
            return ResponseEntity.ok("Password changed successfully");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid old password or user not found");
        }
    }

    @PostMapping("/forgotPassword")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        System.out.println("[" + getTimestamp() + "] INFO: Forgot password attempt for: " + request.email());

        boolean isSent = customerService.forgotPassword(request.email());

        if (isSent) {
            return ResponseEntity.ok("OTP sent to email");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("User not found or failed to send email");
        }
    }

    @PostMapping("/resetPassword")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        System.out.println("[" + getTimestamp() + "] INFO: Reset password attempt for: " + request.email());

        boolean isReset = customerService.resetCustomerPassword(request.email(), request.otp(), request.newPassword());

        if (isReset) {
            return ResponseEntity.ok("Password reset successfully");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired OTP, or user not found");
        }
    }

    @PostMapping("/register")
    public User register(@RequestBody User user) {
        System.out.println("[" + getTimestamp() + "] INFO: Registering new ERP User: " + user.getUsername());
        User savedUser = userService.registerUser(user);
        System.out.println("[" + getTimestamp() + "] SUCCESS: ERP User registered with Role: " + savedUser.getRole());
        return savedUser;
    }

    @Getter
    static class LoginRequest {
        private String username;
        private String password;
    }

    record CustomerLogin(String token, String address, String name, String phone, String email, String governorate) {
    }

    record ERPLogin(String token) {
    }

    record VerifyRequest(String email, String otp) {
    }

    record ChangePasswordRequest(String email, String oldPassword, String newPassword) {
    }

    record ForgotPasswordRequest(String email) {
    }

    record ResetPasswordRequest(String email, String otp, String newPassword) {
    }
}