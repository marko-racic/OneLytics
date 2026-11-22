package rs.racic.onelytics;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

import java.util.List;

/**
 * Osvežava: glavni ekran (globalni property) i svaki widzet za svoj property.
 * Uvek pozivati sa pozadinske niti.
 */
public class Refresher {

    public interface Callback {
        void done(boolean ok, String message);
    }

    public static boolean refresh(Context ctx) {
        return refresh(ctx, null);
    }

    public static boolean refresh(Context ctx, Callback cb) {
        Prefs prefs = new Prefs(ctx);
        String message;
        boolean ok = false;

        // 1) glavni ekran
        try {
            if (!prefs.isConfigured()) {
                message = ctx.getString(R.string.err_not_configured);
            } else {
                String token = validToken(prefs);
                int count = GaApi.sessionsToday(token, prefs.propertyId());
                prefs.result(count, System.currentTimeMillis());
                message = ctx.getString(R.string.ok_updated);
                ok = true;
            }
        } catch (Exception e) {
            prefs.error(e.getMessage() == null ? e.toString() : e.getMessage());
            message = e.getMessage() == null ? e.toString() : e.getMessage();
        }

        // 2) svaki widzet za svoj property
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, WidgetProvider.class));
            if (ids.length > 0) {
                String token = validToken(prefs);
                ensureNames(prefs, token);
                for (int id : ids) {
                    String prop = prefs.widgetProp(id);
                    if (prop == null) continue;
                    if (prefs.widgetName(id) == null) {
                        String n = prefs.nameFor(prop);
                        if (n != null) prefs.widgetName(id, n);
                    }
                    try {
                        int count = GaApi.sessionsToday(token, prop);
                        prefs.widgetResult(id, count, System.currentTimeMillis());
                        ok = true;
                    } catch (Exception e) {
                        prefs.widgetError(id, String.valueOf(e.getMessage()));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        WidgetProvider.updateAll(ctx);
        if (cb != null) cb.done(ok, message);
        return ok;
    }

    /** Upisuje listu property-ja u keš imena (id -> displayName). */
    public static void cacheNames(Prefs prefs, List<GaAdmin.Property> list) {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            for (GaAdmin.Property p : list) {
                if (p.displayName != null && !p.displayName.isEmpty()) o.put(p.id, p.displayName);
            }
            prefs.namesCache(o.toString(), System.currentTimeMillis());
        } catch (Exception ignored) {
        }
    }

    /** Čuva listu u keš (i osvežava keš imena); sortira po nazivu. */
    public static void storeList(Prefs prefs, List<GaAdmin.Property> list) {
        List<GaAdmin.Property> sorted = new java.util.ArrayList<>(list);
        sorted.sort((a, b) -> {
            String an = a.displayName == null ? "" : a.displayName.toLowerCase();
            String bn = b.displayName == null ? "" : b.displayName.toLowerCase();
            return an.compareTo(bn);
        });
        cacheNames(prefs, sorted);
        try {
            prefs.propList(GaAdmin.toJson(sorted), System.currentTimeMillis());
        } catch (Exception ignored) {
        }
    }

    /** Keširana lista property-ja ili null ako je nema. */
    public static List<GaAdmin.Property> cachedList(Prefs prefs) {
        try {
            return GaAdmin.fromJson(prefs.propList());
        } catch (Exception e) {
            return null;
        }
    }

    /** Force sync liste sa Google-a; upisuje je u keš. */
    public static List<GaAdmin.Property> syncProperties(Prefs prefs) throws Exception {
        List<GaAdmin.Property> list = GaAdmin.listProperties(validToken(prefs));
        storeList(prefs, list);
        return list;
    }

    /** Osvežava keš imena najviše jednom na 6 sati (uz validan token). */
    public static void ensureNames(Prefs prefs, String token) {
        if (System.currentTimeMillis() - prefs.propListAt() < 6 * 3600_000L) return;
        try {
            storeList(prefs, GaAdmin.listProperties(token));
        } catch (Exception ignored) {
        }
    }

    /** Vraća važeći access token, po potrebi ga osvežava (keširan u prefs — jedan refresh po rundi). */
    public static String validToken(Prefs prefs) throws Exception {
        String token = prefs.accessToken();
        if (token != null && System.currentTimeMillis() < prefs.accessExpiresAt() - 60_000) {
            return token;
        }
        String[] t = OAuth.refresh(prefs.refreshToken());
        long expiresAt = System.currentTimeMillis() + Long.parseLong(t[2]) * 1000L;
        prefs.tokens(t[0], expiresAt);
        if (t[1] != null) prefs.refreshToken(t[1]);
        return t[0];
    }
}
