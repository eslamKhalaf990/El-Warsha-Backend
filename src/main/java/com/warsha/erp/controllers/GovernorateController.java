package com.warsha.erp.controllers;

import com.warsha.erp.dtos.GovernoratePriceDto;
import com.warsha.erp.services.EgyptGovernorates;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/governorates")
public class GovernorateController {

    @GetMapping
    public ResponseEntity<List<GovernoratePriceDto>> getAllGovernoratePrices() {
        return ResponseEntity.ok(EgyptGovernorates.getAllGovernoratePrices());
    }

    @GetMapping("/map")
    public ResponseEntity<Map<String, Double>> getAllGovernoratePricesMap() {
        return ResponseEntity.ok(EgyptGovernorates.getAllRatesMap());
    }

    @GetMapping("/price")
    public ResponseEntity<?> getGovernoratePrice(@RequestParam String governorate) {
        try {
            return ResponseEntity.ok(EgyptGovernorates.getGovernoratePrice(governorate));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
