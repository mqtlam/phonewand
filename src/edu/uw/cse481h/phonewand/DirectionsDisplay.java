/**
 * @author Michael Q. Lam (mqtlam@cs.washington.edu)
 * @author Levi Lindsey (levisl@cs.washington.edu)
 * @author Chris Raastad (craastad@cs.washington.edu)
 * 
 * Designed to meet the requirements of the Winter 2011 UW course, 
 * CSE 481H: Accessibility Capstone
 * 
 * DirectionsDisplay displays to the user a vertical list of the directions 
 * in the current route.
 */

package edu.uw.cse481h.phonewand;

import android.os.Bundle;
import android.util.Log;

public class DirectionsDisplay extends SlideRuleListActivity {
	// toggle display of debugging log messages
	private static final boolean D = true;
	// log messages tag 
	private static final String TAG = "DirectionsDisplay";
	//TODO: grab directions and put them into mListItems array
	
	private static final String GO_BACK_STRING = "Back to map view";

	/** 
	 * Called when the activity is first created. 
	 **/
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	if (D) Log.v(TAG, "+++ ON CREATE +++");
    	super.onCreate(savedInstanceState);
    	int length = RouteOrienter.mDirectionsText.length;
    	mListItems = new String[length+1];
		mListItems[0] = GO_BACK_STRING;
		System.arraycopy(RouteOrienter.mDirectionsText, 0, mListItems, 1, length);
    }
    
    /** Called when an item is selected. */
    @Override
    protected void onItemSelected(int listItemIndex) {
    	Log.d(TAG, "Item selected [" + listItemIndex + "]: " + mListItems[listItemIndex]);
    	
    	swipeBuzz();
    	
    	// if user selected "Back to map view"
    	if (listItemIndex == 0) {
    		setResult(RESULT_CANCELED);
	    	finish();
    	
    	// TODO: otherwise user selects a valid address
    	}
    }
}
