package com.riansares.r4r.vector.config;

import com.riansares.r4r.ingestion.KnowledgeIngestionService;
import com.riansares.r4r.vector.PostgresAccessGate;
import com.riansares.r4r.vector.pgvector.PgVectorKnowledgeStore;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Method;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;

/** Opt-in only: ordinary PostgreSQL-only startup and the explicit S3 read-only profile are unchanged. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "r4r.rag.vector", name = {"failover-enabled", "s3vectors.enabled"}, havingValue = "true")
@EnableConfigurationProperties(PostgresFailoverProperties.class)
public class PostgresFailoverConfiguration {
    @Bean PostgresAccessGate postgresAccessGate(DataSource dataSource, ObjectProvider<Flyway> flyway,
            PostgresFailoverProperties properties, RagVectorProperties vector) {
        if (!"pgvector".equals(vector.primary())) throw new IllegalArgumentException("PostgreSQL failover requires pgvector primary");
        return new PostgresAccessGate(dataSource, flyway::getIfAvailable, properties);
    }
    @Bean FlywayMigrationStrategy recoverablePostgresMigrations(PostgresAccessGate gate) {
        return gate::migrateAtStartup;
    }
    @Bean static BeanFactoryPostProcessor deferRecoverablePgVector() {
        return factory -> {
            if (factory.containsBeanDefinition("vectorStore")) factory.getBeanDefinition("vectorStore").setLazyInit(true);
        };
    }
    @Bean static BeanPostProcessor boundedPostgresPool(ObjectProvider<PostgresFailoverProperties> settings) {
        return new BeanPostProcessor() {
            @Override public Object postProcessBeforeInitialization(Object bean, String name) {
                if (bean instanceof HikariDataSource pool) {
                    var properties = settings.getObject();
                    pool.setInitializationFailTimeout(-1);
                    pool.setMinimumIdle(0);
                    pool.setConnectionTimeout(properties.connectionTimeout().toMillis());
                    pool.setValidationTimeout(Math.min(1000, properties.connectionTimeout().toMillis() / 2));
                    if (!pool.getDataSourceProperties().containsKey("connectTimeout"))
                        pool.addDataSourceProperty("connectTimeout", Math.max(1, properties.connectionTimeout().toSeconds()));
                    if (!pool.getDataSourceProperties().containsKey("socketTimeout"))
                        pool.addDataSourceProperty("socketTimeout", properties.socketTimeout().toSeconds());
                }
                return bean;
            }
        };
    }
    @Bean @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    Advisor validatePostgresBeforeTransactions(ObjectProvider<PostgresAccessGate> gates) {
        var pointcut = new StaticMethodMatcherPointcut() {
            @Override public boolean matches(Method method, Class<?> target) {
                return KnowledgeIngestionService.class.isAssignableFrom(target) && method.getName().equals("ingest")
                        || PgVectorKnowledgeStore.class.isAssignableFrom(target)
                        && (method.getName().equals("index") || method.getName().equals("replaceSource") || method.getName().equals("search"));
            }
        };
        var advisor = new DefaultPointcutAdvisor(pointcut, (MethodInterceptor) invocation -> {
            var gate = gates.getObject();
            if (invocation.getMethod().getName().equals("search")) gate.beforeRead();
            else gate.beforeWrite();
            try { return invocation.proceed(); }
            catch (RuntimeException failure) { gate.recordFailure(failure); throw failure; }
        });
        advisor.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return advisor;
    }
}
