/**
 * Copyright (c) 2012 Iordan Iordanov
 * Copyright (c) 2010 Michael A. MacDonald
 */
package com.iiordanov.bVNC;

import android.graphics.PointF;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Input handlers delegate to this class to handle keystrokes; this detects keystrokes
 * from the DPad and uses them to perform mouse actions; other keystrokes are passed to
 * VncCanvasActivity.defaultKeyXXXHandler
 * 
 * @author Iordan Iordanov
 * @author Michael A. MacDonald
 *
 */
class DPadMouseKeyHandler {
	private MouseMover mouseMover;
	private boolean mouseDown;
	private VncCanvasActivity activity;
	private VncCanvas canvas;
	private boolean isMoving;
	private boolean useDpadAsArrows = false;
	private boolean rotateDpad      = false;
	
	DPadMouseKeyHandler(VncCanvasActivity activity, Handler handler, boolean arrows, boolean rotate)
	{
		this.activity = activity;
		canvas = activity.vncCanvas;
		mouseMover = new MouseMover(activity, handler);
		useDpadAsArrows = arrows;
		rotateDpad      = rotate;
	}

	public boolean onKeyDown(int keyCode, KeyEvent evt) {
		int xv = 0;
		int yv = 0;
		boolean result = true;
		
		// If we are instructed to rotate the Dpad at 90 degrees, reassign KeyCodes.
		if (rotateDpad) {
			switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_LEFT:
				keyCode = KeyEvent.KEYCODE_DPAD_UP;
				break;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
				break;
			case KeyEvent.KEYCODE_DPAD_UP:
				keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
				break;
			case KeyEvent.KEYCODE_DPAD_DOWN:
				keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
				break;
			}
		}
		
		// If we are supposed to use the Dpad as arrows, pass the event to the default handler.
		if (useDpadAsArrows) {
			return activity.defaultKeyDownHandler(keyCode, evt);
		// Otherwise, control the mouse.
		} else {
			switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_LEFT:
				xv = -1;
				break;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				xv = 1;
				break;
			case KeyEvent.KEYCODE_DPAD_UP:
				yv = -1;
				break;
			case KeyEvent.KEYCODE_DPAD_DOWN:
				yv = 1;
				break;
			case KeyEvent.KEYCODE_DPAD_CENTER:
				if (!mouseDown) {
					mouseDown = true;
					result = canvas.processPointerEvent(canvas.mouseX, canvas.mouseY, MotionEvent.ACTION_DOWN, evt.getMetaState(), mouseDown, canvas.cameraButtonDown);
				}
				break;
			default:
				result = activity.defaultKeyDownHandler(keyCode, evt);
				break;
			}
		}
		if ((xv != 0 || yv != 0) && !isMoving) {
			final int x = xv;
			final int y = yv;
			isMoving = true;
			mouseMover.start(x, y, new Panner.VelocityUpdater() {

				/*
				 * (non-Javadoc)
				 * 
				 * @see com.iiordanov.bVNC.Panner.VelocityUpdater#updateVelocity(android.graphics.Point,
				 *      long)
				 */
				@Override
				public boolean updateVelocity(PointF p, long interval) {
					double scale = (1.2 * (double) interval / 50.0);
					if (Math.abs(p.x) < 500)
						p.x += (int) (scale * x);
					if (Math.abs(p.y) < 500)
						p.y += (int) (scale * y);
					return true;
				}

			});
			canvas.processPointerEvent(canvas.mouseX + x, canvas.mouseY + y, MotionEvent.ACTION_MOVE, evt.getMetaState(), mouseDown, canvas.cameraButtonDown);
			
		}
		return result;
	}

	public boolean onKeyUp(int keyCode, KeyEvent evt) {
		
		// Pass the event on if we are not controlling the mouse.
		if (useDpadAsArrows)
			return activity.defaultKeyUpHandler(keyCode, evt);
		
		boolean result = false;

		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_LEFT:
		case KeyEvent.KEYCODE_DPAD_RIGHT:
		case KeyEvent.KEYCODE_DPAD_UP:
		case KeyEvent.KEYCODE_DPAD_DOWN:
			mouseMover.stop();
			isMoving = false;
			result = true;
			break;
		case KeyEvent.KEYCODE_DPAD_CENTER:
			if (mouseDown) {
				mouseDown = false;
				result = canvas.processPointerEvent(canvas.mouseX, canvas.mouseY, MotionEvent.ACTION_UP, evt.getMetaState(), mouseDown, canvas.cameraButtonDown);
			} else {
				result = true;
			}
			break;
		default:
			result = activity.defaultKeyUpHandler(keyCode, evt);
			break;
		}
		return result;
	}
}