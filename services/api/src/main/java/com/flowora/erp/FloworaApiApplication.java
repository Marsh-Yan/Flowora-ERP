package com.flowora.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import com.flowora.erp.common.api.RequestIdFilter;

@SpringBootApplication
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
}
