package com.flowora.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.beans.factory.annotation.Value;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.flowora.erp.common.api.LegacyApiReadOnlyFilter;
import com.flowora.erp.common.api.RequestIdFilter;

@SpringBootApplication
@ComponentScan(excludeFilters = @ComponentScan.Filter(
        type = FilterType.CUSTOM, classes = {TypeExcludeFilter.class,
                AutoConfigurationExcludeFilter.class, StandaloneComponentFilter.class}))
@EnableScheduling
@EnableAsync
public class FloworaApiApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(FloworaApiApplication.class);
        application.setDefaultProperties(Map.of("flowora.standalone.minimal", "true"));
        application.run(args);
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
