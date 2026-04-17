package com.zenz.boutade.config;

import com.zenz.boutade.util.Member;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@Configuration
@ConfigurationProperties(prefix = "cluster")
public class ClusterConfig {

    private String nodeId;

    private long incarnation;

    private int failureTimeout;

    private List<Member> members;

    private long round;

    private int port = 8080;

    private final Object roundLock = new Object();
    private final Object incarnationLock = new Object();

    public long setIncarnation(final long incarnation) {
        synchronized (incarnationLock) {
            if (incarnation > this.incarnation) {
                this.incarnation = incarnation;
            }

            return this.incarnation;
        }
    }

    public long incrementRound() {
        synchronized (roundLock) {
            return ++round;
        }
    }
}