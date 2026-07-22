import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { MeshBleWeb } from './web';

// The web stub has no BLE hardware behind it, so every behaviour here is pure
// TypeScript logic — no device, WebView or native bridge required. This is the
// only part of the plugin's TypeScript layer that is unit-testable without a
// device; the native radios (android/, ios/) need a real adapter, and
// MeshBleWire's pure wire mechanics already have JVM coverage in
// android/src/test/java/dev/forgesworn/meshble/MeshBleWireTest.java.

const unavailableMessage = 'BLE mesh transport is unavailable on the web';

describe('MeshBleWeb', () => {
  describe('methods that need a real radio reject on the web', () => {
    it('start() rejects', async () => {
      await assert.rejects(
        new MeshBleWeb().start({ room: 'room', selfId: 'self', serviceUuid: '29b8d9f3-2c2b-4ed1-a12c-7401e5b7b37f' }),
        new Error(unavailableMessage),
      );
    });

    it('broadcast() rejects', async () => {
      await assert.rejects(new MeshBleWeb().broadcast({ data: 'hi' }), new Error(unavailableMessage));
    });

    it('send() rejects', async () => {
      await assert.rejects(new MeshBleWeb().send({ peer: 'bob', data: 'hi' }), new Error(unavailableMessage));
    });
  });

  describe('lifecycle and RSSI methods are inert no-ops on the web', () => {
    it('stop() resolves to undefined without a prior start()', async () => {
      assert.equal(await new MeshBleWeb().stop(), undefined);
    });

    it('setKeepaliveFrame() resolves and ignores its payload', async () => {
      const plugin = new MeshBleWeb();
      assert.equal(await plugin.setKeepaliveFrame({ data: 'keepalive' }), undefined);
      assert.equal(await plugin.setKeepaliveFrame({ data: null }), undefined);
    });

    it('startRssiSampling()/stopRssiSampling() resolve rather than reject', async () => {
      // Unlike iOS — where the method is absent from `pluginMethods` and the
      // native bridge auto-rejects the call — the web stub implements both
      // methods and resolves: there is no BLE hardware to sample, so it is
      // inert rather than an error. (This is the behaviour fixed in the
      // `startRssiSampling` doc comment in definitions.ts, which previously
      // claimed web also rejects.)
      const plugin = new MeshBleWeb();
      assert.equal(await plugin.startRssiSampling(), undefined);
      assert.equal(await plugin.startRssiSampling({ intervalMs: 500 }), undefined);
      assert.equal(await plugin.stopRssiSampling(), undefined);
    });
  });

  describe('getStatus()', () => {
    it('reports a stable unsupported/idle status shape', async () => {
      const before = Date.now();
      const status = await new MeshBleWeb().getStatus();
      const after = Date.now();

      assert.ok(status.updatedAt >= before && status.updatedAt <= after, 'updatedAt should be the current time');
      assert.deepEqual(status, {
        native: false,
        platform: 'web',
        supported: false,
        running: false,
        bluetooth: 'unavailable',
        permissions: 'unavailable',
        missingPermissions: [],
        room: null,
        selfId: null,
        serviceUuid: null,
        scanUuids: [],
        advertising: false,
        scanning: false,
        gattServer: false,
        connectedPeers: 0,
        writablePeers: 0,
        knownPeers: 0,
        queuedChunks: 0,
        txFrames: 0,
        txChunks: 0,
        rxFrames: 0,
        rxChunks: 0,
        droppedFrames: 0,
        relayedFrames: 0,
        lastError: null,
        peers: [],
        updatedAt: status.updatedAt,
      });
    });
  });

  describe('addListener()', () => {
    it('resolves a handle whose remove() can be called without throwing', async () => {
      // addListener/removeAllListeners are inherited unchanged from
      // @capacitor/core's WebPlugin base (MeshBleWeb does not override them),
      // so this only checks the shape this plugin's typing promises — not
      // WebPlugin's own bookkeeping, which is that package's concern to test.
      const plugin = new MeshBleWeb();
      const handle = await plugin.addListener('frame', () => undefined);
      assert.equal(typeof handle.remove, 'function');
      await handle.remove();
    });
  });
});
