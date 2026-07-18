import type { PluginListenerHandle } from '@capacitor/core';

export interface MeshBleStartOptions {
  /** Product-defined room/domain separator carried inside every envelope. */
  room: string;
  /** Product-defined local peer identifier, revealed only after a GATT link. */
  selfId: string;
  /** Runtime discovery/GATT UUID. The plugin never derives or rotates it. */
  serviceUuid: string;
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
  addListener(eventName: 'frame', listenerFunc: (event: MeshBleFrameEvent) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'status', listenerFunc: (status: MeshBleStatus) => void): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
}
