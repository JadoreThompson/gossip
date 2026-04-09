package com.zenz.gossip.util;


import java.util.HashSet;
import java.util.Set;

public class MemberList {

    private final Set<Member> members =  new HashSet<>();

    public MemberList() {}

    public void add(final Member member) {
        synchronized (members) {
            members.add(member);
        }
    }

    public boolean remove(final Member member) {
        synchronized (members) {
            return members.remove(member);
        }
    }

    public boolean contains(final Member member) {
        synchronized (members) {
            return members.contains(member);
        }
    }
}
