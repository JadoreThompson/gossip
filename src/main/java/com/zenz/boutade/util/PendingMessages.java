package com.zenz.boutade.util;

import com.zenz.boutade.message.Message;

import java.util.*;


public class PendingMessages implements Iterable<Message> {

    private final Map<UUID, Message> messages = new HashMap<>();

    public void add(final Message message) {
        synchronized (this) {
            if (!messages.containsKey(message.getId())) {
                messages.put(message.getId(), message);
            }
        }
    }

    public boolean remove(final Message message) {
        synchronized (this) {
            return messages.remove(message.getId()) != null;
        }
    }

    public Message get(final UUID id) {
        synchronized (this) {
            return messages.get(id);
        }
    }

    @Override
    public Iterator<Message> iterator() {
        synchronized (this) {
            return new ArrayList<>(messages.values()).iterator();
        }
    }

    @Override
    public String toString() {
        return "PendingMessages{" +
                "messages=" + messages.values() +
                '}';
    }
}