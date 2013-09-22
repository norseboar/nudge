package com.norseboar.mobile.nudge;

import java.io.Serializable;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;

public class NudgeEntry implements Serializable{
	
	// NudgeEntry holds all the data needed for a single task entry in Nudge

	// Used to tell if a list item should be pushed to the front or the back of the list (if it's been checked or un-checked)
	// "completed" cannot be used because once a completed item has been pushed to the end, it should not be pushed again
	public enum PushStatus {
		NONE,
		PUSH_FRONT,
		PUSH_BACK
	}
	
	private static final long serialVersionUID = -725343665455726640L;
	private static final String DEBUG_TAG = "NudgeEntry";
	
	public static final int RECENT_NOTIFICATION_TIME = 1000*60*10;
			
	private Context context;
	private String name;
	private String loc;
	private boolean completed = false;
	private PushStatus ps = PushStatus.NONE;
	private PlacesList list;	
	private long lastNotified = 0;
	private boolean recentlyNotified;

	// Constructors
	public NudgeEntry(Context context, String name){
		this.context = context;
		this.name = name;
		this.loc = null;
	}
	
	public NudgeEntry(Context context, String name, String loc){
		this.context = context;
		this.name = name;
		this.loc = loc;
	}

	// JSON Serialization methods
	public JSONObject serializeToJSON() throws JSONException{
		JSONObject j = new JSONObject();
		j.put("name", name);
		j.put("loc", loc);
		j.put("completed", completed);
		j.put("ps", ps);
		j.put("lastNotified", lastNotified);
		j.put("recentlyNotified", recentlyNotified);
		
		return j;
	}
	
	public static NudgeEntry deserializeFromJSON(JSONObject j, Context c) throws JSONException{
		NudgeEntry ne = new NudgeEntry(c, j.getString("name"), j.getString("loc"));
		ne.completed = j.getBoolean("completed");
		ne.ps = PushStatus.valueOf(j.getString("ps"));
		ne.lastNotified = j.getLong("lastNotified");
		ne.recentlyNotified = j.getBoolean("recentlyNotified");
		
		return ne;
	}
	
	// Getters and setters
	public PushStatus getPs() {
		return ps;
	}

	public void setPs(PushStatus ps) {
		this.ps = ps;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLoc() {
		return loc;
	}

	public void setLoc(String loc) {
		this.loc = loc;
	}

	public boolean isCompleted() {
		return completed;
	}

	public void setCompleted(boolean completed) {
		this.completed = completed;
	}
	
	public PlacesList getList(){
		return list;
	}
	
	public void setList(PlacesList list){
		this.list = list;
	}
	
	public boolean isRecentlyNotified() {
		return recentlyNotified;
	}

	public void setRecentlyNotified(boolean recentlyNotified) {
		this.recentlyNotified = recentlyNotified;
	}
	
	public long getLastNotified() {
		return lastNotified;
	}

	public void setLastNotified(long l) {
		this.lastNotified = l;
	}
	
	public boolean isNotificationReady(){
		return !completed && !recentlyNotified;
	}
	/**
	 * Gets JSON for location info
	 * @param currentLat
	 * @param currentLon
	 */
	public void checkLocationInfo(double currentLat, double currentLon){
		if(!loc.isEmpty()){
			Log.d(DEBUG_TAG, "getting Places JSON");
			new PlacesRequest(context, this, currentLat, currentLon).execute((Void)null);
		}
	}
}
