package com.norseboar.mobile.nudge;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

public class NudgeLocationListener implements LocationListener {

	private static final String LOG_TAG = "NudgeLocationListener";
	private static final int TWO_MINUTES = 1000 * 60 * 2;
	
	private Context context;
	private Location currentBestLocation = null;
	
	public NudgeLocationListener(Context c){
		context = c;
	}
	
	@SuppressLint("NewApi")
	@Override
	public void onLocationChanged(Location loc) {
		Log.d(LOG_TAG, "location changed");
	
		if(isBetterLocation(loc, currentBestLocation)){
			currentBestLocation = loc;
			
			// Send location to NudgeActivity
			Intent i = new Intent(LocationCheckService.LOCATION_CHANGED_INTENT);
			i.putExtra(LocationCheckService.LATITUDE, loc.getLatitude());
			i.putExtra(LocationCheckService.LONGITUDE, loc.getLongitude());
			sendLocationBroadcast(i);
			
			Toast toast = Toast.makeText(context, "sent broadcast", Toast.LENGTH_LONG);
			toast.show();
		}
		if(needsPreciseLocation(currentBestLocation)){
			// Send signal to LocationCheckService to enable GPS location
		}
		else{
			// Send signal to LocationCheckService to disable GPS location
		}
	}

	@Override
	public void onProviderDisabled(String s) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onProviderEnabled(String s) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
		// TODO Auto-generated method stub
		
	}
	
	/** Determines whether one Location reading is better than the current Location fix
	  * @param location  The new Location that you want to evaluate
	  * @param currentBestLocation  The current Location fix, to which you want to compare the new one
	  */
	protected boolean isBetterLocation(Location location, Location currentBestLocation) {
	    if (currentBestLocation == null) {
	        // A new location is always better than no location
	        return true;
	    }
	
	    // Check whether the new location fix is newer or older
	    long timeDelta = location.getTime() - currentBestLocation.getTime();
	    boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
	    boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
	    boolean isNewer = timeDelta > 0;
	
	    // If it's been more than two minutes since the current location, use the new location
	    // because the user has likely moved
	    if (isSignificantlyNewer) {
	        return true;
	    // If the new location is more than two minutes older, it must be worse
	    } else if (isSignificantlyOlder) {
	        return false;
	    }
	
	    // Check whether the new location fix is more or less accurate
	    int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
	    boolean isLessAccurate = accuracyDelta > 0;
	    boolean isMoreAccurate = accuracyDelta < 0;
	    boolean isSignificantlyLessAccurate = accuracyDelta > 200;
	
	    // Check if the old and new location are from the same provider
	    boolean isFromSameProvider = isSameProvider(location.getProvider(),
	            currentBestLocation.getProvider());
	
	    // Determine location quality using a combination of timeliness and accuracy
	    if (isMoreAccurate) {
	        return true;
	    } else if (isNewer && !isLessAccurate) {
	        return true;
	    } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
	        return true;
	    }
	    return false;
	}
	
	/** Checks whether two providers are the same */
	private boolean isSameProvider(String provider1, String provider2) {
	    if (provider1 == null) {
	      return provider2 == null;
	    }
	    return provider1.equals(provider2);
	}
	
	/**
	 * 
	 * @param loc The location being evaluated
	 * @return Whether or not a precise location check should be used
	 */
	private boolean needsPreciseLocation(Location loc){
		return true;
	}
	
	private void sendLocationBroadcast(Intent i){
		i.putExtra("lat", currentBestLocation.getLatitude());
		i.putExtra("lon", currentBestLocation.getLongitude());
		LocalBroadcastManager.getInstance(context).sendBroadcast(i);
	}
}
