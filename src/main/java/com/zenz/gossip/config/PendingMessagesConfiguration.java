package com.zenz.gossip.config;

import com.zenz.gossip.util.PendingMessages;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PendingMessagesConfiguration {

    @Bean
    public PendingMessages pendingMessages() {
        return new PendingMessages();
    }
}
