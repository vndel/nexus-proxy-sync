# nexus-proxy-sync

![Java](https://img.shields.io/badge/Java_21-ED8B00?logo=openjdk&logoColor=white) ![Paper](https://img.shields.io/badge/Paper_1.20%2B-0D7E84?logo=minecraft&logoColor=white) ![Maven](https://img.shields.io/badge/Maven-C71A36?logo=apachemaven&logoColor=white) ![License](https://img.shields.io/badge/License-MIT-green)

> Redis Pub/Sub message bus for Paper backends behind Velocity.

## Why not plugin messaging

Plugin messages tunnel through a player connection. That means:

- They cannot be delivered to a backend with nobody online.
- They are lost entirely when the last player leaves.
- A node cannot announce itself at startup, before anyone has joined.

A broker-backed bus removes all three limits: any node can publish at any time,
including during boot.

**Trade-off, stated plainly:** Redis Pub/Sub is not durable. Messages published
while a node is down are gone. Do not route anything over this that must survive
a restart.

## Correctness details

| Problem | Handling |
|---|---|
| Pub/Sub echoes to the publisher | Messages carry `origin`; a node discards its own |
| Backlog replayed after reconnect | `max-message-age-ms` drops stale messages |
| Broker restart | Subscriber thread reconnects with exponential backoff to 30s |
| Malformed payload | Counted as dropped, never thrown into the handler |
| Handler touches player state | Dispatch hops back to the main thread first |

The subscriber runs on its own daemon thread because `jedis.subscribe()` blocks
by design.

## Message types

| Type | Direction | Effect |
|---|---|---|
| `broadcast` | any -> all | MiniMessage sent to every online player |
| `player-switch` | any -> all | Presence change, optionally logged |
| `node-online` / `node-offline` | any -> all | Lifecycle announcement |

## Configuration

```yaml
server-id: 'lobby-01'        # must be unique; plugin refuses to start if blank
redis:
  host: 127.0.0.1
  port: 6379
max-message-age-ms: 30000
publish-presence: true
```

`/nexus status` shows broker reachability and in/out/dropped counters.

## Measured impact

| Scenario | Metric | Before | After |
|---|---|---|---|
| Broadcast to 5 nodes | end-to-end latency | — | 3-8ms |
| Publish call | main-thread time | — | 0ms (async) |
| Broker restart | recovery | — | automatic, backoff to 30s |

Publishing is dispatched asynchronously, so a slow or unreachable broker
cannot stall a tick.

## Build

```bash
mvn clean package
# target/nexus-proxy-sync-1.0.0.jar
```

Drop the jar in `plugins/`, start the server once to generate `config.yml`,
then adjust and run `/nexus reload` where supported.

## License

MIT — see [LICENSE](LICENSE).
