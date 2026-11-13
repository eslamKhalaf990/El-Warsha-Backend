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
        String s = toLatinDigits(phone).replaceAll("[\\s\\-()]", "");
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

    /** Arabic-Indic (٠-٩) and Persian (۰-۹) digits → 0-9, so "٠١٠..." typed on an Arabic keyboard still matches. */
    private static String toLatinDigits(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            if (ch >= '٠' && ch <= '٩') out.append((char) ('0' + ch - '٠'));
            else if (ch >= '۰' && ch <= '۹') out.append((char) ('0' + ch - '۰'));
            else out.append(ch);
        }
        return out.toString();
    }

    // ------------------------------------------------------------ store accounts

    public static final int MIN_PASSWORD_LENGTH = 6;

    /** The customer whose phone matches once both sides are normalised, if any. */
    public Optional<Customer> findByPhone(String phone) {
        String wanted = normalizePhone(phone);
        if (wanted.isEmpty()) return Optional.empty();
        return customerRepository.findAll().stream()
                .filter(c -> wanted.equals(normalizePhone(c.getPhone())))
                // A store account wins over an ERP-only record if both share the number
                .min((a, b) -> Boolean.compare(!hasPassword(a), !hasPassword(b)));
    }

    /** Store login: "username" is a phone number, or an email for accounts made before phone sign-up. */
    public Optional<Customer> findForLogin(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        String u = username.trim();
        return u.contains("@") ? customerRepository.findByEmail(u) : findByPhone(u);
    }

    private static boolean hasPassword(Customer c) {
        return c.getPassword() != null && !c.getPassword().isBlank();
    }

    /**
     * Store self sign-up with phone + password; email is optional and not verified.
     * The account is active straight away. If staff already entered this phone in the ERP
     * (a record with no password), that record becomes the account so its orders stay with it.
     *
     * @throws ValidationException bad phone, password, governorate or missing name/address
     * @throws ConflictException   the phone (field "phone") or email (field "email") already has a store account
     */
    @Transactional
    public Customer registerFromStore(Customer req) {
        String phone = normalizePhone(req.getPhone());
        if (!phone.matches("01[0125]\\d{8}")) {
            throw new ValidationException("phone", "Enter a valid Egyptian mobile number, e.g. 01012345678.");
        }
        if (req.getPassword() == null || req.getPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("password", "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        String name = clean(req.getFullName());
        if (name.isEmpty()) throw new ValidationException("fullName", "Name is required.");
        String address = blankToNull(req.getAddress());
        if (address == null) throw new ValidationException("address", "Address is required.");
        if (!shippingZoneService.isValid(req.getGovernorate())) {
            throw new ValidationException("governorate", "Invalid governorate.");
        }
        String secondary = normalizePhone(req.getSecondaryPhone());
        String email = blankToNull(req.getEmail());

        Customer customer = findByPhone(phone).orElse(null);
        if (customer != null && hasPassword(customer)) {
            throw new ConflictException("phone", "This phone number is already registered.");
        }
        if (email != null) {
            Long selfId = customer == null ? null : customer.getId();
            customerRepository.findByEmail(email)
                    .filter(other -> !other.getId().equals(selfId))
                    .ifPresent(other -> {
                        throw new ConflictException("email", "This email is already registered.");
                    });
        }

        if (customer == null) {
            customer = new Customer();
            customer.setCreatedAt(LocalDateTime.now());
            customer.setFullName(name);
            customer.setPhone(phone);
            customer.setSecondaryPhone(secondary.isEmpty() ? null : secondary);
            customer.setGovernorate(clean(req.getGovernorate()));
            customer.setCity(blankToNull(req.getCity()));
            customer.setAddress(address);
            customer.setEmail(email);
        } else {
            // Claiming an ERP record: keep what staff entered, fill in only what's blank
            System.out.println("[" + getTimestamp() + "] INFO: Store sign-up claims ERP customer #" + customer.getId());
            if (blankToNull(customer.getFullName()) == null) customer.setFullName(name);
            if (blankToNull(customer.getSecondaryPhone()) == null && !secondary.isEmpty()) customer.setSecondaryPhone(secondary);
            if (blankToNull(customer.getGovernorate()) == null) customer.setGovernorate(clean(req.getGovernorate()));
            if (blankToNull(customer.getCity()) == null) customer.setCity(blankToNull(req.getCity()));
            if (blankToNull(customer.getAddress()) == null) customer.setAddress(address);
            if (blankToNull(customer.getEmail()) == null) customer.setEmail(email);
            customer.setUpdatedAt(LocalDateTime.now());
        }
        customer.setPassword(passwordEncoder.encode(req.getPassword()));
        customer.setStatus("Active");
        customer.setOtp(null);
        customer.setOtpExpirationTime(null);

        Customer saved = customerRepository.save(customer);
        System.out.println("[" + getTimestamp() + "] SUCCESS: Store account ready for customer #" + saved.getId());
        return saved;
    }

    /** Marks a store account active and drops any leftover sign-up code. */
    @Transactional
    public Customer activate(Customer customer) {
        customer.setStatus("Active");
        customer.setOtp(null);
        customer.setOtpExpirationTime(null);
        return customerRepository.save(customer);
    }

    /** Staff set a new store password from the ERP, e.g. for a customer with no email to reset by. */
    @Transactional
    public void setPasswordFromErp(Long id, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("password", "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        Customer customer = getCustomerByID(id);
        customer.setPassword(passwordEncoder.encode(newPassword));
        customer.setStatus("Active");
        customer.setOtp(null);
        customer.setOtpExpirationTime(null);
        customer.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(customer);
        System.out.println("[" + getTimestamp() + "] SUCCESS: Staff set a new store password for customer #" + id);
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

    public boolean changeCustomerPassword(Long id, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) return false;
        Customer customer = customerRepository.findById(id).orElse(null);
        if (customer == null || customer.getPassword() == null
                || !passwordEncoder.matches(oldPassword, customer.getPassword())) {
            return false;
        }
        customer.setPassword(passwordEncoder.encode(newPassword));
        customer.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(customer);
        return true;
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