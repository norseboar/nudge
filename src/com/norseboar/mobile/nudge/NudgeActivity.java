package com.norseboar.mobile.nudge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.util.Iterator;
import java.util.LinkedList;
import org.json.JSONArray;

import com.norseboar.mobile.nudge.NudgeEntry.PushStatus;

import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.DialogFragment;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

public class NudgeActivity extends Activity {
	
	public static final int LOC_CHECK_NOTIFICATION_ID = 1;
	public static final int LOC_CHECK_PERMANENT_ID = 2;
	public static final int PROXIMITY_NOTIFICATION_ID = 1000;
	
	public static final String PACKAGE_NAME = NudgeActivity.class.getPackage().getName();
	public static final String START_LOC_CHECK = "start_loc_check";
	public static final String APP_NAME = "Nudge";
	public static final String LOCATION_ALERT_FILTER = PACKAGE_NAME + "LocationAlertIntent";
	public static final String PLACES_INTENT_NUDGE_ENTRY = "NudgeEntry";
	public static final String PLACES_UPDATED_INTENT = PACKAGE_NAME + "_PLACES_UPDATED_INTENT";
	public static final String CHECK_LOCATION_INTENT = PACKAGE_NAME + "_CHECK_LOCATION_INTENT";
	public static final String LOCATION_UPDATED_INTENT = PACKAGE_NAME + "_LOCATION_UPDATED_INTENT";
	public static final String FORCE_LOCATION_UPDATED_INTENT = PACKAGE_NAME + "FORCE_LOCATION_UPDATED_INTENT";
	
	private static final String LOG_TAG = "NudgeActivity";
	private static final String CREATE_ENTRY_TAG = "create_entry";
	private static final String LIST_PATH = "nudge_entries.json";
	private static final int NOTIFICATION_DISTANCE = 100;
	private static final String PROXIMITY_NOTIFICATION_TEXT_SUFFIX = " is nearby";
	
	private static final String LOCATION_CODE_KEY = "LocationCode";

	// Terrible convention, revisit if this works
	static int proximityNotifications = 0;
	
	// Error codes
	public enum ErrorCode {
		FILE_SAVE,
		FILE_LOAD,
		LOCATION_PROVIDER_ERROR,
		PLACES_ERROR
	}
	
	// Location broadcast codes (so that the app can wait for a good location)
	public enum LocationCode{
		ORDINARY,
		UPDATE_PLACES
	}
	
	private ListView entryList;
	private LinkedList<NudgeEntry> nudgeEntries;
		
	// Default is higher than any possible lat/lon
	private double latEstimate = 400.0;
	private double lonEstimate = 400.0;
	private double speedEstimate = 0.0;
	
	private double lastCheckedLat = 400.0;
	private double lastCheckedLon = 400.0;
	
	private LocationManager locationManager;
	String locationProvider = LocationManager.NETWORK_PROVIDER;
	int locationTimeInterval = //15 * 60000;
			20000;

	// Used to facilitate distance function. Not used for long-term data.
	private float[] distance = new float[3];

	
	public double getLatEstimate() {
		return latEstimate;
	}

	public double getLonEstimate() {
		return lonEstimate;
	}

