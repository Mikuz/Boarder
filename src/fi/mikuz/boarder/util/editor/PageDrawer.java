/* ========================================================================= *
 * Boarder                                                                   *
 * http://boarder.mikuz.org/                                                 *
 * ========================================================================= *
 * Copyright (C) 2013 Boarder                                                *
 *                                                                           *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 * ========================================================================= */

package fi.mikuz.boarder.util.editor;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

import org.acra.ACRA;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import fi.mikuz.boarder.component.soundboard.GraphicalSound;
import fi.mikuz.boarder.component.soundboard.GraphicalSoundboard;
import fi.mikuz.boarder.gui.SoundboardMenu;
import fi.mikuz.boarder.util.SoundPlayerControl;
import fi.mikuz.boarder.util.editor.FadingPage.FadeDirection;
import fi.mikuz.boarder.util.editor.FadingPage.FadeState;

/**
 * Draws pages and page related animations.
 */
public class PageDrawer {
	
	public enum SwipingDirection {NO_DIRECTION, LEFT, RIGHT, NO_ANIMATION}
	
	public static final String TAG = PageDrawer.class.getSimpleName();
	
	private Context context;
	private Joystick joystick;
	
	private GraphicalSoundboard topGsb;
	private List<FadingPage> fadingPages;
	private boolean initialPage;
	
	private boolean initializingAnimation;
	
	private final static Bitmap.Config BITMAP_CONF = Bitmap.Config.ARGB_8888;
	
	private Paint soundImagePaint;
	
	public PageDrawer(Context context) {
		this.context = context;
		this.joystick = null;
		
		this.topGsb = null;
		fadingPages = new ArrayList<FadingPage>();
		initialPage = true;
		
		initializingAnimation = false;
		
		soundImagePaint = new Paint();
        soundImagePaint.setColor(Color.WHITE);
        soundImagePaint.setAntiAlias(true);
        soundImagePaint.setTextAlign(Align.LEFT);
	}
	
	public void giveJoystick(Joystick joystick) {
		this.joystick = joystick;
	}
	
	public boolean needAnimationRefreshSpeed() {
		return (initializingAnimation || fadingPages.size() > 0);
	}
	
	public void startInitializingAnimation() {
		initializingAnimation = true;
	}
	
	
	public void switchPage(GraphicalSoundboard newGsb, SwipingDirection direction) {
		boolean initialPage = this.initialPage;
		this.initialPage = false;
		
		GraphicalSoundboard lastGsb = topGsb;
		topGsb = newGsb;

		if (direction != SwipingDirection.NO_ANIMATION) {
			Bitmap newPageDrawCache = null;
			boolean lastPageAlreadyFading = false;

			for (FadingPage listedPage : fadingPages) {
				if (listedPage.getGsb().getId() == newGsb.getId()) {
					newPageDrawCache = listedPage.getDrawCache();
				} else if (listedPage.getGsb().getId() == lastGsb.getId()) {
					// Last page is already fading. Letting it to fade out  after fading in.
					listedPage.fadeOutWhenFinished();
					if (direction == SwipingDirection.LEFT) {
						listedPage.setFadeDirection(FadeDirection.LEFT);
					} else if (direction == SwipingDirection.RIGHT) {
						listedPage.setFadeDirection(FadeDirection.RIGHT);
					}
					lastPageAlreadyFading = true;
				}
			}
			
			if (fadingPages.size() > 3) {
				try {
					fadingPages.remove(fadingPages.size()-1);
					Log.w(TAG, "Removed last fading page because of flood");
				} catch (IndexOutOfBoundsException e) {}
			}
			
			if (!initialPage && !lastPageAlreadyFading) {
				Bitmap lastPageDrawCache = genPageDrawCache(lastGsb, null);
				FadeDirection lastFadeDirection = FadeDirection.NO_DIRECTION;
				if (direction == SwipingDirection.LEFT) {
					lastFadeDirection = FadeDirection.LEFT;
				} else if (direction == SwipingDirection.RIGHT) {
					lastFadeDirection = FadeDirection.RIGHT;
				}
				FadingPage lastFadingPage = new FadingPage(lastGsb, FadeState.FADING_OUT, lastFadeDirection);
				lastFadingPage.setDrawCache(lastPageDrawCache);
				fadingPages.add(lastFadingPage);
				GraphicalSoundboard.unloadImages(lastGsb);
			}
			
			FadeDirection newFadeDirection = FadeDirection.NO_DIRECTION;
			if (direction == SwipingDirection.LEFT) {
				newFadeDirection = FadeDirection.RIGHT;
			} else if (direction == SwipingDirection.RIGHT) {
				newFadeDirection = FadeDirection.LEFT;
			}
			FadingPage newFadingPage = new FadingPage(newGsb, FadeState.FADING_IN, newFadeDirection);
			if (newPageDrawCache != null) newFadingPage.setDrawCache(newPageDrawCache);
			fadingPages.add(newFadingPage);
		}
		
		initializingAnimation = false;
	}
	
