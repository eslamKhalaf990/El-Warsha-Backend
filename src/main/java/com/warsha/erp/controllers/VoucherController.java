package com.warsha.erp.controllers;

import com.warsha.erp.dtos.ValidateVoucherRequest;
import com.warsha.erp.dtos.VoucherDto;
import com.warsha.erp.dtos.VoucherRequest;
import com.warsha.erp.dtos.VoucherValidationResponse;
import com.warsha.erp.services.VoucherService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/vouchers")
public class VoucherController {

    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    // --- ERP: manage promo codes (ADMIN) ---

    @GetMapping
    public List<VoucherDto> list() {
        return voucherService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VoucherDto create(@Valid @RequestBody VoucherRequest request) {
        return voucherService.create(request);
    }

    @PutMapping("/{id}")
    public VoucherDto update(@PathVariable Long id, @Valid @RequestBody VoucherRequest request) {
        return voucherService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        voucherService.delete(id);
    }

    // --- Store checkout ---

    @PostMapping("/validate")
    public ResponseEntity<VoucherValidationResponse> validateVoucher(@RequestBody ValidateVoucherRequest request) {
        VoucherValidationResponse response = voucherService.validateVoucher(request);
        return ResponseEntity.ok(response);
    }
}