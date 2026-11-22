package rs.racic.onelytics;

import android.content.Context;
import android.content.SharedPreferences;

/** Jednostavno čuvanje stanja aplikacije. */
public class Prefs {

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getSharedPreferences("onelytics", Context.MODE_PRIVATE);
    }

    // --- OAuth ---
    public String refreshToken() { return sp.getString("refresh_token", null); }
    public void refreshToken(String v) { sp.edit().putString("refresh_token", v).apply(); }
    public String accessToken() { return sp.getString("access_token", null); }
    public long accessExpiresAt() { return sp.getLong("access_expires", 0); }
    public void tokens(String access, long expiresAt) {
        sp.edit().putString("access_token", access).putLong("access_expires", expiresAt).apply();
    }
    public String pendingState() { return sp.getString("oauth_state", null); }
    public String pendingVerifier() { return sp.getString("oauth_verifier", null); }
    public void pendingOAuth(String state, String verifier) {
        sp.edit().putString("oauth_state", state).putString("oauth_verifier", verifier).apply();
    }
    public void clearAuth() {
        sp.edit().remove("refresh_token").remove("access_token").remove("access_expires").apply();
    }

    // --- Podešavanja ---
    public String propertyId() { return sp.getString("property_id", ""); }
    public void propertyId(String v) { sp.edit().putString("property_id", v).apply(); }
    public int intervalMin() { return sp.getInt("interval_min", 15); }
    public void intervalMin(int v) { sp.edit().putInt("interval_min", v).apply(); }

    // --- Poslednji rezultat ---
    public int lastCount() { return sp.getInt("last_count", -1); }
    public long lastUpdate() { return sp.getLong("last_update", 0); }
    public String lastError() { return sp.getString("last_error", null); }
    public void result(int count, long when) {
        sp.edit().putInt("last_count", count).putLong("last_update", when).remove("last_error").apply();
    }
    public void error(String msg) { sp.edit().putString("last_error", msg).apply(); }

    // --- Po widzetu (svaki svoj property i boju) ---
    public String widgetProp(int id) { return sp.getString("w_prop_" + id, null); }
    public void widgetProp(int id, String v) { sp.edit().putString("w_prop_" + id, v).apply(); }
    public String widgetName(int id) { return sp.getString("w_name_" + id, null); }
    public void widgetName(int id, String v) { sp.edit().putString("w_name_" + id, v).apply(); }
    public int widgetColor(int id) { return sp.getInt("w_color_" + id, 0); }
    public void widgetColor(int id, int v) { sp.edit().putInt("w_color_" + id, v).apply(); }
    public int widgetCount(int id) { return sp.getInt("w_count_" + id, -1); }
    public long widgetTime(int id) { return sp.getLong("w_time_" + id, 0); }
    public void widgetResult(int id, int count, long when) {
        sp.edit().putInt("w_count_" + id, count).putLong("w_time_" + id, when).remove("w_err_" + id).apply();
    }
    public void widgetError(int id, String msg) { sp.edit().putString("w_err_" + id, msg).apply(); }
    public void widgetRemove(int id) {
        sp.edit().remove("w_prop_" + id).remove("w_name_" + id).remove("w_color_" + id)
                .remove("w_count_" + id).remove("w_time_" + id).remove("w_err_" + id).apply();
    }

    public boolean isConfigured() {
        return !propertyId().isEmpty() && refreshToken() != null;
    }

    // --- Keš imena property-ja (JSON: id -> displayName) ---
    public String namesCache() { return sp.getString("prop_names", null); }
    public long namesAt() { return sp.getLong("prop_names_at", 0); }
    public void namesCache(String json, long when) {
        sp.edit().putString("prop_names", json).putLong("prop_names_at", when).apply();
    }
    public String nameFor(String propId) {
        try {
            String v = new org.json.JSONObject(namesCache()).optString(propId, null);
            return v == null || v.isEmpty() ? null : v;
        } catch (Exception e) {
            return null;
        }
    }

    // --- Keš cele liste property-ja (JSON array: id, name, tz) ---
    public String propList() { return sp.getString("prop_list", null); }
    public long propListAt() { return sp.getLong("prop_list_at", 0); }
    public void propList(String json, long when) {
        sp.edit().putString("prop_list", json).putLong("prop_list_at", when).apply();
    }
}
