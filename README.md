# capacitor-mesh-ble

Canonical configurable BLE mesh transport for Capacitor apps

The plugin provides Android and iOS radios, chunking, bounded reassembly, relay, background lifecycle hooks and a stable Capacitor API. Product identity, discovery UUID policy, cryptography and payload semantics stay in the consuming app. See [the transport contract](docs/contract.md).

## Install

The plugin is not on npm yet. Install it from Git, pinned to an immutable commit:

```bash
npm install 'git+https://github.com/forgesworn/capacitor-mesh-ble.git#<commit-sha>'
```

Sync native files

```bash
npx cap sync
```

## API

<docgen-index>

* [`start(...)`](#start)
* [`stop()`](#stop)
* [`broadcast(...)`](#broadcast)
* [`send(...)`](#send)
* [`setKeepaliveFrame(...)`](#setkeepaliveframe)
* [`getStatus()`](#getstatus)
* [`startRssiSampling(...)`](#startrssisampling)
* [`stopRssiSampling()`](#stoprssisampling)
* [`addListener('frame', ...)`](#addlistenerframe-)
* [`addListener('peer', ...)`](#addlistenerpeer-)
* [`addListener('status', ...)`](#addlistenerstatus-)
* [`addListener('rssi', ...)`](#addlistenerrssi-)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### start(...)

```typescript
start(options: MeshBleStartOptions) => Promise<void>
```

| Param         | Type                                                                |
| ------------- | ------------------------------------------------------------------- |
| **`options`** | <code><a href="#meshblestartoptions">MeshBleStartOptions</a></code> |

--------------------


### stop()

```typescript
stop() => Promise<void>
```

--------------------


### broadcast(...)

```typescript
broadcast(options: { data: string; }) => Promise<{ queuedPeers: number; }>
```

| Param         | Type                           |
| ------------- | ------------------------------ |
| **`options`** | <code>{ data: string; }</code> |

**Returns:** <code>Promise&lt;{ queuedPeers: number; }&gt;</code>

--------------------


### send(...)

```typescript
send(options: { peer: string; data: string; }) => Promise<{ queuedPeers: number; }>
```

| Param         | Type                                         |
| ------------- | -------------------------------------------- |
| **`options`** | <code>{ peer: string; data: string; }</code> |

**Returns:** <code>Promise&lt;{ queuedPeers: number; }&gt;</code>

--------------------


### setKeepaliveFrame(...)

```typescript
setKeepaliveFrame(options: { data: string | null; }) => Promise<void>
```

iOS may repeat this frame while JavaScript is suspended in the background.

| Param         | Type                                   |
| ------------- | -------------------------------------- |
| **`options`** | <code>{ data: string \| null; }</code> |

--------------------


### getStatus()

```typescript
getStatus() => Promise<MeshBleStatus>
```

**Returns:** <code>Promise&lt;<a href="#meshblestatus">MeshBleStatus</a>&gt;</code>

--------------------


### startRssiSampling(...)

```typescript
startRssiSampling(options?: MeshBleStartRssiSamplingOptions | undefined) => Promise<void>
```

Start periodic RSSI sampling: polls connected GATT links and attributes scanned
advertisement RSSI, for peers already identified via a prior frame exchange.
Off by default (battery cost). Idempotent — calling this again while sampling
just updates the interval. Stops automatically when the transport is stopped.

**Android only.** iOS has no such method in its `pluginMethods` list, so a call
there is REJECTED by the native bridge (not silently ignored) — callers must
guard with try/catch if they run cross-platform. The web implementation, by
contrast, DOES implement this method, as an inert no-op: there is no BLE
hardware to sample, so it resolves rather than rejecting. NB the
security-relevant honesty gate (no attribution while relaying) is likewise
Android-only; if iOS ever grows sampling, the gate MUST be ported in the same
change or iOS emits dishonest attributions.

| Param         | Type                                                                                        |
| ------------- | ------------------------------------------------------------------------------------------- |
| **`options`** | <code><a href="#meshblestartrssisamplingoptions">MeshBleStartRssiSamplingOptions</a></code> |

--------------------


### stopRssiSampling()

```typescript
stopRssiSampling() => Promise<void>
```

Stop RSSI sampling. Idempotent — safe to call when not sampling. Shares
startRssiSampling's platform behaviour: Android runs the real stop, iOS
rejects (bridge-level, method not implemented), web resolves as a no-op.

--------------------


### addListener('frame', ...)

```typescript
addListener(eventName: 'frame', listenerFunc: (event: MeshBleFrameEvent) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                                |
| ------------------ | ----------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'frame'</code>                                                                |
| **`listenerFunc`** | <code>(event: <a href="#meshbleframeevent">MeshBleFrameEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('peer', ...)

```typescript
addListener(eventName: 'peer', listenerFunc: (event: MeshBlePeerEvent) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                              |
| ------------------ | --------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'peer'</code>                                                               |
| **`listenerFunc`** | <code>(event: <a href="#meshblepeerevent">MeshBlePeerEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('status', ...)

```typescript
addListener(eventName: 'status', listenerFunc: (status: MeshBleStatus) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                         |
| ------------------ | ---------------------------------------------------------------------------- |
| **`eventName`**    | <code>'status'</code>                                                        |
| **`listenerFunc`** | <code>(status: <a href="#meshblestatus">MeshBleStatus</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('rssi', ...)

```typescript
addListener(eventName: 'rssi', listenerFunc: (event: MeshBleRssiSample) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                                |
| ------------------ | ----------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'rssi'</code>                                                                 |
| **`listenerFunc`** | <code>(event: <a href="#meshblerssisample">MeshBleRssiSample</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

--------------------


### Interfaces


#### MeshBleStartOptions

| Prop                    | Type                  | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| ----------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **`room`**              | <code>string</code>   | Product-defined room/domain separator carried inside every envelope.                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| **`selfId`**            | <code>string</code>   | Product-defined local peer identifier, revealed only after a GATT link.                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| **`serviceUuid`**       | <code>string</code>   | Runtime discovery/GATT UUID. The plugin advertises and hosts its GATT service under exactly this one UUID. The plugin never derives or rotates it.                                                                                                                                                                                                                                                                                                                                                                                               |
| **`scanUuids`**         | <code>string[]</code> | Optional set of service UUIDs the scanner filters for, when a product rotates its `serviceUuid` on a time window and must still discover peers a window or two away (clock skew, a rotation boundary crossed at slightly different moments). Match semantics are OR: a peer advertising ANY of these is a candidate. `serviceUuid` is always implicitly included. Omit (or leave empty) for the classic single-UUID behaviour, where the plugin scans for exactly `serviceUuid`. The plugin advertises only `serviceUuid`, never this whole set. |
| **`hops`**              | <code>number</code>   | Maximum relay hops for locally-originated frames. Zero disables relaying.                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **`foregroundService`** | <code>boolean</code>  | Keep the Android radio anchored in a connected-device foreground service.                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **`notificationTitle`** | <code>string</code>   | Android foreground-service notification title.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **`notificationText`**  | <code>string</code>   | Android foreground-service notification body.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| **`maxEnvelopeBytes`**  | <code>number</code>   | Hard ceiling for a UTF-8 envelope before chunking. Default: 8192.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| **`maxSeenIds`**        | <code>number</code>   | Deduplication window. Default: 512 envelope identifiers.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **`reassemblyTtlMs`**   | <code>number</code>   | Incomplete chunk assembly lifetime. Default: 30000ms.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| **`maxClientLinks`**    | <code>number</code>   | Android client-role GATT link cap. Default: 3.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **`connectCooldownMs`** | <code>number</code>   | Android reconnect backoff per BLE address. Default: 4000ms.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| **`connectThrottleMs`** | <code>number</code>   | Android global connection-attempt throttle. Default: 1500ms.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |


#### MeshBleStatus

| Prop                     | Type                                     | Description                                                                                                                              |
| ------------------------ | ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| **`native`**             | <code>boolean</code>                     |                                                                                                                                          |
| **`platform`**           | <code>'android' \| 'ios' \| 'web'</code> |                                                                                                                                          |
| **`supported`**          | <code>boolean</code>                     |                                                                                                                                          |
| **`running`**            | <code>boolean</code>                     |                                                                                                                                          |
| **`bluetooth`**          | <code>string</code>                      |                                                                                                                                          |
| **`permissions`**        | <code>string</code>                      |                                                                                                                                          |
| **`missingPermissions`** | <code>string[]</code>                    |                                                                                                                                          |
| **`room`**               | <code>string \| null</code>              |                                                                                                                                          |
| **`selfId`**             | <code>string \| null</code>              |                                                                                                                                          |
| **`serviceUuid`**        | <code>string \| null</code>              |                                                                                                                                          |
| **`scanUuids`**          | <code>string[]</code>                    | The UUID set the scanner is filtering for (includes `serviceUuid`). Single entry unless the product supplied a wider `scanUuids` window. |
| **`advertising`**        | <code>boolean</code>                     |                                                                                                                                          |
| **`scanning`**           | <code>boolean</code>                     |                                                                                                                                          |
| **`gattServer`**         | <code>boolean</code>                     |                                                                                                                                          |
| **`connectedPeers`**     | <code>number</code>                      |                                                                                                                                          |
| **`writablePeers`**      | <code>number</code>                      |                                                                                                                                          |
| **`knownPeers`**         | <code>number</code>                      |                                                                                                                                          |
| **`queuedChunks`**       | <code>number</code>                      |                                                                                                                                          |
| **`txFrames`**           | <code>number</code>                      |                                                                                                                                          |
| **`txChunks`**           | <code>number</code>                      |                                                                                                                                          |
| **`rxFrames`**           | <code>number</code>                      |                                                                                                                                          |
| **`rxChunks`**           | <code>number</code>                      |                                                                                                                                          |
| **`droppedFrames`**      | <code>number</code>                      |                                                                                                                                          |
| **`relayedFrames`**      | <code>number</code>                      |                                                                                                                                          |
| **`lastError`**          | <code>string \| null</code>              |                                                                                                                                          |
| **`peers`**              | <code>MeshBlePeerStatus[]</code>         |                                                                                                                                          |
| **`updatedAt`**          | <code>number</code>                      |                                                                                                                                          |


#### MeshBlePeerStatus

| Prop               | Type                  |
| ------------------ | --------------------- |
| **`address`**      | <code>string</code>   |
| **`peerIds`**      | <code>string[]</code> |
| **`connected`**    | <code>boolean</code>  |
| **`writable`**     | <code>boolean</code>  |
| **`mtu`**          | <code>number</code>   |
| **`queuedChunks`** | <code>number</code>   |
| **`writing`**      | <code>boolean</code>  |


#### MeshBleStartRssiSamplingOptions

| Prop             | Type                | Description                                                |
| ---------------- | ------------------- | ---------------------------------------------------------- |
| **`intervalMs`** | <code>number</code> | Sampling interval in ms. Default: 2000, bounded 500-10000. |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### MeshBleFrameEvent

| Prop       | Type                |
| ---------- | ------------------- |
| **`from`** | <code>string</code> |
| **`data`** | <code>string</code> |


#### MeshBlePeerEvent

A product peer became reachable through a learned BLE next hop, or that route was lost.

| Prop            | Type                 |
| --------------- | -------------------- |
| **`peer`**      | <code>string</code>  |
| **`connected`** | <code>boolean</code> |


#### MeshBleRssiSample

A raw RSSI sample, attributed to the mesh peer id bound to the MAC it was observed
on. The binding comes from a prior in-room frame exchange, and attribution is only
emitted on a DIRECT, non-relaying link (discreet mode): while this device relays
(crowd/mesh mode) a bound id can belong to a relayer's MAC, or be injected over the
keyless crowd UUID, so no `rssi` event is emitted at all. It is therefore a
proximity hint on a trusted-neighbour link, NOT a cryptographic identity proof —
a consumer must not treat it as authentication. Banding or distance estimation is a
product concern; this plugin reports dBm only. Emitted on Android only for now.

| Prop          | Type                            | Description                                                                                                                                            |
| ------------- | ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **`peer`**    | <code>string</code>             | The mesh peer id this sample is attributed to (bound via a prior direct frame exchange; only emitted on a non-relaying link — see the interface note). |
| **`address`** | <code>string</code>             | The BLE MAC address the sample was observed at.                                                                                                        |
| **`rssi`**    | <code>number</code>             | Signal strength in dBm.                                                                                                                                |
| **`source`**  | <code>'gatt' \| 'advert'</code> | Whether the reading came from a live GATT link or a scanned advertisement.                                                                             |
| **`at`**      | <code>number</code>             | Epoch ms when the sample was taken.                                                                                                                    |

</docgen-api>
