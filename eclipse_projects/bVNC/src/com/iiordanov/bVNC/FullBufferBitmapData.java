/**
 * Copyright (C) 2012 Iordan Iordanov
 * Copyright (c) 2010 Michael A. MacDonald
 */
package com.iiordanov.bVNC;

import java.io.IOException;
import java.util.Arrays;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.widget.ImageView;

/**
 * @author Michael A. MacDonald
 *
 */
class FullBufferBitmapData extends AbstractBitmapData {
	int xoffset;
	int yoffset;
	int dataWidth;
	int dataHeight;

	
	/**
	 * @author Michael A. MacDonald
	 *
	 */
	class Drawable extends AbstractBitmapDrawable {
		private final static String TAG = "Drawable";
		int drawWidth;
		int drawHeight; 
		int xo, yo;
		Paint paint;
		
		/**
		 * @param data
		 */
		public Drawable(AbstractBitmapData data) {
			super(data);
			paint = new Paint ();
		}

		/* (non-Javadoc)
		 * @see android.graphics.drawable.DrawableContainer#draw(android.graphics.Canvas)
		 */
		@Override
		public void draw(Canvas canvas) {
			// If we are currently (re)allocating bitmapPixels, do not attempt to draw.
			//if (data.bitmapPixels == null)
			//	return;
/*			
			if (vncCanvas.getScaleType() == ImageView.ScaleType.FIT_CENTER)
			{
				canvas.drawBitmap(data.bitmapPixels, 0, data.framebufferwidth, xoffset, yoffset, framebufferwidth, framebufferheight, false, null);				
			}
			else
			{
*/
				if (xoffset < 0)
					xo = 0;
				else if (xoffset >= data.framebufferwidth)
					return;
				else
					xo = xoffset;

				if (yoffset < 0)
					yo = 0;
				else if (yoffset >= data.framebufferheight)
					return;
				else
					yo = yoffset;
				/*
				if (scale == 1 || scale <= 0)
				{
				*/
					drawWidth = vncCanvas.getVisibleWidth();
					if (xo + drawWidth  >= data.framebufferwidth)
						drawWidth  = data.framebufferwidth  - xo - 1;
					drawHeight = vncCanvas.getVisibleHeight();
					if (yo + drawHeight >= data.framebufferheight)
						drawHeight = data.framebufferheight - yo - 1;
					
					try {
						canvas.drawBitmap(data.bitmapPixels, offset(xo, yo), data.framebufferwidth, 
											(float)xo, (float)yo, drawWidth, drawHeight, false, paint);
					} catch (Exception e) {
						Log.e (TAG, "Failed to draw bitmap: xo, yo/drawW, drawH: " + xo + ", " + yo + "/"
									+ drawWidth + ", " + drawHeight);
					}
				/*
				}
				else
				{
					int scalewidth = (int)(vncCanvas.getVisibleWidth() / scale + 1);
					if (scalewidth + xo > data.framebufferwidth)
						scalewidth = data.framebufferwidth - xo;
					int scaleheight = (int)(vncCanvas.getVisibleHeight() / scale + 1);
					if (scaleheight + yo > data.framebufferheight)
						scaleheight = data.framebufferheight - yo;
					canvas.drawBitmap(data.bitmapPixels, offset(xo, yo), data.framebufferwidth, xo, yo, scalewidth, scaleheight, false, null);				
				}
				*/
//			}
			if(data.vncCanvas.connection.getUseLocalCursor())
			{
				setCursorRect(data.vncCanvas.mouseX, data.vncCanvas.mouseY);
				clipRect.set(cursorRect);
				if (canvas.clipRect(cursorRect))
				{
					drawCursor(canvas);
				}
			}
		}
	}

	/**
	 * Multiply this times total number of pixels to get estimate of process size with all buffers plus
	 * safety factor
	 */
	static final int CAPACITY_MULTIPLIER = 7;
	
	/**
	 * @param p
	 * @param c
	 */
	public FullBufferBitmapData(RfbConnectable p, VncCanvas c, int capacity) {
		super(p, c);
		framebufferwidth=rfb.framebufferWidth();
		framebufferheight=rfb.framebufferHeight();
		bitmapwidth=framebufferwidth;
		bitmapheight=framebufferheight;
		dataWidth=framebufferwidth;
		dataHeight=framebufferheight;
		android.util.Log.i("FBBM", "bitmapsize = ("+bitmapwidth+","+bitmapheight+")");
		bitmapPixels = new int[framebufferwidth * framebufferheight];
	}

