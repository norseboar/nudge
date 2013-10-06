package com.norseboar.mobile.nudge;


import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;


// http handling code credited to http://yuvislm.wordpress.com/2012/09/10/google-places-api-and-json-parsing-in-android/
// http handling code now credited to http://www.androidhive.info/2012/08/android-working-with-google-places-and-maps-tutorial/
/**
 * Requests a list of nearby places that match the target location
 * @author Reed
 *
 */
@SuppressLint("NewApi")
public class PlacesRequest extends AsyncTask<Void, Void, Void>{
	private String API_KEY = "";
	private static final String PLACES_URL = "https://maps.googleapis.com/maps/api/place/textsearch/json?";
	private static final String LOG_TAG = NudgeActivity.LOG_TAG;
	
	// Global instance of HTTP Transport
	private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	
	public static final int PLACES_RADIUS = 500;
	public static final boolean USES_SENSOR = true;
	
	private Context context;
	private NudgeEntry entry;
	private double lat;
	private double lon;


	// Constructor allows PlacesRequest to take in multiple types as parameters
	public PlacesRequest(Context c, NudgeEntry entry, double lat, double lon){
		Log.w(NudgeActivity.LOG_TAG, "Creating PlacesRequest for NudgeEntry " + entry.getName());
		context = c;
		API_KEY = "AIzaSyCSqYN3ZU-wqrh3zSOmrQ_JV1056CqQmp0";
		this.entry = entry;
		this.lat = lat;
		this.lon = lon;
		Log.w(NudgeActivity.LOG_TAG, "PlacesRequest constructor complete");
	}
	
	@Override
	protected Void doInBackground(Void... params) {
		try {
			Log.w(NudgeActivity.LOG_TAG, "About to create HttpRequestFactory");
			HttpRequestFactory factory = createRequestFactory(HTTP_TRANSPORT);
			GenericUrl url = new GenericUrl(PLACES_URL);
			url.put("query", entry.getLoc());
			url.put("key", API_KEY);
			url.put("sensor", USES_SENSOR);
			url.put("location", lat + "," + lon);
			url.put("radius", PLACES_RADIUS);
			
			Log.w(LOG_TAG, "About to buildGetRequest");
			HttpRequest request = factory.buildGetRequest(url);

			Log.w(LOG_TAG, "About to execute request");
			HttpResponse r = request.execute();
			Log.w(LOG_TAG, "About to parse placesList");
			PlacesList list = r.parseAs(PlacesList.class);
			Log.w(LOG_TAG, "About to set entry list");
			entry.setList(list);
			
			Log.w(LOG_TAG, "About to create new intent");
			Intent i = new Intent(NudgeActivity.PLACES_UPDATED_INTENT);
			Log.w(LOG_TAG, "About to put extra in entry");
			i.putExtra(NudgeActivity.PLACES_INTENT_NUDGE_ENTRY, entry);
			Log.w(LOG_TAG, "About to send broadcast from PlacesRequest to NudgeActivity");
			LocalBroadcastManager.getInstance(context).sendBroadcast(i);
			
			return null;
		} catch (Exception e) {
			Log.w(LOG_TAG, "Exception: " + e.getMessage());
			Log.w(LOG_TAG, "Trace: " + e.getStackTrace());
			NudgeActivity.handleError(NudgeActivity.ErrorCode.PLACES_ERROR);
		}
		return null; 
	}
	
	
	public static HttpRequestFactory createRequestFactory(final HttpTransport t){
		return t.createRequestFactory(new HttpRequestInitializer(){
			@Override
			public void initialize(HttpRequest r){
				GoogleHeaders h = new GoogleHeaders();
				h.setApplicationName(NudgeActivity.APP_NAME);
				r.setHeaders(h);
				JsonObjectParser p = new JsonObjectParser(new JacksonFactory());
				r.setParser(p);
				
			}
		});
	}
}
