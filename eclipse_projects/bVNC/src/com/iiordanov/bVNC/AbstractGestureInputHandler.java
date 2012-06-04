/**
 * Copyright (C) 2012 Iordan Iordanov
 * Copyright (C) 2009 Michael A. MacDonald
 */
package com.iiordanov.bVNC;

import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;

import com.iiordanov.android.bc.BCFactory;
import com.iiordanov.android.bc.IBCScaleGestureDetector;
import com.iiordanov.android.bc.OnScaleGestureListener;

/**
 * An AbstractInputHandler that uses GestureDetector to detect standard gestures in touch events
 * 
 * @author Michael A. MacDonald
 */
abstract class AbstractGestureInputHandler extends GestureDetector.SimpleOnGestureListener implements AbstractInputHandler, OnScaleGestureListener {
	protected GestureDetector gestures;
	protected IBCScaleGestureDetector scaleGestures;
	private VncCanvasActivity activity;
	
	// This is the initial "focal point" of the gesture (between the two fingers).
	float xInitialFocus;
	float yInitialFocus;
	
	// This is the final "focal point" of the gesture (between the two fingers).
	float xCurrentFocus;
	float yCurrentFocus;
	float xPreviousFocus;
	float yPreviousFocus;
	
	// These variables record whether there was a two-finger swipe performed up or down.
	boolean inSwiping           = false;
	boolean twoFingerSwipeUp    = false;
	boolean twoFingerSwipeDown  = false;
	boolean twoFingerSwipeLeft  = false;
	boolean twoFingerSwipeRight = false;
	
	// These variables indicate whether the dpad should be used as arrow keys
	// and whether it should be rotated.
	boolean useDpadAsArrows    = false;
	boolean rotateDpad         = false;
	
	// The variable which indicates how many scroll events to send per swipe event.
	long    swipeSpeed = 1;
	// If swipe events are registered once every baseSwipeTime miliseconds, then
	// swipeSpeed will be one. If more often, swipe-speed goes up, if less, down.
	final long    baseSwipeTime = 600;
	// This is how far the swipe has to travel before a swipe event is generated.
	final float   baseSwipeDist = 40.f;
	
	boolean inScaling           = false;
	boolean scalingJustFinished = false;
	// The minimum distance a scale event has to traverse the FIRST time before scaling starts.
	final double  minScaleFactor = 0.1;
	
	private static final String TAG = "AbstractGestureInputHandler";
	
	AbstractGestureInputHandler(VncCanvasActivity c)
	{
		activity = c;
		gestures=BCFactory.getInstance().getBCGestureDetector().createGestureDetector(c, this);
		gestures.setOnDoubleTapListener(this);
		scaleGestures=BCFactory.getInstance().getScaleGestureDetector(c, this);
		useDpadAsArrows = activity.getUseDpadAsArrows();
		rotateDpad      = activity.getRotateDpad();
	}

	
	@Override
	public boolean onTouchEvent(MotionEvent evt) {
		scaleGestures.onTouchEvent(evt);
		return gestures.onTouchEvent(evt);
	}

