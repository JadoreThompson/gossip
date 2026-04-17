# Boutade

A Spring Boot-based implementation of the SWIM protocol for distributed systems failure detection and membership
management.

## Overview

### Features

- **Failure Detection**: Nodes are classified as ALIVE, SUSPICIOUS, or DEAD based on heartbeat and timeout thresholds
- **Membership Management**: Dynamic member list with support for nodes joining the cluster
- **Round-based Protocol**: Messages are versioned by "rounds" to ensure ordering and consistency
- **REST API**: HTTP endpoints for joining nodes, pinging, and retrieving cluster state
- **Incarnation Numbers**: Prevents against stale membership information through incarnation counters

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      GossipApplication                       │
│                    (Spring Boot Entry)                       │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Services    │    │    Config   │    │    Route    │
│              │    │             │    │             │
│ FailureDetection│  │ClusterConfig│   │ ApiController│
│   Service    │    │    ...      │    │    ...      │
└──────────────┘    └──────────────┘    └──────────────┘
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────────────────────────────────────────────────────┐
│                        Messages                               │
│                                                               │
│  Message (Interface)                                         │
│    ├── MemberAliveMessage                                    │
│    ├── MemberSuspiciousMessage                                │
│    ├── MemberDeadMessage                                     │
│    ├── RandomMessage                                         │
│    └── JoinMessage                                          │
│                                                               │
│  MessageType (Enum)                                          │
│    ALIVE, SUSPICIOUS, DEAD, JOIN, RANDOM                      │
└──────────────────────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────────────────────────┐
│                         Util                                  │
│                                                               │
│  Member, MemberList, MemberStatus, PendingMessages, Utils     │
└──────────────────────────────────────────────────────────────┘
```

### Key Components

- **ClusterConfig**: Manages node ID, incarnation number, failure timeout, and cluster members
- **FailureDetectionService**: Handles state transitions (ALIVE → SUSPICIOUS → DEAD)
- **ApiController**: REST endpoints for cluster operations
- **Message Types**: Polymorphic message handling via Jackson JsonSubTypes

### Message Flow

1. Nodes periodically send `PingRequest` to random peers
2. If a node doesn't respond to pings, it's marked `SUSPICIOUS`
3. After failure timeout, suspicious nodes are marked `DEAD`
4. New nodes join via `JoinRequest` disseminated as `JoinMessage` with incarnation number

## Extending Messages

To add a new message type:

1. **Implement the Message interface** in a new class:

```java
public class CustomMessage implements Message {
    private MessageType type = MessageType.CUSTOM;
    private long round;

    @Override
    public MessageType getType() {
        return type;
    }

    @Override
    public long getRound() {
        return round;
    }

    @Override
    public void setRound(long round) {
        this.round = round;
    }
}
```

2. **Add the type to the enum** in `MessageType.java` if needed.

3. **Register in JsonSubTypes** in `Message.java`:

```java

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MemberAliveMessage.class, name = "MEMBER_ALIVE"),
        @JsonSubTypes.Type(value = RandomMessage.class, name = "RANDOM_MESSAGE"),
        @JsonSubTypes.Type(value = MemberDeadMessage.class, name = "MEMBER_DEAD"),
        @JsonSubTypes.Type(value = MemberSuspiciousMessage.class, name = "MEMBER_SUSPICIOUS"),
        @JsonSubTypes.Type(value = JoinMessage.class, name = "MEMBER_JOIN"),
        @JsonSubTypes.Type(value = CustomMessage.class, name = "CUSTOM")
})
public interface Message {
    // ...
}
```

## Building and Running

```bash
./mvnw clean install
./mvnw spring-boot:run
```

Run a demo with a seed node and two non-seed nodes :

```bash
docker compose up -d
```

## Configuration

Configure via `application.properties` or environment variables:

| Property                 | Description                    | Default |
|--------------------------|--------------------------------|---------|
| `cluster.nodeId`         | Unique node identifier         | -       |
| `cluster.port`           | HTTP port                      | 8080    |
| `cluster.failureTimeout` | Failure detection timeout (ms) | -       |
| `cluster.members`        | List of seed members           | -       |

## API Endpoints

- `POST /boutade/join` - Join the cluster
- `POST /boutade/ping` - Ping a node
- `GET /boutade/members` - Get cluster membership
- `POST /boutade/messages` - Broadcast a message