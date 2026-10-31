package com.warsha.erp.services;

import com.warsha.erp.dtos.CustomerRequest;
import com.warsha.erp.exceptions.ConflictException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.OrderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import com.warsha.erp.dtos.CustomerCountByGovernorate;
import com.warsha.erp.dtos.CustomerInfoDTO;
import com.warsha.erp.entities.Customer;
import com.warsha.erp.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private EmailService emailService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ShippingZoneService shippingZoneService;
    @Autowired private OrderRepository orderRepository;

    // Formatter for consistent log timestamps
    private final DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private String getTimestamp() {
        return LocalDateTime.now().format(logFormat);
    }

    public List<Customer> getAllCustomers() {
        System.out.println("[" + getTimestamp() + "] INFO: Fetching all customers (Sorted DESC)");
        return customerRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    public List<CustomerCountByGovernorate> getCustomerCountsByGovernorate() {
        System.out.println("[" + getTimestamp() + "] INFO: Fetching customer counts by governorate");
        return customerRepository.countCustomersByGovernorate();
    }

    /** True when a customer already uses this email (case-insensitive trim is left to the DB collation). */
    public boolean emailExists(String email) {
        return email != null && customerRepository.findByEmail(email.trim()).isPresent();
    }

    public Customer findByEmail(String email) {
        System.out.println("[" + getTimestamp() + "] INFO: Finding customer by email: " + email);
        return customerRepository.findByEmail(email)
                .orElseThrow(() -> {
                    System.out.println("[" + getTimestamp() + "] ERROR: Customer not found for email: " + email);
                    return new RuntimeException("Customer not found");
                });
    }

    public Customer getCustomerByID(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
    }

    public CustomerInfoDTO getCustomerInfo(Long id) {
        return toDto(getCustomerByID(id));
    }

    /** Every customer, newest first, without passwords or codes. */
    public List<CustomerInfoDTO> getAllCustomerDtos() {
        return getAllCustomers().stream().map(CustomerService::toDto).toList();
    }

    public static CustomerInfoDTO toDto(Customer customer) {
        return new CustomerInfoDTO(
                customer.getId(),
                customer.getFullName(),
                customer.getGovernorate(),
                customer.getPhone(),
                customer.getCity(),
                customer.getSecondaryPhone(),
                customer.getAddress(),
                customer.getEmail(),
                customer.getCreatedAt(),
                customer.getUpdatedAt(),
                customer.getStatus()
        );
    }

    // ------------------------------------------------------------ ERP admin

    /** A customer entered in the ERP (no password; they can't log in to the store). */
    @Transactional
    public CustomerInfoDTO createFromErp(CustomerRequest request) {
        Customer customer = new Customer();
        apply(customer, request);
        customer.setCreatedAt(LocalDateTime.now());
        return toDto(customerRepository.save(customer));
    }

    @Transactional
    public CustomerInfoDTO updateFromErp(Long id, CustomerRequest request) {
        Customer customer = getCustomerByID(id);
        apply(customer, request);
        customer.setUpdatedAt(LocalDateTime.now());
        return toDto(customerRepository.save(customer));
    }

    private void apply(Customer customer, CustomerRequest request) {
        String phone = normalizePhone(request.getPhone());
        if (!phone.matches("01[0125]\\d{8}")) {
            throw new ValidationException("phone", "Enter a valid Egyptian mobile number, e.g. 01012345678.");
        }
        String secondary = normalizePhone(request.getSecondaryPhone());
        if (!secondary.isEmpty() && !secondary.matches("\\+?[0-9]{6,15}")) {
            throw new ValidationException("secondaryPhone", "Enter a valid phone number.");
        }
        if (!shippingZoneService.isKnown(request.getGovernorate())) {
            throw new ValidationException("governorate", "'" + request.getGovernorate().trim()
                    + "' has no shipping zone. Choose a governorate from the list.");
        }
        customerRepository.findAll().stream()
                .filter(c -> !c.getId().equals(customer.getId()) && phone.equals(normalizePhone(c.getPhone())))
                .findFirst()
                .ifPresent(other -> {
                    throw new ConflictException("phone", "This number belongs to " + other.getFullName()
                            + " (customer #" + other.getId() + ").");
                });
        String email = blankToNull(request.getEmail());
        if (email != null) {
            customerRepository.findByEmail(email)
                    .filter(other -> !other.getId().equals(customer.getId()))
                    .ifPresent(other -> {
                        throw new ConflictException("email", "This email belongs to " + other.getFullName()
                                + " (customer #" + other.getId() + ").");
                    });
        }
        customer.setFullName(clean(request.getFullName()));
        customer.setPhone(phone);
        customer.setSecondaryPhone(secondary.isEmpty() ? null : secondary);
        customer.setGovernorate(clean(request.getGovernorate()));
        customer.setCity(blankToNull(request.getCity()));
        customer.setAddress(blankToNull(request.getAddress()));
        customer.setEmail(email);
    }

    /**
     * Digits only, with Egypt's +20 / 0020 prefix turned into the local 0:
     * "+20 100-123-4567" → "01001234567".
     */
    static String normalizePhone(String phone) {
        if (phone == null) return "";
        String s = phone.replaceAll("[\\s\\-()]", "");
        if (s.startsWith("+20")) s = "0" + s.substring(3);
        else if (s.startsWith("0020")) s = "0" + s.substring(4);
        return s;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static String blankToNull(String s) {
        String c = clean(s);
        return c.isEmpty() ? null : c;
    }

    public Customer createCustomer(Customer customer) {
        System.out.println("[" + getTimestamp() + "] INFO: Creating new customer: " + customer.getFullName());

        customer.setCreatedAt(LocalDateTime.now());

        if (customer.getPassword() != null) {
            customer.setPassword(passwordEncoder.encode(customer.getPassword()));
            if (!shippingZoneService.isValid(customer.getGovernorate())) {
                System.out.println("[" + getTimestamp() + "] WARN: Invalid governorate: " + customer.getGovernorate());
                throw new IllegalArgumentException("Invalid governorate provided: " + customer.getGovernorate());
            }
        }

        Customer saved = customerRepository.save(customer);
        System.out.println("[" + getTimestamp() + "] SUCCESS: Customer saved with ID: " + saved.getId());

        // Generate and save OTP
        if(saved.getEmail() != null && !saved.getEmail().isEmpty()){
            String otp = emailService.sendOtpEmail(saved.getEmail());
            saveOtp(saved.getEmail(), otp);
        }

        return saved;
    }

    public void saveOtp(String email, String otp) {
        System.out.println("[" + getTimestamp() + "] INFO: Saving OTP for customer: " + email);
        Optional<Customer> customerOptional = customerRepository.findByEmail(email);
        
        if (customerOptional.isPresent()) {
            Customer customer = customerOptional.get();
            customer.setOtp(otp);
            customer.setStatus("Pending"); // Keep pending for signup/verification
            customer.setOtpExpirationTime(LocalDateTime.now().plusMinutes(10)); // OTP valid for 10 minutes
            customerRepository.save(customer);
            System.out.println("[" + getTimestamp() + "] SUCCESS: OTP saved for customer: " + email);
        } else {
            System.out.println("[" + getTimestamp() + "] ERROR: Cannot save OTP, customer not found: " + email);
             throw new RuntimeException("Customer not found");
        }
    }

    public void saveResetOtp(String email, String otp) {
        System.out.println("[" + getTimestamp() + "] INFO: Saving Reset OTP for customer: " + email);
        Optional<Customer> customerOptional = customerRepository.findByEmail(email);
        
        if (customerOptional.isPresent()) {
            Customer customer = customerOptional.get();
            customer.setOtp(otp);
            // Do NOT change status to Pending for password resets to avoid locking out the user
            customer.setOtpExpirationTime(LocalDateTime.now().plusMinutes(10));
            customerRepository.save(customer);
            System.out.println("[" + getTimestamp() + "] SUCCESS: Reset OTP saved for customer: " + email);
        } else {
            System.out.println("[" + getTimestamp() + "] ERROR: Cannot save Reset OTP, customer not found: " + email);
             throw new RuntimeException("Customer not found");
        }
    }

    public boolean verifyOtp(String email, String otp) {
        System.out.println("[" + getTimestamp() + "] INFO: Verifying OTP for customer: " + email);
        Optional<Customer> customerOptional = customerRepository.findByEmail(email);

        if (customerOptional.isPresent()) {
            Customer customer = customerOptional.get();
            
            if (customer.getOtp() != null && customer.getOtp().equals(otp)) {
                if (customer.getOtpExpirationTime() != null && LocalDateTime.now().isBefore(customer.getOtpExpirationTime())) {
                    customer.setStatus("Active");
                    customer.setOtp(null);
                    customer.setOtpExpirationTime(null);
                    customerRepository.save(customer);
                    System.out.println("[" + getTimestamp() + "] SUCCESS: OTP verified, customer active: " + email);
                    return true;
                } else {
                     System.out.println("[" + getTimestamp() + "] WARN: OTP expired for customer: " + email);
                }
            } else {
                System.out.println("[" + getTimestamp() + "] WARN: Invalid OTP for customer: " + email);
            }
        } else {
             System.out.println("[" + getTimestamp() + "] ERROR: Customer not found for verification: " + email);
        }
        return false;
    }

    public boolean changeCustomerPassword(String email, String oldPassword, String newPassword) {
        System.out.println("[" + getTimestamp() + "] INFO: Changing password for customer: " + email);
        Optional<Customer> customerOptional = customerRepository.findByEmail(email);

        if (customerOptional.isPresent()) {
            Customer customer = customerOptional.get();

            if (passwordEncoder.matches(oldPassword, customer.getPassword())) {
                customer.setPassword(passwordEncoder.encode(newPassword));
                customerRepository.save(customer);
                System.out.println("[" + getTimestamp() + "] SUCCESS: Password changed successfully for customer: " + email);
                return true;
            } else {
                System.out.println("[" + getTimestamp() + "] WARN: Invalid old password for customer: " + email);
            }
        } else {
            System.out.println("[" + getTimestamp() + "] ERROR: Customer not found for password change: " + email);
        }
        return false;
    }

    public boolean forgotPassword(String email) {
        System.out.println("[" + getTimestamp() + "] INFO: Forgot password requested for customer: " + email);
        Optional<Customer> customerOptional = customerRepository.findByEmail(email);

        if (customerOptional.isPresent()) {
            String otp = emailService.sendOtpEmail(email);
            if (otp != null) {
                saveResetOtp(email, otp); // Use saveResetOtp instead of saveOtp
                System.out.println("[" + getTimestamp() + "] SUCCESS: Forgot password OTP sent and saved for: " + email);
                return true;
            } else {
                System.out.println("[" + getTimestamp() + "] ERROR: Failed to send OTP email for: " + email);
                return false;
            }
        } else {
            System.out.println("[" + getTimestamp() + "] ERROR: Customer not found for forgot password: " + email);
            return false;
        }
    }

    public boolean resetCustomerPassword(String email, String otp, String newPassword) {
        System.out.println("[" + getTimestamp() + "] INFO: Resetting password for customer: " + email);
        Optional<Customer> customerOptional = customerRepository.findByEmail(email);

        if (customerOptional.isPresent()) {
            Customer customer = customerOptional.get();

            if (customer.getOtp() != null && customer.getOtp().equals(otp)) {
                if (customer.getOtpExpirationTime() != null && LocalDateTime.now().isBefore(customer.getOtpExpirationTime())) {
                    customer.setPassword(passwordEncoder.encode(newPassword));
                    // If they were pending, resetting password can activate them. If already active, it remains active.
                    customer.setStatus("Active");
                    customer.setOtp(null);
                    customer.setOtpExpirationTime(null);
                    customerRepository.save(customer);
                    System.out.println("[" + getTimestamp() + "] SUCCESS: Password reset successfully for customer: " + email);
                    return true;
                } else {
                     System.out.println("[" + getTimestamp() + "] WARN: OTP expired for password reset: " + email);
                }
            } else {
                System.out.println("[" + getTimestamp() + "] WARN: Invalid OTP for password reset: " + email);
            }
        } else {
            System.out.println("[" + getTimestamp() + "] ERROR: Customer not found for password reset: " + email);
        }
        return false;
    }

    /**
     * Customers with orders, reviews or activity history are kept: those rows
     * point at them and are needed for the books.
     */
    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = getCustomerByID(id);
        long orders = orderRepository.countByCustomerId(id);
        if (orders > 0) {
            throw new ConflictException(customer.getFullName() + " has " + orders
                    + (orders == 1 ? " order" : " orders") + ", so they can't be deleted.");
        }
        try {
            customerRepository.delete(customer);
            customerRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(customer.getFullName()
                    + " has reviews or account history, so they can't be deleted.");
        }
    }
}