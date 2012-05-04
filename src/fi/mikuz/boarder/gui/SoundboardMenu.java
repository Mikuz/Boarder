package fi.mikuz.boarder.gui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.bugsense.trace.BugSenseHandler;

import fi.mikuz.boarder.R;
import fi.mikuz.boarder.component.GlobalSettings;
import fi.mikuz.boarder.component.SoundPlayer;
import fi.mikuz.boarder.gui.internet.InternetMenu;
import fi.mikuz.boarder.util.ApiKeyLoader;
import fi.mikuz.boarder.util.BoardLocal;
import fi.mikuz.boarder.util.FileProcessor;
import fi.mikuz.boarder.util.IconUtils;
import fi.mikuz.boarder.util.SoundPlayerControl;
import fi.mikuz.boarder.util.dbadapter.BoardsDbAdapter;
import fi.mikuz.boarder.util.dbadapter.GlobalVariablesDbAdapter;
import fi.mikuz.boarder.util.dbadapter.LoginDbAdapter;

/**
 * 
 * @author Jan Mikael Lindl�f
 */
public class SoundboardMenu extends ListActivity {
	public static final String TAG = "SoundboardMenu";
	
	public static final boolean mDevelopmentMode = false; //FIXME for release
	
	public static Context context;
	
	public static final String EXTRA_KEY = "SoundboardMenu.boardToLaunch";
	public static final String EXTRA_HIDE_SOUNDBOARDMENU = "SoundboardMenu.hideSoundboardmenu";

	private static final int ACTIVITY_ADD = 0;
	private static final int ACTIVITY_EDIT = 1;
	
	private BoardsDbAdapter mDbHelper;
    private GlobalVariablesDbAdapter mGlobalVariableDbHelper;
	private int mMoveBoard = -1;
	
	Intent mIntent;
    String mAction;
	
	public static List<SoundPlayer> mSoundPlayerList = new ArrayList<SoundPlayer>();
	public static GlobalSettings mGlobalSettings;
	
	public static final File mBoarderDir = new File(Environment.getExternalStorageDirectory(), "boarder");
	public static final File mSbDir = new File(mBoarderDir, "boards");
	public static final File mBackupDir = new File(mBoarderDir, "backups");
	public static final File mDropboxCache = new File(mBoarderDir, "dropbox.cache");
	public static final File mShareDir = new File(mBoarderDir, "share");
	
	public static List<String> mFunctionSounds = new ArrayList<String>();
	public static final String mPauseSoundFilePath = "/Boarder/functions/pause";
	public static final String mTopBlackBarSoundFilePath = "/Boarder/functions/topBlackBar";
	public static final String mBottomBlackBarSoundFilePath = "/Boarder/functions/bottomBlackBar";
	public static final String mLeftBlackBarSoundFilePath = "/Boarder/functions/leftBlackBar";
	public static final String mRightBlackBarSoundFilePath = "/Boarder/functions/rightBlackBar";
	
	public static final String mExtLinkDonate = "https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=8S2QXHLP2G6YS";
	public static final String mExtLinkXDA = "http://forum.xda-developers.com/showthread.php?p=23224859#post23224859";
	public static final String mExtLinkMarket = "market://details?id=fi.mikuz.boarder";
	
	
	private Button mEnableNotificationButton;
	private Button mDisableNotificationButton;
    private final static int mNotificationId = 0; 
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	if (!mDevelopmentMode) BugSenseHandler.setup(this, ApiKeyLoader.loadBugSenseApiKey(this.getApplicationContext(), TAG));
        super.onCreate(savedInstanceState);
        
        mIntent = getIntent();
        mAction = mIntent.getAction();
        
        if (Intent.ACTION_CREATE_SHORTCUT.equals(mAction)) {
        	setTitle("Select shortcut target:");
        } else {
        	setTitle("Soundboard Menu");
        }
        
