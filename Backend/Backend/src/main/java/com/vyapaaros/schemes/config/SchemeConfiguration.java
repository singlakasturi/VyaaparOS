package com.vyapaaros.schemes.config;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class SchemeConfiguration {
    @Bean public Clock schemeClock() { return Clock.systemUTC(); }
}