	public Canvas drawSurface(Canvas canvas, GraphicalSound pressedSound, boolean editMode) {
		Paint paint = new Paint();
		canvas.drawColor(Color.BLACK);

		boolean topGsgFading = false;

		Iterator<FadingPage> iter = fadingPages.iterator();
		Rect screenRect = null;
		while (iter.hasNext()) {
			try {
				FadingPage listedPage = (FadingPage) iter.next();
	
				if (screenRect == null) {
					screenRect = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
				}
	
				listedPage.updateFadeProgress();
				if (listedPage.getFadeProgress() >= 100 || 
						listedPage.getFadeProgress() <= 0) {
					iter.remove();
				} else {
					Bitmap pageBitmap = listedPage.getDrawCache();
					if (pageBitmap == null) {
						pageBitmap = genPageDrawCache(listedPage.getGsb(), null);
						listedPage.setDrawCache(pageBitmap);
					}
	
					if (listedPage.getGsb() == topGsb) {
						topGsgFading = true;
					}
	
					float fadeProgress = (float) listedPage.getFadeProgress();
					float fadePercentage = fadeProgress/100;
					
					// Full distance means zero image size on fade 0
					float xFullDistance = canvas.getWidth()/2 * (1-fadePercentage);
					float yFullDistance = canvas.getHeight()/2 * (1-fadePercentage);
					
					// Image size is about 1/3 screen size on fade 0
					float xDistance = xFullDistance*7/10;
					float yDistance = yFullDistance*7/10;
					
					float directionEffect = canvas.getWidth()/4*3 * (1-fadePercentage);
					if (listedPage.getFadeDirection() == FadeDirection.LEFT) {
						directionEffect *= -1;
					} else if (listedPage.getFadeDirection() == FadeDirection.RIGHT) {
						directionEffect *= +1;
					} else {
						directionEffect = 0;
					}
					
					float xDistanceLeft = xDistance + directionEffect;
					float xDistanceRight = canvas.getWidth() - xDistance + directionEffect;
					
					RectF fadeRect = new RectF(xDistanceLeft, yDistance, 
							xDistanceRight, canvas.getHeight() - yDistance);
	
					int fadeAlpha = (int) (fadePercentage*255f);
					paint.setAlpha(fadeAlpha);
					canvas.drawBitmap(pageBitmap, screenRect, fadeRect, paint);
				}
			} catch(ConcurrentModificationException cme) {
				Log.w(TAG, "Fading page modification while iterating");
				break;
			}
		}

		if (!topGsgFading) {
			// Drawing directly to SurfaceView canvas is a lot faster.
			drawPage(canvas, topGsb, pressedSound, editMode);
		}

		return canvas;
	}
	
	private Bitmap genPageDrawCache(GraphicalSoundboard gsb, GraphicalSound pressedSound) {
		WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		
		Bitmap pageBitmap = Bitmap.createBitmap(size.x, size.y, BITMAP_CONF);
		Canvas pageCanvas = new Canvas(pageBitmap);
		drawPage(pageCanvas, gsb, pressedSound, false);
		
		return pageBitmap;
	}
	
