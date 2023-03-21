package de.devtal.doorble;

import android.app.Fragment;

import android.os.Bundle;

import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;

import android.widget.ListView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Button;

import android.content.DialogInterface;
import android.app.AlertDialog;

import java.util.ArrayList;

public class FragmentEditor extends Fragment {
	protected Door door;

	public FragmentEditor(){
		super();
		door = null;
	}

	public FragmentEditor(Door d){
		super();
		door = d;
	}

	protected TextView nv;
	protected SuggestionTextView mv;
	protected TextView pv;
	protected TextView sv;
	protected Switch vis;
	protected Switch valtime;
	protected Button save;
	protected TextView err;


	protected class deleteClickListener implements View.OnClickListener {
		public void onClick(View v){
			new AlertDialog.Builder(getActivity())
				.setTitle("delete door "+door.name())
				.setMessage("are you sure to delete door "+door.name()+"?")
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setPositiveButton(android.R.string.yes,
					new DialogInterface.OnClickListener(){
						public void onClick(DialogInterface dia, int btn){
							door.delete();
							getActivity().onNavigateUp();
						};
					})
				.setNegativeButton(android.R.string.no, null)
				.show();
		}
	};
	protected class saveClickListener implements View.OnClickListener {
		public void onClick(View v){
			if(door == null)
				door = new Door();

			try {
				door.start_update()
					.name(nv.getText().toString())
					.mac(mv.getText().toString())
					.pubkey(pv.getText().toString())
					.sub(sv.getText().toString())
					.visible(vis.isChecked())
					.validate_time(valtime.isChecked())
					.commit();
				getActivity().onNavigateUp();
			} catch(IllegalArgumentException e){
				err.setText(e.toString());
			}
		}
	};


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			 Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_editor, container, false);

		nv = (TextView) v.findViewById(R.id.eli_name);
		mv = (SuggestionTextView) v.findViewById(R.id.eli_mac);
		pv = (TextView) v.findViewById(R.id.eli_pubkey);
		sv = (TextView) v.findViewById(R.id.eli_sub);
		vis = (Switch) v.findViewById(R.id.eli_visibility);
		valtime = (Switch) v.findViewById(R.id.eli_valtime);
		v.findViewById(R.id.eli_save)
			.setOnClickListener(new saveClickListener());
		v.findViewById(R.id.eli_delete)
			.setOnClickListener(new deleteClickListener());

		err = (TextView) v.findViewById(R.id.eli_error);

		if (door != null){
			nv.setText(door.name());
			mv.setText(door.mac());
			pv.setText(door.encode_pubkey());
			sv.setText(door.sub());
			vis.setChecked(door.visible());
			valtime.setChecked(door.validate_time());
		} else {
			// default values for new button
			vis.setChecked(true);
			valtime.setChecked(true);
		}


		if (getActivity() != null) {
			getActivity().setTitle((door == null)?
				"Create Door" : "Edit "+door.name());

			DoorBLE.ScannedDeviceAdapter mac_autocomplete =
				((DoorBLE) getActivity()).getSDA();
			mv.setAdapter(mac_autocomplete);
		}

		return v;
	}
}
