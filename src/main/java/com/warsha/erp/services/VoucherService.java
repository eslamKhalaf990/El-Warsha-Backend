package com.warsha.erp.services;

import com.warsha.erp.dtos.ValidateVoucherRequest;
import com.warsha.erp.dtos.VoucherDto;
import com.warsha.erp.dtos.VoucherRequest;
import com.warsha.erp.dtos.VoucherValidationResponse;
import com.warsha.erp.entities.DiscountType;
import com.warsha.erp.entities.Voucher;
import com.warsha.erp.exceptions.BusinessRuleException;
import com.warsha.erp.exceptions.ConflictException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.VoucherRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class VoucherService {

    private final VoucherRepository voucherRepository;

    public VoucherService(VoucherRepository voucherRepository) {
        this.voucherRepository = voucherRepository;
    }

    // --- ERP management ---

    /** Every promo code, newest first. */
    public List<VoucherDto> list() {
        return voucherRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream().map(VoucherDto::from).toList();
    }

    @Transactional
    public VoucherDto create(VoucherRequest request) {
        Voucher voucher = new Voucher();
        voucher.setCurrentUsageCount(0);
        apply(voucher, request, null);
        return VoucherDto.from(voucherRepository.save(voucher));
    }

    @Transactional
    public VoucherDto update(Long id, VoucherRequest request) {
        Voucher voucher = find(id);
        apply(voucher, request, id);
        return VoucherDto.from(voucherRepository.save(voucher));
    }

    /** Unused codes are deleted; a code customers already used is kept for the record and must be turned off instead. */
    @Transactional
    public void delete(Long id) {
        Voucher voucher = find(id);
        int used = voucher.getCurrentUsageCount() == null ? 0 : voucher.getCurrentUsageCount();
        if (used > 0) {
            throw new BusinessRuleException("\"" + voucher.getCode() + "\" was used " + used
                    + (used == 1 ? " time" : " times") + ". Turn it off instead of deleting it.");
        }
        voucherRepository.delete(voucher);
    }

    private Voucher find(Long id) {
        return voucherRepository.findById(id).orElseThrow(() -> new NotFoundException("Promo code", id));
    }

    private void apply(Voucher voucher, VoucherRequest r, Long editingId) {
        String code = r.getCode().trim().toUpperCase();
        voucherRepository.findByCode(code)
                .filter(other -> !other.getId().equals(editingId))
                .ifPresent(other -> {
                    throw new ConflictException("code", "The code " + code + " already exists.");
                });
        if (r.getType() == DiscountType.PERCENTAGE && r.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ValidationException("discountValue", "A percentage can be at most 100.");
        }
        if (r.getEndDate() != null && r.getEndDate().isBefore(r.getStartDate())) {
            throw new ValidationException("endDate", "The end date can't be before the start date.");
        }
        voucher.setCode(code);
        voucher.setType(r.getType());
        voucher.setDiscountValue(r.getDiscountValue());
        voucher.setMinOrderAmount(r.getMinOrderAmount() == null ? BigDecimal.ZERO : r.getMinOrderAmount());
        // A cap only makes sense for percentages
        voucher.setMaxDiscountAmount(r.getType() == DiscountType.PERCENTAGE ? r.getMaxDiscountAmount() : null);
        voucher.setStartDate(r.getStartDate());
        voucher.setEndDate(r.getEndDate());
        voucher.setActive(r.getActive() == null || r.getActive());
        voucher.setMaxUsageLimit(r.getMaxUsageLimit());
    }

    // --- Store checkout ---

    public VoucherValidationResponse validateVoucher(ValidateVoucherRequest request) {
        // 1. Input Sanity Check
        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            return new VoucherValidationResponse(false, "Code cannot be empty", BigDecimal.ZERO, null);
        }

        // 2. Fetch from DB
        Optional<Voucher> voucherOpt = voucherRepository.findByCode(request.getCode());
        
        if (voucherOpt.isEmpty()) {
            return new VoucherValidationResponse(false, "Invalid Coupon Code", BigDecimal.ZERO, request.getCode());
        }

        Voucher voucher = voucherOpt.get();

        // 3. Run Validations
        if (!voucher.isActive()) {
            return new VoucherValidationResponse(false, "This coupon is inactive", BigDecimal.ZERO, request.getCode());
        }

        LocalDate today = LocalDate.now();
        if (today.isBefore(voucher.getStartDate())) {
             return new VoucherValidationResponse(false, "Promotion has not started yet", BigDecimal.ZERO, request.getCode());
        }
        
        if (voucher.getEndDate() != null && today.isAfter(voucher.getEndDate())) {
            return new VoucherValidationResponse(false, "Coupon has expired", BigDecimal.ZERO, request.getCode());
        }

        if (voucher.getMaxUsageLimit() != null && voucher.getCurrentUsageCount() >= voucher.getMaxUsageLimit()) {
            return new VoucherValidationResponse(false, "Coupon usage limit reached", BigDecimal.ZERO, request.getCode());
        }

        if (request.getCartTotal().compareTo(voucher.getMinOrderAmount()) < 0) {
            return new VoucherValidationResponse(false, 
                "Minimum order of " + voucher.getMinOrderAmount() + " required", 
                BigDecimal.ZERO, 
                request.getCode());
        }

        // 4. Calculate Potential Discount (Simulation)
        BigDecimal discountAmount;

        if (voucher.getType() == DiscountType.PERCENTAGE) {
            BigDecimal percentage = voucher.getDiscountValue().divide(BigDecimal.valueOf(100));
            discountAmount = request.getCartTotal().multiply(percentage);

            // Apply Cap
            if (voucher.getMaxDiscountAmount() != null && discountAmount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                discountAmount = voucher.getMaxDiscountAmount();
            }
        } else {
            discountAmount = voucher.getDiscountValue();
        }

        // Safety: Discount can't exceed total
        if (discountAmount.compareTo(request.getCartTotal()) > 0) {
            discountAmount = request.getCartTotal();
        }

        // 5. Return Success
        return new VoucherValidationResponse(true, "Coupon Applied Successfully", discountAmount, voucher.getCode());
    }
}