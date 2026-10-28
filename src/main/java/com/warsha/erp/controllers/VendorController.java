package com.warsha.erp.controllers;

import com.warsha.erp.dtos.VendorRequest;
import com.warsha.erp.entities.Vendor;
import com.warsha.erp.services.VendorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Suppliers (ERP admin only). DELETE archives the vendor. */
@RestController
@RequestMapping("/vendors")
@RequiredArgsConstructor
public class VendorController {
    private final VendorService service;

    @GetMapping
    public List<Vendor> getAll(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.getAllVendors(includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Vendor create(@Valid @RequestBody VendorRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public Vendor update(@PathVariable Long id, @Valid @RequestBody VendorRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.archive(id);
    }
}
