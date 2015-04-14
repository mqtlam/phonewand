/**
 * @author Michael Q. Lam (mqtlam@cs.washington.edu)
 * @author Levi Lindsey (levisl@cs.washington.edu)
 * @author Chris Raastad (craastad@cs.washington.edu)
 * 
 * Designed to meet the requirements of the Winter 2011 UW course, 
 * CSE 481H: Accessibility Capstone
 * 
 * PhoneWandActivity: this is an class intended to unify
 * 	all the repetitive code used in other activity.  This includes
 * 	vibrations, TextToSpeech, DatabaseManagement, constants,
 * 	etc...
 */

package edu.uw.cse481h.phonewand;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Locale;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Address;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Toast;

public class PhoneWandActivity extends Activity implements OnInitListener {
	// -----Debugging Constants-----
	// toggle display of debugging log messages
	private static final boolean D = false;
	// log messages tag 
	private static final String TAG = "RouteService";
	
	
	// -----TextToSpeech Constants and Fields-----
	public static final String TEXT_ENTERED_EXTRA 	= 	"text_entered";
	public static final float  SLOW_SPEECH_RATE		=	0.5f;
	public static final float  MEDIUM_SPEECH_RATE 	=	1f;
	public static final float  FAST_SPEECH_RATE 	=	1.75f;
	
	private float mSpeechRate;
	private boolean mTTSReady;
	public static Locale TTS_LANGUAGE = Locale.UK; //Locale.US;
	private TextToSpeech mTts;
	
	// used for utterances
	private HashMap<String, String> mUtterance = new HashMap<String, String>();
	
	// TODO: TTS init
	// used for TTS initialization
	//protected static final int CHECK_TTS_AVAILABLE = 22367; // arbitrary number
	
	// -----Vibration System Constants and Fields-----
	// handle for vibration system
	private Vibrator mVibrator;
	// vibration array for swipes
	public static final long[] SWIPE_VIBES = {0,100};
	
	
	// -----Gestures, Swipes, and Taps Constants and Fields-----
	// TODO: (more descriptive)variable needed for swipes...
	private int mMinScaledVelocity; 
	// minimum swiping distance
	public static final float DISTANCE_DIP = 16.0f;
	// maximum allowed vertical deviation
	public static final float PATH_DIP = 40.0f;
	// GestureDetector for Swipe and Double Tap listeners
	protected GestureDetector mGestureDetector;
	
	
	// -----Database Access Constants-----
	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "phone_wand";
	private static final String TIMESTAMP = "timestamp";
	
	private static final String BOOKMARKS_TABLE_NAME = "bookmarks";
	private static final String BOOKMARK_ID = "bookmark_id";
	private static final String BOOKMARK_ADDRESS = "bookmark_address";
	private static final String BOOKMARK_LAT = "bookmark_lat";
	private static final String BOOKMARK_LON = "bookmark_lon";
	private static final String CREATE_BOOKMARKS_TABLE = 
		"CREATE TABLE " 	+ BOOKMARKS_TABLE_NAME + " ( " + 
		BOOKMARK_ID 		+ " INTEGER PRIMARY KEY AUTOINCREMENT, " + 
		BOOKMARK_ADDRESS 	+ " TEXT, " + 
		BOOKMARK_LAT 		+ " INTEGER, " + 
		BOOKMARK_LON 		+ " INTEGER, " + 
		TIMESTAMP 			+ " TEXT);";
	
	private static final String RECENTS_TABLE_NAME = "recents";
	private static final String RECENT_ID = "recent_id";
	private static final String CREATE_RECENTS_TABLE = 
		"CREATE TABLE " + RECENTS_TABLE_NAME + " ( " + 
		RECENT_ID 		+ " INTEGER PRIMARY KEY AUTOINCREMENT, " + 
		BOOKMARK_ID 	+ " INTEGER REFERENCES " + BOOKMARKS_TABLE_NAME + "(" + BOOKMARK_ID + ")," + 
		TIMESTAMP 			+ " TEXT);";
	
	// saves the bookmarkId of the most recent address to which the user found a route.
	public static long mCurrentDestinationId = -1;
	
