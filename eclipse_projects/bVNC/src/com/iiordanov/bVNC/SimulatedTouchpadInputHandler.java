package com.iiordanov.bVNC;

import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.iiordanov.android.bc.BCFactory;

public class SimulatedTouchpadInputHandler extends AbstractGestureInputHandler {
	static final String TAG = "SimulatedTouchpadInputHandler";
	
	static final String TOUCHPAD_MODE = "TOUCHPAD_MODE";
	
	/**
	 * In drag mode (entered with long press) you process mouse events
	 * without sending them through the gesture detector
	 */
	private boolean dragMode;
	
	float dragX, dragY;
	
	/**
	 * In right-drag mode (entered when a right-click occurs) you process mouse events
	 * without sending them through the gesture detector, and only send the location of
	 * the pointer to the remote machine.
	 */
	private boolean rightDragMode = false;

	/**
	 * These variables hold the coordinates of the last double-tap down event.
	 */
	float doubleTapX, doubleTapY;
	
	private boolean secondPointerWasDown = false;
	private boolean thirdPointerWasDown = false;
	
	/**
	 * @param c
	 */
	SimulatedTouchpadInputHandler(VncCanvasActivity va, VncCanvas v) {
		super(va, v);
		activity = va;
		vncCanvas = v;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.iiordanov.bVNC.AbstractInputHandler#getHandlerDescription()
	 */
	@Override
	public CharSequence getHandlerDescription() {
		return vncCanvas.getResources().getString(
				R.string.input_mode_touchpad);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.iiordanov.bVNC.AbstractInputHandler#getName()
	 */
	@Override
	public String getName() {
		return TOUCHPAD_MODE;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.iiordanov.bVNC.VncCanvasActivity.ZoomInputHandler#onKeyDown(int,
	 *      android.view.KeyEvent)
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent evt) {
		return keyHandler.onKeyDown(keyCode, evt);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.iiordanov.bVNC.VncCanvasActivity.ZoomInputHandler#onKeyUp(int,
	 *      android.view.KeyEvent)
	 */
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent evt) {
		return keyHandler.onKeyUp(keyCode, evt);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.iiordanov.bVNC.AbstractInputHandler#onTrackballEvent(android.view.MotionEvent)
	 */
	@Override
	public boolean onTrackballEvent(MotionEvent evt) {
		return trackballMouse(evt);
	}

	/**
	 * scale down delta when it is small. This will allow finer control
	 * when user is making a small movement on touch screen.
	 * Scale up delta when delta is big. This allows fast mouse movement when
	 * user is flinging.
	 * @param deltaX
	 * @return
	 */
	private float fineCtrlScale(float delta) {
		float sign = (delta>0) ? 1 : -1;
		delta = Math.abs(delta);
		if (delta>=1 && delta <=3) {
			delta = 1;
		}else if (delta <= 10) {
			delta *= 0.34;
		} else if (delta <= 30 ) {
			delta *= delta/30;
		} else if (delta <= 90) {
			delta *=  (delta/30);
		} else {
			delta *= 3.0;
		}
		return sign * delta;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.view.GestureDetector.SimpleOnGestureListener#onLongPress(android.view.MotionEvent)
	 */
	@Override
	public void onLongPress(MotionEvent e) {
		
		// If we've performed a right/middle-click and the gesture is not over yet, do not start drag mode.
		if (thirdPointerWasDown || secondPointerWasDown)
			return;
		
		activity.showZoomer(true);
		BCFactory.getInstance().getBCHaptic().performLongPressHaptic(vncCanvas);			
		dragMode = true;
		dragX = e.getX();
		dragY = e.getY();
		
		remoteMouseStayPut(e);
		vncCanvas.getPointer().processPointerEvent(e, true);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.view.GestureDetector.SimpleOnGestureListener#onScroll(android.view.MotionEvent,
	 *      android.view.MotionEvent, float, float)
	 */
	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2,
			float distanceX, float distanceY) {

		// onScroll called while scaling/swiping gesture is in effect. We ignore the event and pretend it was
		// consumed. This prevents the mouse pointer from flailing around while we are scaling.
		// Also, if one releases one finger slightly earlier than the other when scaling, it causes Android 
		// to stick a spiteful onScroll with a MASSIVE delta here. 
		// This would cause the mouse pointer to jump to another place suddenly.
		// Hence, we ignore onScroll after scaling until we lift all pointers up.
		boolean twoFingers = false;
		if (e1 != null)
			twoFingers = (e1.getPointerCount() > 1);
		if (e2 != null)
			twoFingers = twoFingers || (e2.getPointerCount() > 1);

		if (twoFingers||inSwiping||inScaling||scalingJustFinished)
			return true;

		activity.showZoomer(true);

		// Compute the relative movement offset on the remote screen.
		float deltaX = -distanceX *vncCanvas.getScale();
		float deltaY = -distanceY *vncCanvas.getScale();
		deltaX = fineCtrlScale(deltaX);
		deltaY = fineCtrlScale(deltaY);

		// Compute the absolute new mouse position on the remote site.
		float newRemoteX = vncCanvas.pointer.mouseX + deltaX;
		float newRemoteY = vncCanvas.pointer.mouseY + deltaY;

		if (dragMode) {
			if (e2.getAction() == MotionEvent.ACTION_UP)
				dragMode = false;

			dragX = e2.getX();
			dragY = e2.getY();
			e2.setLocation(newRemoteX, newRemoteY);
			vncCanvas.getPointer().processPointerEvent(e2, true);
		} else {
			e2.setLocation(newRemoteX, newRemoteY);
			vncCanvas.getPointer().processPointerEvent(e2, false);
		}
		vncCanvas.inScrolling = true;
		return true;
	}

	/**
	 * Modify the event so that the mouse goes where we specify.
	 * @param e
	 */
	private void remoteMouseSetCoordinates(MotionEvent e, float x, float y) {
		//Log.i(TAG, "Setting pointer location in remoteMouseSetCoordinates");
		e.setLocation(x, y);
	}

	/**
	 * Handles actions performed by a mouse.
	 * @param e touch or generic motion event
	 * @return
	 */
	private boolean handleMouseActions (MotionEvent e) {
        final int action     = e.getActionMasked();
		final int bstate     = e.getButtonState();

		switch (action) {
		// If a mouse button was pressed.
		case MotionEvent.ACTION_DOWN:
			switch (bstate) {
			case MotionEvent.BUTTON_PRIMARY:
				changeTouchCoordinatesToFullFrame(e);
				return vncCanvas.getPointer().processPointerEvent(e, true);
			case MotionEvent.BUTTON_SECONDARY:
				changeTouchCoordinatesToFullFrame(e);
	        	return vncCanvas.getPointer().processPointerEvent(e, true, true, false);		
			case MotionEvent.BUTTON_TERTIARY:
				changeTouchCoordinatesToFullFrame(e);
	        	return vncCanvas.getPointer().processPointerEvent(e, true, false, true);
			}
			break;
		// If a mouse button was released.
		case MotionEvent.ACTION_UP:
			switch (bstate) {
			case MotionEvent.BUTTON_PRIMARY:
			case MotionEvent.BUTTON_SECONDARY:
			case MotionEvent.BUTTON_TERTIARY:
				changeTouchCoordinatesToFullFrame(e);
				return vncCanvas.getPointer().processPointerEvent(e, false);
			}
			break;
		// If the mouse was moved between button down and button up.
		case MotionEvent.ACTION_MOVE:
			switch (bstate) {
			case MotionEvent.BUTTON_PRIMARY:
				changeTouchCoordinatesToFullFrame(e);
				return vncCanvas.getPointer().processPointerEvent(e, true);
			case MotionEvent.BUTTON_SECONDARY:
				changeTouchCoordinatesToFullFrame(e);
	        	return vncCanvas.getPointer().processPointerEvent(e, true, true, false);		
			case MotionEvent.BUTTON_TERTIARY:
				changeTouchCoordinatesToFullFrame(e);
	        	return vncCanvas.getPointer().processPointerEvent(e, true, false, true);
			}
		// If the mouse wheel was scrolled.
		case MotionEvent.ACTION_SCROLL:
			float vscroll = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
			float hscroll = e.getAxisValue(MotionEvent.AXIS_HSCROLL);
			int swipeSpeed = 0, direction = 0;
			if (vscroll < 0) {
				swipeSpeed = (int)(-1*vscroll);
				direction = 1;
			} else if (vscroll > 0) {
				swipeSpeed = (int)vscroll;
				direction = 0;
			} else if (hscroll < 0) {
				swipeSpeed = (int)(-1*hscroll);
				direction = 3;
			} else if (hscroll > 0) {
				swipeSpeed = (int)hscroll;
				direction = 2;				
			} else
				return false;
				
			changeTouchCoordinatesToFullFrame(e);   	
        	int numEvents = 0;
        	while (numEvents < swipeSpeed) {
        		vncCanvas.getPointer().processPointerEvent(e, false, false, false, true, direction);
        		vncCanvas.getPointer().processPointerEvent(e, false);
        		numEvents++;
        	}
			break;
		// If the mouse was moved.
		case MotionEvent.ACTION_HOVER_MOVE:
			changeTouchCoordinatesToFullFrame(e);
			return vncCanvas.getPointer().processPointerEvent(e, false, false, false);
		}
		return false;
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see com.iiordanov.bVNC.AbstractGestureInputHandler#onTouchEvent(android.view.MotionEvent)
	 */
	@Override
	public boolean onTouchEvent(MotionEvent e) {
		final int pointerCnt   = e.getPointerCount();
        final int action       = e.getActionMasked();
        final int index        = e.getActionIndex();
        final int pointerID    = e.getPointerId(index);

        if (android.os.Build.VERSION.SDK_INT >= 14) {
	        // Handle actions performed by a (e.g. USB or bluetooth) mouse.
	        if (handleMouseActions (e))
	        	return true;
        }

        // We have put down first pointer on the screen, so we can reset the state of all click-state variables.
        if (pointerID == 0 && action == MotionEvent.ACTION_DOWN) {
        	// Permit sending mouse-down event on long-tap again.
        	secondPointerWasDown = false;
        	// Permit right-clicking again.
        	thirdPointerWasDown = false;
        	// Cancel any effect of scaling having "just finished" (e.g. ignoring scrolling).
			scalingJustFinished = false;
        } else if (pointerID == 0 && action == MotionEvent.ACTION_UP)
        	vncCanvas.inScrolling = false;

    	// Here we only prepare for the second click, which we perform on ACTION_POINTER_UP for pointerID==1.
        if (pointerID == 1 && action == MotionEvent.ACTION_POINTER_DOWN) {
        	// If drag mode is on then stop it and indicate button was released.
        	if (dragMode) {
				dragMode = false;
	        	remoteMouseStayPut(e);
				vncCanvas.getPointer().processPointerEvent(e, false, false, false);       		
        	}
        	// Permit sending mouse-down event on long-tap again.
        	secondPointerWasDown = true;
        	// Permit right-clicking again.
        	thirdPointerWasDown = false;
        }

        // Send scroll up/down events if swiping is happening.
        if (inSwiping) {
        	// Save the coordinates and restore them afterward.
        	float x = e.getX();
        	float y = e.getY();
        	remoteMouseStayPut(e);
        	int numEvents = 0;
        	while (numEvents < swipeSpeed) {
        		if        (twoFingerSwipeUp)   {
        			vncCanvas.getPointer().processPointerEvent(e, false, false, false, true, 0);
        			vncCanvas.getPointer().processPointerEvent(e, false);
        		} else if (twoFingerSwipeDown) {
        			vncCanvas.getPointer().processPointerEvent(e, false, false, false, true, 1);
        			vncCanvas.getPointer().processPointerEvent(e, false);
        		} else if (twoFingerSwipeLeft)   {
        			vncCanvas.getPointer().processPointerEvent(e, false, false, false, true, 2);
        			vncCanvas.getPointer().processPointerEvent(e, false);
        		} else if (twoFingerSwipeRight) {
        			vncCanvas.getPointer().processPointerEvent(e, false, false, false, true, 3);
        			vncCanvas.getPointer().processPointerEvent(e, false);
        		}
        		numEvents++;
        	}
        	// Restore the coordinates so that onScale doesn't get all muddled up.
    		remoteMouseSetCoordinates(e, x, y);
    		return super.onTouchEvent(e);

    	// If user taps with a second finger while first finger is down, then we treat this as
        // a right mouse click, but we only effect the click when the second pointer goes up.
        // If the user taps with a second and third finger while the first 
        // finger is down, we treat it as a middle mouse click. We ignore the lifting of the
        // second index when the third index has gone down (using the thirdPointerWasDown variable)
        // to prevent inadvertent right-clicks when a middle click has been performed.
        } else if (!inScaling && !thirdPointerWasDown && pointerID == 1 && action == MotionEvent.ACTION_POINTER_UP) {     	
        	remoteMouseStayPut(e);
        	// We offset the click down and up by one pixel to workaround for Firefox (and any other application)
        	// where if the two events are in the same spot the context menu may disappear.
        	vncCanvas.getPointer().processPointerEvent(e, true, true, false);
			SystemClock.sleep(50);
        	// Offset the pointer by one pixel to prevent accidental click and disappearing context menu.
        	remoteMouseSetCoordinates(e, vncCanvas.pointer.mouseX - 1.f, vncCanvas.pointer.mouseY);
        	vncCanvas.getPointer().processPointerEvent(e, false, true, false);
        	// Put the pointer where it was before the 1px offset.
        	remoteMouseSetCoordinates(e, vncCanvas.pointer.mouseX + 1.f, vncCanvas.pointer.mouseY);
        	vncCanvas.pointer.mouseX = vncCanvas.pointer.mouseX + 1;
			// Pass this event on to the parent class in order to end scaling as it was certainly
			// started when the second pointer went down.
			return super.onTouchEvent(e);
        } else if (!inScaling && pointerID == 2 && action == MotionEvent.ACTION_POINTER_DOWN) {
        	// This boolean prevents the right-click from firing simultaneously as a middle button click.
        	thirdPointerWasDown = true;
        	remoteMouseStayPut(e);
        	vncCanvas.getPointer().processPointerEvent(e, true, false, true);
			SystemClock.sleep(50);
			return vncCanvas.getPointer().processPointerEvent(e, false, false, true);			
		} else if (pointerCnt == 1 && pointerID == 0 && dragMode) {	

        	// Compute the relative movement offset on the remote screen.
			float deltaX = (e.getX() - dragX) *vncCanvas.getScale();
			float deltaY = (e.getY() - dragY) *vncCanvas.getScale();
			dragX = e.getX();
			dragY = e.getY();
			deltaX = fineCtrlScale(deltaX);
			deltaY = fineCtrlScale(deltaY);

			// Compute the absolute new mouse position on the remote site.
			float newRemoteX = vncCanvas.pointer.mouseX + deltaX;
			float newRemoteY = vncCanvas.pointer.mouseY + deltaY;
			
			e.setLocation(newRemoteX, newRemoteY);

			if (action == MotionEvent.ACTION_UP) {
				dragMode = false;
				return vncCanvas.getPointer().processPointerEvent(e, false, false, false);
			}
			return vncCanvas.getPointer().processPointerEvent(e, true, false, false);
		} else
			return super.onTouchEvent(e);
	}		
	
	/**
	 * Modify the event so that it does not move the mouse on the
	 * remote server.
	 * @param e
	 */
	private void remoteMouseStayPut(MotionEvent e) {
		//Log.i(TAG, "Setting pointer location in remoteMouseStayPut");
		e.setLocation(vncCanvas.pointer.mouseX, vncCanvas.pointer.mouseY);	
	}
	
	/*
	 * (non-Javadoc)
	 * confirmed single tap: do a single mouse click on remote without moving the mouse.
	 * @see android.view.GestureDetector.SimpleOnGestureListener#onSingleTapConfirmed(android.view.MotionEvent)
	 */
	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		activity.showZoomer(true);
		remoteMouseStayPut(e);
		vncCanvas.getPointer().processPointerEvent(e, true);
		SystemClock.sleep(50);
		return vncCanvas.getPointer().processPointerEvent(e, false);
	}

	/*
	 * (non-Javadoc)
	 * double tap: do two  left mouse right mouse clicks on remote without moving the mouse.
	 * @see android.view.GestureDetector.SimpleOnGestureListener#onDoubleTap(android.view.MotionEvent)
	 */
	@Override
	public boolean onDoubleTap(MotionEvent e) {
		remoteMouseStayPut(e);
		vncCanvas.getPointer().processPointerEvent(e, true);
		SystemClock.sleep(50);
		vncCanvas.getPointer().processPointerEvent(e, false);
		SystemClock.sleep(50);
		vncCanvas.getPointer().processPointerEvent(e, true);
		SystemClock.sleep(50);
		return vncCanvas.getPointer().processPointerEvent(e, false);
	}
	
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see android.view.GestureDetector.SimpleOnGestureListener#onDown(android.view.MotionEvent)
	 */
	@Override
	public boolean onDown(MotionEvent e) {
		activity.stopPanner();
		return true;
	}
}