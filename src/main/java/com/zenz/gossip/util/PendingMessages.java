package com.zenz.gossip.util;

import com.zenz.gossip.message.Message;

import java.util.*;

public class PendingMessages implements Iterable<Message> {

    private final Set<Message> messages = new HashSet<>();

    public void add(final Message message) {
        synchronized (messages) {
            messages.add(message);
        }
    }

    public void clear() {
        synchronized (messages) {
            messages.clear();
        }
    }

    public boolean remove(final Message message) {
        synchronized (messages) {
            return messages.remove(message);
        }
    }

    public List<Message> toList() {
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    @Override
    public Iterator<Message> iterator() {
        return messages.iterator();
    }
}
