package com.warsha.erp.services;

import com.warsha.erp.dtos.VoucherDto;
import com.warsha.erp.dtos.VoucherRequest;
import com.warsha.erp.entities.DiscountType;
import com.warsha.erp.entities.Voucher;
import com.warsha.erp.exceptions.AppException;
import com.warsha.erp.exceptions.ConflictException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VoucherServiceTest {

    private final VoucherRepository vouchers = mock(VoucherRepository.class);
    private final VoucherService service = new VoucherService(vouchers);

    private static VoucherRequest request(String code, DiscountType type, String value) {
        VoucherRequest r = new VoucherRequest();
        r.setCode(code);
        r.setType(type);
        r.setDiscountValue(new BigDecimal(value));
        r.setStartDate(LocalDate.of(2026, 1, 1));
        r.setMaxDiscountAmount(new BigDecimal("50"));
        return r;
    }

    private Voucher existing(long id, String code, int used) {
        Voucher v = new Voucher();
        v.setId(id);
        v.setCode(code);
        v.setCurrentUsageCount(used);
        when(vouchers.findById(id)).thenReturn(Optional.of(v));
        when(vouchers.findByCode(code)).thenReturn(Optional.of(v));
        return v;
    }

    @BeforeEach
    void setUp() {
        when(vouchers.findByCode(any())).thenReturn(Optional.empty());
        when(vouchers.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createsWithAnUppercaseCodeAndDefaults() {
        VoucherDto dto = service.create(request(" ramadan10 ", DiscountType.PERCENTAGE, "10"));

        assertThat(dto.code()).isEqualTo("RAMADAN10");
        assertThat(dto.active()).isTrue();
        assertThat(dto.minOrderAmount()).isEqualByComparingTo("0");
        assertThat(dto.maxDiscountAmount()).isEqualByComparingTo("50");
        assertThat(dto.usageCount()).isZero();
    }

    @Test
    void fixedAmountsDropTheCap() {
        assertThat(service.create(request("FLAT50", DiscountType.FIXED, "50")).maxDiscountAmount()).isNull();
    }

    @Test
    void rejectsADuplicateCode() {
        existing(1, "SAVE10", 0);
        assertThatThrownBy(() -> service.create(request("save10", DiscountType.FIXED, "10")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void keepsItsOwnCodeOnEdit() {
        existing(1, "SAVE10", 0);
        assertThat(service.update(1L, request("SAVE10", DiscountType.FIXED, "15")).discountValue())
                .isEqualByComparingTo("15");
    }

    @Test
    void rejectsPercentagesOver100AndBackwardDates() {
        assertThatThrownBy(() -> service.create(request("BIG", DiscountType.PERCENTAGE, "120")))
                .isInstanceOf(ValidationException.class);
        VoucherRequest r = request("LATE", DiscountType.FIXED, "10");
        r.setEndDate(LocalDate.of(2025, 12, 31));
        assertThatThrownBy(() -> service.create(r)).isInstanceOf(ValidationException.class);
    }

    @Test
    void deletesOnlyUnusedCodes() {
        Voucher unused = existing(1, "NEW", 0);
        service.delete(1L);
        verify(vouchers).delete(unused);

        existing(2, "USED", 3);
        assertThatThrownBy(() -> service.delete(2L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("used 3 times");
    }
}