	/* (non-Javadoc)
	 * @see com.iiordanov.android.bc.OnScaleGestureListener#onScale(com.iiordanov.android.bc.IBCScaleGestureDetector)
	 */
	@Override
	public boolean onScale(IBCScaleGestureDetector detector) {

		boolean consumed = true;

		// Get the current focus.
		xCurrentFocus = detector.getFocusX();
		yCurrentFocus = detector.getFocusY();
		
		// If we haven't started scaling yet, we check whether a swipe is being performed.
		// The arbitrary fudge factor may not be the best way to set a tolerance...
		if (!inScaling) {
			
			// Start swiping mode only after we've moved away from the initial focal point some distance.
			if (!inSwiping) {
				if ( (yCurrentFocus < (yInitialFocus - baseSwipeDist)) ||
			         (yCurrentFocus > (yInitialFocus + baseSwipeDist)) ||
			         (xCurrentFocus < (xInitialFocus - baseSwipeDist)) ||
			         (xCurrentFocus > (xInitialFocus + baseSwipeDist)) ) {
					inSwiping      = true;
					xPreviousFocus = xInitialFocus;
					yPreviousFocus = yInitialFocus;
				}
			}
			
			// If in swiping mode, indicate a swipe at regular intervals.
			if (inSwiping) {
				twoFingerSwipeUp    = false;					
				twoFingerSwipeDown  = false;
				twoFingerSwipeLeft  = false;					
				twoFingerSwipeRight = false;
				if        (yCurrentFocus < (yPreviousFocus - baseSwipeDist)) {
					twoFingerSwipeDown   = true;
					xPreviousFocus = xCurrentFocus;
					yPreviousFocus = yCurrentFocus;
				} else if (yCurrentFocus > (yPreviousFocus + baseSwipeDist)) {
					twoFingerSwipeUp     = true;
					xPreviousFocus = xCurrentFocus;
					yPreviousFocus = yCurrentFocus;
				} else if (xCurrentFocus < (xPreviousFocus - baseSwipeDist)) {
					twoFingerSwipeRight  = true;
					xPreviousFocus = xCurrentFocus;
					yPreviousFocus = yCurrentFocus;
				} else if (xCurrentFocus > (xPreviousFocus + baseSwipeDist)) {
					twoFingerSwipeLeft   = true;
					xPreviousFocus = xCurrentFocus;
					yPreviousFocus = yCurrentFocus;
				} else {
					consumed           = false;
				}
				// The faster we swipe, the faster we traverse the screen, and hence, the 
				// smaller the time-delta between consumed events. We take the reciprocal
				// obtain swipeSpeed. If it goes to zero, we set it to at least one.
				long elapsedTime = detector.getTimeDelta();
				swipeSpeed = baseSwipeTime/elapsedTime;
				if (swipeSpeed == 0) swipeSpeed = 1;
				//if (consumed)        Log.d(TAG,"Current swipe speed: " + swipeSpeed);
			}
		}
		
		if (!inSwiping) {
			if ( !inScaling && Math.abs(1.0 - detector.getScaleFactor()) < minScaleFactor ) {
				//Log.i(TAG,"Not scaling due to small scale factor.");
				consumed = false;
			}

			if (consumed)
			{
				inScaling = true;
				//Log.i(TAG,"Adjust scaling " + detector.getScaleFactor());
				if (activity.vncCanvas != null && activity.vncCanvas.scaling != null)
					activity.vncCanvas.scaling.adjust(activity, detector.getScaleFactor(), xCurrentFocus, yCurrentFocus);
			}
		}
		return consumed;
	}

	/* (non-Javadoc)
	 * @see com.iiordanov.android.bc.OnScaleGestureListener#onScaleBegin(com.iiordanov.android.bc.IBCScaleGestureDetector)
	 */
	@Override
	public boolean onScaleBegin(IBCScaleGestureDetector detector) {

		xInitialFocus = detector.getFocusX();
		yInitialFocus = detector.getFocusY();
		inScaling           = false;
		scalingJustFinished = false;
		// Cancel any swipes that may have been registered last time.
		inSwiping           = false;
		twoFingerSwipeUp    = false;					
		twoFingerSwipeDown  = false;
		twoFingerSwipeLeft  = false;					
		twoFingerSwipeRight = false;
		//Log.i(TAG,"scale begin ("+xInitialFocus+","+yInitialFocus+")");
		return true;
	}

	/* (non-Javadoc)
	 * @see com.iiordanov.android.bc.OnScaleGestureListener#onScaleEnd(com.iiordanov.android.bc.IBCScaleGestureDetector)
	 */
	@Override
	public void onScaleEnd(IBCScaleGestureDetector detector) {
		//Log.i(TAG,"scale end");
		inScaling = false;
		inSwiping = false;
		scalingJustFinished = true;
	}
}