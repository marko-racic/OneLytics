package rs.racic.onelytics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** GA4 Admin API — lista property-ja do kojih nalog ima pristup. */
public class GaAdmin {

    public static class Property {
        public final String id;
        public final String displayName;
        public final String timeZone;

        Property(String id, String displayName, String timeZone) {
            this.id = id;
            this.displayName = displayName;
            this.timeZone = timeZone;
        }

        @Override
        public String toString() {
            String label = displayName == null || displayName.isEmpty() ? "(bez imena)" : displayName;
            return label + "  —  " + id + (timeZone == null || timeZone.isEmpty() ? "" : "  (" + timeZone + ")");
        }
    }

    /**
     * properties.list zahteva filter, pa prvo izlistamo accounts,
     * pa po njima properties sa filter=parent="accounts/X".
     */
    public static List<Property> listProperties(String accessToken) throws Exception {
        List<Property> out = new ArrayList<>();

        JSONObject accountsResp = get(accessToken,
                "https://analyticsadmin.googleapis.com/v1beta/accounts?pageSize=200");
        JSONArray accounts = accountsResp.optJSONArray("accounts");
        if (accounts == null) return out;

        for (int i = 0; i < accounts.length(); i++) {
            String parent = accounts.getJSONObject(i).optString("name", ""); // "accounts/123"
            if (parent.isEmpty()) continue;

            String filter = URLEncoder.encode("parent:" + parent, "UTF-8");
            JSONObject propsResp = get(accessToken,
                    "https://analyticsadmin.googleapis.com/v1beta/properties?pageSize=200&filter=" + filter);
            JSONArray props = propsResp.optJSONArray("properties");
            if (props == null) continue;

            for (int j = 0; j < props.length(); j++) {
                JSONObject p = props.getJSONObject(j);
                String name = p.optString("name", ""); // "properties/123456789"
                String id = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
                JSONObject details = p.optJSONObject("propertyDetails");
                String tz = details != null ? details.optString("timeZone", null) : null;
                out.add(new Property(id, p.optString("displayName", ""), tz));
            }
        }
        return out;
    }

    /** Serijalizacija liste za lokalni keš. */
    public static String toJson(List<Property> list) {
        JSONArray a = new JSONArray();
        for (Property p : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", p.id);
                o.put("name", p.displayName == null ? "" : p.displayName);
                o.put("tz", p.timeZone == null ? "" : p.timeZone);
            } catch (Exception ignored) {
            }
            a.put(o);
        }
        return a.toString();
    }

    public static List<Property> fromJson(String json) throws Exception {
        List<Property> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        JSONArray a = new JSONArray(json);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            out.add(new Property(o.optString("id"), o.optString("name"), o.optString("tz", "")));
        }
        return out;
    }

    private static JSONObject get(String accessToken, String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Authorization", "Bearer " + accessToken);

        int code = c.getResponseCode();
        java.io.InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
        }
        if (code >= 400) throw new Exception("HTTP " + code + ": " + sb);
        return new JSONObject(sb.toString());
    }
}
