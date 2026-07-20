package dev.forgesworn.meshble;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure wire mechanics shared by the Android radio and deterministic JVM tests. */
final class MeshBleWire {

    static final byte CHUNK_MAGIC = 0x4d;
    static final byte CHUNK_VERSION = 0x01;
    static final int CHUNK_HEADER_BYTES = 8;
    static final int DEFAULT_MAX_CHUNK_PAYLOAD = 160;
    static final int DEFAULT_MAX_CHUNKS = 255;

    private MeshBleWire() {}

    static List<byte[]> fragment(int mtu, byte[] envelope, int messageId) {
        return fragment(mtu, envelope, messageId, DEFAULT_MAX_CHUNK_PAYLOAD, DEFAULT_MAX_CHUNKS);
    }

    static List<byte[]> fragment(int mtu, byte[] envelope, int messageId, int maxChunkPayload, int maxChunks) {
        if (envelope == null || maxChunkPayload < 1 || maxChunks < 1) return List.of();
        int payloadBytes = Math.max(1, Math.min(maxChunkPayload, mtu - 3 - CHUNK_HEADER_BYTES));
        int total = Math.max(1, (envelope.length + payloadBytes - 1) / payloadBytes);
        if (total > Math.min(maxChunks, 255)) return List.of();

        List<byte[]> chunks = new ArrayList<>(total);
        for (int index = 0; index < total; index += 1) {
            int from = index * payloadBytes;
            int to = Math.min(envelope.length, from + payloadBytes);
            byte[] chunk = new byte[CHUNK_HEADER_BYTES + to - from];
            chunk[0] = CHUNK_MAGIC;
            chunk[1] = CHUNK_VERSION;
            chunk[2] = (byte) ((messageId >>> 24) & 0xff);
            chunk[3] = (byte) ((messageId >>> 16) & 0xff);
            chunk[4] = (byte) ((messageId >>> 8) & 0xff);
            chunk[5] = (byte) (messageId & 0xff);
            chunk[6] = (byte) index;
            chunk[7] = (byte) total;
            System.arraycopy(envelope, from, chunk, CHUNK_HEADER_BYTES, to - from);
            chunks.add(chunk);
        }
        return chunks;
    }

    static Chunk parse(byte[] bytes) {
        if (bytes == null || bytes.length < CHUNK_HEADER_BYTES) return null;
        if (bytes[0] != CHUNK_MAGIC || bytes[1] != CHUNK_VERSION) return null;
        int id = ((bytes[2] & 0xff) << 24) | ((bytes[3] & 0xff) << 16) | ((bytes[4] & 0xff) << 8) | (bytes[5] & 0xff);
        int index = bytes[6] & 0xff;
        int total = bytes[7] & 0xff;
        if (total < 1 || index >= total) return null;
        return new Chunk(id, index, total, Arrays.copyOfRange(bytes, CHUNK_HEADER_BYTES, bytes.length));
    }

    static boolean shouldInitiate(byte[] localTie, byte[] remoteTie, String localId, String remoteId) {
        int length = Math.min(localTie == null ? 0 : localTie.length, remoteTie == null ? 0 : remoteTie.length);
        for (int i = 0; i < length; i += 1) {
            int compared = Integer.compare(localTie[i] & 0xff, remoteTie[i] & 0xff);
            if (compared != 0) return compared > 0;
        }
        int lengthCompared = Integer.compare(localTie == null ? 0 : localTie.length, remoteTie == null ? 0 : remoteTie.length);
        if (lengthCompared != 0) return lengthCompared > 0;
        return String.valueOf(localId).compareTo(String.valueOf(remoteId)) > 0;
    }

    static boolean shouldDeliver(String target, String selfId) {
        return "*".equals(target) || String.valueOf(selfId).equals(target);
    }

    static boolean shouldRelay(String target, String selfId, int localRelayHops, int remainingHops) {
        return localRelayHops > 0 && remainingHops > 0 && ("*".equals(target) || !String.valueOf(selfId).equals(target));
    }

    record Chunk(int messageId, int index, int total, byte[] payload) {}

    static final class Reassembly {

        private final int total;
        private final Map<Integer, byte[]> chunks = new LinkedHashMap<>();

        Reassembly(int total) {
            if (total < 1 || total > 255) throw new IllegalArgumentException("invalid chunk total");
            this.total = total;
        }

        boolean add(Chunk chunk) {
            if (chunk == null || chunk.total() != total || chunk.index() < 0 || chunk.index() >= total) return false;
            if (chunks.containsKey(chunk.index())) return false;
            chunks.put(chunk.index(), chunk.payload());
            return true;
        }

        boolean complete() {
            return chunks.size() == total;
        }

        byte[] join(int maxBytes) {
            if (!complete()) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (int index = 0; index < total; index += 1) {
                byte[] part = chunks.get(index);
                if (part == null || output.size() + part.length > maxBytes) return null;
                output.writeBytes(part);
            }
            return output.toByteArray();
        }
    }

    static final class SeenIds {

        private final int capacity;
        private final LinkedHashMap<String, Boolean> ids = new LinkedHashMap<>();

        SeenIds(int capacity) {
            if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
            this.capacity = capacity;
        }

        synchronized boolean remember(String id) {
            if (id == null || id.isBlank() || ids.containsKey(id)) return false;
            ids.put(id, Boolean.TRUE);
            while (ids.size() > capacity) ids.remove(ids.keySet().iterator().next());
            return true;
        }

        synchronized int size() {
            return ids.size();
        }
    }
}
