package de.devtal.doorble;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.app.FragmentManager;
import android.app.ActionBar;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Filterable;
import android.widget.Filter;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;
import java.lang.StringBuilder;

// import android.permission;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.Intent;
import android.app.AlertDialog;
import android.Manifest;
import android.util.Log;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanRecord;

import android.content.ClipboardManager;
import android.content.ClipData;

import java.io.FileOutputStream;
import java.io.FileInputStream;
// import java.time.Instant;

public class DoorBLE extends Activity
		implements FragmentManager.OnBackStackChangedListener {
	private BluetoothLeScanner ble_scan;
	private BluetoothGatt ble_gatt;
	private boolean dangerous = false; // enables debug logging & options
	private Door selected = null;
	private String selected_action = null;
	private MenuItem mi_scan;
	FragmentMain fragment_main;
	FragmentLog fragment_log;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		fragment_main = new FragmentMain();
		fragment_log = new FragmentLog();

		try{
			Door.load(getApplicationContext());
		} catch(IllegalArgumentException e){
			append_text(" INVALID DOOR: " + e.getMessage());
		}

		// String[] perm_list_a = {"android.permission.BLUETOOTH_CONNECT"};
		// requestPermissions(perm_list_a, 21);

		/*
 		String[] perm_list_a = {"android.permission.BLUETOOTH_SCAN","android.permission.ACCESS_FINE_LOCATION","android.permission.BLUETOOTH_ADMIN"};
		String[] perm_list_b = {"android.permission.ACCESS_FINE_LOCATION"};
		String[] perm_list_z = {Manifest.permission.ACCESS_FINE_LOCATION};
		String[] perm_list_c = {"android.permission.ACCESS_COARSE_LOCATION"};
		String[] perm_list_d = {"android.permission.BLUETOOTH_ADMIN"};
		requestPermissions(perm_list_a, 21);
		requestPermissions(perm_list_b, 22);
		requestPermissions(perm_list_c, 23); requestPermissions(perm_list_d, 24);
		requestPermissions(perm_list_z, 20);

		if (ContextCompat.checkSelfPermission(getApplicationContext(),
		*/

		getFragmentManager().addOnBackStackChangedListener(this);
		getFragmentManager().beginTransaction()
//			.setReorderingAllowed(true)
			.add(R.id.display_fragment, fragment_main, null)
			.commit();

		start_scan();
	}

	public void append_text(String s){
		fragment_log.append_text(s);
	}

	public void to_log(){
		getFragmentManager().beginTransaction()
//			.setReorderingAllowed(true)
			.replace(R.id.display_fragment, fragment_log, null)
			.addToBackStack(null)
			.commit();
	}

	public void to_editlist(){
		Fragment f = new FragmentEditlist();
		getFragmentManager().beginTransaction()
//			.setReorderingAllowed(true)
			.replace(R.id.display_fragment, f, null)
			.addToBackStack(null)
			.commit();
	}

	public void to_editor(Door d){
		Fragment f = new FragmentEditor(d);
		getFragmentManager().beginTransaction()
//			.setReorderingAllowed(true)
			.replace(R.id.display_fragment, f, null)
			.addToBackStack(null)
			.commit();
	}

	public void to_editor(){
		to_editor(null);
	}

	@Override
	public void onBackStackChanged(){
		getActionBar().setDisplayHomeAsUpEnabled(
			getFragmentManager().getBackStackEntryCount()>0);
	}

	@Override
	public boolean onNavigateUp() {
		getFragmentManager().popBackStack();
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem mi) {
		switch (mi.getItemId()){
		case R.id.menu_key_clipboard:
			key_to_clipboard();
			return true;
		case R.id.menu_key_share:
			key_to_share();
			return true;
		case R.id.menu_show_log:
			to_log();
			return true;
		case R.id.menu_scan:
			toggle_scan();
			return true;
		case R.id.menu_flush_db:
			Door.flush_db();
			return true;
		case R.id.menu_load_dummy:
			Door.load_dummy();
			return true;
		default:
			return super.onOptionsItemSelected(mi);
		}
	}
	public void update_scan_status(){
		if(mi_scan == null)
			return;
		if(ble_scan != null){
			mi_scan.setIcon(R.drawable.sensors_off);
			mi_scan.setTitle(R.string.menu_scan_off);
		} else {
			mi_scan.setIcon(R.drawable.sensors);
			mi_scan.setTitle(R.string.menu_scan_on);
		}

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main_menu, menu);
		mi_scan = menu.findItem(R.id.menu_scan);
		if (dangerous) {
			menu.findItem(R.id.menu_flush_db).setVisible(true);
			menu.findItem(R.id.menu_load_dummy).setVisible(true);
		}
		return true;
	}

	public boolean toggle_scan(){
		if(ble_scan != null){
			ble_scan.stopScan(scb);
			ble_scan = null;
			update_scan_status();
			return false;
		} else {
			start_scan();
			return true;
		}
	}
		

	public void start_scan(){
		if (getApplicationContext().checkSelfPermission(
				"android.permission.BLUETOOTH_CONNECT") ==
				PackageManager.PERMISSION_GRANTED) {
			do_bluetooth();
		} else {
			requestPermissions( // getApplicationContext(),
				new String[] {"android.permission.BLUETOOTH_CONNECT"},
				1337);
		}
	}

	private JWTHandler jwt_handler = null;

	private JWTHandler get_jwt_handler(){
		if (jwt_handler == null) {
			try {
				jwt_handler = new JWTHandler();
			} catch (JWTHandler.JWTException e){
				Log.e("DoorBLE", e.toString());
				Context ctx = getApplicationContext();
				// FIXME: resource string
				Toast.makeText(ctx, "failed to get signing key",
							Toast.LENGTH_SHORT).show();
				return null;
			}
			if (jwt_handler.is_key_generated()){
				Context ctx = getApplicationContext();
				Toast.makeText(ctx, R.string.toast_keygen,
							Toast.LENGTH_SHORT).show();
			}
		}
		return jwt_handler;
	}

	private void key_to_clipboard(){
		Context ctx = getApplicationContext();
		try {
			String pem = get_jwt_handler().export_pubkey();

			ClipboardManager clipboard =
				(ClipboardManager) getApplicationContext()
				.getSystemService(CLIPBOARD_SERVICE);
			ClipData clip = ClipData.newPlainText("public key", pem);
			clipboard.setPrimaryClip(clip);
			Toast.makeText(ctx, R.string.toast_keyclip,
				Toast.LENGTH_SHORT).show();
		} catch(JWTHandler.JWTException e) {
			Log.e("DoorBLE", e.toString());
			// FIXME: resource string
			Toast.makeText(ctx, "error getting key",
				Toast.LENGTH_SHORT).show();
		}
	}

	private void key_to_share(){
		try {
			String pem = get_jwt_handler().export_pubkey();

			Intent sendIntent = new Intent();
			sendIntent.setAction(Intent.ACTION_SEND);
			sendIntent.putExtra(Intent.EXTRA_TEXT, pem);
			sendIntent.setType("text/plain");

			Intent shareIntent = Intent.createChooser(sendIntent, null);
			startActivity(shareIntent);
		} catch(JWTHandler.JWTException e) {
			Log.e("DoorBLE", e.toString());
			// FIXME: resource string
			Context ctx = getApplicationContext();
			Toast.makeText(ctx, "error getting key",
				Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * @brief any bluetooth device matching profile found during scan
	 *
	 * @details ScannedDevice describes a instance of a bluetooth device matching the door
	 * profile, but not necessary in our local door database.
	 *
	 * A collection (in form of a hashmap) of these devices is used to recommend MAC
	 * addresses in the editor view
	 **/
	public class ScannedDevice {
		public String mac;
		public String name;
		public int rssi;
		public String toString(){
			return name + " (" + mac + " @ "+ rssi + ")";
		}
	}
	public HashMap<String, ScannedDevice> scanned_devices = new HashMap();
	public ScannedDevice scanned_devices_add(String mac, String name,
			int rssi){
		ScannedDevice d = scanned_devices.get(mac);
		if(d == null) {
			d = new ScannedDevice();
			scanned_devices.put(mac, d);
		}
		d.mac = mac;
		d.name = (name == null)?mac:name;
		d.rssi = rssi;
		return d;
	}

	public class ScannedDeviceAdapter extends BaseAdapter
			implements Filterable {
		// TODO: observer
		private String[] keys(){
			return scanned_devices.keySet().toArray(new String[]{});
		}

		public String getItem(int pos) {
			return scanned_devices.get(keys()[pos]).mac;
		}

		public int getCount() {
			Log.i("DoorBLE", "count"+String.valueOf(keys().length));
			return keys().length;
		}

		public long getItemId (int pos){
			return keys()[pos].hashCode();
		}

		@Override
		public View getView(int pos, View convertView, ViewGroup parent){
			final String s = scanned_devices.get(keys()[pos]).toString();
			View v = getLayoutInflater().inflate(android.R.layout.simple_dropdown_item_1line,
				null, true);
			((TextView) v).setText(s);
			return v;
		}

		public Filter getFilter(){
			return new Filter(){
				protected Filter.FilterResults performFiltering(
						CharSequence constraint){
					// TODO: perform filtering!
					Filter.FilterResults res = new Filter.FilterResults();
					res.count = keys().length;
					res.values = keys();
					return res;
				}
				protected void publishResults(CharSequence constraint,
						Filter.FilterResults res) {
				}
			};
		}
	}
	public ScannedDeviceAdapter getSDA(){
		return new ScannedDeviceAdapter();
	}


	private void do_bluetooth(){
		BluetoothManager bt_man = (BluetoothManager) getApplicationContext().getSystemService(BLUETOOTH_SERVICE);
		BluetoothAdapter bt_a = bt_man.getAdapter();

		if(bt_a != null)
			append_text(bt_a.getAddress());

		ble_scan = bt_a.getBluetoothLeScanner();
		if(ble_scan == null) 
			append_text("scanner failed");
		ble_scan.startScan(scb);
		update_scan_status();
		append_text("scanner started");
	}

	static UUID svc_target_uuid =
		UUID.fromString("12345678-1234-5678-1234-56789abcdef0");
	private ScanCallback scb = new ScanCallback() {
//		private List<String> macs = new ArrayList<String>();
		@Override
		public void onScanResult(int type, ScanResult res){
			final BluetoothDevice dev = res.getDevice();
			final ScanRecord rec = res.getScanRecord();
			final String addr = dev.getAddress();

			append_text(addr);

			Door[] ds = Door.get_all();
			if (ds != null) { // match and update door
				for(Door d: ds){
					if(addr.equals(d.mac())){
						// ble_scan.stopScan(this);
						d.dev(dev, res.getRssi());
						fragment_main.scanned_notify_update();
					}
				}
			}

			// list of scanned mac addresses matching ble profile
			if(rec == null)
				return;
			List<android.os.ParcelUuid> svc_uuids = rec.getServiceUuids();
			if(svc_uuids == null)
				return;
			for(android.os.ParcelUuid pu: svc_uuids){
				if(pu == null)
					continue;
				UUID u = pu.getUuid();
				if(u.equals(svc_target_uuid)){
					Log.e("DoorBLE", "found DoorBLE device "+
						scanned_devices_add(addr, rec.getDeviceName(),
							res.getRssi()).toString());
				}
			}
		}
	};

	public void door_action(Door d, String action){
		// FIXME: cancel running
		selected = d;
		selected_action = action;
		if(selected.dev() != null){
			ble_gatt = selected.dev().connectGatt(
				getApplicationContext(), false, gcb);
		} else
			Log.e("DoorBLE", "connect to null device");
	}

	private BluetoothGattCallback gcb = new BluetoothGattCallback() {
		public void onConnectionStateChange (BluetoothGatt gatt, 
                int status, 
                int newState) {
			if(gatt == null) return;
			
			if(status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
				gatt.discoverServices();
				append_text("connected");
			}
			

		}
		/* public void onCharacteristicRead (BluetoothGatt gatt, 
				BluetoothGattCharacteristic characteristic, 
				byte[] value, 
				int status){*/ 
		@Override
		public void onCharacteristicRead (BluetoothGatt gatt, 
                BluetoothGattCharacteristic characteristic, 
                int status) {
			final byte[] val = characteristic.getValue();
			final StringBuilder sb = new StringBuilder();
			String jwt_str;
			/* for(int i=0;i<val.length;i++)
				sb.append(String.format("%02X", val[i]));*/

			if (dangerous)
				Log.i("DoorBLE", "gatt read: " + new String(val));
			else
				Log.i("DoorBLE", "gatt value read");

			if(val.length > 10){
				try {
					JWTHandler jh = get_jwt_handler();
					long expiry = jh.decode_token(val, selected);

					// invalid tokens throw JWTException
					jwt_str = jh.get_jwt(new String(val), expiry,
						selected_action);
					Log.e("DoorBLE", "issued JWT: " + jwt_str);
					//gatt.beginReliableWrite();
					characteristic.setValue(jwt_str.getBytes());
					gatt.writeCharacteristic(characteristic);
					//gatt.executeReliableWrite();
				} catch(JWTHandler.JWTException e){
					Log.e("DoorBLE", e.toString());
					if(! e.invalid)
						return;

					// FIXME: getApplicationContext -> migrate to class
					runOnUiThread(new Runnable(){
						@Override
						public void run(){
							new AlertDialog.Builder(DoorBLE.this)
								.setTitle("invalid token") // FIXME: R.
								.setMessage("invalid token received")
								.setNeutralButton(android.R.string.ok, null)
								.setIcon(android.R.drawable.ic_dialog_alert)
								.show();
						}
					});
				}
			}
			Log.i("DoorBLE", "sent JWT"); // TODO: option for Toast
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status){
			final StringBuilder out = new StringBuilder();
			// StringBuilder out = new StringBuilder();

			// FIXME: status
			// BluetoothGattService svc = getService(...)
			List<BluetoothGattService> svcs = gatt.getServices();
			Iterator<BluetoothGattService> it_svcs = svcs.iterator();
			BluetoothGattService target_svc = null;
			UUID target_uuid = UUID.fromString("12345678-1234-5678-1234-56789abcdef0");
			while(it_svcs.hasNext()){
				BluetoothGattService s = it_svcs.next();
				out.append("\n"+ s.getUuid().toString());
				if(s.getUuid().equals(target_uuid)){
					target_svc = s;
					out.append("\n  MATCH");
				}
			}
			out.append(String.format("\n%s%d","done", svcs.size()));
			append_text("services discovered" + out.toString());

			final StringBuilder out_c = new StringBuilder();
			if(target_svc != null){
				List<BluetoothGattCharacteristic> chrs = 
					target_svc.getCharacteristics();
				Iterator<BluetoothGattCharacteristic> it_chrs = chrs.iterator();
				while(it_chrs.hasNext()){
					BluetoothGattCharacteristic c = it_chrs.next();
					out_c.append("\n"+ c.getUuid().toString());
				}
			}
			append_text("characteristics discovered" + out_c.toString());
			getServiceValue(gatt);

		}
	};

	public void getServiceValue(BluetoothGatt gatt){
		BluetoothGattService svc = gatt.getService(
			UUID.fromString("12345678-1234-5678-1234-56789abcdef0"));
		if (svc == null) return;
		BluetoothGattCharacteristic chr = svc.getCharacteristic(
			UUID.fromString("12345678-1234-5678-1234-56789abcdef1"));
		if (chr == null) return;

		append_text("YAY");

		if (!gatt.readCharacteristic(chr)) return;
	}

	public void onRequestPermissionsResult(int code, String[] perm, int[] grantResults){
		super.onRequestPermissionsResult(code, perm, grantResults);
		int i;
		int j=0;
		boolean success = true;
		for(i=0;i<grantResults.length;i++){
			if(grantResults[i] == PackageManager.PERMISSION_DENIED)
				success=false;
			j++;
		}


		if ( ! success ) {
			append_text("permission denied" + j);
		} else {
			append_text("permission GRANTED! :-)" + grantResults.length + " " + code + " " + perm.length);
		}

		if(code == 1337 && success){
			append_text("\n1337");
			do_bluetooth();
		}
	}

}
