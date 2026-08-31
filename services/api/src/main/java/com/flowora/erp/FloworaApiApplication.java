package com.flowora.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.flowora.erp.common.api.LegacyApiReadOnlyFilter;
import com.flowora.erp.common.api.RequestIdFilter;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class FloworaApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(FloworaApiApplication.class, args);
    }

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestIdFilter());
        registration.setOrder(Integer.MIN_VALUE);
        registration.addUrlPatterns("/api/*", "/actuator/*");
        return registration;
    }

    @Bean
    FilterRegistrationBean<LegacyApiReadOnlyFilter> legacyApiReadOnlyFilter(
            ObjectMapper objectMapper,
            @Value("${flowora.compatibility.v1-read-only:true}") boolean enabled
    ) {
        FilterRegistrationBean<LegacyApiReadOnlyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new LegacyApiReadOnlyFilter(objectMapper, enabled));
        registration.setOrder(Integer.MIN_VALUE + 1);
        registration.addUrlPatterns("/api/v1/*");
        return registration;
    }
}
