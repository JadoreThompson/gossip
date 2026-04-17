package com.zenz.boutade.config.converter;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

@Component
@ConfigurationPropertiesBinding
public class StringToInetSocketAddressConverter implements Converter<String, InetSocketAddress> {

    @Override
    public InetSocketAddress convert(final String source) {
        final String[] parts = source.split(",");
        final String host = parts[0].trim();
        final int port = Integer.parseInt(parts[1].trim());

        return new InetSocketAddress(host, port);
    }
}