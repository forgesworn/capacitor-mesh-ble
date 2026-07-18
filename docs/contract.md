# Mesh BLE transport contract

`capacitor-mesh-ble` is the shared native transport used by Forgeworn Capacitor products. It owns BLE discovery, GATT links, chunking, bounded reassembly, deduplication, relay, lifecycle and status telemetry. It does not own product identity, cryptography, contact semantics, matching, meeting logic or UI.

## Boundary

| Shared transport | Supplied by each product |
| --- | --- |
| Android central/peripheral radio | `room` domain separator |
| iOS central/peripheral radio | `selfId` product peer identifier |
| Android single-link role arbitration | `serviceUuid` discovery scope/rotation policy |
| Frame chunking and reassembly | `hops` relay policy |
| Bounded deduplication and relay | Signed/encrypted payload carried as opaque `data` |
| Optional Android foreground anchor | Notification wording and whether the anchor is enabled |
| Optional iOS background keepalive | Which safe product frame may be replayed |

`room`, `selfId`, `serviceUuid` and frame `data` are never derived by the plugin. A product must not place unencrypted sensitive data in them merely because the transport is nearby-only.

## JavaScript API

- `start(options)` validates and starts both BLE roles.
- `stop()` tears down scanners, advertisers, links, queues and background work.
- `broadcast({ data })` queues an opaque frame for all writable peers.
- `send({ peer, data })` uses a learned peer mapping when possible and otherwise floods safely.
- `setKeepaliveFrame({ data })` sets or clears the iOS background frame. It is accepted as a no-op on Android.
- `getStatus()` returns a stable cross-platform status shape.
- `frame` emits `{ from, data }`; `status` emits the same shape as `getStatus()`.

## Wire envelope

The transport serialises this JSON envelope before chunking:

```json
{"v":1,"r":"room","t":"*","f":"peer-id","id":"uuid","h":3,"d":"opaque-product-frame"}
```

- `v`: envelope version, currently `1`
- `r`: exact room/domain separator
- `t`: peer id or `*` for broadcast
- `f`: origin peer id
- `id`: globally unique deduplication id, preserved by relays
- `h`: remaining relay hops, decremented by each relay
- `d`: opaque product frame

Unknown versions, wrong rooms, self-origin frames, malformed fields, duplicate ids, oversized envelopes and invalid chunks fail closed.

## Chunk header

Every GATT write/notification starts with this eight-byte header:

| Byte | Meaning |
| --- | --- |
| 0 | Magic `0x4d` |
| 1 | Chunk version `0x01` |
| 2-5 | Big-endian unsigned message id |
| 6 | Zero-based chunk index |
| 7 | Total chunk count, 1-255 |

Frozen fixture for message id `0x01020304`, first and only chunk:

```text
4d 01 01 02 03 04 00 01
```

The Android JVM suite freezes this header and exercises out-of-order bidirectional reassembly, malformed input, oversize rejection, bounded deduplication and deterministic connection arbitration.

## Defaults and bounds

| Option | Default | Accepted range |
| --- | ---: | ---: |
| `hops` | 0 | 0-8 |
| `maxEnvelopeBytes` | 8192 | 256-65536 |
| `maxSeenIds` | 512 | 32-8192 |
| `reassemblyTtlMs` | 30000 | 1000-300000 |
| `maxClientLinks` (Android) | 3 | 1-8 |
| `connectCooldownMs` (Android) | 4000 | 250-60000 |
| `connectThrottleMs` (Android) | 1500 | 100-30000 |

Values outside a safety range are clamped. Android requests MTU 247 and caps each chunk payload at 160 bytes; both sides accept smaller negotiated writes.

## Platform lifecycle

Android uses a write characteristic and a reverse notify characteristic so two Android peers need one physical GATT connection. A random advertised tiebreak selects the initiator and connection caps/backoff contain churn. Products that enable `foregroundService` receive a connected-device foreground service with product-supplied notification copy.

iOS declares and uses CoreBluetooth central/peripheral roles. `setKeepaliveFrame` lets the host provide a safe, already-signed liveness frame that native code may repeat while WebKit is suspended. Payload interpretation remains entirely in the host.

## Compatibility policy

Changes to the envelope fields, chunk header, characteristic UUIDs, event shapes or required start options are protocol changes. Additive options and status fields are library changes. Protocol changes require frozen fixtures and old/new interoperability tests before a major release.
