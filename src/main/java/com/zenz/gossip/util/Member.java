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

    private MemberStatus status = MemberStatus.ALIVE;

    private final Object incarnationLock = new Object();

    public long setIncarnation(final long incarnation) {
        synchronized (incarnationLock) {
            if (incarnation > this.incarnation) {
                this.incarnation = incarnation;
            }
            return this.incarnation;
        }
    }

    @Override
    public String toString() {
        return "Member{" +
                "nodeId='" + nodeId + '\'' +
                ", address=" + address +
                ", incarnation=" + incarnation +
                ", status=" + status +
                '}';
    }
}
