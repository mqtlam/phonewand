/**
 * @author Michael Q. Lam (mqtlam@cs.washington.edu)
 * @author Levi Lindsey (levisl@cs.washington.edu)
 * @author Chris Raastad (craastad@cs.washington.edu)
 * 
 * Designed to meet the requirements of the Winter 2011 UW course, 
 * CSE 481H: Accessibility Capstone
 * 
 * SlideRuleListActivity is an Activity that implements a slide rule-style list.
 * Extend this activity to initialize items and/or add additional functionality.
 */

package edu.uw.cse481h.phonewand;

import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SlideRuleListActivity extends PhoneWandActivity implements OnTouchListener{
	// -----Debugging Constants-----
	// toggle display of debugging log messages
	private static final String TAG = "SlideRuleListActivity";
	// log messages tag 
	private static final boolean D = false;
	
	// strings for next/previous buttons
	protected static final String PREVIOUS 		 = "Previous Items";
	protected static final String NEXT 			 = "Next Items";
	protected static final String PREVIOUS_TAG 	 = "Select to view the previous items in the list.";
	protected static final String NEXT_TAG 		 = "Select to view the next items in the list.";
	protected static final String BLANK 		 = "";
	
	// max number of elements on one screen in the list
	// TODO: eventually eliminate this constant by utilizing the screen size
	protected static final int NUM_OF_ELEMENTS_IN_LIST = 6;
	
	// references views in the layout
	protected TextView[] mViews;
	protected TextView mPrev;
	protected TextView mNext;

	/**
	 * Contains the list of items to present in the list.
	 * Extend this class to add custom list items from a database or map.
	 * 
	 * For example, say you have a class that extends SlideRuleListActivity.
	 * In the onCreate method, set the listItems variable to the items you want
	 * displayed on the screen before calling super.onCreate.
	 */
	protected String[] mListItems = new String[0];
	
	// last location hovered relative to screen
	// NB: mScrollPosition + mLastLoc - 1 = item index of listItem array
	protected int mLastLoc;
	
	// current scroll position (index of first listItem element on screen)
	// NB: mScrollPosition + mLastLoc - 1 = item index of listItem array
	protected int mScrollPosition;
	
	// necessary for double tap control
	//protected GestureDetector mDetector;
	
	/************************** Activity methods **************************/
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	if (D) Log.v(TAG, "+++ ON CREATE +++");
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.slide_rule_list);

        loadViews();
        
    }
    
    /************************** List creation/update **************************/
    
    /**
     * Initializes the screen by building the list and loading items into it.
     */
    private void loadViews() {
    	LinearLayout layout = (LinearLayout) findViewById(R.id.slayout);
    	mLastLoc = -1;
    	mScrollPosition = 0;
    	
    	// TODO: make this dependent on screen size
    	final int numElements = NUM_OF_ELEMENTS_IN_LIST;
    	mViews = new TextView[numElements];
    	
    	// set previous button
    	mPrev = new TextView(this);
    	addElementHelper(mPrev, BLANK, BLANK, layout);
    	
		// fill the middle buttons with list data
    	for (int i = 0; i < mViews.length; i++) {
    		String tag = BLANK;
    		if (i < mListItems.length)
    			tag = mListItems[i];
    		
    		mViews[i] = new TextView(this);
    		addElementHelper(mViews[i], tag, tag, layout);
    	}
    	
    	// set next button
    	mNext = new TextView(this);
    	if (mScrollPosition + mViews.length >= mListItems.length)
    		addElementHelper(mNext, BLANK, BLANK, layout);
    	else
    		addElementHelper(mNext, NEXT, NEXT_TAG, layout);
    }
    
    /**
     * Adds a list element to the screen.
     * 
     * @param v		TextView to add to the screen
     * @param label	label of item displayed on screen
     * @param tag	tag spoken when hovered over
     * @param l		layout of the screen
     */
    private void addElementHelper(	TextView v, final String label,
    								final String tag, LinearLayout l) {
    	// add the list element
		v.setText(label);
		v.setTag(tag);
		v.setMinLines(2);
		v.setMaxLines(2);
		v.setTextSize(24);
		v.setFocusableInTouchMode(true);
		v.setOnTouchListener(this);
		v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus){//&& ttsIsReady()) {
					ttsSpeak(tag, TextToSpeech.QUEUE_FLUSH, null);
				}
			}
		});
		l.addView(v);
		
		// add bottom border
		TextView border = new TextView(this);
		border.setBackgroundColor(Color.DKGRAY);
		border.setTextSize(0.5f);
		l.addView(border);
    }
    
    /**
     * Performs a scroll down the list. Updates the list.
     * 
     * @return <code>true</code> if scroll successful
     */
    private boolean scrollDown() {
    	if (D) Log.d(TAG, "scrolling down");
    	if (mScrollPosition + mViews.length >= mListItems.length)
    		return false;
    	
    	mScrollPosition += mViews.length;
    	scrollUpdate();
    	
    	return true;
    }
    
    /**
     * Performs a scroll up the list. Updates the list.
     * 
     * @return <code>true</code> if scroll successful
     */
    private boolean scrollUp() {
    	if (D) Log.d(TAG, "scrolling up");
    	if (mScrollPosition - mViews.length < 0)
    		return false;
    	
    	mScrollPosition -= mViews.length;
    	scrollUpdate();
    	
    	return true;
    }
    
    /**
     * Updates the screen items on a scroll.
     */
    private void scrollUpdate() {
    	if (D) Log.d(TAG, "scroll update");
    	for (int i = 0; i < mViews.length; i++) {
    		if (mScrollPosition + i < 0)
    			continue;
    		
    		final String tag = (mScrollPosition + i >= mListItems.length) ?
    								"" : mListItems[mScrollPosition + i];
    		changeElementHelper(mViews[i], tag, tag);
    	}
    	
    	final String nextString = (mScrollPosition + mViews.length >= mListItems.length) ? "" : NEXT;
    	final String nextTag = (mScrollPosition + mViews.length >= mListItems.length) ? "" : NEXT_TAG;
    	changeElementHelper(mNext, nextString, nextTag);
		
		final String prevString = (mScrollPosition - mViews.length < 0) ? "" : PREVIOUS;
		final String prevTag = (mScrollPosition - mViews.length < 0) ? "" : PREVIOUS_TAG;
		changeElementHelper(mPrev, prevString, prevTag);
    }
    
    /**
     * Changes an element's label and tag as well as the TTS.
     * 
     * @param v		TextView to add to the screen
     * @param label	label of item displayed on screen
     * @param tag	tag spoken when hovered over
     */
    private void changeElementHelper(TextView v, final String label, final String tag) {
		v.setText(label);
		v.setTag(tag);
		v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus){// && ttsIsReady()) {
					ttsSpeak(tag, TextToSpeech.QUEUE_FLUSH, null);
				}
			}
		});
    }
    
    /**
     * Refreshes the list by updating elements.
     * Also sets the cursor back to 0.
     */
    protected void refreshList() {
    	if (D) Log.d(TAG, "Refreshing list.");
    	mScrollPosition = 0;
    	scrollUpdate();
    }
    
    /************************** Touch Events and Double Tap **************************/
	
	/**
	 *	Called for touches that are not initial button touches 
	 **/
	@Override
	public boolean onTouchEvent(MotionEvent me) {
		mGestureDetector.onTouchEvent(me);
		
		if (D) Log.v(TAG, "onTouchEvent");
		
		if (me.getAction() == MotionEvent.ACTION_DOWN) {
			findKey(me);
		} 
		// Only process movement events that occur more than a
		// predetermined interval (in ms) apart to improve performance
		else if (me.getAction() == MotionEvent.ACTION_MOVE) {
			findKey(me);
		} 
		return false;
	}
    
	/**
	 * TODO: descriptive comment...
	 **/
	protected boolean doubleTap(){
		if (mLastLoc < 0) //|| !ttsIsReady())
			return false;
		
		// handle next and previous buttons
		if (mLastLoc == 0) {
			ttsSpeak("Selected " + PREVIOUS, TextToSpeech.QUEUE_FLUSH, null);
			scrollUp();
		} else if (mLastLoc == mViews.length + 1) {
			ttsSpeak("Selected " + NEXT, TextToSpeech.QUEUE_FLUSH, null);
			scrollDown();
		} else if (mLastLoc != -1) { // user selects an item
			int listItemIndex = mScrollPosition + mLastLoc - 1;
			
			ttsSpeak("Selected " + mListItems[listItemIndex], TextToSpeech.QUEUE_FLUSH, null);
			onItemSelected(listItemIndex);
		}
		
//		TODO: 	(optional) use third parameter of speak() to wait for lady to finish speaking,
//				which is triggered with onUtteranceCompleted()
//		mTts.setOnUtteranceCompletedListener(this);
//		utterance.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, view.getText().toString());
//		mTts.speak("Selected " + view.getText().toString() + ".", TextToSpeech.QUEUE_FLUSH, utterance);
		return true;
	}

	/** Locates the button on which the motion event occurred 
	 * and gives focus to that button.
	 **/
	private boolean findKey(MotionEvent me) {
		double y = me.getRawY();
		double x = me.getRawX();
		int[] loc = new int[2];
		int[] dim = new int[2];
		for (int i = 0; i < mViews.length + 2; i++) {
			TextView view;
			if (i == 0)
				view = mPrev;
			else if (i == mViews.length + 1)
				view = mNext;
			else
				view = mViews[i-1];
			
			view.getLocationOnScreen(loc);
			dim[0] = view.getWidth();
			dim[1] = view.getHeight();
			
			// If the motion event goes over the button, have the button request focus
			if (y <= (loc[1] + dim[1]) && x <= (loc[0] + dim[0])) {
				if (view.getTag().equals(BLANK)) {
					mLastLoc = -1;
				} else if (i != mLastLoc) {
					view.requestFocus();
					mLastLoc = i;
				}
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Called when a user selects an item.
	 * Override to add functionality.
	 * 
	 * @param listItemIndex	index of listItem array that user selected
	 **/
	protected void onItemSelected(int listItemIndex) {
		if (D) Log.d(TAG, "Item selected: " + listItemIndex);
	}
	
	// Orphan Method
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		// TODO Auto-generated method stub
		return false;
	}
}