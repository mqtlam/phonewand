/**
 * @author Michael Q. Lam (mqtlam@cs.washington.edu)
 * @author Levi Lindsey (levisl@cs.washington.edu)
 * @author Chris Raastad (craastad@cs.washington.edu)
 * 
 * Designed to meet the requirements of the Winter 2011 UW course, 
 * CSE 481H: Accessibility Capstone
 * 
 * RouteArchive displays to the user a vertical list of previously entered 
 * directions that have been saved into the internal database.
 */

package edu.uw.cse481h.phonewand;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class RouteArchive extends SlideRuleListActivity {
	// toggle display of debugging log messages
	private static final boolean D = true;
	// log messages tag 
	protected static final String TAG = "RouteArchive";
	
	private static final String GO_BACK_STRING = "Go back to Input Screen.";
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	if (D) Log.v(TAG, "+++ ON CREATE +++");
    	super.onCreate(savedInstanceState);    	
    	
    	// Populate archive with previous routes   	
		String addresses[] = getBookmarkAddresses();
		if (addresses != null) {
			mListItems = new String[addresses.length + 1];
			mListItems[0] = GO_BACK_STRING;
			System.arraycopy(addresses, 0, mListItems, 1, addresses.length);
		} else {
			// error condition: database query failed
			mListItems = new String[1];
			mListItems[0] = GO_BACK_STRING;
		}
		refreshList();
    }
    
    /** Called when the system removes this Activity. */
	@Override
	public void onDestroy() {
		if (D) Log.v(TAG, "--- ON DESTROY ---");
		super.onDestroy();
	}
	
    /** Called when an item is selected. */
    @Override
    protected void onItemSelected(int listItemIndex) {
    	if (D) Log.d(TAG, "Item selected [" + listItemIndex + "]: " 
    			+ mListItems[listItemIndex]);
    	
    	swipeBuzz();
    	
    	// if user selected "None of these"
    	if (listItemIndex == 0) {
    		setResult(RESULT_CANCELED);
	    	finish();
    	} else {
    		//long itemID = mIDs[listItemIndex-1];
    		long itemID = getBookmarkIDByAddress(mListItems[listItemIndex]);
    		
	    	Intent intent = new Intent(RouteArchive.this, RouteInput.class);
	    	intent.putExtra(RECORD_ID_EXTRA, itemID);
	    	setResult(RESULT_OK, intent);
	    	finish();
    	}
    }
}