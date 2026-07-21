# capacitor-mesh-ble

Canonical configurable BLE mesh transport for Capacitor apps

The plugin provides Android and iOS radios, chunking, bounded reassembly, relay, background lifecycle hooks and a stable Capacitor API. Product identity, discovery UUID policy, cryptography and payload semantics stay in the consuming app. See [the transport contract](docs/contract.md).

## Install

Until an npm release exists, pin an immutable Git commit over SSH:

```bash
npm install 'git+ssh://git@github.com/forgesworn/capacitor-mesh-ble.git#<commit-sha>'
```

For a published release:

```bash
npm install capacitor-mesh-ble@0.1.0
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
* [`addListener('frame', ...)`](#addlistenerframe-)
* [`addListener('peer', ...)`](#addlistenerpeer-)
* [`addListener('status', ...)`](#addlistenerstatus-)
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

</docgen-api>
