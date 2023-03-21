package de.devtal.doorble;

import java.security.PublicKey;

import android.bluetooth.BluetoothAdapter; // for checking mac
import android.bluetooth.BluetoothDevice;

import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.Locale; // uppercase string
import java.util.Date;

import android.util.Log;
import android.util.Pair;

import android.provider.BaseColumns;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.content.Context;
import android.content.ContentValues;

public class Door {
	// Door REGISTRY

	public static ArrayList<Door> registry;

	public static class DbEntry implements BaseColumns {
		public static final String TAB_NAME = "doors";
		public static final String COL_NAME = "name";
		public static final String COL_PUBKEY = "pubkey";
		public static final String COL_SUB = "sub";
		public static final String COL_MAC = "mac";
		public static final String COL_VALTIME = "valtime";
		public static final String COL_VISIBLE = "visible";
		public static final String COL_CREATED = "created";
		public static final String COL_UPDATED = "updated";
	}

	private static final String DB_CREATE =
		"CREATE TABLE " + DbEntry.TAB_NAME + " (" +
			DbEntry._ID + " INTEGER PRIMARY KEY NOT NULL, "+
			DbEntry.COL_NAME + " TEXT NOT NULL, " +
			DbEntry.COL_PUBKEY + " TEXT NOT NULL, " +
			DbEntry.COL_SUB + " TEXT NOT NULL, " +
			DbEntry.COL_MAC + " TEXT NOT NULL, " +
			DbEntry.COL_VALTIME + " INTEGER NOT NULL, " +
			DbEntry.COL_VISIBLE + " INTEGER NOT NULL, " +
			DbEntry.COL_CREATED + " TIMESTAMP " +
				"DEFAULT CURRENT_TIMESTAMP NOT NULL, " +
			DbEntry.COL_UPDATED + " TIMESTAMP " +
				"DEFAULT CURRENT_TIMESTAMP NOT NULL );" +
		"CREATE TRIGGER " + DbEntry.TAB_NAME + "_ts_update " +
			"AFTER UPDATE ON " + DbEntry.TAB_NAME + " FOR EACH ROW " +
			"WHEN NEW.updated <= OLD.updated " +
			"BEGIN UPDATE " + DbEntry.TAB_NAME +
				" SET updated_at = CURRENT_TIMESTAMP WHERE " +
				DbEntry._ID + " = OLD." + DbEntry._ID + "; END;";


	private static final String DB_NAME = "doors.db";
	private static final int DB_VER = 1;

	public static class DbHelper extends SQLiteOpenHelper {
		public DbHelper(Context ctx) {
			super(ctx, DB_NAME, null, DB_VER);
		}
		public void onCreate(SQLiteDatabase db){
			db.execSQL(DB_CREATE);
		}
		public void onUpgrade(SQLiteDatabase db, int v_old, int v_new){ }
		public void onDowngrade(SQLiteDatabase db, int v_old, int v_new){ }
	}

	private static DbHelper dbHelper;

	public static Door[] load(Context ctx) throws IllegalArgumentException {
		registry = new ArrayList<Door>();

		dbHelper = new DbHelper(ctx);
		SQLiteDatabase db = dbHelper.getReadableDatabase();

		Cursor c = db.query(DbEntry.TAB_NAME, null /* projection */,
			null /* where */, null, null /* group */, null, null /* order*/);

		while(c.moveToNext()){
			Door d = new Door();
			d.id = c.getLong(c.getColumnIndexOrThrow(DbEntry._ID));
			d.start_update()
				.name(c.getString(c.getColumnIndexOrThrow(DbEntry.COL_NAME)))
				.pubkey(c.getString(
					c.getColumnIndexOrThrow(DbEntry.COL_PUBKEY)))
				.sub(c.getString(c.getColumnIndexOrThrow(DbEntry.COL_SUB)))
				.mac(c.getString(c.getColumnIndexOrThrow(DbEntry.COL_MAC)))
				.validate_time(c.getInt(
					c.getColumnIndexOrThrow(DbEntry.COL_VALTIME)) != 0)
				.visible(c.getInt(
					c.getColumnIndexOrThrow(DbEntry.COL_VISIBLE)) != 0)
				.commit();
			// Log.i("DoorBLE","door from db: " + d.name());
		}

		return get_all();
	}

