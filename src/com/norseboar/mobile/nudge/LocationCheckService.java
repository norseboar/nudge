package com.norseboar.mobile.nudge;

import android.annotation.SuppressLint;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.IBinder;
import android.util.Log;

/**
 * Periodically gets the current location, checks if there are relevant locations nearby, and sends appropriate notifications
 * @author norseboar
 *
 */
public class LocationCheckService extends Service {
	
	private static final String DEBUG_TAG = "LocationCheckService";
	public static final String LOCATION_CHANGED_INTENT = "location_changed";

	public static final String LATITUDE = "current_latitude";
	public static final String LONGITUDE = "current_longitude";
	
	private LocationManager locationManager;
	private NudgeLocationListener listener;

	// Controls how often location is checked
	// Since we are only interested in people being nearby some location,
	// distance and time intervals can be rather large
	int checkTimeInterval = //15 * 60000;
			10000;
	int checkDistanceInterval = 10;
	
	@SuppressLint("NewApi")
	@Override
	public void onCreate(){
		Log.d(DEBUG_TAG, "creating service");
		
		// Do location sensing
		String provider = LocationManager.NETWORK_PROVIDER;
		locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		if(!locationManager.isProviderEnabled(provider)){
			//NudgeActivity.handleError(NudgeActivity.ErrorCode.NETWORK_LOCATION_ERROR);
		}
		
		listener = new NudgeLocationListener(this);

		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, checkTimeInterval, checkDistanceInterval, listener);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, checkTimeInterval, checkDistanceInterval, listener);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(DEBUG_TAG, "starting service");
		
		return START_STICKY;
	}

	@Override
	public void onDestroy(){
		locationManager.removeUpdates(listener);
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	
	
}
