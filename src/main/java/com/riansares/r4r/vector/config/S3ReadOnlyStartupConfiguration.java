package com.riansares.r4r.vector.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;

/** Defer only the PG vector bean in the explicit recovery profile, never all JDBC. */
@Configuration(proxyBeanMethods=false)
@Profile("s3-readonly")
public class S3ReadOnlyStartupConfiguration {
    @Bean static BeanFactoryPostProcessor deferPgVectorInitialization() {
        return factory -> {
            if (factory.containsBeanDefinition("vectorStore")) factory.getBeanDefinition("vectorStore").setLazyInit(true);
        };
    }
}
