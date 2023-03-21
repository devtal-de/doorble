package de.devtal.doorble;

import android.app.Fragment;

import android.os.Bundle;

import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;

import android.widget.ListView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Switch;
import android.widget.CompoundButton;

import android.widget.AdapterView;

import java.util.ArrayList;
import java.util.Arrays;

public class FragmentEditlist extends Fragment {
	public ListAdapter la;
	private ListView lv;

	public FragmentEditlist(){
		super();
	}

	View.OnClickListener add_onclick = new View.OnClickListener() {
				public void onClick(View v){
					DoorBLE act = (DoorBLE) getActivity();
					act.to_editor();
				}};

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			 Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_editlist, container, false);


		if(getActivity() != null) {
			la = new Door.DoorListAdapter() {
				@Override
				public View getView(int position, View convertView,
						ViewGroup parent){
					final Door d = getItem(position);
					View v = getActivity().getLayoutInflater().inflate(
						R.layout.edit_listitem, null, true);
					TextView nv = (TextView) v.findViewById(R.id.eli_name);
					TextView mv = (TextView) v.findViewById(R.id.eli_mac);
					nv.setText(d.name());
					mv.setText(d.mac());
					Switch vis = (Switch) v.findViewById(R.id.tb_visibility);
					vis.setChecked(d.visible());
					vis.setOnCheckedChangeListener(
						new Switch.OnCheckedChangeListener(){
							public void onCheckedChanged(CompoundButton bv,
									boolean isChecked){
								d.start_update().visible(isChecked).commit();
							}
						});
					v.findViewById(R.id.edit_door_button)
						.setOnClickListener(new View.OnClickListener() {
							public void onClick(View v){
								DoorBLE act = (DoorBLE) getActivity();
								act.to_editor(d);
							}});

					return v;
				}
			};

			v.findViewById(R.id.create_door_button)
				.setOnClickListener(add_onclick);

			View fv = inflater.inflate(R.layout.edit_listfooter, null, false);
			fv.findViewById(R.id.add_door_button)
				.setOnClickListener(add_onclick);

			lv = (ListView) v.findViewById(android.R.id.list);
			lv.setEmptyView(v.findViewById(R.id.no_doors_view));
			lv.addFooterView(fv);
			lv.setAdapter(la);

			getActivity().setTitle("Edit Doors");
		}

		return v;
	}
}
