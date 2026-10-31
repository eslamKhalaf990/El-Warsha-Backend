package com.warsha.erp.config;

import com.warsha.erp.entities.Customer;
import com.warsha.erp.repository.CustomerRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads e-commerce customers by email. They get ROLE_CUSTOMER (see {@link CustomerUserDetails}). */
@Service
public class CustomerDetailsService implements UserDetailsService {

    private final CustomerRepository customerRepo;

    public CustomerDetailsService(CustomerRepository customerRepo) {
        this.customerRepo = customerRepo;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Customer customer = customerRepo.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Customer not found"));
        return new CustomerUserDetails(customer);
    }
}
