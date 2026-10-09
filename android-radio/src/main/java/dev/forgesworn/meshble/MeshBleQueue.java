package dev.forgesworn.meshble;

import java.util.ArrayDeque;
import java.util.List;

/** Whole-frame admission. A stalled peer never evicts earlier chunks or grows
 * without bound. The radio may additionally have one chunk in Android's hands. */
final class MeshBleQueue {

    private static final int MAX_CHUNKS = 512;
    private static final int MAX_BYTES = 128 * 1024;
    private final ArrayDeque<byte[]> chunks = new ArrayDeque<>();
    private int bytes;

    synchronized boolean offerFrame(List<byte[]> frame) {
        if (frame.isEmpty() || frame.size() > MAX_CHUNKS - chunks.size()) return false;
        int added = 0;
        for (byte[] chunk : frame) {
            if (chunk == null || chunk.length == 0 || chunk.length > MAX_BYTES - bytes - added) return false;
            added += chunk.length;
        }
        for (byte[] chunk : frame) chunks.add(chunk.clone());
        bytes += added;
        return true;
    }

    synchronized byte[] poll() {
        byte[] chunk = chunks.poll();
        if (chunk != null) bytes -= chunk.length;
        return chunk;
    }

    synchronized int size() {
        return chunks.size();
    }

    synchronized int bytes() {
        return bytes;
    }

    synchronized void clear() {
        chunks.clear();
        bytes = 0;
    }
}
