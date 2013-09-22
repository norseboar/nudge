package com.norseboar.mobile.nudge;

import java.io.Serializable;

import com.google.api.client.util.Key;

//Code credited to http://www.androidhive.info/2012/08/android-working-with-google-places-and-maps-tutorial/

public class Place implements Serializable {
	/**
	 * Object to hold value of a Place from the Google Places API
	 */
	
	private static final long serialVersionUID = -5450647641100627534L;
	
	
	
	@Key
	public String id;
	
	@Key
	public String name;
	
	@Key
	public String reference;
	
	@Key
	public Geometry geometry;
	
	@Override
	public String toString(){
		return name + " - " + id + " - " + reference;
	}
	
	public static class Geometry implements Serializable{
		private static final long serialVersionUID = -7707516441027426539L;
		
		@Key
		public Location location;
	}
	
	public static class Location implements Serializable{
		private static final long serialVersionUID = -9174257329774689635L;

		@Key
		public double lat;
		
		@Key
		public double lng;
	}
}
