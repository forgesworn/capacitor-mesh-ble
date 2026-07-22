# AGENTS.md — capacitor-mesh-ble

Instructions in this file apply to the entire repository.

## Project Summary
- Canonical configurable BLE mesh transport for Capacitor apps: a Capacitor plugin, not a pure JS library.
- Three implementations kept in sync: TypeScript definitions (`src/`), native Android (Java), native iOS (Swift).
- Android is fully implemented, including attributed-RSSI sampling. iOS implements the core transport but not RSSI sampling yet — a known, documented gap. Web has no BLE hardware: radio methods reject, lifecycle/RSSI methods no-op, `getStatus()` reports a fixed unsupported shape.
- Product identity, discovery UUID policy, cryptography and payload semantics are deliberately kept OUT of this plugin — it only owns the radio. See `docs/contract.md` for the full boundary.
- Requires Node.js 22+. Dual build output: a CJS bundle (`main`) for `require()`, an ESM `tsc` output (`module`) for bundlers, and an IIFE bundle (`unpkg`) for `<script>` tag use — package.json has no root `"type"` field by design (a standard Capacitor-plugin-template packaging choice, not an ESM/CJS inconsistency to fix).

## Key Commands
- `npm run build` — clean, regenerate README's API tables from `src/definitions.ts` JSDoc (`docgen`), `tsc`, then Rollup-bundle to `dist/`.
- `npm run typecheck` — `tsc --noEmit`.
- `npm test` / `npm run test:unit` — compile the web-stub tests with `tsconfig.test.json` and run them with Node's built-in test runner (`node --test`). No test framework devDependency — see Coding Conventions.
- `npm run verify` — `verify:ios` + `verify:android` + `verify:web` (full cross-platform build validation; this is what `prepublishOnly` runs).
- `npm run verify:android` — `cd android && ./gradlew test build` (JVM tests + Android library build).
- `npm run verify:ios` — `xcodebuild -scheme CapacitorMeshBle ... build`.
- `cd android && ./gradlew testDebugUnitTest --tests "*MeshBleWireTest*"` — just the JVM suite for the pure wire logic (fast, no emulator).
- `npm run lint` / `npm run fmt` — ESLint + Prettier + SwiftLint, check or autofix.
- `npm run docgen` — regenerate README.md's `<docgen-index>`/`<docgen-api>` blocks from JSDoc alone (also runs as part of `build`).

## Repository Structure
- `src/definitions.ts` — the `MeshBlePlugin` TypeScript interface plus every option/event/status type. Single source of truth for the JS API; `docgen` derives README.md's API tables from its JSDoc comments.
- `src/web.ts` — browser/WebView stub (`MeshBleWeb`): `start`/`broadcast`/`send` reject with "BLE mesh transport is unavailable on the web"; `stop`/`setKeepaliveFrame`/`startRssiSampling`/`stopRssiSampling` resolve as no-ops; `getStatus` returns a fixed unsupported/idle shape.
- `src/index.ts` — `registerPlugin` entry point / barrel re-export.
- `src/web.test.ts` — unit tests for `web.ts` (the only part of the TS layer testable without a device).
- `android/src/main/java/dev/forgesworn/meshble/MeshBlePlugin.java` — Android radio: central+peripheral GATT roles, chunking, relay, foreground-service lifecycle, status telemetry.
- `android/src/main/java/dev/forgesworn/meshble/MeshBleWire.java` — pure wire mechanics (fragment/parse, bounded reassembly, seen-id dedup, connection-arbitration tiebreak, RSSI honesty gate) shared by the Android plugin and the JVM test suite. Package-private by design — it has no Android SDK dependency, which is what makes it independently testable.
- `android/src/main/java/dev/forgesworn/meshble/MeshBleRssiBus.java` — same-process listener bridge so another native component (e.g. a foreground service) can observe RSSI samples without a Capacitor bridge round trip.
- `android/src/main/java/dev/forgesworn/meshble/MeshBleForegroundService.java` — optional connected-device foreground service anchor with product-supplied notification copy.
- `android/src/test/java/dev/forgesworn/meshble/MeshBleWireTest.java` — JVM tests: frozen chunk-header fixture, out-of-order bidirectional reassembly, malformed-input rejection, bounded dedup, deterministic arbitration, RSSI honesty gate.
- `ios/Sources/MeshBlePlugin/MeshBlePlugin.swift` — iOS radio via CoreBluetooth central+peripheral delegates. Single file; no RSSI sampling implementation yet.
- `docs/contract.md` — the transport contract: shared-vs-product boundary, JS API semantics, wire envelope JSON, chunk header layout (with a frozen byte fixture), defaults/bounds table, platform lifecycle notes, compatibility policy for protocol changes.
- `example-app/` — a minimal Capacitor demo app exercising start/broadcast/stop from a plain HTML/JS page.
- `CHANGELOG.md` — Keep a Changelog format; update the `[Unreleased]` section as part of a behaviour change.
- `CapacitorMeshBle.podspec`, `Package.swift` — CocoaPods and Swift Package Manager packaging for iOS.

