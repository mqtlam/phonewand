/**
 * @author Michael Q. Lam (mqtlam@cs.washington.edu)
 * @author Levi Lindsey (levisl@cs.washington.edu)
 * @author Chris Raastad (craastad@cs.washington.edu)
 * 
 * Designed to meet the requirements of the Winter 2011 UW course, 
 * CSE 481H: Accessibility Capstone
 * 
 * PossibleAddresses displays to the user a vertical list of the possible 
 * address matches returned by the Geocoding functionality of the RouteInput 
 * Activity.  The address selected by the user is then returned back to the 
 * RouteInput Activity.
 */

package edu.uw.cse481h.phonewand;

import java.util.List;

import android.content.Intent;
import android.location.Address;
import android.os.Bundle;
import android.util.Log;

public class PossibleAddresses extends SlideRuleListActivity {
	//TODO: should be called with startActivityForResult.  Implement a list 
	//		similar to those in the DirectionsDisplay and RouteArchive 
	//		Activities.  Fill this list with the possible address strings that 
	//		are passed as an extra to this Activity.  Return whichever address 
	//		is selected.
	
	//TODO: the activity result should be canceled if the user selects the 
	//		option "None of these", which should added at the end of the list.
	
	//TODO: access addresses from the static mAddresses field in the RouteInput Activity.
	//TODO: return an Intent that has a String representing the address's textual info 
	//		and both a latitude and a longitude extra
	
	// getString(R.string.multiple_addresses_found)
	
	protected static final String TAG = "PossibleAddresses";
	private static final String NONE_OF_THESE_STRING = "None of these";
	
	private List<Address> mAddressList;
	private int[] mLatitudes;
	private int[] mLongitudes;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	// add addresses to the list
    	mAddressList = RouteInput.mAddresses;
    	int listSize = mAddressList.size();
    	
    	mListItems = new String[listSize + 1];
    	for (int i = 0; i < listSize; i++)
    		mListItems[i] = PhoneWandActivity.getAddressString(mAddressList.get(i));
    	mListItems[mAddressList.size()] = NONE_OF_THESE_STRING;
    }
    
    /** Called when an item is selected. */
    @Override
    protected void onItemSelected(int listItemIndex) {
    	Log.d(TAG, "Item selected [" + listItemIndex + "]: " + mListItems[listItemIndex]);
    	
    	// if user selected "None of these"
    	if (listItemIndex == mAddressList.size()) {
    		setResult(RESULT_CANCELED);
	    	finish();
    	
    	// otherwise user selects a valid address
    	} else {
	    	Intent intent = new Intent(PossibleAddresses.this, RouteInput.class);
	    	intent.putExtra(INDEX_EXTRA, listItemIndex); // TODO: LEVI! CHANGE THIS!!!!!!!!!!!!
	    	setResult(RESULT_OK, intent);
	    	finish();
    	}
    }
}