        String extra = mIntent.getStringExtra(EXTRA_KEY);
        if (extra != null) {
        	try {
        		Intent i = null;

        		for (File boardDirContent : new File(mSbDir, extra).listFiles()) {
        			if (boardDirContent.getName().equals("graphicalBoard")) {
        				i = new Intent(this, GraphicalSoundboardEditor.class);
        				break;
        			}
        		}
        		i.putExtra(BoardsDbAdapter.KEY_TITLE, extra);
        		startActivityForResult(i, ACTIVITY_EDIT);
        	} catch (NullPointerException e) {
        		mIntent = new Intent();
        		Log.e(SoundboardMenu.TAG, "Board not found", e);
        		Toast.makeText(this, "Board not found", Toast.LENGTH_LONG).show();
        	}
        }
        
	    this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
	    
	    mFunctionSounds.add(mPauseSoundFilePath);
	    mFunctionSounds.add(mTopBlackBarSoundFilePath);
	    mFunctionSounds.add(mBottomBlackBarSoundFilePath);
	    mFunctionSounds.add(mLeftBlackBarSoundFilePath);
	    mFunctionSounds.add(mRightBlackBarSoundFilePath);
	    
	    mDbHelper = new BoardsDbAdapter(this);
	    mDbHelper.open();
	    
	    mGlobalVariableDbHelper = new GlobalVariablesDbAdapter(this);
        mGlobalVariableDbHelper.open();
        mGlobalSettings = loadGlobalSettings();
	    
	    if (!mSbDir.exists()) {
			mSbDir.mkdirs();
		}
        
