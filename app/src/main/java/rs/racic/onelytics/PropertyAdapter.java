package rs.racic.onelytics;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

/** Lista property-ja u dve vrste: naziv gore, ID dole prigušeno. */
public class PropertyAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<GaAdmin.Property> items;

    public PropertyAdapter(Context ctx, List<GaAdmin.Property> items) {
        this.inflater = LayoutInflater.from(ctx);
        this.items = items;
    }

    @Override
    public int getCount() { return items.size(); }

    @Override
    public Object getItem(int i) { return items.get(i); }

    @Override
    public long getItemId(int i) { return i; }

    @Override
    public View getView(int i, View v, ViewGroup parent) {
        if (v == null) v = inflater.inflate(R.layout.dialog_property_item, parent, false);
        GaAdmin.Property p = items.get(i);
        ((TextView) v.findViewById(R.id.item_name)).setText(
                p.displayName == null || p.displayName.isEmpty() ? p.id : p.displayName);
        ((TextView) v.findViewById(R.id.item_id)).setText(
                v.getContext().getString(R.string.id_prefix, p.id));
        return v;
    }
}
