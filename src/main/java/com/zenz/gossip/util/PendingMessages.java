package com.zenz.gossip.util;

import com.zenz.gossip.message.Message;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class PendingMessages implements Iterable<Message> {

    private final Set<Message> messages = new HashSet<>();

    public void add(final Message message) {
        synchronized (this) {
            messages.add(message);
        }
    }

    public boolean remove(final Message message) {
        synchronized (this) {
            return messages.remove(message);
        }
    }

    @Override
    public Iterator<Message> iterator() {
        synchronized (this) {
            return new ArrayList<>(messages).iterator();
        }
    }

    @Override
    public String toString() {
        return "PendingMessages{" +
                "messages=" + messages +
                '}';
    }
}
