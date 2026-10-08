package dev.forgesworn.meshble;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** A native host request. Completion is at most once; input is snapshotted. */
public final class MeshBleRequest {

    public interface Completion {
        void resolve(JSONObject result);
        void reject(String message, String code, Exception cause);
    }

    private final JSONObject options;
    private final Completion completion;
    private final AtomicBoolean completed = new AtomicBoolean();

    public MeshBleRequest(JSONObject options, Completion completion) {
        try {
            this.options = new JSONObject(Objects.requireNonNull(options).toString());
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid BLE request", e);
        }
        this.completion = Objects.requireNonNull(completion);
    }

    String getString(String key) {
        Object value = options.opt(key);
        return value instanceof String ? (String) value : null;
    }

    int getInt(String key, int fallback) {
        Object value = options.opt(key);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    boolean getBoolean(String key, boolean fallback) {
        Object value = options.opt(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    JSONArray getArray(String key, JSONArray fallback) {
        JSONArray value = options.optJSONArray(key);
        return value == null ? fallback : value;
    }

    void resolve() {
        resolve(new JSONObject());
    }

    void resolve(JSONObject result) {
        if (completed.compareAndSet(false, true)) completion.resolve(result);
    }

    void reject(String message) {
        reject(message, null, null);
    }

    void reject(String message, String code) {
        reject(message, code, null);
    }

    void reject(String message, String code, Exception cause) {
        if (completed.compareAndSet(false, true)) completion.reject(message, code, cause);
    }
}
