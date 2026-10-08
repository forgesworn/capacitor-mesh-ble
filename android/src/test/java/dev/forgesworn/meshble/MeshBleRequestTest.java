package dev.forgesworn.meshble;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class MeshBleRequestTest {

    private static final class Result implements MeshBleRequest.Completion {

        int resolved, rejected;
        JSONObject result;
        String message, code;
        Exception cause;

        @Override
        public void resolve(JSONObject value) {
            resolved++;
            result = value;
        }

        @Override
        public void reject(String message, String code, Exception cause) {
            rejected++;
            this.message = message;
            this.code = code;
            this.cause = cause;
        }
    }

    @Test
    public void snapshotsOptionsAndPreservesTypedDefaults() throws Exception {
        JSONObject args = new JSONObject()
            .put("room", "private-scope")
            .put("hops", 0)
            .put("foregroundService", true)
            .put("scanUuids", new JSONArray().put("one"));
        MeshBleRequest request = new MeshBleRequest(args, new Result());
        args.put("room", "changed");
        args.getJSONArray("scanUuids").put("two");
        assertEquals("private-scope", request.getString("room"));
        assertEquals(0, request.getInt("hops", 3));
        assertTrue(request.getBoolean("foregroundService", false));
        assertEquals(1, request.getArray("scanUuids", null).length());
        assertNull(request.getString("hops"));
        assertEquals(7, request.getInt("room", 7));
        assertFalse(request.getBoolean("room", false));
        assertNull(request.getArray("room", null));
    }

    @Test
    public void completionIsOnceAndPreservesErrors() {
        Result result = new Result();
        MeshBleRequest request = new MeshBleRequest(new JSONObject(), result);
        Exception cause = new IllegalArgumentException("fixture");
        request.reject("Permission required", "PERMISSION_REQUIRED", cause);
        request.resolve();
        request.reject("late");
        assertEquals(1, result.rejected);
        assertEquals(0, result.resolved);
        assertEquals("PERMISSION_REQUIRED", result.code);
        assertSame(cause, result.cause);
        Result success = new Result();
        MeshBleRequest next = new MeshBleRequest(new JSONObject(), success);
        next.resolve(new MeshBleObject().put("queuedPeers", 0));
        next.reject("late");
        assertEquals(1, success.resolved);
        assertEquals(0, success.rejected);
        assertEquals(0, success.result.optInt("queuedPeers", -1));
    }

    @Test
    public void competingCompletionsHaveOnlyOneWinner() throws Exception {
        AtomicInteger completed = new AtomicInteger();
        MeshBleRequest request = new MeshBleRequest(
            new JSONObject(),
            new MeshBleRequest.Completion() {
                @Override
                public void resolve(JSONObject value) {
                    completed.incrementAndGet();
                }

                @Override
                public void reject(String message, String code, Exception cause) {
                    completed.incrementAndGet();
                }
            }
        );
        Thread success = new Thread(request::resolve),
            failure = new Thread(() -> request.reject("failed"));
        success.start();
        failure.start();
        success.join();
        failure.join();
        assertEquals(1, completed.get());
    }

    @Test
    public void eventBuilderKeepsNativeJsonTypesAndRejectsNonFiniteValues() throws Exception {
        JSONObject event = new MeshBleObject()
            .put("running", true)
            .put("count", 2)
            .put("at", 123L)
            .put("peers", new JSONArray())
            .put("lastError", JSONObject.NULL);
        assertTrue(event.getBoolean("running"));
        assertEquals(2, event.getInt("count"));
        assertEquals(123L, event.getLong("at"));
        assertTrue(event.isNull("lastError"));
        assertThrows(IllegalArgumentException.class, () -> new MeshBleObject().put("bad", Double.NaN));
    }
}
