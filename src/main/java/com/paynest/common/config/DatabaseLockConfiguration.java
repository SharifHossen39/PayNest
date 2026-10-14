package com.paynest.common.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DatabaseLockProperties.class)
public class DatabaseLockConfiguration {

    @Bean
    static BeanPostProcessor databaseLockTimeoutInitializer(
            ObjectProvider<DatabaseLockProperties> properties
    ) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof HikariDataSource dataSource) {
                    long timeoutMilliseconds = properties.getObject().lockTimeout().toMillis();
                    String initializationSql = "SET lock_timeout TO '"
                            + timeoutMilliseconds + "ms'";
                    dataSource.setConnectionInitSql(initializationSql);
                }
                return bean;
            }
        };
    }
}
