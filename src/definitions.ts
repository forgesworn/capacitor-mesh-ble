import type { PluginListenerHandle } from '@capacitor/core';

export interface MeshBleStartOptions {
  /** Product-defined room/domain separator carried inside every envelope. */
  room: string;
  /** Product-defined local peer identifier, revealed only after a GATT link. */
  selfId: string;
  /** Runtime discovery/GATT UUID. The plugin advertises and hosts its GATT service
   *  under exactly this one UUID. The plugin never derives or rotates it. */
  serviceUuid: string;
  /**
   * Optional set of service UUIDs the scanner filters for, when a product rotates
   * its `serviceUuid` on a time window and must still discover peers a window or
   * two away (clock skew, a rotation boundary crossed at slightly different
   * moments). Match semantics are OR: a peer advertising ANY of these is a
   * candidate. `serviceUuid` is always implicitly included. Omit (or leave empty)
   * for the classic single-UUID behaviour, where the plugin scans for exactly
   * `serviceUuid`. The plugin advertises only `serviceUuid`, never this whole set.
   */
  scanUuids?: string[];
  /** Maximum relay hops for locally-originated frames. Zero disables relaying. */
  hops?: number;
  /** Keep the Android radio anchored in a connected-device foreground service. */
  foregroundService?: boolean;
  /** Android foreground-service notification title. */
  notificationTitle?: string;
  /** Android foreground-service notification body. */
  notificationText?: string;
  /** Hard ceiling for a UTF-8 envelope before chunking. Default: 8192. */
  maxEnvelopeBytes?: number;
  /** Deduplication window. Default: 512 envelope identifiers. */
  maxSeenIds?: number;
  /** Incomplete chunk assembly lifetime. Default: 30000ms. */
  reassemblyTtlMs?: number;
  /** Android client-role GATT link cap. Default: 3. */
  maxClientLinks?: number;
  /** Android reconnect backoff per BLE address. Default: 4000ms. */
  connectCooldownMs?: number;
  /** Android global connection-attempt throttle. Default: 1500ms. */
  connectThrottleMs?: number;
}

export interface MeshBleFrameEvent {
  from: string;
  data: string;
}

/** A product peer became reachable through a learned BLE next hop, or that route was lost. */
export interface MeshBlePeerEvent {
  peer: string;
  connected: boolean;
}

export interface MeshBleStartRssiSamplingOptions {
  /** Sampling interval in ms. Default: 2000, bounded 500-10000. */
  intervalMs?: number;
}

/**
 * A raw RSSI sample, attributed to the mesh peer id bound to the MAC it was observed
 * on. The binding comes from a prior in-room frame exchange, and attribution is only
 * emitted on a DIRECT, non-relaying link (discreet mode): while this device relays
 * (crowd/mesh mode) a bound id can belong to a relayer's MAC, or be injected over the
 * keyless crowd UUID, so no `rssi` event is emitted at all. It is therefore a
 * proximity hint on a trusted-neighbour link, NOT a cryptographic identity proof —
 * a consumer must not treat it as authentication. Banding or distance estimation is a
 * product concern; this plugin reports dBm only. Emitted on Android only for now.
 */
export interface MeshBleRssiSample {
  /** The mesh peer id this sample is attributed to (bound via a prior direct frame
   *  exchange; only emitted on a non-relaying link — see the interface note). */
  peer: string;
  /** The BLE MAC address the sample was observed at. */
  address: string;
  /** Signal strength in dBm. */
  rssi: number;
  /** Whether the reading came from a live GATT link or a scanned advertisement. */
  source: 'gatt' | 'advert';
  /** Epoch ms when the sample was taken. */
  at: number;
}

export interface MeshBlePeerStatus {
  address?: string;
  peerIds: string[];
  connected: boolean;
  writable: boolean;
  mtu: number;
  queuedChunks: number;
  writing: boolean;
}

export interface MeshBleStatus {
  native: boolean;
  platform: 'android' | 'ios' | 'web';
  supported: boolean;
  running: boolean;
  bluetooth: string;
  permissions: string;
  missingPermissions: string[];
  room: string | null;
  selfId: string | null;
  serviceUuid: string | null;
  /** The UUID set the scanner is filtering for (includes `serviceUuid`). Single
   *  entry unless the product supplied a wider `scanUuids` window. */
  scanUuids: string[];
  advertising: boolean;
  scanning: boolean;
  gattServer: boolean;
  connectedPeers: number;
  writablePeers: number;
  knownPeers: number;
  queuedChunks: number;
  txFrames: number;
  txChunks: number;
  rxFrames: number;
  rxChunks: number;
  droppedFrames: number;
  relayedFrames: number;
  lastError: string | null;
  peers: MeshBlePeerStatus[];
  updatedAt: number;
}

export interface MeshBlePlugin {
  start(options: MeshBleStartOptions): Promise<void>;
  stop(): Promise<void>;
  broadcast(options: { data: string }): Promise<{ queuedPeers: number }>;
  send(options: { peer: string; data: string }): Promise<{ queuedPeers: number }>;
  /** iOS may repeat this frame while JavaScript is suspended in the background. */
  setKeepaliveFrame(options: { data: string | null }): Promise<void>;
  getStatus(): Promise<MeshBleStatus>;
  /**
   * Start periodic RSSI sampling: polls connected GATT links and attributes scanned
   * advertisement RSSI, for peers already identified via a prior frame exchange.
   * Off by default (battery cost). Idempotent — calling this again while sampling
   * just updates the interval. Stops automatically when the transport is stopped.
   * Android only for now; a no-op elsewhere.
   */
  startRssiSampling(options?: MeshBleStartRssiSamplingOptions): Promise<void>;
  /** Stop RSSI sampling. Idempotent — safe to call when not sampling. */
  stopRssiSampling(): Promise<void>;
  addListener(eventName: 'frame', listenerFunc: (event: MeshBleFrameEvent) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'peer', listenerFunc: (event: MeshBlePeerEvent) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'status', listenerFunc: (status: MeshBleStatus) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'rssi', listenerFunc: (event: MeshBleRssiSample) => void): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
}
