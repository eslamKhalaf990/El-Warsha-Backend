package com.warsha.erp.services;

import com.warsha.erp.dtos.GovernoratePriceDto;
import com.warsha.erp.dtos.ShippingZoneRequest;
import com.warsha.erp.entities.ShippingZone;
import com.warsha.erp.exceptions.ConflictException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.ShippingZoneRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Delivery prices per governorate. The ShippingZones table edited in the ERP is the
 * only source: the store's price list, store checkout and customer sign-up all read it,
 * so a price change in the ERP applies everywhere on the next request.
 */
@Service
public class ShippingZoneService {

    private final ShippingZoneRepository shippingZoneRepository;

    public ShippingZoneService(ShippingZoneRepository shippingZoneRepository) {
        this.shippingZoneRepository = shippingZoneRepository;
    }

    // ------------------------------------------------------------ ERP admin

    public List<ShippingZone> getAllShippingZones() {
        return shippingZoneRepository.findAllByOrderByIdAsc();
    }

    public ShippingZone getShippingZone(Integer id) {
        return findZone(id);
    }

    public ShippingZone create(ShippingZoneRequest request) {
        ShippingZone zone = new ShippingZone();
        apply(zone, request);
        if (zone.getIsActive() == null) zone.setIsActive(true);
        return shippingZoneRepository.save(zone);
    }

    public ShippingZone update(Integer id, ShippingZoneRequest request) {
        ShippingZone zone = findZone(id);
        apply(zone, request);
        return shippingZoneRepository.save(zone);
    }

    /**
     * Customers in a deleted zone can no longer check out; deactivating
     * ({@code isActive = false}) hides it from the store without losing the price.
     */
    public void deleteShippingZone(Integer id) {
        shippingZoneRepository.delete(findZone(id));
    }

    private void apply(ShippingZone zone, ShippingZoneRequest request) {
        String name = request.getCityName() == null ? "" : request.getCityName().trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new ValidationException("cityName", "Enter the governorate name.");
        }
        requirePrice(request.getShippingFee());
        boolean taken = shippingZoneRepository.findAll().stream()
                .anyMatch(z -> !Objects.equals(z.getId(), zone.getId()) && key(z.getCityName()).equals(key(name)));
        if (taken) {
            throw new ConflictException("cityName", "'" + name + "' already has a delivery price.");
        }
        zone.setCityName(name);
        zone.setShippingFee(request.getShippingFee());
        if (request.getIsActive() != null) zone.setIsActive(request.getIsActive());
    }

    // -------------------------------------------------- store and checkout

    /** Active governorates with their prices, in the order the ERP lists them. */
    public List<GovernoratePriceDto> activePrices() {
        return shippingZoneRepository.findAllByOrderByIdAsc().stream()
                .filter(z -> !Boolean.FALSE.equals(z.getIsActive()))
                .map(z -> new GovernoratePriceDto(z.getCityName(), z.getShippingFee().doubleValue()))
                .toList();
    }

    /** Same as {@link #activePrices()}, as governorate → price. */
    public Map<String, Double> activePriceMap() {
        Map<String, Double> map = new LinkedHashMap<>();
        activePrices().forEach(p -> map.putIfAbsent(p.getGovernorate(), p.getPrice()));
        return map;
    }

    /** True when the governorate has an active delivery price. */
    public boolean isValid(String governorate) {
        return findActive(governorate).isPresent();
    }

    /** True when the governorate has a zone, active or not (spelling variants match). */
    public boolean isKnown(String governorate) {
        if (governorate == null || governorate.isBlank()) return false;
        String wanted = key(governorate);
        return shippingZoneRepository.findAll().stream().anyMatch(z -> key(z.getCityName()).equals(wanted));
    }

    public GovernoratePriceDto priceFor(String governorate) {
        ShippingZone zone = findActive(governorate).orElseThrow(() -> new ValidationException("governorate",
                "We don't deliver to '" + governorate + "' yet. Choose another governorate."));
        return new GovernoratePriceDto(zone.getCityName(), zone.getShippingFee().doubleValue());
    }

    /** Delivery price in EGP for a store order. */
    public double deliveryPrice(String governorate) {
        return priceFor(governorate).getPrice();
    }

    // ---------------------------------------------------------------- helpers

    private Optional<ShippingZone> findActive(String governorate) {
        if (governorate == null || governorate.isBlank()) return Optional.empty();
        String wanted = key(governorate);
        return shippingZoneRepository.findAllByOrderByIdAsc().stream()
                .filter(z -> !Boolean.FALSE.equals(z.getIsActive()))
                .filter(z -> key(z.getCityName()).equals(wanted))
                .findFirst();
    }

    private ShippingZone findZone(Integer id) {
        return shippingZoneRepository.findById(id).orElseThrow(() -> new NotFoundException("Shipping zone", id));
    }

    private static void requirePrice(BigDecimal price) {
        if (price == null || price.signum() < 0) {
            throw new ValidationException("shippingFee", "Enter a delivery price of 0 or more.");
        }
    }

    /**
     * Matching key for Arabic governorate names, so "الاسكندريه", "الإسكندرية" and
     * "اسكندرية" are the same place: unify alef/teh marbuta/yeh forms, drop diacritics,
     * extra spaces and a leading "ال".
     */
    static String key(String name) {
        if (name == null) return "";
        String s = name.trim()
                .replaceAll("[\\u064B-\\u065F\\u0640]", "")   // diacritics, tatweel
                .replaceAll("[أإآ]", "ا")
                .replace('ة', 'ه')
                .replace('ى', 'ي')
                .replaceAll("\\s+", " ");
        return s.startsWith("ال") ? s.substring(2) : s;
    }
}
