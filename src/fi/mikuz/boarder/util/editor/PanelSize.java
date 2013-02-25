package fi.mikuz.boarder.util.editor;

import com.bugsense.trace.BugSenseHandler;

import android.os.Looper;
import android.util.Log;
import fi.mikuz.boarder.gui.BoardEditor;
import fi.mikuz.boarder.gui.BoardEditor.DrawingPanel;

/**
 * Attempts to define panel size.
 * 
 * @author Jan Mikael Lindl�f
 */
public class PanelSize {
	private static final String TAG = PanelSize.class.getSimpleName();
	
	private int width = -1;
	private int height = -1;

	public PanelSize(BoardEditor editor) {
		if (Looper.myLooper() != Looper.getMainLooper()) {
			// Wait for panel to initialize if called from non-UI thread.
			int i = 0;
			while (i < 400) {
				DrawingPanel panel = editor.getPanel();
				
				if (panel != null) {
					width = panel.getWidth();
					height = panel.getHeight();
					if (width > 0 && height > 0) {
						break;
					}
				}
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					Log.e(TAG, "Unable to sleep while initializing mPanel", e);
				}
				i++;
			}
		} else {
			// Waiting would suppress panel initialization in UI thread.
			Log.w(TAG, "Resolving panel size in UI thread");
			DrawingPanel panel = editor.getPanel();
			width = panel.getWidth();
			height = panel.getHeight();
		}

		if (width <= 0 || height <= 0) {
			Exception e = new IllegalStateException("Unable to find real panel size");
			Log.e(TAG, "Unable to find real panel size", e);
			BugSenseHandler.sendException(e);
		}
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}
}