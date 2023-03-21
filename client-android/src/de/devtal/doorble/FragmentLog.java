package de.devtal.doorble;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;

import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;

import android.widget.TextView;
import android.text.method.ScrollingMovementMethod;

import java.lang.StringBuilder;

public class FragmentLog extends Fragment {
	private StringBuilder out;

	public FragmentLog(){
		super();
		out = new StringBuilder("Hello, world");
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			 Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_log, container, false);

		TextView text = (TextView) v.findViewById(R.id.my_text);
		text.setText(out);
		text.setMovementMethod(new ScrollingMovementMethod());

		if (getActivity() != null)
			getActivity().setTitle("DoorBLE log");

		return v;
	}

	public void append_text(String s){
		out.append("\n"+s);

		Activity a = getActivity();
		if(a != null)
			a.runOnUiThread(new Runnable(){
				@Override
				public void run(){
					((TextView) getView().findViewById(R.id.my_text))
						.setText(new String(out));
				}
			});
	}
}
