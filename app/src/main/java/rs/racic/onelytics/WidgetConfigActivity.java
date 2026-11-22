package rs.racic.onelytics;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Ekrancic koji se otvara pri dodavanju widzeta: izbor property-ja i boje.
 * Property je read-only — bira se iskljucivo iz Google liste.
 */
public class WidgetConfigActivity extends Activity {

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private TextView propNameText;
    private TextView propIdText;
    private LinearLayout colorRow;
    private TextView colorName;
    private int selectedColor = 0;
    private String pickedPropId;
    private String pickedPropName;
    private String[] paletteNames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_widget_config);

        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }

        propNameText = findViewById(R.id.config_property_name);
        propIdText = findViewById(R.id.config_property_id);
        colorRow = findViewById(R.id.config_color_row);
        colorName = findViewById(R.id.config_color_name);
        Button pickButton = findViewById(R.id.config_pick_button);
        Button saveButton = findViewById(R.id.config_save_button);
        findViewById(R.id.config_property_box).setOnClickListener(v -> showPropertyPicker());

        // podešavanja postojećeg widžeta (ako se ponovo otvara)
        Prefs prefs = new Prefs(this);
        String existing = prefs.widgetProp(widgetId);
        if (existing != null) {
            pickedPropId = existing;
            pickedPropName = prefs.widgetName(widgetId);
        }
        selectedColor = prefs.widgetColor(widgetId);

        renderProperty();
        paletteNames = getResources().getStringArray(R.array.palette_names);
        buildColorRow();
        pickButton.setOnClickListener(v -> showPropertyPicker());
        saveButton.setOnClickListener(v -> save());
    }

    private void renderProperty() {
        if (pickedPropId == null) {
            propNameText.setText("—");
            propIdText.setText(getString(R.string.hint_tap_to_pick));
        } else {
            propNameText.setText(pickedPropName != null && !pickedPropName.isEmpty()
                    ? pickedPropName : pickedPropId);
            propIdText.setText(pickedPropName != null && !pickedPropName.isEmpty()
                    ? getString(R.string.id_prefix, pickedPropId) : "");
        }
    }

    private void buildColorRow() {
        colorRow.removeAllViews();
        int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 44, getResources().getDisplayMetrics());
        int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 7, getResources().getDisplayMetrics());

        for (int i = 0; i < Palette.COLORS.length; i++) {
            final int index = i;
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            lp.gravity = Gravity.CENTER_VERTICAL;
            dot.setLayoutParams(lp);
            dot.setContentDescription(paletteNames[i]);
            dot.setOnClickListener(v -> {
                selectedColor = index;
                buildColorRow();
            });
            colorRow.addView(dot);
        }
        updateColorDots();
    }

    /** Kruzic u boji pozadine palete, prsten u boji broja; deblji prsten = izabrano. */
    private void updateColorDots() {
        for (int i = 0; i < colorRow.getChildCount(); i++) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Palette.bg(i));
            d.setStroke(i == selectedColor ? 6 : 2, Palette.num(i));
            colorRow.getChildAt(i).setBackground(d);
        }
        colorName.setText(paletteNames[Palette.idx(selectedColor)]);
    }

    private void showPropertyPicker() {
        final Prefs prefs = new Prefs(this);
        if (prefs.refreshToken() == null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.err_not_configured)
                    .setMessage(R.string.config_login_needed)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        List<GaAdmin.Property> cached = Refresher.cachedList(prefs);
        if (cached != null && !cached.isEmpty()) {
            showPropertyDialog(prefs, cached);
            return;
        }
        syncPropertyList(prefs);
    }

    private void syncPropertyList(final Prefs prefs) {
        Toast.makeText(this, R.string.loading_properties, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<GaAdmin.Property> list;
            try {
                list = Refresher.syncProperties(prefs);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.err_properties, String.valueOf(e.getMessage())),
                        Toast.LENGTH_LONG).show());
                return;
            }
            final List<GaAdmin.Property> finalList = list;
            runOnUiThread(() -> {
                if (finalList.isEmpty()) {
                    Toast.makeText(this, R.string.err_properties_empty, Toast.LENGTH_SHORT).show();
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
                    GaAdmin.Property p = list.get(which);
                    pickedPropId = p.id;
                    pickedPropName = p.displayName;
                    renderProperty();
                })
                .setNeutralButton(R.string.sync_analytics, (d, w) -> syncPropertyList(prefs))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void save() {
        if (pickedPropId == null || pickedPropId.isEmpty()) {
            Toast.makeText(this, R.string.err_not_configured, Toast.LENGTH_SHORT).show();
            return;
        }
        Prefs prefs = new Prefs(this);
        prefs.widgetProp(widgetId, pickedPropId);
        prefs.widgetColor(widgetId, selectedColor);
        prefs.widgetName(widgetId,
                pickedPropName != null && !pickedPropName.isEmpty() ? pickedPropName : null);

        // odmah povuci broj za ovaj widzet
        new Thread(() -> {
            try {
                int count = GaApi.sessionsToday(Refresher.validToken(prefs), pickedPropId);
                prefs.widgetResult(widgetId, count, System.currentTimeMillis());
            } catch (Exception e) {
                prefs.widgetError(widgetId, String.valueOf(e.getMessage()));
            }
            WidgetProvider.updateAll(WidgetConfigActivity.this);
        }).start();

        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, result);
        finish();
    }
}
