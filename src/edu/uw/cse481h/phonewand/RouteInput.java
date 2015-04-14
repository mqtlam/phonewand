/**
 * @author Michael Q. Lam (mqtlam@cs.washington.edu)
 * @author Levi Lindsey (levisl@cs.washington.edu)
 * @author Chris Raastad (craastad@cs.washington.edu)
 * 
 * Designed to meet the requirements of the Winter 2011 UW course, 
 * CSE 481H: Accessibility Capstone
 * 
 * RouteInput prompts the user to input a new destination location.  
 * RouteInput also provides the ability for the user to select a previously 
 * entered location.
 */

package edu.uw.cse481h.phonewand;

import java.util.List;

import android.content.Intent;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.TextView;

import com.google.android.maps.GeoPoint;

//public class RouteInput extends TTSActivity {
public class RouteInput extends PhoneWandActivity{
	// -----Debugging Constants-----
	// toggle display of debugging log messages
	private static final boolean D = true;
	// log messages tag 
	private static final String TAG = "RouteInput";
	
	// TextToSpeech Strings
	private static final String tts_directions = "Please enter a walking route "
		+ "destination.  Swipe left to confirm.  Swipe right for saved and "
		+ "previously entered routes";
	
	// -----Geographical Address Entry Contants and Fields-----
	//TODO: Rename more descriptive.
	// How many addresses matches to allow the Geocoding functionality to return.
	private static final int MAX_ADDRESS_MATCHES = 5;
	//TODO: Rename more descriptive.
	// Contains possible Address matches for the current destination String.
	public static List<Address> mAddresses;
	//TODO: Rename more descriptive.
	// The destination entry EditText View.
	private TextView mDestinationDisplay;
	
	
	/** Called when the system creates this Activity. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		if (D) Log.v(TAG, "+++ ON CREATE +++");
		super.onCreate(savedInstanceState);
		
		setTitle(R.string.app_name);
		setContentView(R.layout.route_input);
		
		if(mCurrentDestinationId > 0) {
			// Get a String representing the most recently seen destination.
			Cursor addressCursor = getBookmarkRecord(
					mCurrentDestinationId);
			String addressString = addressCursor.getString(0);
			addressCursor.close();
			// Set the destination EditText View to reflect the most recently seen destination.
			mDestinationDisplay.setText(addressString);
		}
		
		// Destination entry field.
		mDestinationDisplay = (TextView) findViewById(R.id.destinationdisplay);
		
		ttsSpeak(tts_directions, TextToSpeech.QUEUE_FLUSH, null);
	}
	
	/** Called when the system removes this Activity. */
	@Override
	public void onDestroy() {
		if (D) Log.v(TAG, "--- ON DESTROY ---");
		super.onDestroy();
	}
	
	/**
	 * Callback method that is called when the PossibleAddresses Activity 
	 * returns.  The PossibleAddresses Activity will return both a latitude 
	 * and longitude extra representing the destination selected by the user.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		
		// If request was canceled, do nothing.
		if (resultCode == RESULT_CANCELED) {
			return;
		}
		
		int latitude = -1;
		int longitude = -1;
		long bookmarkId = -1;
		
		switch(requestCode) {
		// Destination entered in BlindText screen.
		case REQUEST_CODE_BLIND_TEXT:
			String dest = intent.getStringExtra(TEXT_ENTERED_EXTRA);
			break;
			
		// Address selected in RouteArchive screen.
		case REQUEST_CODE_ROUTE_ARCHIVE:
			// Get address record id returned from RouteArchive.
			bookmarkId = intent.getLongExtra(RECORD_ID_EXTRA, -1);
			// Ensure the extra was passed.
			if(bookmarkId < 0) {
				if (D) Log.e(TAG, getString(R.string.address_id_extra_fail));
				finish();
			}
			
			// Retrieve destination from database.
			Cursor addressCursor = getBookmarkRecord(bookmarkId);
			latitude = addressCursor.getInt(1);
			longitude = addressCursor.getInt(2);
			addressCursor.close();
			break;
			
		// Address selected in PossibleAddresses screen.
		case REQUEST_CODE_POSSIBLE_ADDRESSES:
			// Get the latitude and longitude values returned from PossibleAddresses.
			int index = intent.getIntExtra(INDEX_EXTRA, -1);
			// Ensure the extra was passed.
			if(index < 0) {
				if (D) Log.e(TAG, getString(R.string.address_index_extra_fail));
				finish();
			}
			
			// Extract the lat and lon.
			Address destination = mAddresses.get(index);
			latitude = doubleToInt(destination.getLatitude());
			longitude = doubleToInt(destination.getLongitude());
			// Save the destination in the database.
			bookmarkId = addBookmarkRecord(
					getAddressString(destination), latitude, longitude);
			break;
			
		default:
			if (D) Log.e(TAG, getString(R.string.invalid_request_code_fail)+" "+requestCode);
			finish();
			break;
		}
		
		// TODO: this probably needs to be rewritten with more robust transition code.
		// Open the RouteOrienter screen with the given location information.
		openRouteOrienter(this, latitude, longitude, bookmarkId);
	}
	
	/**
	 * Find the user's current location, query Google Maps for a route, and 
	 * open the RouteOrienter screen with this information.
	 */
	private void findRoute() {
		//TODO: need to copy some of the location finding and route retrieving 
		//		code from RouteOrienter, so that we can ensure that the user 
		//		currently has a GPS fix, currently has an Internet connection, 
		//		and has entered a valid destination location.
		
		// Temporarily just send the coordinates of the Space Needle to the RouteOrienter
		// openRouteOrienter((int)(47.6204*1E6), (int)(-122.3491*1E6));
		
		// Try to find some locations that the user's destination String could represent.
		new GetAddresses().execute();
	}
	