	public static Door[] get_all(){
		if (registry != null)
			return registry.toArray(new Door[]{});
		else
			return new Door[]{};
	}

	public static void load_dummy() {
		String pk = "-----BEGIN PUBLIC KEY-----\n"+
			"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEOhGJMRxs1gSoWOQJzypiG9sqN1yA"+
			"fpU3j/v7BxvSDsl05yynF1DbA/+rAkNB8T3yGZJvVCCFQE94rx4E1mIk4Q=="+
			"-----END PUBLIC KEY-----";
		String[] num_word = {"first", "second", "third", "fourth", "fifth",
			"sixth", "seventh", "eigth", "ninth"};
		new Door("prison cell", "door1", pk, "58:BF:25:17:1E:96");
		for(int i = 1 ; i <= 9; i++) {
			Door d = new Door(String.format("%s gate", num_word[i-1]),
				String.format("gate%d",i), pk, "58:BF:25:82:2E:26");
			if (i<9)
				d.visible = false;
		}
	}

	public static void flush_db () {
		dbHelper.getWritableDatabase().delete(DbEntry.TAB_NAME, null, null);
	}

	// Door INSTANCE

	private String name;
	private PublicKey pubkey;
	private String sub;
	private String mac;
	private boolean validate_time = true;
	private boolean visible = true;
	private long id = -1;
	private BluetoothDevice dev = null;
	private Date scan_time = null;
	private int scan_rssi = 0;

	// setters and getters for member variables to be updated through Update
	public String name(){ return name; }
	public PublicKey pubkey(){ return pubkey; }
	public String sub(){ return sub; }
	public String mac(){ return mac; }
	public boolean validate_time(){ return validate_time; }
	public boolean visible(){ return visible; }
	public Pair<String, String>[] buttons() {
		return new Pair[]{ new Pair("Open", "open"),
			new Pair("Lock", "close") };
	}

	public void dev(BluetoothDevice dev, int rssi){
		boolean notify = (this.dev == null);
		this.dev = dev;
		this.scan_rssi = rssi;
		scan_time = new Date();
		if (notify)
			notify_changed();
	}
	public BluetoothDevice dev(){ return dev; }
	public Date scan_time(){ return scan_time; }
	public int scan_rssi(){ return scan_rssi; }

	// create compiled-in door
	static private Door create_door(String name, String sub, String pubkey,
			String mac) throws IllegalArgumentException {
		for (Door i : registry)
			if(name.equals(i.name)) return i;
		return new Door(name, sub, pubkey, mac);
	}

	/**
	 * create new door. registration only happens when there is data applied
	 * through Door.Update.commit()
	 **/
	public Door () {
		super();
	}

	private Door (String p_name, String p_sub, String p_pubkey, String p_mac)
			throws IllegalArgumentException {
		super();
		this.id = -1;
		Update u = new Update();
		u.name(p_name)
			.pubkey(p_pubkey)
			.sub(p_sub)
			.mac(p_mac);
		u.commit();
	}

	public void delete(){
		String[] args = { String.valueOf(id) };
		dbHelper.getWritableDatabase().delete(DbEntry.TAB_NAME, DbEntry._ID + " = ?", args);
		registry.remove(this);
		notify_changed_list();
	}

	private boolean decode_pubkey(String pem) {
		PublicKey r = CryptoHelper.decode_pubkey(pem);
		if(r != null)
			pubkey = r;
		return r != null;
	}

	public String encode_pubkey() {
		return (pubkey != null) ? CryptoHelper.encode_pubkey(pubkey) : null;
	}

	private static ArrayList<DoorListAdapter> observers;
	private static void register_observer (DoorListAdapter o) {
		if(observers == null)
			observers = new ArrayList<DoorListAdapter>();
		observers.add(o);
	}

	private static void notify_changed () {
		for(DoorListAdapter o: observers)
			o.notifyDataSetChanged();
	}

