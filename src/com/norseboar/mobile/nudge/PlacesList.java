package com.norseboar.mobile.nudge;

import java.io.Serializable;
import java.util.List;

import com.google.api.client.util.Key;

// Code credited to http://www.androidhive.info/2012/08/android-working-with-google-places-and-maps-tutorial/
public class PlacesList implements Serializable {
	
	private static final long serialVersionUID = 7790654656137974480L;

	@Key
	public String status;
	
	@Key
	public List<Place> results;
}
