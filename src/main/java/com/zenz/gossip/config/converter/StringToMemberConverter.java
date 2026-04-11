package com.zenz.gossip.config.converter;

import com.zenz.gossip.util.Member;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

@Component
@ConfigurationPropertiesBinding
public class StringToMemberConverter implements Converter<String, Member> {

    @Override
    public Member convert(final String source) {
        final String[] parts = source.split(":");
        final String nodeId = parts[0];
        final String host = parts.length > 1 ? parts[1] : "localhost";
        final int port = parts.length > 2 ? Integer.parseInt(parts[2]) : 8080;

        return new Member(nodeId, new InetSocketAddress(host, port));
    }
}