## Coding Conventions
- Keep `src/definitions.ts` JSDoc and README.md in sync via `npm run docgen` — never hand-edit the generated `<docgen-index>`/`<docgen-api>` blocks in README.md directly; fix the JSDoc source and regenerate.
- Keep the TypeScript definitions and BOTH native implementations in sync: a method or option added to `definitions.ts` needs a matching native implementation, or a deliberate, documented platform gap (see the `startRssiSampling` doc comment for the pattern — state exactly which platforms implement it, which reject, and which no-op; these three are not interchangeable).
- No test framework devDependency (no vitest/jest/mocha). `src/web.test.ts` uses only Node's built-in `node:test` + `node:assert/strict`, compiled to CommonJS via the dedicated `tsconfig.test.json` (kept separate from the main `tsconfig.json`, which targets `esnext`/DOM and has no Node types in scope). Prefer this over adding a new test framework for small additions; if a change genuinely needs one, raise it rather than silently adding a dependency.
- Protocol-level changes (envelope fields, chunk header, characteristic UUIDs, event shapes, required `start` options) need frozen fixtures and old/new interoperability tests before a major release — see `docs/contract.md`'s Compatibility policy. Additive options (like `scanUuids`) and new status fields are library changes, not protocol changes.
- Product identity, cryptography, contact semantics, matching and UI stay out of this plugin.

## Working Guidelines
- Prefer targeted tests for the area changed: `web.ts` logic → `npm run test:unit`; `MeshBleWire` pure logic → the JVM suite (`./gradlew testDebugUnitTest --tests "*MeshBleWireTest*"`). Full native radio behaviour needs a real device — there is no way to unit-test actual GATT/CoreBluetooth I/O.
- Do not hand-edit generated output: `dist/`, `dist-test/` (test compile output, gitignored), `dist/docs.json`, or README's docgen-managed blocks.
- iOS RSSI sampling is a known gap, not an oversight. If it is ever added, the honesty gate (Android's `MeshBleWire.shouldAttributeRssi` equivalent — suppress attribution while relaying) MUST land in the same change, or iOS emits dishonest attributions.
- When touching `MeshBleStartOptions` bounds/defaults, update all three places that state them: `src/definitions.ts` JSDoc, `docs/contract.md`'s defaults/bounds table, and the native clamping logic (Android and iOS both clamp independently — there is no shared bounds-checking code between platforms).

## Release Notes
- Conventional commit-style prefixes: `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`.
- `CHANGELOG.md` follows Keep a Changelog + Semantic Versioning; add to `[Unreleased]` as part of the change, not as an afterthought.
- CI (`.github/workflows/ci.yml`) runs on every push/PR: lint, typecheck, build, unit tests (`test:unit`), Android JVM tests + assemble, example-app build, and iOS `xcodebuild` — across two jobs (`web-and-android` on Ubuntu, `ios` on macOS).
