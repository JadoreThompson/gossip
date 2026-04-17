package com.zenz.gossip.util;

import java.util.*;

public class MemberList implements Iterable<Member> {

    private final Random random = new Random();
    private final Map<String, Member> membersMap = new HashMap<>();

    public void add(final Member member) {
        synchronized (this) {
            membersMap.put(member.getNodeId(), member);
        }
    }

    public boolean remove(final Member member) {
        synchronized (this) {
            return membersMap.remove(member.getNodeId()) != null;
        }
    }

    public Member get(final String nodeId) {
        synchronized (this) {
            return membersMap.get(nodeId);
        }
    }

    /**
     * Used for debugging
     */
    public List<Member> getAll() {
        synchronized (this) {
            return new ArrayList<>(membersMap.values());
        }
    }

    public Member getRandom() {
        synchronized (this) {
            if (membersMap.isEmpty()) {
                return null;
            }
            final List<Member> members = new ArrayList<>(membersMap.values());
            final int index = random.nextInt(members.size());
            return members.get(index);
        }
    }

    public boolean contains(final Member member) {
        synchronized (this) {
            return membersMap.containsKey(member.getNodeId());
        }
    }

    public boolean contains(final String nodeId) {
        synchronized (this) {
            return membersMap.containsKey(nodeId);
        }
    }

    public void addAll(final Collection<Member> member) {
        synchronized (this) {
            for (Member m : member) {
                membersMap.put(m.getNodeId(), m);
            }
        }
    }

    public int size() {
        synchronized (this) {
            return membersMap.size();
        }
    }

    public Iterator<Member> iterator() {
        return new ArrayList<>(membersMap.values()).iterator();
    }
}