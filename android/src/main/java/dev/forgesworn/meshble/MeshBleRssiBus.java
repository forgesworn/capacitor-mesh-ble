// Same-process bridge for attributed RSSI samples. MeshBlePlugin runs inside the
// host app's process; this lets another component in that same process (e.g. a
// native foreground service driving a radar UI while the WebView is asleep)
// observe samples without a round trip through the Capacitor bridge.
package dev.forgesworn.meshble;

/** Deliberately minimal: one volatile listener, no queueing, no threading of its
 *  own. `publish` calls straight through on whatever thread the sample arrived on
 *  (currently MeshBlePlugin's BLE callback threads — see emitRssiForAddress) so a
 *  listener that needs a specific thread must hop there itself. Only one listener
 *  at a time; a second setListener call replaces the first. */
public final class MeshBleRssiBus {

    public interface Listener {
        void onRssi(String peer, String address, int rssi, String source, long at);
    }

    private static volatile Listener listener;

    private MeshBleRssiBus() {}

    public static void setListener(Listener next) {
        listener = next;
    }

    public static void clearListener() {
        listener = null;
    }

    public static void publish(String peer, String address, int rssi, String source, long at) {
        Listener current = listener;
        if (current != null) current.onRssi(peer, address, rssi, source, at);
    }
}
