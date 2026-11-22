package rs.racic.onelytics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** GA4 Data API — broј sesija danas. */
public class GaApi {

    /** Vraća broj sesija danas za property. */
    public static int sessionsToday(String accessToken, String propertyId) throws Exception {
        String url = "https://analyticsdata.googleapis.com/v1beta/properties/"
                + propertyId + ":runReport";
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Authorization", "Bearer " + accessToken);
        c.setRequestProperty("Content-Type", "application/json");

        JSONObject body = new JSONObject();
        JSONArray ranges = new JSONArray();
        ranges.put(new JSONObject()
                .put("startDate", "today")
                .put("endDate", "today"));
        JSONArray metrics = new JSONArray();
        metrics.put(new JSONObject().put("name", "sessions"));
        body.put("dateRanges", ranges).put("metrics", metrics);

        try (OutputStream os = c.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        String resp = OAuth.readBody(c);
        JSONObject o = new JSONObject(resp);
        JSONArray rows = o.optJSONArray("rows");
        if (rows == null || rows.length() == 0) return 0; // nema podataka → 0
        // redovi: [{"metricValues":[{"value":"104"}]}] (u starijim odgovorima i običan niz stringova)
        Object row = rows.get(0);
        if (row instanceof JSONArray) {
            return Integer.parseInt(rows.getJSONArray(0).getString(0));
        }
        return Integer.parseInt(rows.getJSONObject(0)
                .getJSONArray("metricValues").getJSONObject(0).getString("value"));
    }
}
