package com.storm.pepper;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;
import java.util.TreeMap;

public class SensesListAdapter extends ArrayAdapter {
    private final Activity context;

    private final TreeMap<String, String> sensValues;
    List<String> senses;

    public SensesListAdapter(Activity context, List<String> senses, TreeMap<String, String> sensValues) {

        super(context, R.layout.senses_row, senses);

        this.context = context;
        this.sensValues = sensValues;
        this.senses = senses;
    }

    public void isStale() {
        if(senses.size() != this.getCount()) {
            this.notifyDataSetChanged();
        }
    }

    public View getView(int position, View view, ViewGroup parent) {
        String sense = senses.get(position);

        LayoutInflater inflater = context.getLayoutInflater();
        View rowView = inflater.inflate(R.layout.senses_row, null,true);

        // this code gets references to objects in the row xml file
        TextView senseName = rowView.findViewById(R.id.senseName);
        TextView senseValue = rowView.findViewById(R.id.senseValue);

        //this code sets the values of the objects to values from the arrays
        senseName.setText(sense);
        String value = sensValues.get(sense);
        senseValue.setText(value);

        if (value != null && ((String)value).toLowerCase().equals("true")) {
            senseName.setTypeface(null, Typeface.BOLD);
            rowView.setBackgroundColor(Color.CYAN);
        } else {
            senseName.setTypeface(null, Typeface.NORMAL);
            rowView.setBackgroundColor(Color.alpha(0));
        }

        return rowView;
    }
}
