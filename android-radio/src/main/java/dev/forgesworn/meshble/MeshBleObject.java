package dev.forgesworn.meshble;

import org.json.JSONException;
import org.json.JSONObject;

/** Internal object builder; the public engine boundary is ordinary Android JSON. */
final class MeshBleObject extends JSONObject {

    @Override
    public MeshBleObject put(String key, Object value) {
        try {
            super.put(key, value);
            return this;
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid BLE event value", e);
        }
    }

    @Override
    public MeshBleObject put(String key, boolean value) {
        return put(key, (Object) value);
    }

    @Override
    public MeshBleObject put(String key, int value) {
        return put(key, (Object) value);
    }

    @Override
    public MeshBleObject put(String key, long value) {
        return put(key, (Object) value);
    }

    @Override
    public MeshBleObject put(String key, double value) {
        return put(key, (Object) value);
    }
}