	// database access objects
	private DbHelper mOpenHelper;
	private SQLiteDatabase mDataBase;
	
	
	// -----Control Flow Message Constants-----
	// Constants used for identifying assorted Messages.
	public static final int MESSAGE_START_ROUTE_RETRIEVAL =		0;
	public static final int MESSAGE_DISPLAY_ROUTE =				1;
	public static final int MESSAGE_GET_REQUEST_ERROR =			2;
	public static final int MESSAGE_ROUTE_PARSING_ERROR =		3;
	public static final int MESSAGE_END_OF_ROUTE =				4;
	public static final int MESSAGE_START_ADDRESS_RETRIEVAL =	5;
	public static final int MESSAGE_CURRENT_LOCATION_UNKNOWN =	6;
	public static final int MESSAGE_GET_ADDRESSES_SUCCESS =		7;
	public static final int MESSAGE_GET_ADDRESSES_FAIL =		8;	
	
	// Constants used for identifying assorted notifications.
	public static final int NOTIFY_NO_INTERNET =				0;
	public static final int NOTIFY_NO_GPS =						1;
	public static final int NOTIFY_END_OF_ROUTE =				2;
	public static final int NOTIFY_PARSING_ERROR =				3;
	public static final int NOTIFY_NO_DESTINATION_STRING =		4;
	public static final int NOTIFY_NO_ADDRESSES_FOUND =			5;	
	
	// Constants used for determining which Activity was/is called for returning a result.
	public static final int REQUEST_CODE_ROUTE_ARCHIVE =		1;
	public static final int REQUEST_CODE_POSSIBLE_ADDRESSES =	2;
	public static final int REQUEST_CODE_BLIND_TEXT =			3;	
	
	// For passing the latitude and longitude extras in the Intent.
	public static final String LATITUDE_EXTRA =					"latitude";
	public static final String LONGITUDE_EXTRA =				"longitude";
	public static final String INDEX_EXTRA =					"index";
	public static final String RECORD_ID_EXTRA =				"record_id";
	public static final String STARTING_TEXT_EXTRA =			"starting_text";	
	
	// For determining if the latitude and longitude extras were passed 
	// 	correctly in the Intent.
	public static final int MICRODEGREE_UPPER_BOUND = 360000000;

	// For one time calls on application start.
	private static boolean onApplicationStart = false;
	
	// -----Activity Life Cycle Methods-----
	@Override
	public void onCreate(Bundle savedInstanceState) {
		if (D) Log.v(TAG, "+++ ON CREATE +++");
		super.onCreate(savedInstanceState);
		
		// open database
		mOpenHelper = new DbHelper(this);
		try{
			mDataBase = mOpenHelper.getWritableDatabase();
		} catch(SQLiteException e) {
			Log.e(TAG,getString(R.string.db_fail)+": "+getStackTrace(e));
		}
		
        // TODO: TTS prompt
        // TTS (set up later)
        //mTTSReady = false;
		mTts = new TextToSpeech(this, this);                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           
        
        // set up vibration system.
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		
    	// initialize gesture detector for double taps and swipes
        mGestureDetector = new GestureDetector(new GestureController());
	}
	
	@Override
	public void onDestroy() {
		if (D) Log.v(TAG, "--- ON DESTROY ---");
		super.onDestroy();
		
		mOpenHelper.close();
	}
	
	
	// -----TextToSpeech Methods-----
	public float getSpeechRate() {
		return mSpeechRate;
	}
	
	public void setSpeechRate() {
		mTts.setSpeechRate(mSpeechRate);
	}
	
	/* // TODO: TTS prompt
	public boolean initTTS(TextToSpeech tts) {
		if (!mTTSReady) {
			mTts = tts;
			mTTSReady = true;
			return true;
		} else {
			return false;
		}
	}
	
	public boolean ttsIsReady() {
		return mTTSReady;
	}
	*/
	
	public boolean ttsSpeak(String text, int queueMode, HashMap<String, String> params) {
		// TODO: TTS prompt
		//if (ttsIsReady()) {
			mTts.speak(text, queueMode, params);
		//	return true;
		//} else {
		//	return false;
		//}
			return true;
	}
	
