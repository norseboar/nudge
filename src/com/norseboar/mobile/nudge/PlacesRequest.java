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
public class PlacesRequest extends AsyncTask<Void, Void, Void>{
	private static final String API_KEY = "AIzaSyDsdoY4Ce_LoK_0dshLcvYZUwjlrw6mTsw";
	private static final String PLACES_URL = "https://maps.googleapis.com/maps/api/place/textsearch/json?";
	private static final String LOG_TAG = "PlacesRequest";
	
	// Global instance of HTTP Transport
	private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	
	public static final int PLACES_RADIUS = 1000;
	public static final boolean USES_SENSOR = true;
	
	private Context context;
	private NudgeEntry entry;
	private double lat;
	private double lon;


	// Constructor allows PlacesRequest to take in multiple types as parameters
	public PlacesRequest(Context c, NudgeEntry entry, double lat, double lon){
		context = c;
		this.entry = entry;
		this.lat = lat;
		this.lon = lon;
	}
	
	@Override
	protected Void doInBackground(Void... params) {
		try {
			HttpRequestFactory factory = createRequestFactory(HTTP_TRANSPORT);
			GenericUrl url = new GenericUrl(PLACES_URL);
			url.put("key", API_KEY);
			url.put("query", entry.getLoc());
			url.put("location", lat + "," + lon);
			url.put("radius", PLACES_RADIUS);
			url.put("sensor", USES_SENSOR);
			HttpRequest request = factory.buildGetRequest(url);

			HttpResponse r = request.execute();
			PlacesList list = r.parseAs(PlacesList.class);
			entry.setList(list);
			
			Intent i = new Intent(NudgeActivity.PLACES_UPDATED_INTENT);
			i.putExtra(NudgeActivity.PLACES_INTENT_NUDGE_ENTRY, entry);
			LocalBroadcastManager.getInstance(context).sendBroadcast(i);
			
			return null;
		} catch (Exception e) {
			if(!e.getMessage().isEmpty()){
				Log.e(LOG_TAG, e.getMessage());
			}
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
