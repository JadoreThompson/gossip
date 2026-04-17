# Boutade

A Spring Boot-based implementation of the SWIM protocol for distributed systems failure detection and membership
management.

## Inspiration

My team were running 20 services and planning to scale to 50+ services, and we needed a reliable way to distribute
configuration and metadata updates across the cluster. The key constraint was that the solution had to be horizontally
scalable and fault-tolerant, which ruled out introducing a central manager or coordination node. Any such component
would become a bottleneck and a single point of failure as the system grew.

We instead adopted a gossip based dissemination model inspired by protocols like
the [SWIM protocol](https://www.cs.cornell.edu/projects/Quicksilver/public_pdfs/SWIM.pdf). Each node maintains
a partial view of the cluster and periodically exchanges state with a small, random subset of peers. Configuration
changes and metadata updates are piggybacked onto these exchanges and propagate through the system in an epidemic
fashion.

This approach gave us several advantages. It scales naturally because propagation time grows logarithmically with the
number of nodes, so moving from 20 to 50 services does not materially impact latency. It is resilient because there is
no central dependency, so nodes can fail or restart without disrupting the overall system. It also avoids coordination
overhead since updates are eventually consistent rather than strongly consistent, which was acceptable for our use case.

To make this reliable in practice, we layered in a few important mechanisms. Each update is assigned a unique identifier
and a version or incarnation, allowing nodes to deduplicate messages and resolve conflicts deterministically. Updates
are retransmitted for a bounded number of rounds, typically proportional to log N, to ensure high probability delivery
without flooding the network indefinitely. We also included lightweight health checking so that nodes could avoid
disseminating to unresponsive peers.

The result was a system where configuration changes could be introduced at any node and would rapidly and reliably
converge across the cluster, without the operational and scaling concerns of a centralized solution.

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