	/* (non-Javadoc)
	 * @see com.iiordanov.bVNC.AbstractBitmapData#copyRect(android.graphics.Rect, android.graphics.Rect, android.graphics.Paint)
	 */
	@Override
	public void copyRect(Rect src, Rect dest) {
		int srcOffset, dstOffset;
		int dstH = dest.height();
		int dstW = dest.width();
		
		int startSrcY, endSrcY, dstY, deltaY;
		if (src.top > dest.top) {
			startSrcY = src.top;
			endSrcY = src.top + dstH;
			dstY = dest.top;
			deltaY = +1;
		} else {
			startSrcY = src.top + dstH - 1;
			endSrcY = src.top - 1;
			dstY = dest.top + dstH - 1;
			deltaY = -1;
		}
		for (int y = startSrcY; y != endSrcY; y += deltaY) {
			srcOffset = offset(src.left, y);
			dstOffset = offset(dest.left, dstY);
			System.arraycopy(bitmapPixels, srcOffset, bitmapPixels, dstOffset, dstW);
			dstY += deltaY;
		}
		updateBitmap(dest.left, dest.top, dstW, dstH);
	}

	/* (non-Javadoc)
	 * @see com.iiordanov.bVNC.AbstractBitmapData#createDrawable()
	 */
	@Override
	AbstractBitmapDrawable createDrawable() {
		return new Drawable(this);
	}

	/* (non-Javadoc)
	 * @see com.iiordanov.bVNC.AbstractBitmapData#drawRect(int, int, int, int, android.graphics.Paint)
	 */
	@Override
	void drawRect(int x, int y, int w, int h, Paint paint) {
		int color = paint.getColor();
		int offset = offset(x,y);
		if (w > 10)
		{
			for (int j = 0; j < h; j++, offset += framebufferwidth)
			{
				Arrays.fill(bitmapPixels, offset, offset + w, color);
			}
		}
		else
		{
			for (int j = 0; j < h; j++, offset += framebufferwidth - w)
			{
				for (int k = 0; k < w; k++, offset++)
				{
					bitmapPixels[offset] = color;
				}
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.iiordanov.bVNC.AbstractBitmapData#offset(int, int)
	 */
	@Override
	public int offset(int x, int y) {
		return x + y * framebufferwidth;
	}

	/* (non-Javadoc)
	 * @see com.iiordanov.bVNC.AbstractBitmapData#scrollChanged(int, int)
	 */
	@Override
	void scrollChanged(int newx, int newy) {
		xoffset = newx;
		yoffset = newy;
	}

	/* (non-Javadoc)
	 * @see com.iiordanov.bVNC.AbstractBitmapData#frameBufferSizeChanged(RfbProto)
	 */
	@Override
	public void frameBufferSizeChanged () {
		framebufferwidth=rfb.framebufferWidth();
		framebufferheight=rfb.framebufferHeight();
		bitmapwidth=framebufferwidth;
		bitmapheight=framebufferheight;
		android.util.Log.i("FBBM", "bitmapsize changed = ("+bitmapwidth+","+bitmapheight+")");
		if ( dataWidth < framebufferwidth || dataHeight < framebufferheight ) {
			bitmapPixels = null;
			System.gc();
			dataWidth  = framebufferwidth;
			dataHeight = framebufferheight;
			bitmapPixels = new int[framebufferwidth * framebufferheight];
		}
	}
	
	/* (non-Javadoc)
	 * @see com.iiordanov.bVNC.AbstractBitmapData#syncScroll()
	 */
	@Override
	void syncScroll() {
		// Don't need to do anything here

	}

	/* (non-Javadoc)
	 * @see com.iiordanov.bVNC.AbstractBitmapData#updateBitmap(int, int, int, int)
	 */
	@Override
	public void updateBitmap(int x, int y, int w, int h) {
		int right  = x+w;
		int bottom = y+h;
		dirtyRect.union(x, y, right, bottom);
	}

	/* (non-Javadoc)
	 * @see com.iiordanov.bVNC.AbstractBitmapData#validDraw(int, int, int, int)
	 */
	@Override
	boolean validDraw(int x, int y, int w, int h) {
	    if (x + w > bitmapwidth || y + h > bitmapheight)
	    	return false;
	    return true;
	}

	/* (non-Javadoc)
	 * @see com.iiordanov.bVNC.AbstractBitmapData#writeFullUpdateRequest(boolean)
	 */
	@Override
	void writeFullUpdateRequest(boolean incremental) throws IOException {
		rfb.requestUpdate(incremental);
	}

}