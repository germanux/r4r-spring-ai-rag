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
                .allowedHeaders("Content-Type", "Accept", "X-R4R-Visitor", "X-R4R-Session", "X-R4R-Analytics-Consent", "X-R4R-History-Consent", "X-R4R-Request-Id")
                .exposedHeaders("Retry-After", "X-R4R-RateLimit-Min-Interval-Seconds")
                .maxAge(3600);
    }

    @Bean
    FilterRegistrationBean<RagApiSecurityFilter> ragApiSecurityFilter(org.springframework.beans.factory.ObjectProvider<com.riansares.r4r.usage.AssistantUsage> usage,
            @org.springframework.beans.factory.annotation.Value("${r4r.api-security.trusted-proxy-addresses:}") String trustedProxies) {
        var filter = new RagApiSecurityFilter(securityProperties);
        filter.setUsage(usage.getIfAvailable());
        filter.setTrustedProxyAddresses(trustedProxies);
        FilterRegistrationBean<RagApiSecurityFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/rag/*");
        registration.setAsyncSupported(true);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
