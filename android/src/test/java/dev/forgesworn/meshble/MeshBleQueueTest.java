package dev.forgesworn.meshble;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class MeshBleQueueTest {

    @Test
    public void stalledPeerRejectsWholeFramesWithoutEvictingEarlierOnes() {
        MeshBleQueue queue = new MeshBleQueue();
        List<byte[]> frame = MeshBleWire.fragment(247, new byte[32 * 1024], 1);
        assertEquals(205, frame.size());
        assertTrue(queue.offerFrame(frame));
        assertTrue(queue.offerFrame(frame));
        for (int i = 0; i < 100_000; i++) assertFalse(queue.offerFrame(frame));
        assertEquals(410, queue.size());
        for (int i = 0; i < 2; i++) for (byte[] chunk : frame) assertArrayEquals(chunk, queue.poll());
        assertNull(queue.poll());
        assertEquals(0, queue.bytes());
        assertTrue(queue.offerFrame(frame));
    }

    @Test
    public void bytesBoundIsIndependentOfChunkCountAndClearReleasesCapacity() {
        MeshBleQueue queue = new MeshBleQueue();
        assertTrue(queue.offerFrame(List.of(new byte[128 * 1024])));
        assertFalse(queue.offerFrame(List.of(new byte[1])));
        assertEquals(1, queue.size());
        assertEquals(128 * 1024, queue.bytes());
        queue.clear();
        assertEquals(0, queue.bytes());
        assertNull(queue.poll());
        assertTrue(queue.offerFrame(List.of(new byte[1])));
    }

    @Test
    public void admissionSnapshotsAndRejectsInvalidBatchAtomically() {
        MeshBleQueue queue = new MeshBleQueue();
        byte[] input = new byte[] { 1, 2 };
        assertTrue(queue.offerFrame(List.of(input)));
        input[0] = 9;
        assertFalse(queue.offerFrame(List.of(new byte[1], new byte[128 * 1024])));
        assertFalse(queue.offerFrame(List.of(new byte[0])));
        assertFalse(queue.offerFrame(List.of()));
        assertArrayEquals(new byte[] { 1, 2 }, queue.poll());
        assertNull(queue.poll());
    }

    @Test
    public void competingGattAndHostThreadsCannotOverbookTheQueue() throws Exception {
        MeshBleQueue queue = new MeshBleQueue();
        AtomicInteger accepted = new AtomicInteger();
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Thread worker = new Thread(() -> {
                for (int n = 0; n < 1000; n++) if (queue.offerFrame(List.of(new byte[256]))) accepted.incrementAndGet();
            });
            workers.add(worker);
            worker.start();
        }
        for (Thread worker : workers) worker.join();
        assertEquals(512, accepted.get());
        assertEquals(512, queue.size());
        assertEquals(128 * 1024, queue.bytes());
    }
}
