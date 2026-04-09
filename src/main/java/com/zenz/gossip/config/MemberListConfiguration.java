package com.zenz.gossip.config;

import com.zenz.gossip.util.MemberList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MemberListConfiguration {

    @Bean
    public MemberList memberList() {
        return new MemberList();
    }
}
