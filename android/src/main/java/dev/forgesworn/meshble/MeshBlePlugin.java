package dev.forgesworn.meshble;

import android.Manifest;
import android.os.Build;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import org.json.JSONException;
import org.json.JSONObject;

/** Capacitor permission/lifecycle facade over the shared Android radio. */
@CapacitorPlugin(
    name = "MeshBle",
    permissions = {
        @Permission(
            alias = "bleModern",
            strings = { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT }
        ),
        @Permission(alias = "bleLegacy", strings = { Manifest.permission.ACCESS_FINE_LOCATION })
    }
)
public class MeshBlePlugin extends Plugin {

    private MeshBleRadio radio;
    private boolean destroyed;

    private MeshBleRadio radio() {
        if (destroyed) throw new IllegalStateException("BLE plugin is closed");
        if (radio == null) radio = new MeshBleRadio(getContext(), (name, event) -> notifyListeners(name, toJs(event)));
        return radio;
    }

    private MeshBleRequest request(PluginCall call, boolean requestPermission) {
        return new MeshBleRequest(
            call.getData(),
            new MeshBleRequest.Completion() {
                @Override
                public void resolve(JSONObject result) {
                    call.resolve(toJs(result));
                }

                @Override
                public void reject(String message, String code, Exception cause) {
                    if (requestPermission && "PERMISSION_REQUIRED".equals(code) && !destroyed) {
                        String alias = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? "bleModern" : "bleLegacy";
                        requestPermissionForAlias(alias, call, "blePermissionCallback");
                    } else {
                        call.reject(message, "PERMISSION_REQUIRED".equals(code) ? "PERMISSION_DENIED" : code, cause);
                    }
                }
            }
        );
    }

    private static JSObject toJs(JSONObject value) {
        try {
            return JSObject.fromJSONObject(value);
        } catch (JSONException error) {
            throw new IllegalStateException("Invalid native BLE event", error);
        }
    }

    @PluginMethod
    public void start(PluginCall call) {
        radio().start(request(call, true));
    }

    @PermissionCallback
    private void blePermissionCallback(PluginCall call) {
        if (call == null) return;
        if (destroyed) {
            call.reject("BLE plugin is closed", "CLOSED");
            return;
        }
        radio().start(request(call, false));
    }

    @PluginMethod
    public void stop(PluginCall call) {
        radio().stop(request(call, false));
    }

    @PluginMethod
    public void broadcast(PluginCall call) {
        radio().broadcast(request(call, false));
    }

    @PluginMethod
    public void send(PluginCall call) {
        radio().send(request(call, false));
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        radio().getStatus(request(call, false));
    }

    @PluginMethod
    public void setKeepaliveFrame(PluginCall call) {
        radio().setKeepaliveFrame(request(call, false));
    }

    @PluginMethod
    public void startRssiSampling(PluginCall call) {
        radio().startRssiSampling(request(call, false));
    }

    @PluginMethod
    public void stopRssiSampling(PluginCall call) {
        radio().stopRssiSampling(request(call, false));
    }

    @Override
    protected void handleOnDestroy() {
        destroyed = true;
        if (radio != null) radio.close();
        super.handleOnDestroy();
    }
}