        refreshBoards();
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.soundboard_menu_list,
        		(ViewGroup) findViewById(R.id.soundboard_menu_root));
        mEnableNotificationButton = (Button) layout.findViewById(R.id.enableNotificationButton);
        mDisableNotificationButton = (Button) layout.findViewById(R.id.disableNotificationButton);

        mEnableNotificationButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		updateNotification(SoundboardMenu.this, "Soundboard menu", null);
        	}
        });

        mDisableNotificationButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        		notificationManager.cancel(SoundboardMenu.TAG, mNotificationId);
        	}
        });
        
        setContentView(layout);
        registerForContextMenu(getListView());
        context = this.getBaseContext();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        initializeBoardUpdateThread();
    }
    
    public static void updateNotification(Context context, String description, String boardToLaunch) {
    	Notification notification = new Notification(R.drawable.icon, "Notification enabled", System.currentTimeMillis());
    	final Intent notificationIntent = new Intent(context, SoundboardMenu.class);
    	notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    	
    	if (boardToLaunch != null) {
    		notificationIntent.putExtra(SoundboardMenu.EXTRA_KEY, boardToLaunch);
    	}
        
    	notification.setLatestEventInfo(context, "Boarder", description,
    			PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT));
    	notification.flags = Notification.FLAG_ONGOING_EVENT;
    	final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    	notificationManager.notify(SoundboardMenu.TAG, mNotificationId, notification);
    }
    
    private void initializeBoardUpdateThread() {
    	Thread t = new Thread() {
			public void run() {
				updateBoards();
				mHandler.post(mFinalizeBoardUpdating);
	        }
	    };
	    t.start();
    }
    
    private void updateBoards() {
    	
    	Log.v(TAG, "updating boardlist");
    	if (Environment.getExternalStorageDirectory().canRead() == false) {
    		Toast msg = Toast.makeText(this, "Can't read sdcard", Toast.LENGTH_LONG);
    		msg.show();
    	} else if (mSbDir.canRead()) {

    		Cursor boardsCursor = mDbHelper.fetchAllBoards();
    		startManagingCursor(boardsCursor);

    		File[] files = mSbDir.listFiles();

    		for(int i = 0; i < files.length; i++) {

    			if (files[i].isDirectory() && files[i].listFiles().length > 0) {

    				boolean databaseContainsFile = false;

    				if (boardsCursor.moveToFirst()) {
    					do {
    						String boardName = boardsCursor.getString(
    								boardsCursor.getColumnIndexOrThrow(BoardsDbAdapter.KEY_TITLE));

    						if (boardName.equals(files[i].getName().toString())) {
    							databaseContainsFile = true;
    							break;
    						}
    					} while (boardsCursor.moveToNext());
    				}
    				if (databaseContainsFile == false && !files[i].getName().toString().equals(mBackupDir.getName())) {
    					int boardLocaltion = 0;
    					try {
    						boardLocaltion = 
    								BoardLocal.testIfBoardIsLocal(files[i].getName().toString());
    					} catch (IOException e) {
    						e.printStackTrace();
    					}
    					mDbHelper.createBoard(files[i].getName().toString(), boardLocaltion);
    				}
    			} else if (files[i].equals(SoundboardMenu.mDropboxCache) || files[i].equals(mBackupDir)) {
    			} else {
    				files[i].delete();
    			}
    		}

    		if (boardsCursor.moveToFirst()) {
    			do {
    				String boardName = boardsCursor.getString(
    						boardsCursor.getColumnIndexOrThrow(BoardsDbAdapter.KEY_TITLE));

    				boolean boardInDbExists = false;
    				for (int i = 0; i < files.length; i++) {
    					if (files[i].getName().toString().equals(boardName)) {
    						boardInDbExists = true;
    						break;
    					}
    				}

    				if (boardInDbExists) {

    					int boardLocal = BoardsDbAdapter.LOCAL_RED;
    					try {
    						boardLocal = BoardLocal.testIfBoardIsLocal(boardName);
    					} catch (IOException e) {
    						e.printStackTrace();
    					}

    					mDbHelper.updateBoard(
    							boardsCursor.getInt(boardsCursor.getColumnIndexOrThrow(BoardsDbAdapter.KEY_ROWID)), 
    							boardsCursor.getString(boardsCursor.getColumnIndexOrThrow(BoardsDbAdapter.KEY_TITLE)), 
    							boardLocal);

    				} else {
    					mDbHelper.deleteBoard(boardsCursor.getInt(boardsCursor.getColumnIndexOrThrow(
    							BoardsDbAdapter.KEY_ROWID)));
    				}
    			} while (boardsCursor.moveToNext());
    		}
    	}

    }
    
    private void refreshBoards() {
    	setListAdapter(new BoardsCursorAdapter(mDbHelper.fetchAllBoards()));
    }
    
    final Runnable mFinalizeBoardUpdating = new Runnable() {
    	public void run() {
    		refreshBoards();
    	}
    };

    
    public class BoardsCursorAdapter extends SimpleCursorAdapter implements Filterable {

        private int layout;

        public BoardsCursorAdapter (Cursor c) {
            super(SoundboardMenu.this, R.layout.soundboard_menu_row, c, 
            		new String[]{BoardsDbAdapter.KEY_ROWID}, new int[]{R.layout.soundboard_menu_row});
            startManagingCursor(c);
            this.layout = R.layout.soundboard_menu_row;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
        	startManagingCursor(cursor);

            Cursor c = getCursor();
            startManagingCursor(c);

            final LayoutInflater inflater = LayoutInflater.from(context);
            View v = inflater.inflate(layout, parent, false);

            String title = c.getString(c.getColumnIndex(BoardsDbAdapter.KEY_TITLE));
            int local = c.getInt(c.getColumnIndex(BoardsDbAdapter.KEY_LOCAL));
            
            TextView title_text = (TextView) v.findViewById(R.id.soundboardName);
            if (title_text != null) {
                title_text.setText(title);
            }
            
            if (local == BoardsDbAdapter.LOCAL_GREEN) {
            	title_text.setTextColor(Color.parseColor("#A2F600"));
			} else if (local == BoardsDbAdapter.LOCAL_YELLOW) {
				title_text.setTextColor(Color.YELLOW);
			} else if (local == BoardsDbAdapter.LOCAL_WHITE) {
				title_text.setTextColor(Color.parseColor("#F1FFFF"));
			} else if (local == BoardsDbAdapter.LOCAL_RED) {
				title_text.setTextColor(Color.RED);
			} else if (local == BoardsDbAdapter.LOCAL_ORANGE) {
				title_text.setTextColor(Color.parseColor("#FF6600"));
			}
            
            File icon = new File(mSbDir, title + "/icon.png");
            if (icon.exists()) {
	            final ImageView title_icon = (ImageView) v.findViewById(R.id.soundboardIcon);
				Bitmap bitmap = BitmapFactory.decodeFile(icon.getAbsolutePath());
				int viewSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) 48, getResources().getDisplayMetrics());
            	
            	title_icon.setAdjustViewBounds(true);
            	title_icon.setMaxHeight(viewSize);
            	title_icon.setMaxWidth(viewSize);
            	title_icon.setMinimumHeight(viewSize);
            	title_icon.setMinimumWidth(viewSize);
            	title_icon.setScaleType(ImageView.ScaleType.FIT_XY);
            	title_icon.setImageBitmap(bitmap);
            }

            return v;
        }

        @Override
        public void bindView(View v, Context context, Cursor c) {
        	startManagingCursor(c);

        	String title = c.getString(c.getColumnIndex(BoardsDbAdapter.KEY_TITLE));
            int local = c.getInt(c.getColumnIndex(BoardsDbAdapter.KEY_LOCAL));
            
            TextView title_text = (TextView) v.findViewById(R.id.soundboardName);
            if (title_text != null) {
                title_text.setText(title);
            }
            
            if (local == BoardsDbAdapter.LOCAL_GREEN) {
            	title_text.setTextColor(Color.parseColor("#A2F600"));
			} else if (local == BoardsDbAdapter.LOCAL_YELLOW) {
				title_text.setTextColor(Color.YELLOW);
			} else if (local == BoardsDbAdapter.LOCAL_WHITE) {
				title_text.setTextColor(Color.parseColor("#F1FFFF"));
			} else if (local == BoardsDbAdapter.LOCAL_RED) {
				title_text.setTextColor(Color.RED);
			}
            
        }

    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.soundboard_menu_bottom, menu);
	    return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_add_board:
            	Intent addBoardIntent = new Intent(this, GraphicalSoundboardEditor.class);
        		startActivityForResult(addBoardIntent, ACTIVITY_ADD);
                return true;
            
            case R.id.menu_help:
            	AlertDialog.Builder helpBuilder = new AlertDialog.Builder(this);
            	helpBuilder.setTitle("Help");
            	
            	TextView tv = new TextView(this); 
            	helpBuilder.setView(tv);
            	
            	tv.setText(Html.fromHtml(getResources().getString(R.string.menu_help_text)));
            	tv.setMovementMethod(LinkMovementMethod.getInstance());
            	
            	AlertDialog helpAlert = helpBuilder.create();
            	helpAlert.show();
            	return true;
            	
            case R.id.menu_about:
            	AlertDialog.Builder aboutBuilder = new AlertDialog.Builder(this);
            	aboutBuilder.setTitle("About");
            	aboutBuilder.setMessage(
            			"Boarder is an opensource project. It's created and maintained by Jan Mikael Lindl�f\n" +
            			"\n" +
            			"It exists to be good and reliable software to create soundboards " +
            			"for Android.\n" +
            			"\n" +
            			"You can download full source from Github.\n" +
            			"\n" +
            			"I'd like to talk with you. You can email me to seek for help or say hello.\n\n" +
            			"You should also post on XDA-forums to meet other soundboard loving people :)\n\n" +
            			"You can find these medias in menu in \'soudboard menu\' (that screen you see on startup!)");
            	AlertDialog aboutAlert = aboutBuilder.create();
            	aboutAlert.show();
            	return true;
            	
            case R.id.menu_play_pause:
            	SoundPlayerControl.togglePlayPause();
            	return true;
            	
            case R.id.menu_internet:
            	Intent i = new Intent(this, InternetMenu.class);
            	startActivityForResult(i, 0);
            	return true;
            	
            case R.id.menu_dropbox:
            	Intent iDrop = new Intent(this, DropboxMenu.class);
            	startActivity(iDrop);
            	return true;

            case R.id.menu_global_settings:
            	LayoutInflater inflater = (LayoutInflater) SoundboardMenu.this.getSystemService(LAYOUT_INFLATER_SERVICE);
            	View layout = inflater.inflate(R.layout.soundboard_menu_global_settings, (ViewGroup) findViewById(R.id.alert_settings_root));

            	final EditText fadeInInput = (EditText) layout.findViewById(R.id.fadeInInput);
            	fadeInInput.setText(Integer.toString(mGlobalSettings.getFadeInDuration()));
            	
            	final EditText fadeOutInput = (EditText) layout.findViewById(R.id.fadeOutInput);
            	fadeOutInput.setText(Integer.toString(mGlobalSettings.getFadeOutDuration()));

            	AlertDialog.Builder builder = new AlertDialog.Builder(SoundboardMenu.this);
            	builder.setView(layout);
            	builder.setTitle("Sound settings");

            	builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            		public void onClick(DialogInterface dialog, int whichButton) {
            			try {
            				int fadeIn = Integer.valueOf(fadeInInput.getText().toString()).intValue();
            				mGlobalVariableDbHelper.updateIntVariable(GlobalVariablesDbAdapter.FADE_IN_DURATION_KEY, fadeIn);
            				
            				int fadeOut = Integer.valueOf(fadeOutInput.getText().toString()).intValue();
            				mGlobalVariableDbHelper.updateIntVariable(GlobalVariablesDbAdapter.FADE_OUT_DURATION_KEY, fadeOut);
            				
            				mGlobalSettings = loadGlobalSettings();
            			} catch(NumberFormatException nfe) {
            				Toast.makeText(getApplicationContext(), "Incorrect value", Toast.LENGTH_SHORT).show();
            			}
            		}
            	});

            	builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            		public void onClick(DialogInterface dialog, int whichButton) {
            		}
            	});

            	builder.show();
            	return true;
            	
            case R.id.xda:
            	Intent browserXdaIntent = new Intent(Intent.ACTION_VIEW, 
            			Uri.parse(mExtLinkXDA));
            	startActivity(browserXdaIntent);
            	return true;
            	
            case R.id.menu_email:
            	final Intent emailIntent = new Intent( android.content.Intent.ACTION_SEND);
            	emailIntent.setType("plain/text");
            	emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL,
            			new String[] { "mikuz.dev@gmail.com" });
            	startActivity(Intent.createChooser(emailIntent, "Send mail"));
            	return true;
            	
            case R.id.menu_github:
            	Intent browserGithubIntent = new Intent(Intent.ACTION_VIEW, 
            			Uri.parse("https://github.com/Mikuz/Boarder"));
            	startActivity(browserGithubIntent);
            	return true;
            	
            case R.id.menu_donate:
            	Intent browserDonateIntent = new Intent(Intent.ACTION_VIEW, 
            			Uri.parse(mExtLinkDonate));
            	startActivity(browserDonateIntent);
            	return true;
            	
            case R.id.menu_rate:
            	Intent browserRateIntent = new Intent(Intent.ACTION_VIEW, 
            			Uri.parse(mExtLinkMarket));
            	startActivity(browserRateIntent);
            	return true;
        }

        return super.onMenuItemSelected(featureId, item);
    }
    
    private GlobalSettings loadGlobalSettings() {
    	GlobalSettings globalSettings = new GlobalSettings();
    	int fadeInInput = 0;
    	try {
    		Cursor variableCursor = mGlobalVariableDbHelper.fetchVariable(GlobalVariablesDbAdapter.FADE_IN_DURATION_KEY);
			startManagingCursor(variableCursor);
			fadeInInput = variableCursor.getInt(variableCursor.getColumnIndexOrThrow(LoginDbAdapter.KEY_DATA));
        } catch (Exception e) {
        	mGlobalVariableDbHelper.createIntVariable(GlobalVariablesDbAdapter.FADE_IN_DURATION_KEY, 0);
        	Log.d(TAG, "Couldn't get fade-in", e);
		}
    	globalSettings.setFadeInDuration(fadeInInput);
    	int fadeOutInput = 0;
    	try {
    		Cursor variableCursor = mGlobalVariableDbHelper.fetchVariable(GlobalVariablesDbAdapter.FADE_OUT_DURATION_KEY);
			startManagingCursor(variableCursor);
			fadeOutInput = variableCursor.getInt(variableCursor.getColumnIndexOrThrow(LoginDbAdapter.KEY_DATA));
        } catch (Exception e) {
        	mGlobalVariableDbHelper.createIntVariable(GlobalVariablesDbAdapter.FADE_OUT_DURATION_KEY, 0);
        	Log.d(TAG, "Couldn't get fade-out", e);
		}
    	globalSettings.setFadeOutDuration(fadeOutInput);
    	return globalSettings;
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    	super.onCreateContextMenu(menu, v, menuInfo);
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.soundboard_menu_context, menu);
    }
    
    @Override
    public boolean onContextItemSelected(final MenuItem item) {
    	final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    	
    	final Cursor selection = mDbHelper.fetchBoard(info.id);
    	startManagingCursor(selection);
		final String selectionTitle = selection.getString(
			selection.getColumnIndexOrThrow(BoardsDbAdapter.KEY_TITLE));
		final int selectionLocal = selection.getInt(
				selection.getColumnIndexOrThrow(BoardsDbAdapter.KEY_LOCAL));
    	
        switch(item.getItemId()) {
        	
        	case R.id.menu_rename_board:
        		
        		AlertDialog.Builder alert = new AlertDialog.Builder(this);
    		  	alert.setTitle("Set name for board");

    		  	final EditText input = new EditText(this);
    		  	input.setText(selectionTitle);
    		  	alert.setView(input);

    		  	alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
    		  		public void onClick(DialogInterface dialog, int whichButton) {
    		  			
    		  			String inputText = input.getText().toString();
    		  			if (inputText.contains("\n")) {
    		  				inputText = inputText.substring(0, inputText.indexOf("\n"));
		  				}
    		  			
    		  			new File(mSbDir, selectionTitle).renameTo(
    		  					new File(mSbDir, inputText));
    		  			
    		  			mDbHelper.updateBoard(info.id, inputText, 
    		  					selection.getInt(selection.getColumnIndexOrThrow(BoardsDbAdapter.KEY_ROWID)));
    		  			initializeBoardUpdateThread();
    		  		}
    		  	});
    		  	alert.show();
        		return true;
        		
        	case R.id.menu_delete_board:
        		
        		AlertDialog.Builder deleteAlert = new AlertDialog.Builder(this);
        		deleteAlert.setTitle("Delete " + selectionTitle + "?");

        		deleteAlert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
    		  		public void onClick(DialogInterface dialog, int whichButton) {
    		  			
    		  			File boardDir = new File(mSbDir + "/" + selectionTitle);
    		  			try {
							FileProcessor.delete(boardDir);
						} catch (IOException e) {
							Log.e(TAG, "Error deleting " + selectionTitle, e);
						}
    		  			initializeBoardUpdateThread();
    		  		}
    		  	});
        		deleteAlert.setNegativeButton("No", new DialogInterface.OnClickListener() {
    		  		public void onClick(DialogInterface dialog, int whichButton) {}
    		  	});
        		
    		  	deleteAlert.show();
        		return true;
        		
        	case R.id.menu_move_board:
        		mMoveBoard = (int) info.id;
        		return true;
        		
        	case R.id.menu_duplicate_board:
        		duplicateBoard(selectionTitle);
        		return true;
        		
        	case R.id.menu_zip_board:
        		if (selectionLocal == BoardsDbAdapter.LOCAL_GREEN) {
        			mWaitDialog = ProgressDialog.show(this, "", "Please wait\n\n", true);
            		Toast.makeText(SoundboardMenu.this, "You will find the zip in:\n" + mShareDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
            		Thread t = new Thread() {
            			public void run() {
            				FileProcessor zipper = new FileProcessor();
            				zipper.zipBoard(selectionTitle);
            				mHandler.post(mDismissDialog);
            	        }
            	    };
            	    t.start();
        		} else {
        			Toast.makeText(SoundboardMenu.this, "Board is not green! Select 'Convert' in board.", Toast.LENGTH_LONG).show();
        		}
        		return true;
        	
        	default:
            	return super.onContextItemSelected(item);
        }
    }
    
    final Runnable mDismissDialog = new Runnable() {
    	public void run() {
    		mWaitDialog.dismiss();
    	}
    };
    
    final Handler mHandler = new Handler();
    ProgressDialog mWaitDialog;
    
    private void duplicateBoard(final String boardName) {
		mWaitDialog = ProgressDialog.show(this, "", "Please wait", true);
		
		Thread t = new Thread() {
			public void run() {
				FileProcessor.duplicateBoard(boardName);
				mHandler.post(mUpdateResults);
	        }
	    };
	    t.start();
	}
	
    final Runnable mUpdateResults = new Runnable() {
    	public void run() {
    		initializeBoardUpdateThread();
    		mWaitDialog.dismiss();
    	}
    };
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        
        boolean hideSoundboardMenu = mIntent.getBooleanExtra(EXTRA_HIDE_SOUNDBOARDMENU, false);
        if (hideSoundboardMenu) {
        	finish();
        	return;
        }
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        
        Cursor selection = mDbHelper.fetchBoard(id);
    	startManagingCursor(selection);
    	final String boardName = selection.getString(selection.getColumnIndexOrThrow(BoardsDbAdapter.KEY_TITLE));
    	int boardLocal = selection.getInt(selection.getColumnIndexOrThrow(BoardsDbAdapter.KEY_LOCAL));
    	File boardDir = new File(mSbDir, boardName);
        
        if (Intent.ACTION_CREATE_SHORTCUT.equals(mAction)) {
        	
        	Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
            shortcutIntent.setClassName(this, this.getClass().getName());
            shortcutIntent.putExtra(EXTRA_KEY, boardName);
            shortcutIntent.putExtra(EXTRA_HIDE_SOUNDBOARDMENU, true);
            shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            Intent intent = new Intent();
            intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, selection.getString(
            		selection.getColumnIndexOrThrow(BoardsDbAdapter.KEY_TITLE)));
            
            File icon = new File(mSbDir, boardName + "/icon.png");
            if (icon.exists()) {
				Bitmap bitmap = BitmapFactory.decodeFile(icon.getAbsolutePath());
				intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, IconUtils.resizeIcon(this, bitmap, (40/12)));
            } else {
	            Parcelable iconResource = Intent.ShortcutIconResource.fromContext(this,  R.drawable.board_icon);
	            intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
            }

            setResult(RESULT_OK, intent);
            
            finish();
            return;
        	
        } else if (mMoveBoard > -1) {
        	
        	Cursor moveFrom = mDbHelper.fetchBoard(mMoveBoard);
        	startManagingCursor(moveFrom);
        	
        	String moveSourceTitle = moveFrom.getString(moveFrom.getColumnIndexOrThrow(BoardsDbAdapter.KEY_TITLE));
        	int moveSourceLocal = moveFrom.getInt(moveFrom.getColumnIndexOrThrow(BoardsDbAdapter.KEY_LOCAL));
        	
        	mDbHelper.updateBoard((int) id, moveSourceTitle, moveSourceLocal);
        	mDbHelper.updateBoard(mMoveBoard, boardName, boardLocal);
        	
        	mMoveBoard = -1;
        	
        	initializeBoardUpdateThread();
        	
        } else {
        	
        	try {
        		boolean boardFileFound = false;
        		for (File boardDirContent : boardDir.listFiles()) {
        			if (boardDirContent.getName().equals("graphicalBoard")) {
        				Intent i = new Intent(this, GraphicalSoundboardEditor.class);
        				i.putExtra(BoardsDbAdapter.KEY_TITLE, boardName);
        				startActivityForResult(i, ACTIVITY_EDIT);
        				boardFileFound = true;
        				break;
        			}
        		}
	        	if (!boardFileFound) {
	        		Intent i = new Intent(this, GraphicalSoundboardEditor.class);
	                i.putExtra(BoardsDbAdapter.KEY_TITLE, boardName);
	        		startActivityForResult(i, ACTIVITY_ADD);
	        	}
        	} catch (NullPointerException npe) {
        		Log.e(SoundboardMenu.TAG, "Unable to list boards", npe);
        	}
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDbHelper != null) {
        	mDbHelper.close();
        }
        if (mGlobalVariableDbHelper != null) {
        	mGlobalVariableDbHelper.close();
        }
    }
}