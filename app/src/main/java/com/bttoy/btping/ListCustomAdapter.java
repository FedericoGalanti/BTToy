package com.bttoy.btping;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.altbeacon.beacon.Beacon;

import java.util.ArrayList;

public class ListCustomAdapter extends BaseAdapter {

    private Context ctx;
    private ArrayList<Beacon> sauce;

    public ListCustomAdapter(Context ctx, ArrayList<Beacon> sauce) {
        this.ctx = ctx;
        this.sauce = sauce;
    }

    @Override
    public int getCount() {
        return sauce.size();
    }

    @Override
    public Object getItem(int position) {
        return sauce.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {

        ItemListHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(ctx).inflate(R.layout.adapter_list_view, parent, false);
            holder = new ItemListHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ItemListHolder) convertView.getTag();
        }
        Beacon item = (Beacon) getItem(position);

        holder.uid.setText(item.getId1().toString());
        holder.major.setText(item.getId2().toString());
        holder.minor.setText(item.getId3().toString());

        return convertView;
    }


    static class ItemListHolder {
        TextView uid;
        TextView major;
        TextView minor;

        ItemListHolder(@NonNull View itemView) {
            uid = itemView.findViewById(R.id.item_name);
            major = itemView.findViewById(R.id.item_descr1);
            minor = itemView.findViewById(R.id.item_descr2);
        }
    }
}