	private static void notify_changed_list () {
		for(DoorListAdapter o: observers)
			o.update_doors();
	}

	private void write () {
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		ContentValues v = new ContentValues();
		v.put(DbEntry.COL_NAME, name());
		v.put(DbEntry.COL_PUBKEY, encode_pubkey());
		v.put(DbEntry.COL_SUB, sub());
		v.put(DbEntry.COL_MAC, mac());
		v.put(DbEntry.COL_VALTIME, validate_time()?1:0);
		v.put(DbEntry.COL_VISIBLE, visible()?1:0);

		if(id == -1) // new dataset
			db.insert(DbEntry.TAB_NAME, null, v);
		else {
			String[] args = { String.valueOf(id) };
			db.update(DbEntry.TAB_NAME, v, DbEntry._ID + " = ?", args);
		}
	}

	public static abstract class DoorListAdapter extends BaseAdapter {
		private Door[] doors;

		public DoorListAdapter () {
			super();
			register_observer(this);
			update_doors();
		}

		public void update_doors () {
			if(registry == null){
				doors = new Door[]{};
				return;
			}
			ArrayList<Door> n = new ArrayList<Door>();
			for(Door d: registry){
				if(filter(d))
					n.add(d);
			}
			doors = n.toArray(new Door[]{});
			notifyDataSetChanged();
		}

		protected boolean filter (Door d) {
			return true;
		}

		@Override
		public Door getItem (int position) {
			return doors[position];
		}

		@Override
		public int getCount () {
			return doors.length;
		}

		@Override
		public long getItemId (int position) { // FIXME?
			return position;
		}
	}

	public class Update {
		// TODO: java.lang.reflect
		private String name;
		private String mac;
		private String pubkey;
		private String sub;
		private boolean visible;
		private boolean validate_time;

		public Update(){
			name = Door.this.name;
			mac = Door.this.mac;
			pubkey = Door.this.encode_pubkey();
			sub = Door.this.sub;
			visible = Door.this.visible;
			validate_time = Door.this.validate_time;
		}

		public Update name(String name) throws IllegalArgumentException {
			if (name == "" || name == null)
				throw new IllegalArgumentException("name must not be empty");
			this.name = name;
			return this;
		}

		public Update mac(String mac) throws IllegalArgumentException {
			String n = mac.toUpperCase(Locale.ROOT); // need an upper case MAC
			if(BluetoothAdapter.checkBluetoothAddress(n))
				this.mac = n;
			else
				throw new IllegalArgumentException("invalid MAC address");
			return this;
		}

		public Update pubkey(String pubkey) throws IllegalArgumentException {
			if(CryptoHelper.decode_pubkey(pubkey) != null)
				this.pubkey = pubkey;
			else
				throw new IllegalArgumentException("invalid public key");
			return this;
		}

		public Update sub(String sub) throws IllegalArgumentException {
			if (sub == "" || sub == null)
				throw new IllegalArgumentException("subject must not be empty");
			this.sub = sub;
			return this;
		}

		public Update visible(boolean visible){
			this.visible = visible;
			return this;
		}

		public Update validate_time(boolean validate_time){
			this.validate_time = validate_time;
			return this;
		}

		public void commit(){
			assert(registry != null);
			boolean update = false;
			boolean update_list = (Door.this.pubkey == null);
			update = (Door.this.name != name) ? true : update;
			Door.this.name = name;

			update = (Door.this.mac != mac) ? true : update;
			Door.this.mac = mac;

			update = (Door.this.encode_pubkey() != pubkey) ? true : update;
			Door.this.decode_pubkey(pubkey);

			update = (Door.this.sub != sub) ? true : update;
			Door.this.sub = sub;

			update = (Door.this.visible != visible) ? true : update;
			Door.this.visible = visible;

			update = (Door.this.validate_time != validate_time) ? true : update;
			Door.this.validate_time = validate_time;

			if(update_list){ // new door added
				registry.add(Door.this);
				Door.this.notify_changed_list();
			}else if(update)
				Door.this.notify_changed();
			Door.this.write();
		}
	}

	public Update start_update(){
		return new Update();
	}

}
