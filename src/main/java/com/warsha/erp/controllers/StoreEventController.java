package com.warsha.erp.controllers;

import com.warsha.erp.config.CustomerUserDetails;
import com.warsha.erp.dtos.TrackEventsRequest;
import com.warsha.erp.services.StoreTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Store visitor tracking.
 * <pre>
 *   POST /events    batch of visitor actions (+ cart snapshot); public, 204 even when dropped
 * </pre>
 * A customer token is optional: when present, the events are linked to that customer.
 */
@RestController
@RequestMapping("/events")
public class StoreEventController {

    private final StoreTrackingService trackingService;

    public StoreEventController(StoreTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void track(@Valid @RequestBody TrackEventsRequest request,
                      @AuthenticationPrincipal Object principal,
                      HttpServletRequest http) {
        Long customerId = principal instanceof CustomerUserDetails c ? c.getId() : null;
        trackingService.track(request, customerId, http.getHeader(HttpHeaders.USER_AGENT), clientIp(http));
    }

    /** The visitor's IP behind Cloudflare or another proxy; only used for rate limiting. */
    static String clientIp(HttpServletRequest http) {
        String cf = http.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) {
            return cf.trim();
        }
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
