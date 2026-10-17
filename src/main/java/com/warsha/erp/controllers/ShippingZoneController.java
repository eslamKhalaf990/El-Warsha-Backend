package com.warsha.erp.controllers;

import com.warsha.erp.dtos.GovernoratePriceDto;
import com.warsha.erp.entities.ShippingZone;
import com.warsha.erp.services.EgyptGovernorates;
import com.warsha.erp.services.ShippingZoneService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/shipping")
public class ShippingZoneController {

    @Autowired
    private ShippingZoneService shippingZoneService;

    @GetMapping
    public List<ShippingZone> getAll() {
        return shippingZoneService.getAllShippingZones();
    }

    @GetMapping("/governorates")
    public ResponseEntity<List<GovernoratePriceDto>> getAllGovernoratePrices() {
        return ResponseEntity.ok(EgyptGovernorates.getAllGovernoratePrices());
    }

    @GetMapping("/governorates/map")
    public ResponseEntity<Map<String, Double>> getAllGovernoratePricesMap() {
        return ResponseEntity.ok(EgyptGovernorates.getAllRatesMap());
    }

    @GetMapping("/governorates/price")
    public ResponseEntity<?> getGovernoratePrice(@RequestParam String governorate) {
        try {
            return ResponseEntity.ok(EgyptGovernorates.getGovernoratePrice(governorate));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShippingZone> getById(@PathVariable Integer id) {
        Optional<ShippingZone> shippingZone = shippingZoneService.getShippingZoneById(id);
        return shippingZone.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ShippingZone create(@RequestBody ShippingZone shippingZone) {
        return shippingZoneService.saveShippingZone(shippingZone);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        shippingZoneService.deleteShippingZone(id);
    }

    @PutMapping("/{id}")
    public ShippingZone updatePrice(@PathVariable Integer id, @RequestParam BigDecimal shippingFee) {
        return shippingZoneService.updateShippingPrice(id, shippingFee);
    }
}
