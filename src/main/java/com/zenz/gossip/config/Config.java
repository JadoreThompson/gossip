package com.zenz.gossip.config;

import org.springframework.beans.factory.annotation.Value;

public class Config {

    @Value("${node.id}")
    public static String nodeId;
}
