# CLAUDE.md — capacitor-mesh-ble

Canonical configurable BLE mesh transport for Capacitor apps — Android + iOS native radios, chunking, relay and status, behind one stable TypeScript API.

## Commands

- `npm run build` — clean, regenerate README's API tables from JSDoc (`docgen`), `tsc`, Rollup bundle to `dist/`
- `npm run typecheck` — `tsc --noEmit`
- `npm test` / `npm run test:unit` — compile + run the `web.ts` unit tests (Node's built-in test runner, no framework devDependency)
- `npm run verify` — full cross-platform validation: iOS `xcodebuild`, Android `gradlew test build`, web build
- `cd android && ./gradlew testDebugUnitTest --tests "*MeshBleWireTest*"` — just the JVM wire-logic suite
- `npm run lint` / `npm run fmt` — ESLint + Prettier + SwiftLint, check or autofix

## Structure

- `src/definitions.ts` — the `MeshBlePlugin` TS interface + all option/event/status types (source of truth; README's API tables are generated from its JSDoc)
- `src/web.ts` — browser/WebView stub: radio methods reject, lifecycle/RSSI methods no-op, `getStatus()` returns a fixed unsupported shape
- `src/index.ts` — `registerPlugin` entry point
- `src/web.test.ts` — unit tests for `web.ts`
- `android/src/main/java/dev/forgesworn/meshble/` — `MeshBlePlugin.java` (Android radio), `MeshBleWire.java` (pure wire mechanics — fragment/parse/reassembly/dedup/arbitration/RSSI honesty gate), `MeshBleRssiBus.java` (same-process RSSI listener bridge), `MeshBleForegroundService.java`
- `android/src/test/java/dev/forgesworn/meshble/MeshBleWireTest.java` — JVM tests for `MeshBleWire`
- `ios/Sources/MeshBlePlugin/MeshBlePlugin.swift` — iOS radio via CoreBluetooth (no RSSI sampling yet)
- `docs/contract.md` — wire protocol: envelope JSON, chunk header (with frozen byte fixture), defaults/bounds, compatibility policy
- `example-app/` — minimal Capacitor demo app

## API

- `start(options)` / `stop()` — start/stop both BLE roles (`room`, `selfId`, `serviceUuid` required; `scanUuids`, `hops`, foreground-service and tuning options optional)
- `broadcast({ data })` / `send({ peer, data })` — queue an opaque frame for all writable peers, or one peer via a learned route
- `setKeepaliveFrame({ data })` — iOS-only background liveness replay; no-op elsewhere
- `getStatus()` — stable cross-platform status snapshot
- `startRssiSampling(options?)` / `stopRssiSampling()` — **Android only.** iOS rejects (method absent from its native `pluginMethods`); web resolves as an inert no-op (no radio to sample). The relay-honesty gate (no RSSI attribution while `hops > 0`) is Android-only too.
- `addListener('frame' | 'peer' | 'status' | 'rssi', fn)` / `removeAllListeners()`

Full field-level reference: [README.md](README.md) (docgen-generated from `src/definitions.ts`).

## Conventions

- **British English** in prose and comments
- **Git:** commit messages use `type: description` format (`feat:`, `fix:`, `docs:`, `chore:`, `refactor:`)
- **Git:** do NOT include `Co-Authored-By` lines in commits
- Keep `src/definitions.ts` JSDoc and README.md in sync via `npm run docgen` — never hand-edit the generated `<docgen-index>`/`<docgen-api>` blocks
- Keep the TS definitions and BOTH native implementations in sync; a documented platform gap (like iOS RSSI) is fine, a silent one is not
- No test-framework devDependency: `src/web.test.ts` runs on Node's built-in `node:test` via the dedicated `tsconfig.test.json`, deliberately kept separate from the DOM-targeting main `tsconfig.json`
- Protocol changes (envelope fields, chunk header, characteristic UUIDs, event shapes, required `start` options) need frozen fixtures and interoperability tests — see `docs/contract.md`'s compatibility policy. Additive options/status fields are library changes, not protocol changes

## Release & Versioning

No automated publish workflow (no `anvil`-style release pipeline) — releases are manual, and the package is **not yet published to npm** (consumers currently pin an immutable Git commit over SSH; see README's Install section).

1. Bump `package.json`'s version by hand.
2. Move the `CHANGELOG.md` `[Unreleased]` content under a new version heading (Keep a Changelog format).
3. Commit, push main.
4. Tag the commit (`git tag vX.Y.Z && git push --tags`) — `v0.1.0` and `v0.2.0` exist this way.
5. `npm publish`, gated by the `prepublishOnly` hook (`npm run verify`: iOS + Android + web all build clean first).

`package.json`'s version is currently ahead of the last tag: `0.3.0` in `package.json` vs. tag `v0.2.0`, because the RSSI-sampling and `scanUuids` work landed under `CHANGELOG.md`'s `[Unreleased]` heading but hasn't been tagged/released yet.

**Known gap:** iOS has no RSSI sampling implementation — `startRssiSampling`/`stopRssiSampling` are absent from its Swift `pluginMethods` list, so a call rejects at the native bridge. If iOS RSSI sampling is ever added, the relay-honesty gate (see Android's `MeshBleWire.shouldAttributeRssi`) MUST land in the same change, or iOS will emit dishonest proximity attributions.

Semver rules of thumb:

| Change | Bump |
|---|---|
| Bug fix, no API change | Patch |
| New feature, backwards compatible (additive option/status field) | Minor |
| Breaking API or wire-protocol change | Major |
| Tooling, docs, refactor with no behaviour change | Patch or none |
