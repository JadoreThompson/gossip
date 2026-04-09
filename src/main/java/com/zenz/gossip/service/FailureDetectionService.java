package com.zenz.gossip.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

@Service
public class FailureDetectionService {

    @PostConstruct
    public void init() {

    }

    @PreDestroy
    public void destroy() {}
}
