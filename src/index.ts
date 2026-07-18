import { registerPlugin } from '@capacitor/core';

import type { MeshBlePlugin } from './definitions';

const MeshBle = registerPlugin<MeshBlePlugin>('MeshBle', {
  web: () => import('./web').then((m) => new m.MeshBleWeb()),
});

export * from './definitions';
export { MeshBle };
