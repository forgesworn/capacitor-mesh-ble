# Native Android hosts

`android-radio` builds the same Android BLE engine without Capacitor or a WebView.
The existing Capacitor plugin is a permission/lifecycle facade over that engine;
both modules compile one canonical source tree. Use one of the two artifacts in
an app, since they contain the same radio classes.

The JavaScript API, GATT UUIDs, envelope JSON, chunk header, hop limits, bounds and
RSSI attribution rules remain as documented in [contract.md](contract.md).
This addition is Android-only. The Swift radio remains in its Capacitor plugin.

## Build and consume

With installed repository dependencies and the Android SDK configured:

```sh
cd android
./gradlew :mesh-ble-radio:testDebugUnitTest :mesh-ble-radio:assembleRelease
```

The AAR is `android-radio/build/outputs/aar/mesh-ble-radio-release.aar`.
It targets Android SDK 35, supports API 24+, emits Java 17 bytecode and needs
`androidx.core:core:1.15.0` (or a compatible version) in a consuming application.
A raw AAR does not carry transitive dependency metadata. No Maven publication is
provided by this change. A host may include the canonical native sources in its
own Android library module using the host's build tooling/repositories.

The manifest includes BLE permissions and the optional non-exported connected
device foreground service. The application owns permission requests and must
start foreground services only when Android permits it. No room identifiers,
signing keys or discovery UUID policy are supplied by the radio library.

## Lifecycle

Create one `MeshBleRadio(applicationContext, listener)` per active radio owner.
Serialise host calls and close the owner before another owner takes over. The
listener receives `frame`, `peer`, `status` and `rssi` with ordinary Android
`JSONObject` payloads on the main handler. Fields match the Capacitor events;
frame data remains an opaque base64 string.

Methods accept a `MeshBleRequest(options, completion)` with the existing option
names. Options are copied on construction; completion resolves/rejects at most
once. `broadcast`/`send` resolve with `queuedPeers`, a local queue result rather
than a delivery acknowledgement. The native host supplies all room crypto.

`start` validates its arguments and device capability before checking permissions.
Missing permissions reject `PERMISSION_REQUIRED`; the engine never opens a
permission dialog. After an explicit user action, the host requests Android
permissions and calls `start` again only if granted. The Capacitor facade
preserves its existing request flow. Native consumers must not turn this failure
into a fallback internet connection.

`stop` releases the radio but permits another `start`. `close` is terminal and
idempotent: it releases resources and suppresses queued event callbacks. Start
or RSSI start after close rejects `CLOSED`. Android keepalive remains a no-op.

## Validation boundary

Both modules run the same frozen wire tests. Request tests cover deep snapshots,
typed defaults, completion races, error codes and native JSON types. Building the
standalone AAR proves it has no Capacitor dependency. Actual GATT, permissions,
discovery, foreground/background transitions and delivery still require device
testing. The extraction does not qualify those physical behaviours.
