package com.warsha.erp.services;

import com.warsha.erp.repository.AnalyticsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Spring must be able to build the service on its own (it has a second, test-only constructor). */
class AnalyticsServiceWiringTest {

    @Test
    void springPicksTheRepositoryConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AnalyticsRepository.class, () -> mock(AnalyticsRepository.class));
            context.register(AnalyticsService.class);
            context.refresh();

            assertThat(context.getBean(AnalyticsService.class)).isNotNull();
        }
    }
}
