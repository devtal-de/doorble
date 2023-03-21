package de.devtal.doorble;

import android.app.Fragment;
import android.os.Bundle;

import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;

import android.widget.ListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.AdapterView;
import android.widget.TextView;

import java.util.Date;

import android.util.Log;
import android.util.Pair;

public class FragmentMain extends Fragment { // FIXME: -> ListFragment?
	private Door.DoorListAdapter la;
	private ListView lv;
	private View gv;
	private LayoutInflater lay_inf;

	// door was selected manually (i.e. clicked on)
	public boolean manual_select = false;

	public FragmentMain(){
		super();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			 Bundle savedInstanceState) {
		lay_inf = inflater;
		final View v = inflater.inflate(R.layout.fragment_main, container,
			false);
		gv = v;

		if(getActivity() != null){
			lv = (ListView) v.findViewById(R.id.lv_door_select);

			View fv = inflater.inflate(R.layout.main_listfooter, null, false);
			fv.findViewById(R.id.edit_door_button)
				.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v){
						DoorBLE act = (DoorBLE) getActivity();
						act.to_editlist();
					}});
			lv.addFooterView(fv);
			lv.setEmptyView(v.findViewById(R.id.no_doors_view));
			lv.setOnItemClickListener(
				new AdapterView.OnItemClickListener(){
					public void onItemClick(AdapterView parent, View view,
							int pos, long id){
						manual_select = true;
						enable_buttons();
					}
				});

			if (la == null) {
				la = new Door.DoorListAdapter(){
					@Override
					protected boolean filter(Door d){
						return d.visible();
					}

					public boolean isEnabled(int position){
						return getItem(position).dev() != null;
					}

					public View getView(int position, View convertView,
							ViewGroup parent){
						Door d = getItem(position);
						View v = getActivity().getLayoutInflater().inflate(
							R.layout.main_listitem, null, true);
						TextView nv = (TextView) v.findViewById(R.id.mli_name);
						TextView mv = (TextView) v.findViewById(R.id.mli_mac);
						nv.setText(d.name());
						mv.setText(d.mac());
						boolean e = isEnabled(position);
						nv.setEnabled(e);
						mv.setEnabled(e);
						v.setEnabled(e);
						return v;
					}
				};
			}
			lv.setAdapter(la);

			v.findViewById(R.id.create_door_button)
				.setOnClickListener( new View.OnClickListener() {
					public void onClick(View v){
						DoorBLE act = (DoorBLE) getActivity();
						if(Door.get_all().length > 0)
							act.to_editlist();
						else
							act.to_editor();
					}});


			getActivity().setTitle("DoorBLE - Open Door");
		}

		return v;
	}

	@Override
	public void onViewStateRestored(Bundle savedInstanceState) {
		super.onViewStateRestored(savedInstanceState);
		if (lv.getCheckedItemPosition() != AdapterView.INVALID_POSITION)
			enable_buttons();
		else Log.i("DoorBLE", "none selected");
	}


	private Door cached_door_selection;

	public void enable_buttons(){
		View cv = gv.findViewById(R.id.cmd_button_container);
		if (! (cv instanceof ViewGroup)) return;
		ViewGroup c = (ViewGroup) cv;

		Door d = la.getItem(lv.getCheckedItemPosition());
		if(c.getChildCount() != 0 && cached_door_selection == d)
			return; // selection did not change, view already build

		cached_door_selection = d;
		c.removeAllViews();

		for (final Pair<String, String> bd : d.buttons()){
			Button b = (Button) lay_inf.inflate(R.layout.main_door_button, c, false);
			b.setText(bd.first);
			b.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v){
					DoorBLE act = (DoorBLE) getActivity();
					int pos = lv.getCheckedItemPosition();
					if (pos != AdapterView.INVALID_POSITION)
						act.door_action((Door) la.getItem(pos), bd.second);
				}
			});
			c.addView(b);
		}
	}


	/**
	 * notification that a new door was detected
	 **/
	public void scanned_notify_update() {
		la.notifyDataSetChanged();

		// TODO: add hysteresis
		final int r_ms = 60*1000;
		int best_pos = -1;
		if (!manual_select) {
			for(int i = 0; i < la.getCount(); i++){
				Door d = la.getItem(i);
				if (d.scan_time() == null)
					continue;
				if (d.scan_time().getTime() < (new Date().getTime() - r_ms))
					continue;
				if (best_pos < 0 ||
						d.scan_rssi() > la.getItem(best_pos).scan_rssi())
					best_pos = i;
			}
			if (best_pos >= 0){
				int cur_pos = lv.getCheckedItemPosition();
				if (best_pos != cur_pos){
					lv.setItemChecked(cur_pos, false);
					Log.i("DoorBLE", "selection changed automatically");
					lv.setItemChecked(best_pos, true);
					enable_buttons();
				}
			}
		}
	}
}
