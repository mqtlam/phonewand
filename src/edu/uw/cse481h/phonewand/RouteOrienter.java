/**
 * @author Michael Q. Lam (mqtlam@cs.washington.edu)
 * @author Levi Lindsey (levisl@cs.washington.edu)
 * @author Chris Raastad (craastad@cs.washington.edu)
 * 
 * Designed to meet the requirements of the Winter 2011 UW course, 
 * CSE 481H: Accessibility Capstone
 * 
 * RouteOrienter allows the user to query in which direction he/she should be 
 * going in order to reach either a given destination or an intermediate 
 * intersection on a route to the given destination.  The user can also 
 * provide additional input to declare when he/she is at an intersection.  
 * RouteOrienter uses vibrations to tell the user both direction and distance.
 */

package edu.uw.cse481h.phonewand;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.DataFormatException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

public 	class 		RouteOrienter 
	 	extends 	MapActivity 
	 	implements 	LocationListener, SensorEventListener, OnInitListener {

	private boolean D = false;
	
	// Useful for debugging.  When true, the entire route and a small amount 
	// of padding will be displayed on the screen.  When false, only a few 
	// blocks to either side of the current location will be displayed.
	private static final boolean SHOW_ENTIRE_ROUTE = false;
	
	// For debugging with the Log messages.
	private static final String TAG = "RouteOrienter";
	
	// For power saving
	private boolean mOnPause;
	
    // For the sensor services.
	private SensorManager mSensorManager;
    // For the location services.
	private LocationManager mLocationManager;
	
	// For determining relative goodness of current location reading.
	private Location mCurrentBestLocation;
	
	// Start location.
	private GeoPoint mCurrentLocation;
	// End location.
	private GeoPoint mDestination;
	// The array of Step objects which constitutes the route.
	private RouteStep[] mRouteSteps;
	// Current Route Step
	private int mCurRouteStep = -1;
	// Current Geostep, next geostem is CurStep + 1
	private int   mCurGeoStep = -1;
	// Are you at the destination?
	private boolean atDestination = false;
	
	// Determines whether a route is being displayed at any given time.
	private boolean mRouteExists;
	// Determines whether we are currently trying to get a route from Google Maps.
	private boolean mGettingRoute;
	
	// Shows the user when the app is performing a time-intensive task; e.g. 
	// getting a fix on a GPS location or getting a route from Google Maps.
	private ProgressDialog mProgressDialog;
	
	// For determining a significant time gap between locations.
	private static final int FORTY_FIVE_SECONDS = 45000;	// milliseconds
	
	// Constants used in determining how often to update the user's location.
	private static final int MIN_UPDATE_TIME = 60000;		// in milliseconds (1 minute)
	private static final int MIN_UPDATE_DISTANCE = 100;		// in meters (100 meters)
	
	// Route color and opacity
	private static final int ROUTE_COLOR = 0xFFAA6600;		// Brown
	private static final int ROUTE_ALPHA_VALUE = 120;
	
	// Constants used for identifying assorted Dialogs.
	public static final int GPS_DIALOG =						0;
	public static final int INTERNET_DIALOG =					1;
	
	// The MapView on which the route is displayed.
	private MapView mMapView;
	// For panning and zooming the MapView.
	private MapController mMapController;
	// For displaying important locations on the MapView.
	private Overlay mCurrentLocationOverlay;
	private Overlay mDestinationOverlay;
	// For specifying padding between Overlays' and MapView's boundaries when 
	// SHOW_ENTIRE_ROUTE is set to true.
	private static final double VERTICAL_PADDING_RATIO = 1.0 / 18;
	private static final double HORIZONTAL_PADDING_RATIO = 1.0 / 26;
	// For specifying the width of a city block so only a few blocks on either 
	// side of the current location can be displayed when SHOW_ENTIRE_ROUTE is 
	// set to false.
	private static final double LAT_DEG_PER_CITY_BLOCK_RATIO = 1.0 / 1200;
	// For determining whether to use the metric or imperial systems for distances.
	private boolean mUseMetricSystem;
	// For converting between meters and feet.
	private static final double METER_PER_FOOT_RATIO = 0.3048;
	
	// Provides access to the project's resources (e.g. icons).
	private Resources mResources;
	
	// Handle for compass directions.
	private Sensor mSensor;
	
	// Handle for vibration system.
	//private Vibrator mVibrator;
	
	// Gesture detector, Swipes and Double Taps
	private GestureDetector mGestureDetector;
	// Gesture controller for doing something with these input.
	
	// -----Vibration System Constants and Fields-----
	// handle for vibration system
	private Vibrator mVibrator;
	// vibration array for swipes
	public static final long[] SWIPE_VIBES = {0,100};
	
	// Vibrator control array;
	private static final long[] VIBES = { 0, 50, 100 };
	
	//Allowed vibrational direction to target location
	private static final int ORIENTING_EPS = 5;
	//Number of meters within waypoint geopoint to move onto next point.
	private static final int DISTANCE_EPS = 15;
	
	// Hard coded directions 
	private static final int NORTH = 0;
	
    // Current compass heading in degrees.
	private ModulusInteger mHeading = new ModulusInteger(0,360);
	
	// Desired compass heading for user.
	private ModulusInteger mNextHeading = new ModulusInteger(NORTH,360);
	
	// Controls when to give vibrational guidance.
	private boolean mOrienter = false;
	// Controls whether the beeep is active or not
	private boolean mBeeper = false;
	
	// variable needed for swipes...
	private int mMinScaledVelocity; 
	
	// Contains the textual directions for the current route.
	public static String[] mDirectionsText;
	
    /**
     * Minimum number of degrees the compass reading
     * has to change (plus or minus the current heading)
     * to update the current heading
     */
    private static final int MIN_HEADING_CHANGE = 1;
    
	// -----TextToSpeech Constants and Fields-----
	public static final String TEXT_ENTERED_EXTRA 	= 	"text_entered";
	public static final float  SLOW_SPEECH_RATE		=	0.5f;
	public static final float  MEDIUM_SPEECH_RATE 	=	1f;
	public static final float  FAST_SPEECH_RATE 	=	1.75f;
	
	private float mSpeechRate;
	private boolean mTTSReady;
	private TextToSpeech mTts;
    
	
	/** Called when the system creates this Activity. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
        if (D) Log.v(TAG, "+++ ON CREATE  +++");
        
		super.onCreate(savedInstanceState);
		
		// Setup the screen's title and layout.
		setTitle(R.string.app_name);
		setContentView(R.layout.route_orienter);
		
		// Find the route.
		new GetDirections().execute(mCurrentLocation, mDestination);
		
		// Instantiate access to project resources.
		mResources = this.getResources();
		
		// Set whether to use the metric system.
		// TODO: should this be settable by the user eventually?
		mUseMetricSystem = false;
		
		// Get the destination.
		Intent intent = getIntent();
		mDestination = new GeoPoint(
				intent.getIntExtra(PhoneWandActivity.LATITUDE_EXTRA,  PhoneWandActivity.MICRODEGREE_UPPER_BOUND), 
				intent.getIntExtra(PhoneWandActivity.LONGITUDE_EXTRA, PhoneWandActivity.MICRODEGREE_UPPER_BOUND));
		
		if (D) Log.i(TAG, "Destination: lat=" + mDestination.getLatitudeE6()  + 
				              ", lon=" + mDestination.getLongitudeE6() );
		
		// Close the Activity if the destination was passed incorrectly.
		if(mDestination.getLatitudeE6()  >= PhoneWandActivity.MICRODEGREE_UPPER_BOUND || 
				mDestination.getLongitudeE6() >= PhoneWandActivity.MICRODEGREE_UPPER_BOUND) {
			if (D) Log.e(TAG, getString(R.string.get_extra_fail));
			finish();
		}
		
		mOnPause = false;
		
		mRouteExists = false;
		mGettingRoute = false;
		
		mMapView = (MapView) findViewById(R.id.map);
		mMapController = mMapView.getController();
		
		mTts = new TextToSpeech(this, (OnInitListener) this);
		
		// Set gesture detector that catches double taps and swipes.
		mGestureDetector = new GestureDetector(new GestureController());
		mMinScaledVelocity = ViewConfiguration.get(this).getScaledMinimumFlingVelocity();
		
		// Handle to system Location Manager
		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		// Handle to system Sensor manager.
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		
		// Set up compass sensor system.
		mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        
        // Set up vibration system.
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
	
        // Initialize destination Overlay.
		mDestinationOverlay = new StartAndEndOverlay(mDestination, true);
		// Initialize the current location Overlay.
		findCurrentLocation();
	}
	/** Called ttsStarts up **/
	public void welcomeSpeech(){
		
	}
	
	/** Called when the system starts this Activity. */
	@Override
	public void onStart() {
        if (D) Log.v(TAG, "  + ON START   +");
        
		super.onStart();
		mOnPause = false;
		mOrienter = false;
	}
	
	/** Called when the system resumes this Activity. */
	@Override
	protected void onResume() {
		if (D) Log.v(TAG, "+++ ON PAUSE   +++");
		
		super.onResume();
		//TODO
        
		mOnPause = false;
		
		// Register the listener with the LocationManager to receive location 
		// updates.
		mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 
				MIN_UPDATE_TIME, MIN_UPDATE_DISTANCE, this);
		// Register the listener with the LocationManager to receive network 
		// location updates.
		mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 
				MIN_UPDATE_TIME, MIN_UPDATE_DISTANCE, this);
	}
	
	/** Called when the system pauses this Activity. */
	@Override
	public synchronized void onPause() {
		if (D) Log.v(TAG, "  - ON PAUSE   -");
		
		super.onPause();
		
    	mVibrator.cancel();
    	mOnPause = true;
    	mOrienter = false;
    	//mSensorManager.unregisterListener(this); //not sure if needed...
		mLocationManager.removeUpdates(this);
	}
	
	/** Called when the system stops this Activity. */
	@Override
	public void onStop() {
        if (D) Log.v(TAG, "--- ON STOP---");
        
        mOrienter = false;
		super.onStop();
	}
		
	/** Called when the system removes this Activity. */
	@Override
	public void onDestroy() {
        if (D) Log.v(TAG, "--- ON DESTROY ---");
        
		super.onDestroy();

    	mSensorManager.unregisterListener(this); //necessary?
		mProgressDialog.dismiss();
	}
	
	/**
	 * Override the default instructions for the phone's back, menu, and 
	 * search hard-key buttons.  The home button cannot be overridden.
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch(keyCode) {
		case KeyEvent.KEYCODE_BACK:
			return true;	// Do nothing (disable the button).
		case KeyEvent.KEYCODE_HOME:
			return true;	// Do nothing (disable the button).
		case KeyEvent.KEYCODE_SEARCH:
			return true;	// Do nothing (disable the button).
		default:			// Use the button's default functionality.
			return super.onKeyDown(keyCode, event);
		}
	}
	
	/**
	 * Called when the user is checking his/her current location/direction.
	 * TODO
	 */
	private void checkRoute() {
        if (D) Log.v(TAG, getString(R.string.check_route));
        
    	findCurrentLocation();
    	mOrienter = false;
		
		// Find the route.
		new GetDirections().execute(mCurrentLocation,mDestination);
	}
	
	public void openCheckRouteScreen(){
		//TODO: Insert code for the checking route screen.
		checkRoute();
	}
	
	/**
	 * Open the DirectionsDisplay screen with the list of textual directions 
	 * for the current route.
	 */
	private void openDirectionsDisplayScreen() {
		startActivityForResult(new Intent(this, DirectionsDisplay.class), -1);
	}
	
	/**
	 * Called when the user is declaring that he/she is currently at an 
	 * intersection.
	 * TODO: are we still implementing this?
	 */
	private void declareIntersection() {
		if (D) Log.v(TAG,"declareIntersection");
		//TODO: adjust current location and route information accordingly
        //What does this function do?!?!
	}
	
	/**
	 * Uses the LocationManager to get a GeoPoint representing the latitude 
	 * and longitude of the user's current location, and then calls 
	 * updateCurrentLocation.
	 */
	private void findCurrentLocation() {
		//TODO: How often is this lastKnownLocation updated?  
		//		Is this accurate enough?
		//		Should I change this to use the LocationListener 
		//		thing manually (which will presumably leave the GPS on--
		//		consuming battery, but offering more accurate results)?

		// Specifies how to determine the user's location.
		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		criteria.setAltitudeRequired(false);
		Location lastKnownLocation;
		
		// Show progress dialog.
		showDialog(GPS_DIALOG);
		
		lastKnownLocation = mLocationManager.getLastKnownLocation(
				mLocationManager.getBestProvider(criteria, true));
		
		// Hide progress dialog.
		dismissDialog(GPS_DIALOG);
		
		if(lastKnownLocation != null) {
			updateCurrentLocation(lastKnownLocation);
		}
	}
	
	/**
	 * Updates the mCurrentLocation and mCurrentLocationOverlay fields to the 
	 * given location, redraws all of the MapViews Overlays, then zooms and 
	 * pans the map to fit optimally with the new current location.
	 * @param location
	 */
	private void updateCurrentLocation(Location location) {
        // Don't do anything until we've connected to the RouteService.
		mCurrentBestLocation = isBetterLocation(location) ? location : mCurrentBestLocation;
		
		// Parse the Location data into a GeoPoint object.
		int lat = (int)(location.getLatitude()*1E6);
		int lon = (int)(location.getLongitude()*1E6);
		mCurrentLocation = new GeoPoint(lat, lon);
		mCurrentLocationOverlay = 
			new StartAndEndOverlay(mCurrentLocation, false);
		
		if (D) Log.d(TAG,"updateCurrentLocation: lat="+lat+", lon="+lon);
		
		// Don't try to display anything, if we haven't retrieved a route from 
		// Google Maps yet.
		if(mRouteExists) {
			// Update the MapView contents accordingly.
			drawPath();
			
		// If we don't have a route and aren't currently waiting for Google 
		// Maps to return one, then get one.
		} else if(!mGettingRoute) {
			new GetDirections().execute(mCurrentLocation, mDestination);
		}
		//TODO: notify user via vibrations?
	}
	
	/** Determines whether one Location reading is better than the current Location reading.
	  * @param location The new Location that you want to evaluate
	  * @param currentBestLocation The current Location fix, to which you want to compare the new one
	  */
	private boolean isBetterLocation(Location testLocation) {
		if (mCurrentBestLocation == null) {
			// A new location is always better than no location.
			return true;
		}
		
		// Check whether the new location fix is newer or older.
		long timeDelta = testLocation.getTime() - mCurrentBestLocation.getTime();
		boolean isSignificantlyNewer = timeDelta > FORTY_FIVE_SECONDS;
		boolean isSignificantlyOlder = timeDelta < -FORTY_FIVE_SECONDS;
		boolean isNewer = timeDelta > 0;
		
		// If it's been more than two minutes since the current location, use the new location
		// because the user has likely moved.
		if (isSignificantlyNewer) {
			return true;
		// If the new location is more than two minutes older, it must be worse.
		} else if (isSignificantlyOlder) {
			return false;
		}
		
		// Check whether the new location fix is more or less accurate.
		int accuracyDelta = 
			(int) (testLocation.getAccuracy() - mCurrentBestLocation.getAccuracy());
		boolean isLessAccurate = accuracyDelta > 0;
		boolean isMoreAccurate = accuracyDelta < 0;
		boolean isSignificantlyLessAccurate = accuracyDelta > 200;
		
		// Check if the old and new location are from the same provider.
		boolean isFromSameProvider = isSameProvider(testLocation.getProvider(),
				mCurrentBestLocation.getProvider());
		
		// Determine location quality using a combination of timeliness and accuracy.
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
	 * Zooms and Pans the MapView to optimally fit the current location, 
	 * destination, and route Overlays.
	 */
	private void fitToOverlays() {
		int latSpan, lonSpan, latCenter, lonCenter;
		
		// Show the whole route on the screen.
		if(SHOW_ENTIRE_ROUTE) {
			// Find the latitudinal and longitudinal distances between the current 
			// location and the destination.
			latSpan = (int)(Math.abs(mCurrentLocation.getLatitudeE6() - 
							mDestination.getLatitudeE6()) * 
							(1 + (HORIZONTAL_PADDING_RATIO * 2)));
			lonSpan = (int)(Math.abs(mCurrentLocation.getLongitudeE6() - 
							mDestination.getLongitudeE6()) * 
							(1 + (VERTICAL_PADDING_RATIO * 2)));
			
			// Find the center point between the current location and the 
			// destination.
			latCenter = (mCurrentLocation.getLatitudeE6() + 
							mDestination.getLatitudeE6()) / 2;
			lonCenter = (mCurrentLocation.getLongitudeE6() + 
							mDestination.getLongitudeE6()) / 2;
			
		// Zoom in on the user's current location.
		} else {
			// Display a span of about 6 blocks on either side of the current location.
			latSpan = (int)(LAT_DEG_PER_CITY_BLOCK_RATIO * 12 * 1E6);
			lonSpan = (int)(LAT_DEG_PER_CITY_BLOCK_RATIO * 12 * 1E6);
			
			// Set the center point at the current location.
			latCenter = mCurrentLocation.getLatitudeE6();
			lonCenter = mCurrentLocation.getLongitudeE6();
		}
		
		// Pan the MapView.
		mMapController.animateTo(new GeoPoint(latCenter, lonCenter));
		// Zoom the MapView.
		mMapController.zoomToSpan(latSpan, lonSpan);
		
		if (D) Log.d(TAG, "fitToOverlays:" +
				"\n\tZoomed to: latSpan=" + latSpan+", lngSpan=" + lonSpan + 
				"\n\tPanned to: latCenter=" + latCenter + ", lngCenter=" + lonCenter);
	}
	
	/**
	 * Adds the Overlays, which display the GeoPoints for our route, to the 
	 * MapView.
	 * @param geoPoints The List of GeoPoints which are part of our route.
	 */
	private void drawPath() {
		if (D) Log.v(TAG,"drawPath");
		
		if(mRouteExists) {
			fitToOverlays();
			
			// Get the list of Overlays and empty it.
			List<Overlay> overlays = mMapView.getOverlays();
			overlays.clear();
			
			// Draw the route Overlays.
			for(int i = 0; i < mRouteSteps.length; i++) {
				List<GeoPoint> polyline = mRouteSteps[i].getPolyline();
				int length = polyline.size();
				for(int j = 1; j < length; j++) {
					overlays.add(new RouteOverlay(polyline.get(j - 1), 
							polyline.get(j)));
				}
			}
			
			// Draw the start and end markers.
			overlays.add(mCurrentLocationOverlay);
			overlays.add(mDestinationOverlay);
		}
	}
	
	/**
	 * Inner (non-static) class which implements the Overlay abstract class 
	 * and is used to represent either the current location or the destination.
	 */
	public class StartAndEndOverlay extends Overlay {
		private GeoPoint geoPoint;
		private boolean isDestination;
		
		public StartAndEndOverlay(GeoPoint geoPoint, 
				boolean isDestination) {
			
			this.geoPoint = geoPoint;
			this.isDestination = isDestination;
		}
		
		/**
		 * Returns true if this Overlay represents the destination.  
		 * Returns false if it represents the current location.
		 */
		public boolean isDestination() {return isDestination;}
		
		// "Draw the Overlay over the map."
		@Override
		public void draw(Canvas canvas, MapView mapView, boolean shadow) {
			if(shadow) {
				int r = isDestination ? R.drawable.destination : 
					R.drawable.pointer;
//				Drawable icon = mResources.getDrawable(r);
//				icon.setBounds(0, 0, icon.getIntrinsicWidth(), 
//						icon.getIntrinsicHeight());
				Projection projection = mapView.getProjection();
				Point point = new Point();
				projection.toPixels(geoPoint, point);
//				drawAt(canvas, icon, point.x, point.y, shadow);

				Bitmap bm1 = BitmapFactory.decodeResource(mResources, r);
//				if (r == R.drawable.pointer)            
//		                canvas.rotate(-(float)mHeading);
				
				Matrix matrix = new Matrix();
				matrix.postRotate((float)mHeading.getValue());
				Bitmap bm2 = Bitmap.createBitmap(bm1, 0, 0, bm1.getWidth(), bm1.getHeight(), matrix, true);
				
				canvas.drawBitmap(bm2, point.x - (bm2.getWidth() / 2), 
						point.y - (bm2.getHeight() / 2), null);
			}
			super.draw(canvas, mapView, shadow);
		}
	}
	
	/**
	 * Inner (non-static) class used to represent two nodes and the line 
	 * between them in the path from the user's current location to the 
	 * destination. Each RouteOverlay includes two GeoPoints.
	 */
	public class RouteOverlay extends Overlay {
		private GeoPoint geoPoint1;
		private GeoPoint geoPoint2;
		
		public RouteOverlay(GeoPoint geoPoint1, GeoPoint geoPoint2) {
			this.geoPoint1 = geoPoint1;
			this.geoPoint2 = geoPoint2;
		}
		
		// "Draw the Overlay over the map." --Anonymous
		@Override
		public void draw(Canvas canvas, MapView mapView, boolean shadow) {
			if(shadow) {
				Projection projection = mapView.getProjection();
				Paint paint = new Paint();
				Point point = new Point();
				projection.toPixels(geoPoint1, point);
				paint.setColor(ROUTE_COLOR);
				Point point2 = new Point();
				projection.toPixels(geoPoint2, point2);
				paint.setStrokeWidth(5);
				paint.setAlpha(ROUTE_ALPHA_VALUE);
				canvas.drawLine(point.x, point.y, point2.x, point2.y, paint);
			}
			super.draw(canvas, mapView, shadow);
		}
	}
	
	/**
	 * Inner (non-static) class which operates on a separate thread in order 
	 * to send a GET request to the Google Maps servers, parse the encoded 
	 * result into a List of GeoPoints, and then return the result to the main 
	 * thread's Handler.
	 */
	private class GetDirections extends AsyncTask<GeoPoint, Void, Void> {
		@Override
		protected Void doInBackground(GeoPoint... startAndEnd) {
	        // Don't do anything until we've connected to the RouteService.
			Message msg = mHandler.obtainMessage();
			
			// If we are unable to get a GPS fix on the current location yet, 
			// then don't try to get directions.
			if(startAndEnd[0] == null) {
				msg.what = PhoneWandActivity.MESSAGE_CURRENT_LOCATION_UNKNOWN;
				mHandler.sendMessage(msg);
				return null;
			}
			
			mGettingRoute = true;
			            
			// Send a Message which will start a progress dialog.
			msg.what = PhoneWandActivity.MESSAGE_START_ROUTE_RETRIEVAL;
			mHandler.sendMessage(msg);
			msg = mHandler.obtainMessage();
			
			try {
				String jsonString = 
						callWebService(getURL(startAndEnd[0],startAndEnd[1]));
				
				if (D) Log.i(TAG, getString(R.string.google_maps_result)+"\n"+jsonString);
				
				mGettingRoute = false;

				msg.obj = parseRoute(jsonString);
				
				// Route steps have been returned.
				if(msg.obj != null) {
					// Send a success message to to the main thread's Handler.
					msg.what = PhoneWandActivity.MESSAGE_DISPLAY_ROUTE;
					
				// There are no more steps in the route; we're there!
				} else {
					// Send a success message to to the main thread's Handler.
					msg.what = PhoneWandActivity.MESSAGE_END_OF_ROUTE;
				}
			} catch(DataFormatException e) {
				mGettingRoute = false;
				
				// Send an error message to to the main thread's Handler.
				msg.what = PhoneWandActivity.MESSAGE_ROUTE_PARSING_ERROR;
				msg.obj = e;
			} catch(Exception e) {
				mGettingRoute = false;
				
				// Send an error message to to the main thread's Handler.
				msg.what = PhoneWandActivity.MESSAGE_GET_REQUEST_ERROR;
				msg.obj = e;
			}
			
			mHandler.sendMessage(msg);
			return null;
		}
		
		/**
		 * Execute a GET request using the given URL.
		 * @param A String representing the URL to be used for the GET request.
		 * @return A String representing the returned content from the request,
		 * 			or null if the request failed.
		 * @throws IOException 
		 * @throws ClientProtocolException 
		 */
		public String callWebService(String url) throws ClientProtocolException, IOException{
			// Setup request
			HttpClient httpclient = new DefaultHttpClient();
			HttpGet request = new HttpGet(url);
			String result = null;
			ResponseHandler<String> handler = new BasicResponseHandler();
			
			/*try {
				@SuppressWarnings("unused")
				InetAddress i = InetAddress.getByName(url);
			} catch (UnknownHostException e1) {}*/
			
			// Execute request
			result = httpclient.execute(request, handler);
			
			// Close request
			httpclient.getConnectionManager().shutdown();
			
			return result;
		}
		
		/**
		 * Creates a Google Maps URL which contains source and destination 
		 * address parameters based upon the values of the given src and dest 
		 * GeoPoint parameters.
		 * @param src The GeoPoint representing the source address
		 * @param dest The GeoPoint representing the destination address
		 * @return The String representing the Google Maps URL with parameters 
		 * 			which match the given parameters.
		 */
		private String getURL(GeoPoint src, GeoPoint dest) {
			// Parse mCurrentLocation and mDestination into Strings.
			String origin = 
					Double.toString((double) src.getLatitudeE6() / 1.0E6)+","+
					Double.toString((double) src.getLongitudeE6() / 1.0E6);
			String destination = 
					Double.toString((double) dest.getLatitudeE6() / 1.0E6)+","+
					Double.toString((double) dest.getLongitudeE6() / 1.0E6);
			
			StringBuilder urlString = new StringBuilder();
			// The URL before parameters.
			urlString.append("http://maps.googleapis.com/maps/api/directions/json?origin=");
			// Get directions from the current location.
			urlString.append(origin);
			// Get directions to the given destination.
			urlString.append("&destination=");
			urlString.append(destination);
			// Get walking directions (as opposed to driving directions), get 
			// only the single best direction, use metric units, and add the 
			// system parameter which is required by Google.
			urlString.append("&mode=walking&alternatives=false&units=metric&sensor=true");
			
			String url = urlString.toString();
			
			if (D) Log.d(TAG, getString(R.string.url_created) + " " + url);
			
			return url;
		}
		
		/**
		 * Parses the directions from the given JSON response.  If the routes 
		 * array is empty, then null is returned.
		 * @param encoded The encoded String from Google Maps.
		 * @return An array of Steps parsed from the Google Maps route.
		 * @throws DataFormatException if the JSON response is constructed in an 
		 * 			unexpected fashion.
		 */
		private RouteStep[] parseRoute(String jsonResponse) throws DataFormatException {
			if (D) Log.d(TAG, getString(R.string.start_JSON_parsing));
			try {
				JSONObject json = new JSONObject(jsonResponse);
				String status = json.getString("status");
				
				if(!status.equals("OK")) {
					throw new Exception("Invalid status: "+status);
				}
				
				JSONArray routes = json.getJSONArray("routes");
				
				// If routes array is empty, then presumably we are at the destination.
				if(routes.length() == 0) {
					return null;
				}
				
				JSONArray legs = routes.getJSONObject(0).getJSONArray("legs");
				JSONArray steps = legs.getJSONObject(0).getJSONArray("steps");
				
				int length = steps.length();
				RouteStep[] stepArray = new RouteStep[length];
				mDirectionsText = new String[length];
				
				JSONObject jsonStep, location;
				GeoPoint currentStart, currentEnd;
				List<GeoPoint> currentPolyline;
				int currentDuration, currentDistance, lat, lon;
				String currentDescription;
				
				// Loop over the steps, creating a new Step object for each 
				// and adding it to the mSteps array.
				for(int i = 0; i < length; i++) {
					if (D) Log.d(TAG, getString(R.string.parsing_new_step));
					
					// Start location.
					jsonStep = steps.getJSONObject(i);
					location = jsonStep.getJSONObject("start_location");
					lat = (int)(location.getDouble("lat")*1E6);
					lon = (int)(location.getDouble("lng")*1E6);
					currentStart = new GeoPoint(lat, lon);
					// End location.
					location = jsonStep.getJSONObject("end_location");
					lat = (int)(location.getDouble("lat")*1E6);
					lon = (int)(location.getDouble("lng")*1E6);
					currentEnd = new GeoPoint(lat, lon);
					// Distance and duration.
					currentDuration = jsonStep.getJSONObject("duration").getInt("value");
					currentDistance = jsonStep.getJSONObject("distance").getInt("value");
					// Textual description.
					currentDescription = jsonStep.getString("html_instructions");
					currentDescription = currentDescription.replaceAll("<div", " <div");
					currentDescription = currentDescription.replaceAll("<(.|\n)*?>", "");
					// Set of GeoPoints detailing the step's path.
					currentPolyline = parsePolyLine(
							jsonStep.getJSONObject("polyline").getString("points"));
					
					// Create and add new Step.
					stepArray[i] = new RouteStep(currentStart, currentEnd, currentPolyline, 
							currentDuration, currentDistance, currentDescription);
					// Add the step's textual direction to the mDirectionText array.
					mDirectionsText[i] = currentDescription;
				}
				
				if (D) Log.d(TAG, getString(R.string.end_JSON_parsing));
				return stepArray;
				
			} catch(Exception e) {
				throw new DataFormatException(PhoneWandActivity.getStackTrace(e));
			}
		}
		
		/**
		 * Parse the given polyline into an array of GeoPoints.
		 * The algorithm is from http://facstaff.unca.edu/.
		 * @param encodedString The polyline String which is to be decoded.
		 * @return An array of GeoPoints representing the given polyline.
		 */
		private List<GeoPoint> parsePolyLine(String encodedString) throws DataFormatException {
			// Replace double backslashes by single.
			encodedString = encodedString.replace("\\\\", "\\"); //escape char lol
			
			// Decode
			List<GeoPoint> polyline = new ArrayList<GeoPoint>();
			int index = 0, len = encodedString.length();
			int lat = 0, lon = 0;
			
			if (D) Log.d(TAG, getString(R.string.start_decoding));
			
			while (index < len) {
				int b, shift = 0, result = 0;
				
				do { //?????
					b = encodedString.charAt(index++) - 63;
					result |= (b & 0x1f) << shift;
					shift += 5;
				} while (b >= 0x20);
				
				int dlat = ((result & 1) != 0 ? ~(result >> 1) : 
						(result >> 1));
				lat += dlat;
				
				shift = 0;
				result = 0;
				
				do {
					b = encodedString.charAt(index++) - 63;
					result |= (b & 0x1f) << shift;
					shift += 5;
				} while (b >= 0x20);
				
				int dlon = ((result & 1) != 0 ? ~(result >> 1) : 
						(result >> 1));
				lon += dlon;
				
				GeoPoint p = new GeoPoint((int)(((double)lat/1E5)*1E6), 
									(int)(((double)lon/1E5)*1E6));
				polyline.add(p);
				
				if (D) Log.d(TAG, getString(R.string.added_geopoint)+
						": lat=" + p.getLatitudeE6() +
						", lng=" + p.getLongitudeE6());
			}
			if (D) Log.d(TAG, getString(R.string.end_decoding));
			
			return polyline;
		}
	}
	
	/**
	 * Inner (non-static) class used to represent a step in the route.  Each 
	 * step consists of a start location GeoPoint, an end location GeoPoint, a 
	 * distance int (meters), an estimated duration int (seconds), and 
	 * description String.
	 */
	private class RouteStep {
		private GeoPoint startLocation;
		private GeoPoint endLocation;
		private List<GeoPoint> polyline;
		private int distance;
		private int duration;
		private String description;
		
		// Constructor.
		public RouteStep(GeoPoint startLocation, GeoPoint endLocation, 
				List<GeoPoint> polyline, int distance, int duration, 
				String description) {
			this.startLocation = startLocation;
			this.endLocation = endLocation;
			this.distance = distance;
			this.duration = duration;
			this.description = description;
			this.polyline = polyline;
		}
		
		/** Returns a GeoPoint representing the start location of this step. */
		public GeoPoint getStartLocation() {return startLocation;}
		
		/** Returns a GeoPoint representing the end location of this step. */
		public GeoPoint getEndLocation() {return endLocation;}
		
		/** Returns the polyline representing the path of this step. */
		public List<GeoPoint> getPolyline() {return polyline;}
		
		/** Returns an int representing the distance of this step (in meters). */
		public int getDistance() {return distance;}
		
		/** Returns an int representing the duration of this step (in seconds). */
		public int getDuration() {return duration;}
		
		/**
		 * Returns a String which contains a textual description of this step 
		 * in the route.
		 */
		public String getDescription() {return description;}
	}
	
	// Sends and receives Messages and Runnables between threads.
	public final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case PhoneWandActivity.MESSAGE_START_ROUTE_RETRIEVAL:
				if (D) Log.d(TAG,getString(R.string.get_directions_start));
				
				showDialog(INTERNET_DIALOG);
				break;
			case PhoneWandActivity.MESSAGE_DISPLAY_ROUTE:
				if (D) Log.d(TAG, getString(R.string.get_directions_end_success));
				
				dismissDialog(INTERNET_DIALOG);
				
				// Update the current route.
				mRouteSteps = (RouteStep[]) msg.obj;
				mRouteExists = true;
				mCurRouteStep = findNearestRouteStep();
				mCurGeoStep   = findNearestGeoStep();

				// Draw the path.
				drawPath();
				// Invalidate the MapView so that it (re)draws itself.
				mMapView.invalidate();
				break;
			case PhoneWandActivity.MESSAGE_ROUTE_PARSING_ERROR:
				if (D) Log.e(TAG, getString(R.string.route_parsing_fail));
				
				dismissDialog(INTERNET_DIALOG);
				notifyUser(PhoneWandActivity.NOTIFY_PARSING_ERROR, PhoneWandActivity.getStackTrace((Throwable)msg.obj));
				
				//TODO: use come sort of external method for screen transitions that can be called from anywhere
				startActivity(new Intent(RouteOrienter.this, RouteInput.class));
				finish();
				
				break;
			case PhoneWandActivity.MESSAGE_END_OF_ROUTE:
				dismissDialog(INTERNET_DIALOG);
				notifyUser(PhoneWandActivity.NOTIFY_END_OF_ROUTE, null);
				
				//TODO: use come sort of external method for screen transitions that can be called from anywhere
				startActivity(new Intent(RouteOrienter.this, RouteInput.class));
				finish();
				
				break;
			case PhoneWandActivity.MESSAGE_GET_REQUEST_ERROR:
				if (D) Log.w(TAG, getString(R.string.get_directions_end_fail));
				
				dismissDialog(INTERNET_DIALOG);
				notifyUser(PhoneWandActivity.NOTIFY_NO_INTERNET, 
						PhoneWandActivity.getStackTrace((Throwable)msg.obj));
				
				// Try again to find the route (can form an endless loop).
				new GetDirections().execute(mCurrentLocation,mDestination);
				break;
			case PhoneWandActivity.MESSAGE_CURRENT_LOCATION_UNKNOWN:
				notifyUser(PhoneWandActivity.NOTIFY_NO_GPS);
				break;
			default:
				if (D) Log.e(TAG,getString(R.string.invalid_message_fail)+msg.what);
				break;
			}
		}
	};
	
	public int findNearestRouteStep(){
		return 0;
	}
	
	public int findNearestGeoStep(){
		return 0;
	}
	
	private long[] geoTurnVibes = {0, 100, 120, 220, 240, 340, 360, 460};
	public void advanceGeoStep(){
		//vibration feedback
		mVibrator.cancel();
		mVibrator.vibrate(geoTurnVibes,-1);
		
		mCurGeoStep++;
		if(mCurGeoStep==mRouteSteps[mCurRouteStep].getPolyline().size()-1)
			advanceRouteStep();
	}
	
	private long[] stepTurnVibes = {0, 400, 450, 850, 900, 1400}; 
	public void advanceRouteStep(){
		//vibration feedback
		mVibrator.cancel();
		mVibrator.vibrate(stepTurnVibes,-1);
		
		mCurGeoStep=0;
		mCurRouteStep++;
		if(mCurGeoStep==mRouteSteps.length-1)
			atDestination = true;
	}
	
	public int computeBearing(double lat1, double lon1, double lat2, double lon2){
		double dLon = Math.toRadians(lon2-lon1);
		double y = Math.sin(dLon) * Math.cos(lat2);
		double x = Math.cos(lat1)*Math.sin(lat2) -
		        Math.sin(lat1)*Math.cos(lat2)*Math.cos(dLon);
		return (int) Math.toDegrees(Math.atan2(y, x));
	}
	
	// Sets mNextHeanding by computing the compass heading to the next geoPoint.
	public void computeHeading(){
		if (mRouteSteps != null) {
			GeoPoint p1 = mCurrentLocation;
			GeoPoint p2 = mRouteSteps[mCurGeoStep].getPolyline().get(mCurGeoStep+1);
			double lat1 = p1.getLatitudeE6()  / 1e6;
			double lon1 = p1.getLongitudeE6() / 1e6;
			double lat2 = p2.getLatitudeE6()  / 1e6;
			double lon2 = p2.getLongitudeE6() / 1e6;
			float[] results = new float[3];
			
			// This computes distance, initial heading, final heading 
			Location.distanceBetween(lat1, lon1, lat2, lon2, results);
			
			// Check if we are near enough the next GeoStep to move over!
			if(results[0] < DISTANCE_EPS){
				advanceGeoStep();
			}else{// compute compass direction
				//mNextHeading.setValue(computeBearing(lat1,lon1,lat2,lon2));
				// mCurrentLocation;
				mNextHeading.setValue((int)results[2]);
				
				if(D){
				Log.d(TAG,"computeHeading: mNextHeading   = " + mNextHeading.getValue());
				Log.d(TAG,"computeHeading: distance       = " + results[0]);
				Log.d(TAG,"computeHeading: bearing  init? = " + results[1]);
				Log.d(TAG,"computeHeading: bearing final? = " + results[2]);
				}
			}
		}
	}
	
	/** Called when a dialog is to be created for display. */
	protected Dialog onCreateDialog(int id, Bundle args) {
		switch(id) {
		case GPS_DIALOG:
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setMessage(getString(R.string.finding_gps));
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			// TODO: play some sort of audio notification
			return mProgressDialog;
		case INTERNET_DIALOG:
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setMessage(getString(R.string.downloading_route));
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			// TODO: play some sort of audio notification
			return mProgressDialog;
		default:
			return null;
		}
	}
	
	/**
	 * Called when a new location is found by the network location provider.  
	 * Forwards the given location to updateCurrentLocation.
	 */
	public void onLocationChanged(Location l) {
		updateCurrentLocation(l);
		computeHeading();
	}
	
	/**
	 * TODO
	 */
	public void onStatusChanged(String provider, int status, Bundle extras) {
		if (D) Log.v(TAG,"onStatusChanged: status="+status);
		//TODO
		
	}
	
	/**
	 * TODO
	 */
	public void onProviderEnabled(String provider) {
		if (D) Log.v(TAG,"onProviderEnabled");
		//TODO
		
	}
	
	/**
	 * TODO
	 */
	public void onProviderDisabled(String provider) {
		if (D) Log.v(TAG,"onProviderDisabled");
		//TODO
		
	}
	
	/** This method must be truthfully implemented for legal issues. */
	@Override
	protected boolean isRouteDisplayed() {return mRouteExists;}

	/** Methods for updating and controlling compass heading */
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		//if(mSensor.equals(sensor)) 
			//Log.d(TAG, "onAccuracyChanged: accuracy=" + accuracy);
	}
	
	private static long[] mOrientorPulseVibes = {0, 100, 1500, 1600, 3000};

	@Override
	public void onSensorChanged(SensorEvent event) {
		if(mSensor.equals(event.sensor)) {
			float[] values = event.values;
			int heading = (int) values[0];
			//Log.d(TAG, "onSensorChanged: (" + heading + 
			//		", " + values[0] + ", "+ values[1] + ", " + values[2] + ")");
			
			//  Only set heading if it moves far enough
			if(!mOnPause && !mHeading.inRange(heading, MIN_HEADING_CHANGE)) {
				mHeading.setValue(heading);
				
				//redraw compass
				drawPath();
				
				//Log.d(TAG, "Revibrate: \tmHeading="+mHeading.getValue()+
				//		"\theading="+heading+
				//		"\tchange="+Math.abs(mHeading.getValue()-heading));	
					
				// Don't forget this line!!!
				mVibrator.cancel();
				//Vibrate if in range of target heading.
				if(mOrienter && 
						mHeading.inRange(mNextHeading.getValue(), ORIENTING_EPS)){
					mVibrator.vibrate(VIBES,0);
					mBeeper = false;
				}else if (mOrienter && !mBeeper){
					mVibrator.cancel();
					mVibrator.vibrate(mOrientorPulseVibes, 1);
					mBeeper = true;
				}
				
				//More complicated directional guidance
				//int n = mHeading.getErrorPart(mNextHeading,ORIENTING_PARTS);
				//double vc    = 1.5;
				//long[] vibes = {0, (int)vc*n*10, (int)vc*(n*10+n*n), (int)vc*(2*n*10+n*n)};//,n*300};
				//long[] vibes = {0, 100, n*100, 100+n*100}; 
				//mVibrator.vibrate(vibes,1);
			}
		}		
	}
	
	/**
     * Used for detecting the user's gestures on the screen for the purpose 
     * of opening new screens or interacting with the user in other ways.
     */
	@Override
    public boolean onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        return true;
    }
   
    private long[]  mMagicOnVibes = {0,300};
    private long[] mMagicOffVibes = {0,70,90,160};
    
    /** Used to detect swipes and double-taps. */
    private class GestureController extends GestureDetector.SimpleOnGestureListener {
    	public boolean onDoubleTap(MotionEvent e){
    		if (D) Log.d(TAG, "onDoubleTap: We double tapped!!!");
    		mVibrator.cancel();
    		if(!mOrienter)
    			mVibrator.vibrate(mMagicOnVibes, -1);
    		else
    			mVibrator.vibrate(mMagicOffVibes, -1);
    		mOrienter = !mOrienter;
    		return true;
    	}

    	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
    			float velocityY) {
    		int scaledDistance;
    		int scaledPath;

    		// get distance between points of the fling
    		double vertical = Math.abs(e1.getY() - e2.getY());
    		double horizontal = Math.abs(e1.getX() - e2.getX());

    		// convert dip measurements to pixels
    		final float scale = getResources().getDisplayMetrics().density;
    		scaledDistance = (int) (PhoneWandActivity.DISTANCE_DIP * scale + 0.5f);
    		scaledPath = (int) (PhoneWandActivity.PATH_DIP * scale + 0.5f);

    		// If horizontal motion is greater than vertical motion, then try for a horizontal 
    		// swipe.
    		if(horizontal >= vertical) {
    			// test vertical distance
    			if (vertical > scaledPath) {
    				return false;

    				// test horizontal distance and velocity
    			} else if (horizontal > scaledDistance && Math.abs(velocityX) > mMinScaledVelocity) {
    				if (velocityX < 0) { // right to left swipe
    					if (D) Log.v(TAG, "Forward swipe");
    					swipeBuzz();
    					// open directions display screen
    					openDirectionsDisplayScreen();
    				} else { // left to right swipe
    					if (D) Log.v(TAG, "Backward swipe");
    					swipeBuzz();
    					// open enter new route screen
    					openNewRouteScreen(RouteOrienter.this);
    				}
    				return true;
    			} else {
    				//not a good enough swipe
    				return false;
    			}

    			// Vertical motion is greater than horizontal motion, so try for a vertical swipe.
    		} else {
    			// test horizontal distance
    			if (horizontal > scaledPath) {
    				return false;

    				// test vertical distance and velocity
    			} else if (vertical > scaledDistance && Math.abs(velocityY) > mMinScaledVelocity) {
    				if (velocityY < 0) { // top to bottom swipe
    					if (D) Log.v(TAG, "Downward swipe");
    					swipeBuzz();
    					// open check route status screen
    					openCheckRouteScreen();
    				} else { // bottom to top swipe
    					if (D) Log.v(TAG, "Upward swipe");
    					// do nothing!
    				}
    				return true;
    			} else {
    				//not a good enough swipe
    				return false;
    			}
    		}
    	}
    }
    
	/*
	 * Class for integer and modulus for easier wrapping around
	 *  and range comparison methods.
	 */
	public class ModulusInteger {
		private int value;	 	//0<= value <= modulus
		private int modulus;	//modulus base of integer
		
		public ModulusInteger(int val, int mod){
			if(mod > 0)
				modulus = mod;
			else
				throw new IllegalArgumentException();
			setValue(val);
		}
		
		public int getValue(){
			return value;
		}
		
		public void setValue(int val){
			value = putInMod(val);
		}
		
		//returns value to be in range of modulus
		public int putInMod(int val){
			while(val < 0)
					val+=modulus;
			while(val > modulus)
					val-=modulus;
			return val;
		}
		
		// Check if current value is in range of Anchor anc
		//	+/- epsilon eps.
		public boolean inRange(int anc, int eps){
			if( eps >= modulus/2)
				return true;
			int low  = putInMod(anc - eps);
			int high = putInMod(anc + eps);
			if( high < low ) //spillover top
				return (value >= low || value <= high);
			else
				return (value >= low && value <= high);
		}
		
		public int getErrorPart(int anc, int parts){
			if(parts <= 1)
				return 1;
			anc = putInMod(anc);
			int gran = (modulus/2)/parts;
			for(int i=1; i<= parts-1; i++)
				if( inRange(anc,i*gran) )
					return i;
			return parts;
			
		}
	}
	
	// -----Transition Methods-----
	/**
	 * Called when the user is declaring that he/she is currently at an 
	 * intersection.
	 * TODO: are we still implementing this?
	 **/
	public void openNewRouteScreen(Activity caller){
		Intent intent = new Intent(this, RouteInput.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
		caller.finish();
	}
	
	// -----Vibration Methods-----
	public void swipeBuzz(){
		mVibrator.cancel();
		mVibrator.vibrate(SWIPE_VIBES,-1);
	}
	
	/**
	 * Notify the user via both a Toast message and an audio clip and record 
	 * an appropriate Log message.
	 * @param notification An int global constant representing the type of 
	 * 		notification to display.
	 **/
	public void notifyUser(int notification) {
		notifyUser(notification, null);
	}
	
	/**
	 * Notify the user via both a Toast message and an audio clip and record 
	 * an appropriate Log message.
	 * @param notification An int global constant representing the type of 
	 * 		notification to display.
	 * @param extra This Object can be any sort of extra data that is needed 
	 * 		for determining what exactly to tell the user.
	 **/
	public void notifyUser(int notification, Object extra) {
		String logString, toastString;
		// int audioResource;
		// TODO: need to create audio resource messages, select them in the switch statement, and play them at the bottom
		
		// choose which dialog box Toast to display
		switch(notification) {
		case PhoneWandActivity.NOTIFY_NO_INTERNET:
			logString 	= getString(R.string.get_route_address_fail) + "\n" + extra;
			toastString = getString(R.string.please_connect_to_internet);
			break;
		case PhoneWandActivity.NOTIFY_NO_GPS:
			logString 	= getString(R.string.get_gps_fail) + "\n" + extra;
			toastString = getString(R.string.unable_to_find_location);
			break;
		case PhoneWandActivity.NOTIFY_PARSING_ERROR:
			logString 	= getString(R.string.route_parsing_fail) + "\n" + extra;
			toastString = getString(R.string.invalid_google_response);
			break;
		case PhoneWandActivity.NOTIFY_END_OF_ROUTE:
			logString 	= getString(R.string.end_of_route);
			toastString = getString(R.string.end_of_route);
			break;
		case PhoneWandActivity.NOTIFY_NO_DESTINATION_STRING:
			logString 	= getString(R.string.invalid_destination_string_log);
			toastString = getString(R.string.invalid_destination_string_toast);
			break;
		case PhoneWandActivity.NOTIFY_NO_ADDRESSES_FOUND:
			logString 	= getString(R.string.no_matches_found_log);
			toastString = getString(R.string.no_matches_found_toast);
			break;
		default:
			logString 	= getString(R.string.unknown_notification_fail) 
							+ " " + notification;
			toastString = logString;
			break;
		}
		
		// Log the problem.
		Log.w(TAG, logString);
		// Play an audio message.
		// TODO: play the audio message here
		// Display a toast message.
		Toast.makeText(this, toastString, Toast.LENGTH_SHORT).show();
	}
	
	/**
     * Called to signal completion of TTS setup.
     */
	@Override
	public void onInit(int status) {
		mTts.setLanguage(Locale.US);
		welcomeSpeech();
		//mTTSReady = true;
		
		if (D) Log.d(TAG, "TTS initialized");
	}
}