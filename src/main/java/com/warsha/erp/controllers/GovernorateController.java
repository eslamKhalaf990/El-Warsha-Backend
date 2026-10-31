package com.warsha.erp.controllers;

import com.warsha.erp.dtos.GovernoratePriceDto;
import com.warsha.erp.services.ShippingZoneService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/governorates")
public class GovernorateController {

    // Prices come from the ShippingZones table the ERP edits
    private final ShippingZoneService shippingZoneService;

    public GovernorateController(ShippingZoneService shippingZoneService) {
        this.shippingZoneService = shippingZoneService;
    }

    @GetMapping
    public ResponseEntity<List<GovernoratePriceDto>> getAllGovernoratePrices() {
        return ResponseEntity.ok(shippingZoneService.activePrices());
    }

    @GetMapping("/map")
    public ResponseEntity<Map<String, Double>> getAllGovernoratePricesMap() {
        return ResponseEntity.ok(shippingZoneService.activePriceMap());
    }

    @GetMapping("/price")
    public ResponseEntity<?> getGovernoratePrice(@RequestParam String governorate) {
        return ResponseEntity.ok(shippingZoneService.priceFor(governorate));
    }
}
