package com.warsha.erp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled jobs (today only the nightly tracking cleanup). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
