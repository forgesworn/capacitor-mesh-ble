import { MeshBle } from 'capacitor-mesh-ble';

const value = (id) => document.getElementById(id).value;
const status = document.getElementById('status');

document.getElementById('peer').value = `example-${Math.random().toString(16).slice(2, 10)}`;

const render = (payload) => {
  status.textContent = JSON.stringify(payload, null, 2);
};

MeshBle.addListener('status', render);
MeshBle.addListener('frame', (frame) => render({ received: frame, at: new Date().toISOString() }));

document.getElementById('start').addEventListener('click', async () => {
  try {
    await MeshBle.start({
      room: value('room'),
      selfId: value('peer'),
      serviceUuid: value('service'),
      hops: 3,
      foregroundService: true,
      notificationTitle: 'Mesh BLE example active',
    });
    render(await MeshBle.getStatus());
  } catch (error) {
    render({ error: String(error) });
  }
});

document.getElementById('send').addEventListener('click', async () => {
  try {
    render(await MeshBle.broadcast({ data: value('frame') }));
  } catch (error) {
    render({ error: String(error) });
  }
});

document.getElementById('stop').addEventListener('click', async () => {
  await MeshBle.stop();
  render(await MeshBle.getStatus());
});