	// -----Gesture Control-----
	protected boolean doubleTap(){
		findRoute();
		return true;
	}
	
	protected void swipeDown(){
		swipeBuzz();
		openActivityForResult(REQUEST_CODE_BLIND_TEXT);
	}
	
	protected void swipeLeft(){
		swipeBuzz();
		// open directions display screen
		findRoute();
	}
	
	protected void swipeRight(){
		swipeBuzz();
		// open enter new route screen
		openActivityForResult(REQUEST_CODE_ROUTE_ARCHIVE);
	}
	
	/**
	 * Inner (non-static) class which operates on a separate thread in order 
	 * to get a list of addresses which may match the destination which the 
	 * user has entered in the destinationEntry EditText field, and then 
	 * return the result to the main thread's Handler.
	 */
	private class GetAddresses extends AsyncTask<GeoPoint, Void, Void> {
		@Override
		protected Void doInBackground(GeoPoint... startAndEnd) {
			Message msg = mHandler.obtainMessage();
			
			// Send a starting message to the main thread.
			msg.what = MESSAGE_START_ADDRESS_RETRIEVAL;
			mHandler.sendMessage(msg);
			msg = mHandler.obtainMessage();
			
			String destinationString = (String) mDestinationDisplay.getText();
			
			// Ensure that the user has entered some sort of destination String.
			if(destinationString == null || destinationString.length() < 3) {
				// Send a message which will post a notification that the user 
				//  needs to enter a valid destination String.
				msg.what = MESSAGE_GET_ADDRESSES_FAIL;
				msg.arg1 = NOTIFY_NO_DESTINATION_STRING;
				
				// Send the result to the main thread.
				mHandler.sendMessage(msg);
				
				return null;
			}
			
			// Get a Geocoder object.
			Geocoder geocoder = new Geocoder(RouteInput.this);
			
			// Try to find a list of matching addresses.
			try {
				// Get a list of potential addresses.
				List<Address> addresses = 
						geocoder.getFromLocationName(destinationString, MAX_ADDRESS_MATCHES);
				
				// If at least one address was found, then return the list.
				if(addresses != null && addresses.size() > 0) {
					// Send a message returning the list of addresses found.
					msg.what = MESSAGE_GET_ADDRESSES_SUCCESS;
					msg.obj = addresses;
					
				// If no address was found, then notify the user.
				} else {
					// Send a message which will post a notification that the 
					// user needs to enter a better destination string.
					msg.what = MESSAGE_GET_ADDRESSES_FAIL;
					msg.arg1 = NOTIFY_NO_ADDRESSES_FOUND;
				}
				
			// Most likely the getFromLocationName method had a problem connecting to the network.
			} catch (Exception e) {
				// Send a message which will post a notification that the user 
				// needs to establish an Internet connection.
				msg.what = MESSAGE_GET_ADDRESSES_FAIL;
				msg.arg1 = NOTIFY_NO_INTERNET;
				msg.obj = e;
			}
			
			// Send the result to the main thread.
			mHandler.sendMessage(msg);
			
			return null;
		}
	}
	
	// Sends and receives Messages and Runnables between threads.
	public final Handler mHandler = new Handler() {
		@SuppressWarnings("unchecked")
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_START_ADDRESS_RETRIEVAL:
				if (D) Log.d(TAG, getString(R.string.address_retrieval_started) + " " + 
						(String) mDestinationDisplay.getText());
				
				// TODO: should we display a ProgressDialog here?
				
				break;
			case MESSAGE_GET_ADDRESSES_FAIL:
				notifyUser(msg.arg1, msg.obj);
				
				break;
			case MESSAGE_GET_ADDRESSES_SUCCESS:
				mAddresses = (List<Address>) msg.obj;
				
				// If there is only one Address in the list returned, then 
				// open the RouteOrienter Activity with this location.
				if(mAddresses.size() == 1 ) {
					Address destination = mAddresses.get(0);
					
					if(D) Log.d(TAG, getString(R.string.one_address_found_log)+" "+destination.toString());
					
					// Extract the lat and lon.
					int latitude = doubleToInt(destination.getLatitude());
					int longitude = doubleToInt(destination.getLongitude());
					
					// Save the destination in the database.
					long bookmarkId = addBookmarkRecord(
							getAddressString(destination), latitude, longitude);
					
					// Open the RouteOrienter screen with the given location information.
					openRouteOrienter(RouteInput.this,latitude, longitude, bookmarkId);
					
				// There are multiple Addresses in the list, so open the 
				// PossibleAddresses Activity.
				} else {
					if (D) Log.d(TAG, getString(R.string.multiple_addresses_found_log));
					
					openActivityForResult(REQUEST_CODE_POSSIBLE_ADDRESSES);
				}
				
				break;
			default:
				if (D) Log.e(TAG,getString(R.string.invalid_message_fail)+msg.what);
				break;
			}
		}
	};
}