	SwipeDismissListViewTouchListener touchListener;
		
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_nudge);
		
		// Start location services
		locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		if(!locationManager.isProviderEnabled(locationProvider)){
			NudgeActivity.handleError(NudgeActivity.ErrorCode.LOCATION_PROVIDER_ERROR);
		}
		
		IntentFilter filter = new IntentFilter();
		filter.addAction(PLACES_UPDATED_INTENT);
		filter.addAction(LOCATION_UPDATED_INTENT);
		filter.addAction(FORCE_LOCATION_UPDATED_INTENT);
		filter.addAction(CHECK_LOCATION_INTENT);
		registerReceiver(mMessageReceiver, filter);
		
		// Check if data for nudgeEntries exists
		File file = getBaseContext().getFileStreamPath(LIST_PATH);
	
		nudgeEntries = new LinkedList<NudgeEntry>();
		if(file.exists()){
			try{
				Log.d(LOG_TAG, "Reading file");
				FileInputStream fis = openFileInput(LIST_PATH);
				StringBuffer fileContent = new StringBuffer("");
				
				byte[] buffer = new byte[1024];
				while(fis.read(buffer) != -1){
					fileContent.append(new String(buffer));
				}
				
				JSONArray ja = new JSONArray(fileContent.toString());
				for(int i = 0; i < ja.length(); i++){
					NudgeEntry ne = NudgeEntry.deserializeFromJSON(ja.getJSONObject(i), this);
					nudgeEntries.add(ne);	
				}
				
				fis.close();
			} catch(Exception e){
				handleError(ErrorCode.FILE_LOAD);
			}
		}
		
		updateLocation(true);

		entryList = (ListView) findViewById(R.id.entry_list);
		NudgeEntryAdapter adapter = new NudgeEntryAdapter(this, nudgeEntries);
		entryList.setAdapter(adapter);
		
		// Display list information
		refreshEntryList();
	
		touchListener =
	            new SwipeDismissListViewTouchListener(entryList, adapter);
	
		entryList.setOnTouchListener(touchListener);
	    // Setting this scroll listener is required to ensure that during ListView scrolling,
	    // we don't look for swipes.
	    entryList.setOnScrollListener(touchListener.makeScrollListener());
		
	     // TODO: Check that Google Places APK is available
	}

	
	private void updateLocation(){
		updateLocation(false);
	}
	
	/**
	 * If the user's location has changed, update it and schedule another check for an appropriate time.
	 * @param lc Location code to be send with the intent
	 */
	private void updateLocation(boolean force) {
		Log.d(LOG_TAG, "Location about to update");
		// Ensure location providers are enabled
		if(!locationManager.isProviderEnabled(locationProvider)){
			NudgeActivity.handleError(NudgeActivity.ErrorCode.LOCATION_PROVIDER_ERROR);
			return;
		}

		Intent i;
		if(force){
			i = new Intent(FORCE_LOCATION_UPDATED_INTENT);
		}
		else{
			i = new Intent(LOCATION_UPDATED_INTENT);
		}
		PendingIntent pi = PendingIntent.getBroadcast(getApplicationContext(), 0, i,
				PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);
		Criteria c = new Criteria();
		c.setAccuracy(Criteria.ACCURACY_FINE);
		c.setSpeedAccuracy(Criteria.ACCURACY_LOW);
		c.setSpeedRequired(true);
		Log.d(LOG_TAG, "requesting");

		locationManager.requestSingleUpdate(c, pi);
	}

	/**
	 * Schedule a location update
	 */
	private void scheduleLocationUpdate() {
		Intent i = new Intent(CHECK_LOCATION_INTENT);
		PendingIntent pi = PendingIntent.getBroadcast(getApplicationContext(), 0, i,
				PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);
		AlarmManager am = (AlarmManager) (this.getSystemService(Context.ALARM_SERVICE));
		am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + locationTimeInterval, pi);
		
		Log.d(LOG_TAG, "Alarm set");
		// TODO: destroy alarm manager
	}

	/**
	 * Broadcast receiver to handle internal messages
	 */
	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver(){
		@Override
		public void onReceive(Context c, Intent intent){
			Log.d(LOG_TAG, "Received broadcast");
			Toast toast = Toast.makeText(getApplicationContext(), "received broadcast", Toast.LENGTH_LONG);
			toast.show();
			
			String action = intent.getAction();
			if(action.equals(LOCATION_UPDATED_INTENT)){
				processLocationUpdate(intent, false);
			}
			else if(action.equals(FORCE_LOCATION_UPDATED_INTENT)){
				processLocationUpdate(intent, true);
			}
			else if(action.equals(PLACES_UPDATED_INTENT)){
				Log.d(LOG_TAG, "received places updated intent");
				// Since google places returns locations out of range, prune those out
				NudgeEntry ne = (NudgeEntry) intent.getSerializableExtra(PLACES_INTENT_NUDGE_ENTRY);
				if(ne.getList() != null){
					for(Iterator<Place> it = ne.getList().results.iterator(); it.hasNext();){
						Place p = it.next();
						Location.distanceBetween(latEstimate, lonEstimate, p.geometry.location.lat, p.geometry.location.lng, distance);
						if(distance[0] > PlacesRequest.PLACES_RADIUS){
							it.remove();
						}
					}
				}
				checkPlaces(ne);
			}
			else if(action.equals(CHECK_LOCATION_INTENT)){
				Log.d(LOG_TAG, "Check location intent received");
				updateLocation();
			}
		}
	};
	
	public void processLocationUpdate(Intent intent, boolean forceUpdate){
		Log.d(LOG_TAG, "Location updated intent received");
		
//		// TODO: check lat and lon for errors

		if(intent.getExtras().containsKey(LocationManager.KEY_LOCATION_CHANGED)){
			Location l = (Location) intent.getExtras().get(LocationManager.KEY_LOCATION_CHANGED);
			latEstimate = l.getLatitude();
			lonEstimate = l.getLongitude();
			speedEstimate = l.getSpeed();
			
			Log.d(LOG_TAG, "location estimates are " + latEstimate + ", " + lonEstimate + ", speed is " +
					speedEstimate);
			
			// If location has changed substantially since the last places update, run a new places update
			Location.distanceBetween(latEstimate, lonEstimate, lastCheckedLat, lastCheckedLon, distance);
			if(forceUpdate || distance[0] > PlacesRequest.PLACES_RADIUS/2){
				Log.d(LOG_TAG, "Updating places in location broadcast area");
				updatePlaces();
			}
			else{
				// CheckPlaces will be carried out after entries are updated if the former condition is true
				checkPlaces();
			}
		}
		else{
			handleError(ErrorCode.LOCATION_PROVIDER_ERROR);
		}
		scheduleLocationUpdate();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.nudge, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		switch(item.getItemId()){
		case R.id.action_create:
			createEntry();
			break;
		}
		return true;
	}
	
	/**
	 * Creates and adds an entry to nudgeEntries, to be displayed in the main activity.
	 * Launches a dialog to create the entry.
	 */
	private void createEntry(){
		DialogFragment df = new CreateEntryDialogFragment();
		df.show(getFragmentManager(), CREATE_ENTRY_TAG);
	}
	
	/**
	 * Adds a NudgeEntry to the list to be displayed by Nudge. Also refreshes display and saves list to storage.
	 * @param ne
	 */
	public void addEntry(NudgeEntry ne){
		Log.d(LOG_TAG, "Entry added");
		nudgeEntries.add(ne);
		refreshEntryList();
		updateLocation(true);
	}

	/**
	 * Updates displayed todo list with current information, and writes the current list to storage.
	 * This method is terribly inefficient for large lists. This method is called frequently, and presumes there are a small number of elements (perhaps 100)
	 * that can be managed quickly.
	 */
	public void refreshEntryList(){
		// If nudgeEntries isn't empty, hide the hint message		
		if(nudgeEntries.size() > 0){
			findViewById(R.id.empty_notice).setVisibility(View.GONE);
		}
		
		// Check if any entries must be pushed and push them accordingly
		Iterator<NudgeEntry> iter = nudgeEntries.iterator();
		NudgeEntry ne;
		
		// Remove all NudgeEntries which must be pushed somewhere
		while(iter.hasNext()){
			ne = iter.next();
			if(ne.isCompleted()){
				iter.remove();
			}
		}
		
		// After list has been re-ordered, reload adapter it
		NudgeEntryAdapter nea = (NudgeEntryAdapter) entryList.getAdapter();
		nea.notifyDataSetChanged();
		
		saveList();
	}
	
	public void checkPlaces(){
		checkPlaces(true);
	}
	
	/**
	 * Checks all possibly relevant locations, and sends a notification if the user is in range of one.
	 * @param b Whether or not the recentness of the last check should affect anything
	 */
	public void checkPlaces(boolean b){
		Log.d(LOG_TAG, "Checking places");
		if(!nudgeEntries.isEmpty()){
			for(NudgeEntry ne : nudgeEntries){
				checkPlaces(ne, b);
			}
			saveList();
		}
		else{
			Toast toast = Toast.makeText(getApplicationContext(), "no coordinates to measure", Toast.LENGTH_LONG);
			toast.show();
		}
	}
	
	public void checkPlaces(NudgeEntry ne){
		checkPlaces(ne, true);
	}
	
	public void checkPlaces(NudgeEntry ne, boolean b){
		Log.d(LOG_TAG, "Checking places for " + ne.getName());
		if(ne.getList() == null || (!ne.isNotificationReady() && b)){
			return;
		}
		for(Place p : ne.getList().results){
			Log.d(LOG_TAG, "Checking " + p.name);
			float[] distance = new float[3];
			Location.distanceBetween(latEstimate, lonEstimate, p.geometry.location.lat, p.geometry.location.lng, distance);
			if(distance[0] <= NOTIFICATION_DISTANCE){
				// Send notification
				Log.d(LOG_TAG, "Sending notification");
				sendPlacesNotification(ne, p);
				ne.setRecentlyNotified(true);
				ne.setLastNotified(System.currentTimeMillis());
			}
			else{
				Log.d(LOG_TAG, "No notification necessary");
			}
		}
	}

	/**
	 * Updates the places of all entries
	 */
	private void updatePlaces(){
		for(NudgeEntry ne : nudgeEntries){
			Log.v(LOG_TAG, "About to check location info for " + ne.getName());
			ne.checkLocationInfo(latEstimate, lonEstimate);
			lastCheckedLat = latEstimate;
			lastCheckedLon = lonEstimate;
			
			// If the user's location has changed substantially, and it has been a long time since 
			// the user has been notified, allow them to be notified again
			if(System.currentTimeMillis() > ne.getLastNotified() + NudgeEntry.RECENT_NOTIFICATION_TIME){
				ne.setRecentlyNotified(false);
			}
		}
		
		saveList();
	}
	
	public void updatePlacesButton(View v){
		updatePlaces();
	}
	
	/**
	 * Sends a toast notification to the user when some error occurs. Notification is dependent upon error code.
	 * @param errorCode
	 */
	public static void handleError(ErrorCode ec){
		Log.e(LOG_TAG, ec.name());
	}
	
	@SuppressLint({ "NewApi", "DefaultLocale" })
	private void sendPlacesNotification(NudgeEntry ne, Place p){
		NotificationCompat.Builder mBuilder =
		        new NotificationCompat.Builder(this)
		        .setSmallIcon(android.R.drawable.ic_dialog_map)
		        .setContentTitle(ne.getName())
		        .setContentText(p.name + PROXIMITY_NOTIFICATION_TEXT_SUFFIX);

		// Create an intent to send the user to maps, directed at the relevant place
		com.norseboar.mobile.nudge.Place.Location l = p.geometry.location;
		String uri = String.format("geo:%1$f,%2$f?q=%1$f,%2$f?z=23", l.lat, l.lng);
		Intent resultIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));

		// The stack builder object will contain an artificial back stack for the
		// started Activity.
		// This ensures that navigating backward from the Activity leads out of
		// your application to the Home screen.
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
		// Adds the back stack for the Intent (but not the Intent itself)
		stackBuilder.addParentStack(NudgeActivity.class);
		// Adds the Intent that starts the Activity to the top of the stack
		stackBuilder.addNextIntent(resultIntent);
		PendingIntent resultPendingIntent =
		        stackBuilder.getPendingIntent(
		            0,
		            PendingIntent.FLAG_UPDATE_CURRENT
		        );
		mBuilder.setContentIntent(resultPendingIntent);
		NotificationManager mNotificationManager =
		    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Notification n = mBuilder.build();
		n.flags |= Notification.FLAG_AUTO_CANCEL;
		n.defaults = Notification.DEFAULT_ALL;
		
		mNotificationManager.notify(PROXIMITY_NOTIFICATION_ID + proximityNotifications++, n);
	}
	
	/**
	 * Save the entries list to a JSON file
	 */
	public void saveList(){
		FileOutputStream fos;
		try {
			Log.d(LOG_TAG, "Writing file");
			fos = openFileOutput(LIST_PATH, Context.MODE_PRIVATE);
			JSONArray ja = new JSONArray();
			for(NudgeEntry ne : nudgeEntries){
				ja.put(ne.serializeToJSON());
			}
			fos.write(ja.toString().getBytes());
			fos.close();
		} catch (Exception e) {
			handleError(ErrorCode.FILE_SAVE);
			e.printStackTrace();
		}
	}
	
	@Override
	public void onDestroy(){
		unregisterReceiver(mMessageReceiver);
		super.onDestroy();
	}
}