	private Canvas drawPage(Canvas canvas, GraphicalSoundboard drawGsb, GraphicalSound pressedSound, boolean editMode) {
		
		canvas.drawColor(drawGsb.getBackgroundColor());
		
		if (drawGsb.getUseBackgroundImage() == true && drawGsb.getBackgroundImagePath() != null && drawGsb.getBackgroundImagePath().exists()) {
			RectF bitmapRect = new RectF();
			bitmapRect.set(drawGsb.getBackgroundX(), drawGsb.getBackgroundY(), 
					drawGsb.getBackgroundWidth() + drawGsb.getBackgroundX(), drawGsb.getBackgroundHeight() + drawGsb.getBackgroundY());
			
			Paint bgImage = new Paint();
			bgImage.setColor(drawGsb.getBackgroundColor());
			
			try {
				canvas.drawBitmap(drawGsb.getBackgroundImage(), null, bitmapRect, bgImage);
			} catch(NullPointerException npe) {
				Log.e(TAG, "Unable to draw image " + drawGsb.getBackgroundImagePath().getAbsolutePath());
				drawGsb.loadPlaceholderBackgroundImage(context);
			}
		}
		
		try {
			ArrayList<GraphicalSound> drawList = new ArrayList<GraphicalSound>();
			drawList.addAll(drawGsb.getSoundList());
			if (pressedSound != null) drawList.add(pressedSound);
			
			for (GraphicalSound sound : drawList) {
				Paint barPaint = new Paint();
				if (editMode) {
					barPaint.setColor(Color.argb(125, 255, 178, 102));
				} else {
					barPaint.setColor(sound.getNameFrameInnerColor());
				}
				
				String soundPath = sound.getPath().getAbsolutePath();
				if (soundPath.equals(SoundboardMenu.mTopBlackBarSoundFilePath)) {
					canvas.drawRect(0, 0, canvas.getWidth(), sound.getNameFrameY(), barPaint);
				} else if (soundPath.equals(SoundboardMenu.mBottomBlackBarSoundFilePath)) {
					canvas.drawRect(0, sound.getNameFrameY(), canvas.getWidth(), canvas.getHeight(), barPaint);
				} else if (soundPath.equals(SoundboardMenu.mLeftBlackBarSoundFilePath)) {
					canvas.drawRect(0, 0, sound.getNameFrameX(), canvas.getHeight(), barPaint);
				} else if (soundPath.equals(SoundboardMenu.mRightBlackBarSoundFilePath)) {
					canvas.drawRect(sound.getNameFrameX(), 0, canvas.getWidth(), canvas.getHeight(), barPaint);
				} else {
					if (sound.getHideImageOrText() != GraphicalSound.HIDE_TEXT) {
						float NAME_DRAWING_SCALE = SoundNameDrawing.NAME_DRAWING_SCALE;
						
						
						canvas.scale(1/NAME_DRAWING_SCALE, 1/NAME_DRAWING_SCALE);
						SoundNameDrawing soundNameDrawing = new SoundNameDrawing(sound);
						
						Paint nameTextPaint = soundNameDrawing.getBigCanvasNameTextPaint();
						Paint borderPaint = soundNameDrawing.getBorderPaint();
						Paint innerPaint = soundNameDrawing.getInnerPaint();
						
						RectF bigCanvasNameFrameRect = soundNameDrawing.getBigCanvasNameFrameRect();
						
						if (sound.getShowNameFrameInnerPaint() == true) {
					    	canvas.drawRoundRect(bigCanvasNameFrameRect, 2*NAME_DRAWING_SCALE, 2*NAME_DRAWING_SCALE, innerPaint);
					    }
						
						if (sound.getShowNameFrameBorderPaint()) {
							canvas.drawRoundRect(bigCanvasNameFrameRect, 2*NAME_DRAWING_SCALE, 2*NAME_DRAWING_SCALE, borderPaint);
						}
					    
						int i = 0;
					    for (String row : sound.getName().split("\n")) {
				    		canvas.drawText(row, (sound.getNameFrameX()+2)*NAME_DRAWING_SCALE, 
				    				sound.getNameFrameY()*NAME_DRAWING_SCALE+(i+1)*sound.getNameSize()*NAME_DRAWING_SCALE, nameTextPaint);
				    		i++;
					    }
					    canvas.scale(NAME_DRAWING_SCALE, NAME_DRAWING_SCALE);
					}
				    
				    if (sound.getHideImageOrText() != GraphicalSound.HIDE_IMAGE) {
					    RectF imageRect = new RectF();
					    imageRect.set(sound.getImageX(), 
								sound.getImageY(), 
								sound.getImageWidth() + sound.getImageX(), 
								sound.getImageHeight() + sound.getImageY());
						
					    try {
					    	if (SoundPlayerControl.isPlaying(sound.getPath()) && sound.getActiveImage(context) != null) {
					    		try {
					    			canvas.drawBitmap(sound.getActiveImage(context), null, imageRect, soundImagePaint);
					    		} catch(NullPointerException npe) {
					    			Log.e(TAG, "Unable to draw active image for sound " + sound.getName());
									sound.setDefaultActiveImage();
					    			canvas.drawBitmap(sound.getImage(context), null, imageRect, soundImagePaint);
					    		}
					    		
					    	} else {
					    		canvas.drawBitmap(sound.getImage(context), null, imageRect, soundImagePaint);
					    	}
						} catch(NullPointerException npe) {
							Log.e(TAG, "Unable to draw image for sound " + sound.getName());
							npe.printStackTrace();
							ACRA.getErrorReporter().handleException(npe);
							sound.setDefaultImage(context);
						}
				    }
				    
				    if (drawGsb.getAutoArrange() && sound == pressedSound) {
				    	int width = canvas.getWidth();
						int height = canvas.getHeight();
						
						Paint linePaint = new Paint();
						Paint outerLinePaint = new Paint(); {
						linePaint.setColor(Color.WHITE);
						outerLinePaint.setColor(Color.YELLOW);
						outerLinePaint.setStrokeWidth(3);
						}
						
				    	for (int i = 1; i < drawGsb.getAutoArrangeColumns(); i++) {
				    		float X = i*(width/drawGsb.getAutoArrangeColumns());
				    		canvas.drawLine(X, 0, X, height, outerLinePaint);
				    		canvas.drawLine(X, 0, X, height, linePaint);
				    	}
				    	for (int i = 1; i < drawGsb.getAutoArrangeRows(); i++) {
				    		float Y = i*(height/drawGsb.getAutoArrangeRows());
				    		canvas.drawLine(0, Y, width, Y, outerLinePaint);
				    		canvas.drawLine(0, Y, width, Y, linePaint);
				    	}
				    }
				}
			}
		} catch(ConcurrentModificationException cme) {
			Log.w(TAG, "Sound list modification while iterating");
		}
		
		if (joystick != null) {
			canvas.drawBitmap(Joystick.getJoystickImage(context), null, joystick.getJoystickImageRect(), soundImagePaint);
		}
		
		return canvas;
	}
}
