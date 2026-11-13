package com.warsha.erp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.warsha.erp.dtos.CustomerInfoDTO;
import com.warsha.erp.dtos.CustomerRequest;
import com.warsha.erp.entities.Customer;
import com.warsha.erp.exceptions.AppException;
import com.warsha.erp.exceptions.ConflictException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.CustomerRepository;
import com.warsha.erp.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerServiceTest {

    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final ShippingZoneService zones = mock(ShippingZoneService.class);
    private final EmailService email = mock(EmailService.class);
    private final CustomerService service = new CustomerService();
    private final List<Customer> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "customerRepository", customers);
        ReflectionTestUtils.setField(service, "orderRepository", orders);
        ReflectionTestUtils.setField(service, "shippingZoneService", zones);
        ReflectionTestUtils.setField(service, "emailService", email);
        when(customers.findAll()).thenAnswer(inv -> List.copyOf(rows));
        when(customers.findAll(any(org.springframework.data.domain.Sort.class))).thenAnswer(inv -> List.copyOf(rows));
        when(customers.save(any())).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            if (c.getId() == null) c.setId(100L);
            return c;
        });
        when(customers.findByEmail(any())).thenReturn(Optional.empty());
        when(zones.isKnown(any())).thenAnswer(inv -> "القاهرة".equals(inv.getArgument(0)));
    }

    private Customer existing(long id, String name, String phone) {
        Customer c = new Customer();
        c.setId(id);
        c.setFullName(name);
        c.setPhone(phone);
        c.setPassword("$2a$hash");
        c.setOtp("998877");
        rows.add(c);
        when(customers.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    private static CustomerRequest request(String phone) {
        CustomerRequest r = new CustomerRequest();
        r.setFullName("  Mona   Ali ");
        r.setPhone(phone);
        r.setGovernorate("القاهرة");
        return r;
    }

    @Test
    void createNormalisesThePhoneAndTidiesText() {
        CustomerRequest r = request("+20 100-123-4567");
        r.setCity("  ");

        CustomerInfoDTO dto = service.createFromErp(r);

        assertThat(dto.getPhone()).isEqualTo("01001234567");
        assertThat(dto.getFullName()).isEqualTo("Mona Ali");
        assertThat(dto.getCity()).isNull();
        verifyNoInteractions(email);   // staff-entered customers get no verification email
    }

    @Test
    void phoneMustBeAnEgyptianMobile() {
        assertThatThrownBy(() -> service.createFromErp(request("0221234567")))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((AppException) e).getDetails().get(0).field()).isEqualTo("phone"));
    }

    @Test
    void governorateMustHaveAZone() {
        CustomerRequest r = request("01012345678");
        r.setGovernorate("Atlantis");
        assertThatThrownBy(() -> service.createFromErp(r))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("has no shipping zone");
    }

    @Test
    void aPhoneBelongsToOneCustomerWhateverTheFormat() {
        existing(7, "Sara", "0100 123 4567");

        assertThatThrownBy(() -> service.createFromErp(request("+201001234567")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This number belongs to Sara (customer #7).");
    }

    @Test
    void editingKeepsTheCustomersOwnPhone() {
        existing(7, "Sara", "01001234567");
        assertThat(service.updateFromErp(7L, request("01001234567")).getFullName()).isEqualTo("Mona Ali");
    }

    @Test
    void missingCustomersAreNotFound() {
        assertThatThrownBy(() -> service.updateFromErp(9L, request("01012345678"))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.deleteCustomer(9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void customersWithOrdersAreKept() {
        existing(7, "Sara", "01001234567");
        when(orders.countByCustomerId(7L)).thenReturn(3L);

        assertThatThrownBy(() -> service.deleteCustomer(7L)).hasMessage("Sara has 3 orders, so they can't be deleted.");
        verify(customers, never()).delete(any());
    }

    @Test
    void otherHistoryAlsoBlocksDeletionWithAClearMessage() {
        existing(7, "Sara", "01001234567");
        doThrow(new DataIntegrityViolationException("FK")).when(customers).flush();

        assertThatThrownBy(() -> service.deleteCustomer(7L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("reviews or account history");
    }

    @Test
    void passwordsAndCodesNeverReachJson() throws Exception {
        Customer c = existing(7, "Sara", "01001234567");
        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(c);

        assertThat(json).doesNotContain("password", "hash", "otp", "998877");
        assertThat(new ObjectMapper().readValue("{\"password\":\"secret\"}", Customer.class).getPassword()).isEqualTo("secret");
    }

    // ------------------------------------------------------------ store sign-up (phone + password, no OTP)

    private Customer signUp(String phone, String email) {
        ReflectionTestUtils.setField(service, "passwordEncoder",
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4));
        when(zones.isValid(any())).thenAnswer(inv -> "القاهرة".equals(inv.getArgument(0)));
        Customer c = new Customer();
        c.setFullName(" Mona  Ali ");
        c.setPhone(phone);
        c.setEmail(email);
        c.setPassword("secret1");
        c.setGovernorate("القاهرة");
        c.setAddress("12 Nile St");
        return c;
    }

    @Test
    void signUpWithoutEmailIsActiveAtOnceAndSendsNoCode() {
        Customer saved = service.registerFromStore(signUp("٠١٠٠ ١٢٣ ٤٥٦٧", "  "));

        assertThat(saved.getPhone()).isEqualTo("01001234567");
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getStatus()).isEqualTo("Active");
        assertThat(saved.getOtp()).isNull();
        assertThat(saved.getPassword()).startsWith("$2a$");
        verifyNoInteractions(email);
    }

    @Test
    void signUpRefusesAPhoneThatAlreadyHasAStoreAccount() {
        existing(1, "Old", "+20 100 123 4567");

        assertThatThrownBy(() -> service.registerFromStore(signUp("01001234567", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("phone");
    }

    @Test
    void signUpClaimsAnErpRecordWithThatPhoneAndKeepsStaffDetails() {
        Customer erp = existing(7, "Mona (shop)", "01001234567");
        erp.setPassword(null);
        erp.setStatus("Active");

        Customer saved = service.registerFromStore(signUp("01001234567", "mona@mail.com"));

        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getFullName()).isEqualTo("Mona (shop)");
        assertThat(saved.getAddress()).isEqualTo("12 Nile St");
        assertThat(saved.getEmail()).isEqualTo("mona@mail.com");
        assertThat(saved.getPassword()).startsWith("$2a$");
    }

    @Test
    void signUpRefusesAnEmailUsedByAnotherCustomer() {
        Customer other = new Customer();
        other.setId(9L);
        when(customers.findByEmail("taken@mail.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.registerFromStore(signUp("01001234567", "taken@mail.com")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("email");
    }

    @Test
    void signUpRejectsABadPhoneOrShortPassword() {
        assertThatThrownBy(() -> service.registerFromStore(signUp("12345", null)))
                .isInstanceOf(ValidationException.class);
        Customer shortPw = signUp("01001234567", null);
        shortPw.setPassword("123");
        assertThatThrownBy(() -> service.registerFromStore(shortPw))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void loginFindsByPhoneInAnyFormatOrByEmail() {
        Customer c = existing(3, "Ali", "01112223333");
        when(customers.findByEmail("ali@mail.com")).thenReturn(Optional.of(c));

        assertThat(service.findForLogin("+20 111 222 3333")).contains(c);
        assertThat(service.findForLogin("ali@mail.com")).contains(c);
        assertThat(service.findForLogin("01000000000")).isEmpty();
    }
}
