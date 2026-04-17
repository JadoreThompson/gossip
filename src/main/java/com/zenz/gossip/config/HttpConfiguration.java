package com.zenz.gossip.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class HttpConfiguration {

    @Bean
    public HttpClient httpClient() {
        final HttpClient.Builder client = HttpClient.newBuilder();
        client.connectTimeout(Duration.ofMillis(2000L));
        return client.build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
