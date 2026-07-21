import { WebPlugin } from '@capacitor/core';

import type { MeshBlePlugin, MeshBleStartOptions, MeshBleStartRssiSamplingOptions, MeshBleStatus } from './definitions';

const unavailable = (): Error => new Error('BLE mesh transport is unavailable on the web');

export class MeshBleWeb extends WebPlugin implements MeshBlePlugin {
  async start(_options: MeshBleStartOptions): Promise<void> {
    void _options;
    throw unavailable();
  }

  async stop(): Promise<void> {
    return;
  }

  async broadcast(_options: { data: string }): Promise<{ queuedPeers: number }> {
    void _options;
    throw unavailable();
  }

  async send(_options: { peer: string; data: string }): Promise<{ queuedPeers: number }> {
    void _options;
    throw unavailable();
  }

  async setKeepaliveFrame(_options: { data: string | null }): Promise<void> {
    void _options;
    return;
  }

  async startRssiSampling(_options?: MeshBleStartRssiSamplingOptions): Promise<void> {
    void _options;
    return;
  }

  async stopRssiSampling(): Promise<void> {
    return;
  }

  async getStatus(): Promise<MeshBleStatus> {
    return {
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
      updatedAt: Date.now(),
    };
  }
}
