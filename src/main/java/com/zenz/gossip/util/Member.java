package com.zenz.gossip.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.net.InetSocketAddress;

@Getter
@Setter
@RequiredArgsConstructor
public class Member {

    private final String nodeId;

    private final InetSocketAddress address;

    private long incarnation;

    private MemberStatus status;
}
