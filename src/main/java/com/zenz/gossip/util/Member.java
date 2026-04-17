package com.zenz.gossip.util;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.net.InetSocketAddress;

@Getter
@Setter
@RequiredArgsConstructor
@EqualsAndHashCode(of = "nodeId")
public class Member {

    private final String nodeId;

    private final InetSocketAddress address;

    private long incarnation;

    private MemberStatus status = MemberStatus.ALIVE;

    private final Object incarnationLock = new Object();

    /**
     * The round the node was found suspicious
     */
    private long round = -1;

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
