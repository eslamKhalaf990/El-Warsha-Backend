package com.warsha.erp.config;

import com.warsha.erp.entities.Customer;
import com.warsha.erp.repository.CustomerRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads e-commerce customers from a token subject: the customer id, or the email in tokens
 * issued before phone sign-up. They get ROLE_CUSTOMER (see {@link CustomerUserDetails}).
 */
@Service
public class CustomerDetailsService implements UserDetailsService {

    private final CustomerRepository customerRepo;

    public CustomerDetailsService(CustomerRepository customerRepo) {
        this.customerRepo = customerRepo;
    }

    @Override
    public UserDetails loadUserByUsername(String subject) throws UsernameNotFoundException {
        Customer customer = (subject != null && subject.matches("\\d+")
                ? customerRepo.findById(Long.parseLong(subject))
                : customerRepo.findByEmail(subject))
                .orElseThrow(() -> new UsernameNotFoundException("Customer not found"));
        return new CustomerUserDetails(customer);
    }
}
