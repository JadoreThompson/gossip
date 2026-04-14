package com.zenz.gossip.util;


import java.util.*;

public class MemberList {

    private final Random random = new Random();
    private final Set<Member> members = new HashSet<>();

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

    public Member get(final String nodeId) {
        synchronized (members) {
            for (Member member : members) {
                if (member.getNodeId().equals(nodeId)) {
                    return member;
                }
            }
        }
        return null;
    }

    public Member getRandom() {
        synchronized (members) {
            if (members.isEmpty()) {
                return null;
            }
            final List<Member> members = new ArrayList<>(this.members);
            final int index = random.nextInt(members.size());
            return members.get(index);
        }
    }

    public boolean contains(final Member member) {
        synchronized (members) {
            return members.contains(member);
        }
    }

    public void addAll(final Collection<Member> member) {
        synchronized (members) {
            members.addAll(member);
        }
    }

    public int size() {
        synchronized (members) {
            return members.size();
        }
    }
}
