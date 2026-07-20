package dev.forgesworn.meshble;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class MeshBleWireTest {

    @Test
    public void freezesTheSharedFlockAndMeatchatChunkHeader() {
        byte[] payload = "fixture".getBytes(StandardCharsets.UTF_8);
        List<byte[]> chunks = MeshBleWire.fragment(247, payload, 0x01020304);
        assertEquals(1, chunks.size());
        assertArrayEquals(new byte[] { 0x4d, 0x01, 0x01, 0x02, 0x03, 0x04, 0x00, 0x01 }, java.util.Arrays.copyOf(chunks.get(0), 8));
    }

    @Test
    public void fragmentsAndReassemblesOutOfOrderInBothDirections() {
        assertRoundTrip("A-to-B:" + "x".repeat(900), 0x10203040);
        assertRoundTrip("B-to-A:" + "y".repeat(700), 0x50607080);
    }

    private static void assertRoundTrip(String text, int id) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        List<byte[]> chunks = MeshBleWire.fragment(80, bytes, id);
        assertTrue(chunks.size() > 1);
        Collections.reverse(chunks);
        MeshBleWire.Reassembly reassembly = new MeshBleWire.Reassembly(chunks.size());
        for (byte[] bytesChunk : chunks) assertTrue(reassembly.add(MeshBleWire.parse(bytesChunk)));
        assertArrayEquals(bytes, reassembly.join(8192));
    }

    @Test
    public void malformedChunksFailClosed() {
        assertNull(MeshBleWire.parse(null));
        assertNull(MeshBleWire.parse(new byte[7]));
        assertNull(MeshBleWire.parse(new byte[] { 0x00, 0x01, 0, 0, 0, 1, 0, 1 }));
        assertNull(MeshBleWire.parse(new byte[] { 0x4d, 0x02, 0, 0, 0, 1, 0, 1 }));
        assertNull(MeshBleWire.parse(new byte[] { 0x4d, 0x01, 0, 0, 0, 1, 1, 1 }));
        assertTrue(MeshBleWire.fragment(23, new byte[9000], 1).isEmpty());
    }

    @Test
    public void duplicateChunksAndOversizeJoinAreRejected() {
        List<byte[]> chunks = MeshBleWire.fragment(40, "a bounded message".repeat(10).getBytes(StandardCharsets.UTF_8), 8);
        MeshBleWire.Reassembly reassembly = new MeshBleWire.Reassembly(chunks.size());
        MeshBleWire.Chunk first = MeshBleWire.parse(chunks.get(0));
        assertTrue(reassembly.add(first));
        assertFalse(reassembly.add(first));
        for (int i = 1; i < chunks.size(); i += 1) assertTrue(reassembly.add(MeshBleWire.parse(chunks.get(i))));
        assertNull(reassembly.join(4));
    }

    @Test
    public void seenIdsAreBoundedAndDeduplicate() {
        MeshBleWire.SeenIds seen = new MeshBleWire.SeenIds(2);
        assertTrue(seen.remember("a"));
        assertFalse(seen.remember("a"));
        assertTrue(seen.remember("b"));
        assertTrue(seen.remember("c"));
        assertEquals(2, seen.size());
        assertTrue(seen.remember("a"));
    }

    @Test
    public void arbitrationCreatesExactlyOneBidirectionalPhysicalLink() {
        byte[] a = { 1, 2, 3, 4 };
        byte[] b = { 5, 6, 7, 8 };
        assertFalse(MeshBleWire.shouldInitiate(a, b, "alice", "bob"));
        assertTrue(MeshBleWire.shouldInitiate(b, a, "bob", "alice"));
        byte[] tied = { 9, 9, 9, 9 };
        assertFalse(MeshBleWire.shouldInitiate(tied, tied, "alice", "bob"));
        assertTrue(MeshBleWire.shouldInitiate(tied, tied, "bob", "alice"));
    }

    @Test
    public void directedFramesDeliverAtTargetAndRelayElsewhere() {
        assertTrue(MeshBleWire.shouldDeliver("*", "bob"));
        assertTrue(MeshBleWire.shouldDeliver("bob", "bob"));
        assertFalse(MeshBleWire.shouldDeliver("carol", "bob"));

        assertTrue(MeshBleWire.shouldRelay("*", "bob", 3, 2));
        assertTrue(MeshBleWire.shouldRelay("carol", "bob", 3, 2));
        assertFalse(MeshBleWire.shouldRelay("bob", "bob", 3, 2));
        assertFalse(MeshBleWire.shouldRelay("carol", "bob", 0, 2));
        assertFalse(MeshBleWire.shouldRelay("carol", "bob", 3, 0));
    }
}
