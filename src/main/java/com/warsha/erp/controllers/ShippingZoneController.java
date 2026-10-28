package com.warsha.erp.controllers;

import com.warsha.erp.dtos.GovernoratePriceDto;
import com.warsha.erp.dtos.ShippingZoneRequest;
import com.warsha.erp.entities.ShippingZone;
import com.warsha.erp.services.ShippingZoneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Delivery prices per governorate. GETs are public (store); changes need an admin. */
@RestController
@RequestMapping("/shipping")
public class ShippingZoneController {

    private final ShippingZoneService shippingZoneService;

    public ShippingZoneController(ShippingZoneService shippingZoneService) {
        this.shippingZoneService = shippingZoneService;
    }

    @GetMapping
    public List<ShippingZone> getAll() {
        return shippingZoneService.getAllShippingZones();
    }

    @GetMapping("/governorates")
    public ResponseEntity<List<GovernoratePriceDto>> getAllGovernoratePrices() {
        return ResponseEntity.ok(shippingZoneService.activePrices());
    }

    @GetMapping("/governorates/map")
    public ResponseEntity<Map<String, Double>> getAllGovernoratePricesMap() {
        return ResponseEntity.ok(shippingZoneService.activePriceMap());
    }

    @GetMapping("/governorates/price")
    public ResponseEntity<GovernoratePriceDto> getGovernoratePrice(@RequestParam String governorate) {
        return ResponseEntity.ok(shippingZoneService.priceFor(governorate));
    }

    @GetMapping("/{id}")
    public ShippingZone getById(@PathVariable Integer id) {
        return shippingZoneService.getShippingZone(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShippingZone create(@Valid @RequestBody ShippingZoneRequest request) {
        return shippingZoneService.create(request);
    }

    @PutMapping("/{id}")
    public ShippingZone update(@PathVariable Integer id, @Valid @RequestBody ShippingZoneRequest request) {
        return shippingZoneService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        shippingZoneService.deleteShippingZone(id);
    }
}
