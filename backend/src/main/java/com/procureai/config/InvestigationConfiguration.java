package com.procureai.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InvestigationConfiguration {

    @Bean
    public Clock investigationClock() {
        return Clock.systemUTC();
    }
}
