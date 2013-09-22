package com.norseboar.mobile.nudge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.util.Iterator;
import java.util.LinkedList;
import org.json.JSONArray;

import com.norseboar.mobile.nudge.NudgeEntry.PushStatus;

import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.app.Activity;
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
	
	public static final String PACKAGE_NAME = "com.norseboar.mobile.nudge";
	public static final String START_LOC_CHECK = "start_loc_check";
	public static final String APP_NAME = "Nudge";
	public static final String LOCATION_ALERT_FILTER = PACKAGE_NAME + "LocationAlertIntent";
	public static final String PLACES_INTENT_NUDGE_ENTRY = "NudgeEntry";
	public static final String PLACES_UPDATED_INTENT = PACKAGE_NAME + "PLACES_UPDATED_INTENT";
	public static final String CHECK_LOCATION_INTENT = PACKAGE_NAME + "CHECK_LOCATION_INTENT";
	
	private static final String LOG_TAG = "NudgeActivity";
	private static final String CREATE_ENTRY_TAG = "create_entry";
	private static final String LIST_PATH = "nudge_entries.json";
	private static final int NOTIFICATION_DISTANCE = 100;
	private static final String PROXIMITY_NOTIFICATION_TITLE = "You can complete a task nearby!";
	private static final String PROXIMITY_NOTIFICATION_TEXT_SUFFIX = " is nearby";

	// Terrible convention, revisit if this works
	static int proximityNotifications = 0;
	
	// Error codes
	public enum ErrorCode {
		FILE_SAVE,
		FILE_LOAD,
		LOCATION_PROVIDER_ERROR,
		PLACES_ERROR
	}
	
	private ListView entryList;
	private LinkedList<NudgeEntry> nudgeEntries;
		
	private double latEstimate;
	private double lonEstimate;

	private double lastCheckedLat;
	private double lastCheckedLon;

	private LocationManager locationManager;
	private NudgeLocationListener listener;

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
					ne.checkLocationInfo(latEstimate, lonEstimate);
					nudgeEntries.add(ne);	
				}
				
				fis.close();
			} catch(Exception e){
				handleError(ErrorCode.FILE_LOAD);
			}
		}
		
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
		
		// Start location services
	    String provider = LocationManager.NETWORK_PROVIDER;
		locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		if(!locationManager.isProviderEnabled(provider)){
			NudgeActivity.handleError(NudgeActivity.ErrorCode.LOCATION_PROVIDER_ERROR);
		}
		
		listener = new NudgeLocationListener(this);
		updateLocation();
		
		Log.d(LOG_TAG, "About to start LocationCheckService");
		Intent service = new Intent(this, LocationCheckService.class);
		this.startService(service);
		
		IntentFilter filter = new IntentFilter();
		filter.addAction(LocationCheckService.LOCATION_CHANGED_INTENT);
		filter.addAction(PLACES_UPDATED_INTENT);
		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, filter);
	}
     
	private void updateLocation() {
		
	}

	/**
	 * Broadcast receiver to handle internal messages
	 */
	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver(){
		@Override
		public void onReceive(Context c, Intent intent){
			Toast toast = Toast.makeText(getApplicationContext(), "received broadcast", Toast.LENGTH_LONG);
			toast.show();
			
			String action = intent.getAction();
			if(action.equals(LocationCheckService.LOCATION_CHANGED_INTENT)){
				// TODO: check lat and lon for errors
				Log.d(LOG_TAG, "receiving location message");
				latEstimate = intent.getDoubleExtra(LocationCheckService.LATITUDE, 0);
				lonEstimate = intent.getDoubleExtra(LocationCheckService.LONGITUDE, 0);
				
				Log.d(LOG_TAG, "location estimates are " + latEstimate + ", " + lonEstimate);
				
				// If location has changed substantially since the last places update, run a new places update
				Location.distanceBetween(latEstimate, lonEstimate, lastCheckedLat, lastCheckedLon, distance);
				if(distance[0] > PlacesRequest.PLACES_RADIUS/2){
					updatePlaces();
				}
				else{
					// CheckPlaces will be carried out after entries are updated if the former condition is true
					checkPlaces();
				}
			}
			else if(action.equals(PLACES_UPDATED_INTENT)){
				
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
					
				checkPlaces();
			}
			else if(action.equals(CHECK_LOCATION_INTENT)){
				// Ensure location providers are enabled
				if(!locationManager.isProviderEnabled(provider)){
					NudgeActivity.handleError(NudgeActivity.ErrorCode.NETWORK_LOCATION_ERROR);
				}
			}
		}
	};
	
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
		nudgeEntries.add(ne);
		refreshEntryList();
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
		LinkedList<NudgeEntry> frontList = new LinkedList<NudgeEntry>();
		LinkedList<NudgeEntry> backList = new LinkedList<NudgeEntry>();

		// Remove all NudgeEntries which must be pushed somewhere
		while(iter.hasNext()){
			ne = iter.next();
			if(ne.getPs() == PushStatus.PUSH_FRONT){
				iter.remove();
				ne.setPs(PushStatus.NONE);
				frontList.push(ne);
			}
			if(ne.getPs() == PushStatus.PUSH_BACK){
				iter.remove();
				ne.setPs(PushStatus.NONE);
				backList.push(ne);
			}
		}
		
		// Push all NudgeEntries to the appropriate position on the list
		for(NudgeEntry n : frontList){
			nudgeEntries.addFirst(n);
		}
		for(NudgeEntry n : backList){
			nudgeEntries.addLast(n);
		}
		
		// After list has been re-ordered, reload adapter it
		NudgeEntryAdapter nea = (NudgeEntryAdapter) entryList.getAdapter();
		nea.notifyDataSetChanged();
		
		saveList();
	}
	
	public void checkPlaces(){
		checkPlaces(false);
	}
	
	/**
	 * Checks all possibly relevant locations, and sends a notification if the user is in range of one.
	 * @param b Whether or not the recentness of the last check should affect anything
	 */
	public void checkPlaces(boolean b){
		if(!nudgeEntries.isEmpty()){
			for(NudgeEntry ne : nudgeEntries){
				if(ne.getList() == null || (!ne.isNotificationReady() && !b)){
					continue;
				}
				for(Place p : ne.getList().results){
					float[] distance = new float[3];
					Location.distanceBetween(latEstimate, lonEstimate, p.geometry.location.lat, p.geometry.location.lng, distance);
					if(distance[0] <= NOTIFICATION_DISTANCE){
						// Send notification
						sendPlacesNotification(p);
						ne.setRecentlyNotified(true);
						ne.setLastNotified(System.currentTimeMillis());
					}
				}
			}
			saveList();
		}
		else{
			Toast toast = Toast.makeText(getApplicationContext(), "no coordinates to measure", Toast.LENGTH_LONG);
			toast.show();
		}
	}
	
	/**
	 * Refreshes and checks all places, regardless of how recently they were checked before
	 * @param v
	 */
	public void refreshPlaces(View v){
		updatePlaces();
		checkPlaces(true);
	}
	
	/**
	 * Refreshes data every time a checkbox is clicked
	 * @param v
	 */
	public void onCheckboxClicked(View v){
		refreshEntryList();
	}
	
	
	
	/**
	 * Sends a toast notification to the user when some error occurs. Notification is dependent upon error code.
	 * @param errorCode
	 */
	public static void handleError(ErrorCode ec){
		Log.d(LOG_TAG, "ERROR");
	}
	
	@SuppressLint({ "NewApi", "DefaultLocale" })
	private void sendPlacesNotification(Place p){
		NotificationCompat.Builder mBuilder =
		        new NotificationCompat.Builder(this)
		        .setSmallIcon(android.R.drawable.ic_dialog_map)
		        .setContentTitle(PROXIMITY_NOTIFICATION_TITLE)
		        .setContentText(p.name + PROXIMITY_NOTIFICATION_TEXT_SUFFIX);

		// Create an intent to send the user to maps, directed at the relevant place
		com.norseboar.mobile.nudge.Place.Location l = p.geometry.location;
		String uri = String.format("geo:%1$f,%2$f?q=%1$f,%2$f", l.lat, l.lng);
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
	 * Updates the places of all entries
	 */
	private void updatePlaces(){
		for(NudgeEntry ne : nudgeEntries){
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
	
	private void saveList(){
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
}
