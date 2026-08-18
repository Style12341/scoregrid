package com.scoregrid.score.shared.config;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class ClientConfig {

    @Bean
    @Primary
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
