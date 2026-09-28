package com.riansares.r4r.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(RagApiSecurityProperties.class)
public class WebConfiguration implements WebMvcConfigurer {

    private final RagApiSecurityProperties securityProperties;

    public WebConfiguration(RagApiSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(securityProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Bean
    FilterRegistrationBean<RagApiSecurityFilter> ragApiSecurityFilter() {
        FilterRegistrationBean<RagApiSecurityFilter> registration =
                new FilterRegistrationBean<>(new RagApiSecurityFilter(securityProperties));
        registration.addUrlPatterns("/api/rag/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
