package com.warsha.erp.services;

import com.warsha.erp.dtos.VendorRequest;
import com.warsha.erp.entities.Vendor;
import com.warsha.erp.exceptions.ConflictException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Suppliers. Deleting a vendor archives it (IsActive = false) so its record
 * survives for purchase history; archived vendors are hidden from the list.
 */
@Service
@RequiredArgsConstructor
public class VendorService {
    private final VendorRepository repository;

    @Transactional(readOnly = true)
    public List<Vendor> getAllVendors(boolean includeArchived) {
        return repository.findAll().stream()
                .filter(v -> includeArchived || isActive(v))
                .sorted(Comparator.comparing(Vendor::getVendorId))
                .toList();
    }

    @Transactional
    public Vendor create(VendorRequest request) {
        Vendor vendor = new Vendor();
        apply(vendor, request);
        vendor.setIsActive(true);
        vendor.setCreatedAt(LocalDateTime.now());
        return repository.save(vendor);
    }

    @Transactional
    public Vendor update(Long id, VendorRequest request) {
        Vendor vendor = find(id);
        apply(vendor, request);
        vendor.setUpdatedAt(LocalDateTime.now());
        return repository.save(vendor);
    }

    @Transactional
    public void archive(Long id) {
        Vendor vendor = find(id);
        if (!isActive(vendor)) return;
        vendor.setIsActive(false);
        vendor.setUpdatedAt(LocalDateTime.now());
        repository.save(vendor);
    }

    private Vendor find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Vendor", id));
    }

    private void apply(Vendor vendor, VendorRequest request) {
        String name = clean(request.getName());
        boolean taken = repository.findAll().stream()
                .anyMatch(v -> isActive(v) && !v.getVendorId().equals(vendor.getVendorId())
                        && key(v.getName()).equals(key(name)));
        if (taken) {
            throw new ConflictException("name", "A vendor named \"" + name + "\" already exists.");
        }
        vendor.setName(name);
        vendor.setPhone(blankToNull(request.getPhone()));
        vendor.setEmail(blankToNull(request.getEmail()));
        vendor.setAddress(blankToNull(request.getAddress()));
        vendor.setTaxNumber(blankToNull(request.getTaxNumber()));
        vendor.setContactPerson(blankToNull(request.getContactPerson()));
        vendor.setNotes(request.getNotes() == null || request.getNotes().isBlank() ? null : request.getNotes().trim());
    }

    /** Rows created before IsActive existed have NULL there; they count as active. */
    private static boolean isActive(Vendor v) {
        return !Boolean.FALSE.equals(v.getIsActive());
    }

    static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    static String key(String name) {
        return clean(name).toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        String c = clean(s);
        return c.isEmpty() ? null : c;
    }
}
