/**
 * @author Michael Q. Lam (mqtlam@cs.washington.edu)
 * @author Levi Lindsey (levisl@cs.washington.edu)
 * @author Chris Raastad (craastad@cs.washington.edu)
 * 
 * Designed to meet the requirements of the Winter 2011 UW course, 
 * CSE 481H: Accessibility Capstone
 * 
 * TTSActivity is an Activity that initializes TTS.
 */

package edu.uw.cse481h.phonewand;

import java.util.HashMap;
import java.util.Locale;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.util.Log;

public class TTSActivity extends Activity implements OnInitListener, OnUtteranceCompletedListener {
	// used for Log
	protected static final String TAG = "TTSActivity";

	// debugging, whether to show logs
	private static final boolean D = true;
	
	// used for TTS initialization
	protected static final int CHECK_TTS_AVAILABLE = 22367; // arbitrary number
	
	// main text to speech object
	protected TextToSpeech mTts;
	protected boolean mTTSReady;
	
	// used for utterances
	protected HashMap<String, String> mUtterance = new HashMap<String, String>();
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	if(D) Log.v(TAG, "--- ON CREATE ---");
    	super.onCreate(savedInstanceState);
        
        // initialize text to speech and views
    	Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, CHECK_TTS_AVAILABLE);
    }
    
    /** Called when the system removes this Activity. */
	@Override
	public void onDestroy() {
		if (D) Log.v(TAG, "--- ON DESTROY ---");
		
		if (mTTSReady) {
			mTts.shutdown();
			mTTSReady = false;
		}
		
		super.onDestroy();
	}
    
    /**
     * Called when activity exits.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	
    	// check if TTS is available
    	if (requestCode == CHECK_TTS_AVAILABLE) {
    		mTTSReady = false;
    		if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
    			mTts = new TextToSpeech(this, this);
    		} else {
    			// missing data, install it
    			Intent installIntent = new Intent();
    			installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
    			startActivity(installIntent);
    		}
    	}
    }
    
    /**
     * Called to signal completion of TTS setup.
     */
	@Override
	public void onInit(int status) {
		mTts.setLanguage(Locale.US);
		mTTSReady = true;
		
		if (D) Log.d(TAG, "TTS initialized");
	}
	
	/* Called when utterance is completed. */
	@Override
	public void onUtteranceCompleted(String utteranceId) {
		if (D) Log.d(TAG, "UtteranceCompleted: " + utteranceId);

//		TODO: 	(optional) let the lady finish speaking before literally
//				performing a scroll up, scroll down or item selection.
//				this method is called when an utterance is finished.
//
//		if (utteranceId.equalsIgnoreCase(PREV)) {
//			scrollUp();
//		} else if (utteranceId.equalsIgnoreCase(NEXT)) {
//			scrollDown();
//		}
	}
	
	/************************** Convenience Methods **************************/
	
	public boolean ttsIsReady() {
		return mTTSReady;
	}
	
	public void ttsSpeak(String text, int queueMode, HashMap<String, String> params) {
		if (ttsIsReady())
			mTts.speak(text, queueMode, params);
	}
}
