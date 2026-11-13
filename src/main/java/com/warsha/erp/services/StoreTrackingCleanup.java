package com.warsha.erp.services;

import com.warsha.erp.repository.StoreCartRepository;
import com.warsha.erp.repository.StoreEventRepository;
import com.warsha.erp.repository.StoreVisitorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Every night, deletes store events, carts and visitors older than {@code tracking.retention-days}
 * (365 by default). Events go in small batches so the table is never locked for long.
 */
@Slf4j
@Component
public class StoreTrackingCleanup {

    static final int BATCH_SIZE = 5_000;

    private final StoreEventRepository events;
    private final StoreCartRepository carts;
    private final StoreVisitorRepository visitors;
    private final int retentionDays;

    public StoreTrackingCleanup(StoreEventRepository events, StoreCartRepository carts,
                                StoreVisitorRepository visitors,
                                @Value("${tracking.retention-days:365}") int retentionDays) {
        this.events = events;
        this.carts = carts;
        this.visitors = visitors;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${tracking.cleanup-cron:0 30 3 * * *}")
    public void purge() {
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        long deleted = 0;
        int batch;
        do {
            batch = events.deleteOlderThan(before, BATCH_SIZE);
            deleted += batch;
        } while (batch == BATCH_SIZE);
        int cartsDeleted = carts.deleteOlderThan(before);
        int visitorsDeleted = visitors.deleteOlderThan(before);
        if (deleted > 0 || cartsDeleted > 0 || visitorsDeleted > 0) {
            log.info("Tracking cleanup removed {} events, {} carts and {} visitors older than {}",
                    deleted, cartsDeleted, visitorsDeleted, before);
        }
    }
}