	@Override
	public void onInit(int arg0) {
		mTts.setLanguage(TTS_LANGUAGE);
		if(!onApplicationStart){
    		ttsSpeak("Welcome to the phone wand application.");
    		ttsSpeak("Double tap anywhere on the screen to begin.", TextToSpeech.QUEUE_ADD);
    		onApplicationStart = !onApplicationStart;
		}
	}
	
	public boolean ttsSpeak(String text, int queueMode) {
		return ttsSpeak(text, queueMode, null);
	}
	
	public boolean ttsSpeak(String text) {
		return ttsSpeak(text, TextToSpeech.QUEUE_FLUSH, null);
	}
	
	
	// -----Vibration Methods-----
	public void swipeBuzz(){
		mVibrator.cancel();
		mVibrator.vibrate(SWIPE_VIBES,-1);
	}
	
	
	// -----Gestures Methods-----
	// used to detect swipes and double taps 
	@Override
    public boolean onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        return true;
    }
	
	// stub gesture methods intended to be overwritten by subclasses
	protected boolean doubleTap (){
		return false;
	}
	protected void swipeUp	 (){;}
	protected void swipeDown (){;}
	protected void swipeLeft (){;}
	protected void swipeRight(){;}
	
	
	// -----Database Management Methods-----
	/** 
	 * Insert a record into the bookmarks table and return the new record's id. 
	 **/
	public long addBookmarkRecord(String address, int lat, int lon) {
		String timestamp = getTimeStamp();
		
		ContentValues values = new ContentValues();
		values.put(BOOKMARK_ADDRESS, address);
		values.put(BOOKMARK_LAT, lat);
		values.put(BOOKMARK_LON, lon);
		values.put(TIMESTAMP, timestamp);
		
		try {
			long bookmarkId = mDataBase.insert(BOOKMARKS_TABLE_NAME, null, values);
			
			Log.w(TAG, "Record added to " + BOOKMARKS_TABLE_NAME + " table: ("+bookmarkId + ", " + 
					address + ", " + lat + ", " + lon + ", " + timestamp + ")");
			
			return bookmarkId;
		} catch (Exception e) {
			Log.e(TAG, getString(R.string.add_fail) + ": " + getStackTrace(e));
			return -1;
		}
	}
	
	/** 
	 * Return the record stored for the given bookmarkId in the bookmarks table 
	 * {address,lat,lon,timestamp} or null if there exists no such bookmarkId.
	 **/
	public Cursor getBookmarkRecord(long bookmarkId) {
		try{
			Cursor c = mDataBase.query(true, BOOKMARKS_TABLE_NAME, new String[] {BOOKMARK_ADDRESS, 
					BOOKMARK_LAT, BOOKMARK_LON, TIMESTAMP}, BOOKMARK_ID + 
					"=\'" + bookmarkId + "\'", null, null, null, null, null);
			if(c.getCount() < 1){
				c.close();
				return null;
			}
			c.moveToFirst();
			return c;
		}catch(Exception e){
			Log.e(TAG, getString(R.string.get_fail) + ": " + getStackTrace(e));
			return null;
		}
	}
	
	/** 
	 * Return the record ID stored for the given bookmarkAddress in the bookmarks table 
	 * or -1 if there exists no such bookmarkAddress.
	 **/
	public long getBookmarkIDByAddress(String bookmarkAddress) {
		try{
			Cursor c = mDataBase.query(true, BOOKMARKS_TABLE_NAME, new String[] {BOOKMARK_ID},
					BOOKMARK_ADDRESS + "=\'" + bookmarkAddress + "\'", null, null, null, null, null);
			if(c.getCount() < 1){
				c.close();
				return -1;
			}
			c.moveToFirst();
			long bookmarkID = c.getLong(0);
			c.close();
			
			return bookmarkID;
		}catch(Exception e){
			Log.e(TAG, getString(R.string.get_fail) + ": " + getStackTrace(e));
			return -1;
		}
	}
	
	/** 
	 * Return the addresses stored in the bookmarks table 
	 * or an empty string array if the table is empty.
	 **/
	public String[] getBookmarkAddresses() {
		try{
			Cursor c = mDataBase.query(true, BOOKMARKS_TABLE_NAME, new String[] {BOOKMARK_ADDRESS},
					null, null, null, null, null, null);
			int count = c.getCount();
			if(count < 1){
				c.close();
				return new String[0]; // return no records
			}
			
			String[] records = new String[count];
			
			c.moveToFirst();
			records[0] = c.getString(0);
			for (int i = 1; i < count; i++) {
				if(!c.moveToNext()) {
					c.close();
					return null;
				}
				records[i] = c.getString(0);
			}
			
			return records;
		}catch(Exception e){
			Log.e(TAG, getString(R.string.get_fail) + ": " + getStackTrace(e));
			return null;
		}
	}
	
	/** 
	 * Delete the bookmark record corresponding to the given bookmarkId. 
	 **/
	public void deleteBookmarkRecord(long bookmarkId) {
		try {
			if(mDataBase.delete(BOOKMARKS_TABLE_NAME, BOOKMARK_ID+"=\'" + bookmarkId + 
					"\'", null) > 0) {
				Log.w(TAG,"Record deleted from " + BOOKMARKS_TABLE_NAME + 
						" table: bookmarkId = " + bookmarkId);
			}
		} catch (Exception e) {
			Log.e(TAG, getString(R.string.delete_fail) + ": " + getStackTrace(e));
		}
	}
	
	
	// -----OTHER Methods-----
	//TODO: Check here for methods that belong in their own category.
	/**
	 * Extract a single address String from the given Address object.
	 * @param address The Address object from which to extract the String.
	 * @return The String representation.
	 **/
	public static String getAddressString(Address address) {
		String addressString = "";
		int length = address.getMaxAddressLineIndex();
		
		for(int i = 0; i < length; i++) {
			addressString += address.getAddressLine(i)+" ";
		}
		
		return addressString;
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
		case NOTIFY_NO_INTERNET:
			logString 	= getString(R.string.get_route_address_fail) + "\n" + extra;
			toastString = getString(R.string.please_connect_to_internet);
			break;
		case NOTIFY_NO_GPS:
			logString 	= getString(R.string.get_gps_fail) + "\n" + extra;
			toastString = getString(R.string.unable_to_find_location);
			break;
		case NOTIFY_PARSING_ERROR:
			logString 	= getString(R.string.route_parsing_fail) + "\n" + extra;
			toastString = getString(R.string.invalid_google_response);
			break;
		case NOTIFY_END_OF_ROUTE:
			logString 	= getString(R.string.end_of_route);
			toastString = getString(R.string.end_of_route);
			break;
		case NOTIFY_NO_DESTINATION_STRING:
			logString 	= getString(R.string.invalid_destination_string_log);
			toastString = getString(R.string.invalid_destination_string_toast);
			break;
		case NOTIFY_NO_ADDRESSES_FOUND:
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
	 * Return a String representing the current time in the form 
	 * 	'yyyy-mm-dd hh:mm:ss.nnnnnnnnn'. 
	 **/
	public static String getTimeStamp() {
		return new Timestamp(System.currentTimeMillis()).toString();
	}
	
	/**
	 * Get a Throwable's stack trace in a readable form.  This is used for 
	 * displaying an Exception's stack trace in the Log even if it is caught 
	 * and handled by our code.
	 * 
	 * @param t A Throwable whose stack trace is to be printed.
	 * @return The String representing the stack trace.
	 **/
	public static String getStackTrace(Throwable t) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw, true);
		t.printStackTrace(pw);
		pw.flush();
		sw.flush();
		return sw.toString();
	}
	
	/**
	 * Convert the given latitude or longitude from a double representation 
	 * 	into an int representation.
	 * @param location The double representation.
	 * @return The int representation.
	 **/
	public static int doubleToInt(double location) {
		return (int)(location*1E6);
	}
	
	/**
	 * Convert the given latitude or longitude from an int representation into 
	 * 	a double representation.
	 * @param location The int representation.
	 * @return The double representation.
	 **/
	public static double intToDouble(int location) {
		return 1.0/1E6*location;
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
	
	/**
	 * Open the RouteOrienter Activity with the given latitude and longitude 
	 * 	extras.
	 **/
	public void openRouteOrienter(Activity caller, int latitude, int longitude, long bookmarkId) {
		// Record the bookmarkId of the current destination address.
		mCurrentDestinationId = bookmarkId;
		
		Log.d(TAG, "Opening RouteOrienter with: latitude = "+latitude+"; longitude = "+longitude);
		
		// Open the RouteOrienter screen with the given destination.
		Intent intent = new Intent(this, RouteOrienter.class);
		intent.putExtra(LATITUDE_EXTRA, latitude);
		intent.putExtra(LONGITUDE_EXTRA, longitude);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
		caller.finish();
	}
	
	/** 
	 * Open the given screen which will return a result. 
	 **/
	public void openActivityForResult(int requestCode) {
		Intent intent;
		
		switch(requestCode) {
		case REQUEST_CODE_BLIND_TEXT:
			intent = new Intent(this, BlindText.class);
			break;
		case REQUEST_CODE_ROUTE_ARCHIVE:
			intent = new Intent(this, RouteArchive.class);
			break;
		case REQUEST_CODE_POSSIBLE_ADDRESSES:
			intent = new Intent(this, PossibleAddresses.class);
			break;
		default:
			Log.e(TAG, "Error: an invalid requestCode was passed to openActivityForResult " + 
					requestCode);
			finish();
			return;
		}
		
		startActivityForResult(intent, requestCode);
	}
	
	/**
	 * Override the default instructions for the phone's back, menu, and 
	 * 	search hard-key buttons.  The home button cannot be overridden.
	 **/
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
	
	
	// -----Private Helper Classes-----
	/** 
	 * A helper class to manage database creation and version management
	 * 	of database. 
	 **/
	public class DbHelper extends SQLiteOpenHelper {
		DbHelper(Context context) {
			super(context.getApplicationContext(), DATABASE_NAME, null, 
					DATABASE_VERSION);
		}
		
		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(CREATE_BOOKMARKS_TABLE);
			db.execSQL(CREATE_RECENTS_TABLE);
			
			Log.i(TAG, DATABASE_NAME + " database created!");
		}
		
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, 
				int newVersion) {
			db.execSQL("DROP IF TABLE EXISTS " + BOOKMARKS_TABLE_NAME);
			db.execSQL("DROP IF TABLE EXISTS " + RECENTS_TABLE_NAME);
			onCreate(db);

			Log.i(TAG, DATABASE_NAME + " database upgraded!");
		} 
	}
	
	/**
	 * Controls all gesture related actions.
	 **/
	private class GestureController extends GestureDetector.SimpleOnGestureListener {
		public boolean onDoubleTap(MotionEvent e){
			Log.d(TAG, "Double Tap");
			doubleTap();
			return true;
		}
	
		// used to implement swipes
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
    		int scaledDistance;
    		int scaledPath;

    		// get distance between points of the fling
    		double vertical 	= Math.abs(e1.getY() - e2.getY());
    		double horizontal 	= Math.abs(e1.getX() - e2.getX());

    		// convert dip measurements to pixels
    		final float scale = getResources().getDisplayMetrics().density;
    		scaledDistance 	= (int) (DISTANCE_DIP * scale + 0.5f);
    		scaledPath 		= (int) (PATH_DIP * scale + 0.5f);

    		// If horizontal motion is greater than vertical motion, then try for a horizontal 
    		// swipe.
    		if(horizontal >= vertical) {
    			// test vertical distance
    			if (vertical > scaledPath) {
    				return false;

    			// test horizontal distance and velocity
    			} else if (horizontal > scaledDistance && Math.abs(velocityX) > mMinScaledVelocity) {
    				if (velocityX < 0) { // right to left swipe
    					if (D) Log.v(TAG, "Leftward Swipe");
    					swipeLeft();
    				} else { // left to right swipe
    					if (D) Log.v(TAG, "Rightward Swipe");
    					swipeRight();
    				}
    				return true;
    				
    			//not a good enough swipe
    			} else {
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
    					swipeDown();
    				} else { // bottom to top swipe
    					if (D) Log.v(TAG, "Upward swipe");
    					swipeUp();
    				}
    				return true;
    				
    			//not a good enough swipe
    			} else {
    				return false;
    			}
    		}
		}
	}	
}