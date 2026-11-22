package rs.racic.onelytics;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {

    private TextView statusNumber, statusText;
    private TextView propertyName, propertyIdText;
    private Button loginButton, refreshButton, logoutButton, pickButton, intervalButton;
    private boolean autoLoginTriggered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusNumber = findViewById(R.id.status_number);
        statusText = findViewById(R.id.status_text);
        propertyName = findViewById(R.id.property_name);
        propertyIdText = findViewById(R.id.property_id);
        loginButton = findViewById(R.id.login_button);
        refreshButton = findViewById(R.id.refresh_button);
        logoutButton = findViewById(R.id.logout_button);
        pickButton = findViewById(R.id.pick_button);
        intervalButton = findViewById(R.id.interval_button);
        intervalButton.setOnClickListener(v -> showIntervalPicker());

        View propertyBox = findViewById(R.id.property_box);
        propertyBox.setOnClickListener(v -> showPropertyPicker());

        // uvek otvori na vrhu, bez obzira na staru poziciju skrola
        ScrollView scroll = findViewById(R.id.main_scroll);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_UP));

        loginButton.setOnClickListener(v -> startLogin());
        refreshButton.setOnClickListener(v -> doRefresh());
        pickButton.setOnClickListener(v -> showPropertyPicker());
        logoutButton.setOnClickListener(v -> {
            new Prefs(this).clearAuth();
            render();
        });

        // Trag pada od proslog pokretanja? Korisniku se pokazuje kratka poruka,
        // a sirovi stack trace ide u clipboard tek ako on to zatrazi.
        String crash = CrashReport.consume(this);
        if (crash != null) {
            final String trace = crash;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.crash_title)
                    .setMessage(R.string.crash_message)
                    .setPositiveButton(R.string.crash_copy, (dialog, which) -> {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("OneLytics", trace));
                        Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show();
                    })
                    .setNeutralButton(android.R.string.ok, null)
                    .show();
        } else if (savedInstanceState == null && new Prefs(this).refreshToken() == null) {
            // Pri prvom pokretanju (nema tokena) — odmah idi na prijavu
            autoLoginTriggered = true;
            startLogin();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Prefs prefs = new Prefs(this);
        render();
        if (prefs.isConfigured()) {
            Scheduler.schedule(this);
            doRefresh();
        } else if (prefs.refreshToken() != null && prefs.propertyId().isEmpty() && !pickerShown) {
            // Prijavljeni smo, ali property još nije izabran → ponudi listu
            showPropertyPicker();
        }
    }

    private boolean pickerShown = false;

    private void startLogin() {
        try {
            String state = OAuth.randomUrlToken();
            String verifier = OAuth.randomUrlToken();
            new Prefs(this).pendingOAuth(state, verifier);
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(OAuth.authUrl(state, verifier)));
            startActivity(i);
        } catch (Exception e) {
            toast(e.getMessage());
        }
    }

    /** Lista property-ja za izbor — iz lokalnog keša ako postoji, inače sa Google-a. */
    private void showPropertyPicker() {
        final Prefs prefs = new Prefs(this);
        if (prefs.refreshToken() == null) {
            toast(getString(R.string.err_not_configured));
            return;
        }
        List<GaAdmin.Property> cached = Refresher.cachedList(prefs);
        if (cached != null && !cached.isEmpty()) {
            showPropertyDialog(prefs, cached);
            return;
        }
        syncPropertyList(prefs);
    }

    /** Force povlačenje liste sa Google-a, upis u keš, pa prikaz dijaloga. */
    private void syncPropertyList(final Prefs prefs) {
        pickerShown = true;
        toast(getString(R.string.loading_properties));
        new Thread(() -> {
            List<GaAdmin.Property> list;
            String error = null;
            try {
                list = Refresher.syncProperties(prefs);
            } catch (Exception e) {
                list = null;
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final List<GaAdmin.Property> finalList = list;
            final String finalError = error;
            runOnUiThread(() -> {
                pickerShown = false;
                if (finalList == null) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.err_properties_title)
                            .setMessage(finalError)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }
                if (finalList.isEmpty()) {
                    toast(getString(R.string.err_properties_empty));
                    return;
                }
                showPropertyDialog(prefs, finalList);
            });
        }).start();
    }

    private void showPropertyDialog(final Prefs prefs, final List<GaAdmin.Property> list) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.pick_property_title)
                .setAdapter(new PropertyAdapter(this, list), (d, which) -> {
                    String id = list.get(which).id;
                    prefs.propertyId(id);
                    render();
                    doRefresh();
                })
                .setNeutralButton(R.string.sync_analytics, (d, w) -> syncPropertyList(prefs))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Dijalog za izbor učestalosti pozadinskog osvežavanja. */
    private void showIntervalPicker() {
        final Prefs prefs = new Prefs(this);
        final String[] labels = getResources().getStringArray(R.array.interval_labels);
        final int[] values = getResources().getIntArray(R.array.interval_values);
        int cur = prefs.intervalMin();
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == cur) checked = i;

        new AlertDialog.Builder(this)
                .setTitle(R.string.interval_title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    prefs.intervalMin(values[which]);
                    Scheduler.schedule(this); // zamenjuje postojeći raspored
                    render();
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void doRefresh() {
        Prefs prefs = new Prefs(this);
        if (!prefs.isConfigured()) {
            toast(getString(R.string.err_not_configured));
            return;
        }
        statusNumber.setText("…");
        new Thread(() -> Refresher.refresh(this, (ok, msg) ->
                runOnUiThread(() -> {
                    render();
                    if (!ok) toast(msg); // uspeh je tih — broj se samo ažurira
                }))).start();
    }

    private void render() {
        Prefs prefs = new Prefs(this);
        boolean authed = prefs.refreshToken() != null;
        loginButton.setText(authed ? R.string.login_again : R.string.login);
        logoutButton.setVisibility(authed ? View.VISIBLE : View.GONE);
        refreshButton.setEnabled(authed);
        pickButton.setEnabled(authed);

        String pid = prefs.propertyId();
        if (pid.isEmpty()) {
            propertyName.setText("—");
            propertyIdText.setText(authed ? getString(R.string.hint_tap_to_pick)
                    : getString(R.string.hint_login_needed));
        } else {
            String name = prefs.nameFor(pid);
            propertyName.setText(name != null ? name : pid);
            propertyIdText.setText(name != null ? getString(R.string.id_prefix, pid) : "");
        }

        String[] labels = getResources().getStringArray(R.array.interval_labels);
        int[] values = getResources().getIntArray(R.array.interval_values);
        int cur = prefs.intervalMin();
        String label = labels[0];
        for (int i = 0; i < values.length; i++) if (values[i] == cur) label = labels[i];
        intervalButton.setText(getString(R.string.refresh_every, label));

        int count = prefs.lastCount();
        String error = prefs.lastError();
        if (count >= 0) {
            statusNumber.setText(String.valueOf(count));
            statusText.setText(error != null
                    ? getString(R.string.status_last_ok_error)
                    : getString(R.string.status_sessions_today));
        } else if (error != null) {
            statusNumber.setText("—");
            statusText.setText(error);
        } else {
            statusNumber.setText("—");
            statusText.setText(R.string.status_no_data);
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
