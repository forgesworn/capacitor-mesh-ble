# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `startRssiSampling`/`stopRssiSampling` and a new `rssi` event (Android only for now): polls RSSI on connected GATT links and attributes scanned advertisement RSSI, but only for a BLE MAC already bound to a peer id via a prior in-room frame exchange — an unbound advert never produces a sample. Attribution is emitted **only on a direct, non-relaying link** (discreet single-hop mode): while the device relays (crowd/mesh mode, `hops > 0`), a bound id can belong to a relayer's MAC rather than the sender's, or be injected over the keyless crowd discovery UUID, so no `rssi` event is emitted at all. The sample is therefore a trusted-neighbour proximity hint, not a cryptographic identity proof — consumers must not treat it as authentication. Off by default (battery cost), bounded 500-10000ms interval, stops automatically when the transport is stopped. A new `MeshBleRssiBus` (Android) lets a native component in the same process (e.g. a foreground service) observe samples without going through the WebView bridge.
- `scanUuids` start option: a product that rotates `serviceUuid` on a time window can now have the scanner filter for a set of UUIDs (OR semantics, `serviceUuid` always included), so it still discovers members a window or two away across clock skew and rotation boundaries. The device still advertises and hosts its GATT under `serviceUuid` alone. The client now locates a peer's service by the frame characteristic rather than an exact service-UUID match, so a peer serving under an adjacent-window UUID is still bound. `scanUuids` is also reported in `getStatus()`.

### Fixed

- Cancel the underlying GATT connection when dropping a peripheral on iOS, instead of only clearing local bookkeeping — a discovery/write error no longer leaks a scarce CoreBluetooth connection slot.

## [0.2.0] - 2026-07-20

### Added

- Add the `peer` route-lifecycle event after a valid product peer id is learned or its BLE next-hop mapping is lost.

### Fixed

- Relay directed Android envelopes toward non-local targets instead of rejecting them before forwarding.
- Honour the receiving product's zero-hop relay policy consistently on iOS and Android.

[0.2.0]: https://github.com/forgesworn/capacitor-mesh-ble/compare/v0.1.0...v0.2.0

## [0.1.0] - 2026-07-18

Initial release of `capacitor-mesh-ble` — the canonical configurable BLE
mesh transport for Capacitor apps.

### Added

- Android and iOS radios, chunking, bounded reassembly, relay,
  background lifecycle hooks, and a stable Capacitor API, extracted as
  a standalone plugin.

### Changed

- Distributable Git dependencies prepared for consumption outside the
  originating repository.
- CI updated for reproducible example-app installs and cross-platform
  Rollup optional dependencies.

[0.1.0]: https://github.com/forgesworn/capacitor-mesh-ble/releases/tag/v0.1.0
