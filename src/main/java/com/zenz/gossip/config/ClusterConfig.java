package com.zenz.gossip.config;

import com.zenz.gossip.util.Member;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.List;

@Getter
@Setter
@Component
@Configuration
@ConfigurationProperties(prefix = "cluster")
public class ClusterConfig {

    private String nodeId;

    private InetSocketAddress address;

    private long incarnation;

    private int failureTimeout;

    private List<Member> members;
}