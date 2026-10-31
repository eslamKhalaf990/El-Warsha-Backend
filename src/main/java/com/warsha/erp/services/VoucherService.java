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
import java.math.RoundingMode;
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
        int used = used(voucher);
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
    // Messages here are shown to store customers, so they are in Arabic.

    /** Cart page: what the code would take off a cart worth {@code cartTotal}. Nothing is saved. */
    public VoucherValidationResponse validateVoucher(ValidateVoucherRequest request) {
        String code = normalize(request.getCode());
        if (code == null) {
            return rejected(new Problem("EMPTY", "اكتب كود الخصم الأول"), null);
        }
        Optional<Voucher> found = voucherRepository.findByCode(code);
        if (found.isEmpty()) {
            return rejected(new Problem("NOT_FOUND", "كود الخصم ده غير صحيح"), code);
        }
        BigDecimal cartTotal = request.getCartTotal() == null ? BigDecimal.ZERO : request.getCartTotal();
        Problem problem = problemWith(found.get(), cartTotal);
        if (problem != null) {
            return rejected(problem, code);
        }
        return new VoucherValidationResponse(true, "تم تطبيق كود الخصم", discountFor(found.get(), cartTotal), code, null);
    }

    private static VoucherValidationResponse rejected(Problem problem, String code) {
        return new VoucherValidationResponse(false, problem.message(), BigDecimal.ZERO, code, problem.reason());
    }

    /**
     * Order placement: checks the code again with its row locked, counts one use and
     * returns the discount for {@code cartTotal} (items after product discounts).
     * Runs inside the order's transaction, so a failed order gives the use back.
     */
    @Transactional
    public BigDecimal redeem(String rawCode, BigDecimal cartTotal) {
        String code = normalize(rawCode);
        Voucher voucher = voucherRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new ValidationException("promoCode", "كود الخصم ده غير صحيح أو متوقف"));
        Problem problem = problemWith(voucher, cartTotal);
        if (problem != null) {
            throw new ValidationException("promoCode", problem.message());
        }
        voucher.setCurrentUsageCount(used(voucher) + 1);
        voucherRepository.save(voucher);
        return discountFor(voucher, cartTotal);
    }

    /** Codes are saved in capitals; blank means no code. */
    public static String normalize(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase();
    }

    /** Why a code can't be used: a stable {@code reason} for reports and an Arabic message for the customer. */
    record Problem(String reason, String message) {
    }

    private static Problem problemWith(Voucher voucher, BigDecimal cartTotal) {
        LocalDate today = LocalDate.now();
        if (!voucher.isActive()) return new Problem("INACTIVE", "كود الخصم ده متوقف حالياً");
        if (today.isBefore(voucher.getStartDate())) return new Problem("NOT_STARTED", "كود الخصم ده لسه مبدأش");
        if (voucher.getEndDate() != null && today.isAfter(voucher.getEndDate())) {
            return new Problem("EXPIRED", "كود الخصم ده انتهى");
        }
        if (voucher.getMaxUsageLimit() != null && used(voucher) >= voucher.getMaxUsageLimit()) {
            return new Problem("USED_UP", "كود الخصم ده خلص عدد مرات استخدامه");
        }
        BigDecimal min = voucher.getMinOrderAmount() == null ? BigDecimal.ZERO : voucher.getMinOrderAmount();
        if (cartTotal.compareTo(min) < 0) {
            return new Problem("MIN_ORDER", "الكود ده محتاج طلب بـ " + min.stripTrailingZeros().toPlainString() + " جنيه على الأقل");
        }
        return null;
    }

    /** Percentage (capped by the maximum discount) or a fixed amount, never more than the cart. */
    private static BigDecimal discountFor(Voucher voucher, BigDecimal cartTotal) {
        BigDecimal discount;
        if (voucher.getType() == DiscountType.PERCENTAGE) {
            discount = cartTotal.multiply(voucher.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (voucher.getMaxDiscountAmount() != null && discount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                discount = voucher.getMaxDiscountAmount();
            }
        } else {
            discount = voucher.getDiscountValue();
        }
        return discount.min(cartTotal).max(BigDecimal.ZERO);
    }

    private static int used(Voucher voucher) {
        return voucher.getCurrentUsageCount() == null ? 0 : voucher.getCurrentUsageCount();
    }
}
