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

package fi.mikuz.boarder.gui.internet;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import org.json.JSONException;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import fi.mikuz.boarder.R;
import fi.mikuz.boarder.app.BoarderActivity;
import fi.mikuz.boarder.connection.ConnectionErrorResponse;
import fi.mikuz.boarder.connection.ConnectionListener;
import fi.mikuz.boarder.connection.ConnectionManager;
import fi.mikuz.boarder.connection.ConnectionSuccessfulResponse;
import fi.mikuz.boarder.connection.ConnectionUtils;
import fi.mikuz.boarder.util.Security;
import fi.mikuz.boarder.util.TimeoutProgressDialog;
import fi.mikuz.boarder.util.dbadapter.LoginDbAdapter;

public class Login extends BoarderActivity implements ConnectionListener {
	private static final String TAG = "InternetLogin";
	
	final Handler mHandler = new Handler();
	TimeoutProgressDialog mWaitDialog;
	
	private Button mLogin;
	private EditText mUsername;
	private EditText mPassword;
	
	private boolean mRememberSession = false;
	private static final int PASSWORD_OPERATION_NONE = 0;
	private static final int PASSWORD_OPERATION_SAVE = 1;
	private int mPasswordOperation = PASSWORD_OPERATION_NONE;
	
	HashMap<String,String> mReturnSession = null;
	
	LoginDbAdapter mDbHelper;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		mDbHelper = new LoginDbAdapter(Login.this);
	    mDbHelper.open();
		
		Bundle extras = getIntent().getExtras();
		if (extras.getSerializable(InternetMenu.LOGIN_KEY) != null) {
			mWaitDialog = new TimeoutProgressDialog(Login.this, "Logging out", TAG, true);
			
			@SuppressWarnings("unchecked")
			HashMap<String,String> lastSession = (HashMap<String,String>) extras.getSerializable(InternetMenu.LOGIN_KEY);
			mReturnSession = lastSession;
			
			mDbHelper.deleteLogin(InternetMenu.USER_ID_KEY);
			mDbHelper.deleteLogin(InternetMenu.SESSION_TOKEN_KEY);
			HashMap<String, String> sendList = new HashMap<String, String>();
			sendList.put(InternetMenu.USER_ID_KEY, lastSession.get(InternetMenu.USER_ID_KEY));
			sendList.put(InternetMenu.SESSION_TOKEN_KEY, lastSession.get(InternetMenu.SESSION_TOKEN_KEY));
			new ConnectionManager(Login.this, InternetMenu.mLogoutURL, sendList);
		} else {
			showLoggedOutView();
		}
		
		
	}
    
	private void showLoggedOutView() {
		setContentView(R.layout.internet_login_logged_out);
		
		mLogin = (Button)findViewById(R.id.submit);
		mUsername = (EditText)findViewById(R.id.userName);
		mPassword = (EditText)findViewById(R.id.userPassword);
		
		boolean usernameInDb = false;
		String dbPassword = null;
		
		try {
			Cursor loginCursor = mDbHelper.fetchLogin(InternetMenu.USERNAME_KEY);
			startManagingCursor(loginCursor);
			mUsername.setText(loginCursor.getString(
				loginCursor.getColumnIndexOrThrow(LoginDbAdapter.KEY_DATA)));
			usernameInDb = true;
		} catch (SQLException e) {Log.d(TAG, "Couldn't get database login info", e);
		} catch (CursorIndexOutOfBoundsException e) {Log.d(TAG, "Couldn't get database login info", e);}
		
		dbPassword = getDbPassword();
		
		final CheckBox rememberPassword = (CheckBox)findViewById(R.id.rememberPassword);
		
		if (dbPassword == null) {
			rememberPassword.setChecked(false);
		} else {
			rememberPassword.setChecked(true);
			mPassword.setText(dbPassword);
		}
		
		final CheckBox rememberUsername = (CheckBox)findViewById(R.id.rememberUsername);
		rememberUsername.setChecked(usernameInDb);
		
		mLogin.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	mWaitDialog = new TimeoutProgressDialog(Login.this, "Waiting for response", TAG, false);
            	
            	String username = mUsername.getText().toString();
            	String password = mPassword.getText().toString();
            	Boolean entrancePassword = true;
            	String dbPassword = getDbPassword();
            	
            	// Password in the Android database is always the same as in the server database
            	// Being an entrance password means that the password is not the same as in the server database
            	if (dbPassword != null) {
            		if (dbPassword.equals(password)) {
            			entrancePassword = false;
            		}
            	}
            	
            	// If the password is now an entrance password then it's plain text and wants to be hashed
            	if (entrancePassword) {
            		try {
						password = Security.passwordHash(password);
					} catch (NoSuchAlgorithmException e) {
						String msg = "Couldn't make a password hash";
						Toast.makeText(Login.this, msg, Toast.LENGTH_LONG).show();
						Log.e(TAG, msg, e);
					}
            	}
            	
            	if (rememberUsername.isChecked()) {
            		mDbHelper.putLogin(InternetMenu.USERNAME_KEY, username);
            	} else if (!rememberUsername.isChecked()) {
            		mDbHelper.deleteLogin(InternetMenu.USERNAME_KEY);
            	}
            	
            	if (rememberPassword.isChecked()) {
            		if (!entrancePassword) {
            			mPasswordOperation = PASSWORD_OPERATION_NONE;
            			mDbHelper.putLogin(InternetMenu.PASSWORD_KEY, password);
            		} else {
            			mPasswordOperation = PASSWORD_OPERATION_SAVE;
            		}
            	} else if (!rememberPassword.isChecked()) {
            		mDbHelper.deleteLogin(InternetMenu.PASSWORD_KEY);
            	}
            	
            	mDbHelper.deleteLogin(InternetMenu.USER_ID_KEY);
            	mDbHelper.deleteLogin(InternetMenu.SESSION_TOKEN_KEY);
            	CheckBox rememberSession = (CheckBox)findViewById(R.id.rememberSession);
            	if (rememberSession.isChecked()) {
            		mRememberSession = true;
            	}
            	
            	HashMap<String, String> sendList = new HashMap<String, String>();
            	sendList.put(InternetMenu.USERNAME_KEY, username);
            	sendList.put(InternetMenu.PASSWORD_KEY, password);
            	sendList.put(InternetMenu.ENTRANCE_PASSWORD_KEY, entrancePassword ? "1" : "0");
            	new ConnectionManager(Login.this, InternetMenu.mLoginURL, sendList);
            }
        });
		
		Button recoverPassword = (Button)findViewById(R.id.recoverPassword);
		recoverPassword.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	
            	LayoutInflater inflater = (LayoutInflater) Login.this.getSystemService(LAYOUT_INFLATER_SERVICE);
            	View layout = inflater.inflate(R.layout.internet_login_alert_recover_password, 
            			(ViewGroup) findViewById(R.id.alert_settings_root));
            	
            	final EditText input = (EditText) layout.findViewById(R.id.input);
            	Button submitButton = (Button) layout.findViewById(R.id.submitButton);
            	
            	AlertDialog.Builder builder = new AlertDialog.Builder(Login.this);
            	builder.setView(layout);
            	builder.setTitle("Password recovery");
            	
            	submitButton.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                    	mWaitDialog = new TimeoutProgressDialog(Login.this, "Waiting for response", TAG, false);
                    	String inputText = input.getText().toString();
                    	
                    	HashMap<String, String> sendList = new HashMap<String, String>();
                    	sendList.put(InternetMenu.EMAIL_KEY, inputText);
                    	new ConnectionManager(Login.this, InternetMenu.mRecoverPasswordURL, sendList);
                    }
            	});

            	builder.show();
            }
		});
		
	}
	
	private String getDbPassword() {
		String dbPassword = null;
		try {
			Cursor loginCursor = mDbHelper.fetchLogin(InternetMenu.PASSWORD_KEY);
			startManagingCursor(loginCursor);
			dbPassword = loginCursor.getString(loginCursor.getColumnIndexOrThrow(LoginDbAdapter.KEY_DATA));
		} catch (SQLException e) {Log.d(TAG, "Couldn't get database login info", e);
		} catch (CursorIndexOutOfBoundsException e) {Log.d(TAG, "Couldn't get database login info", e);}
		return dbPassword;
	}
	
	private void logout() {
		Bundle bundle = new Bundle();
		bundle.putSerializable(InternetMenu.LOGIN_KEY, null);
		
		Intent intent = new Intent();
		intent.putExtras(bundle);
		
		bundle.putString(InternetMenu.ACCOUNT_MESSAGE_KEY, "");
		setResult(RESULT_OK, intent);
		try {
			finish();
		} catch (Throwable e) {
			Log.e(TAG, "Couldn't finish", e);
		}
	}
	
	@Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDbHelper != null) {
        	mDbHelper.close();
        }
    }

	@Override
	public void onConnectionSuccessful(ConnectionSuccessfulResponse connectionSuccessfulResponse) throws JSONException {
		ConnectionUtils.connectionSuccessful(Login.this, connectionSuccessfulResponse);
		mWaitDialog.dismiss();
		
		if (ConnectionUtils.checkConnectionId(connectionSuccessfulResponse, InternetMenu.mLogoutURL)) {
			Toast.makeText(Login.this, connectionSuccessfulResponse.getJSONObject().getString(ConnectionUtils.returnMessage), Toast.LENGTH_LONG).show();
			logout();
		} else if (ConnectionUtils.checkConnectionId(connectionSuccessfulResponse, InternetMenu.mLoginURL)) {
			String password = connectionSuccessfulResponse.getJSONObject().getString(InternetMenu.PASSWORD_KEY);
			String sessionToken = connectionSuccessfulResponse.getJSONObject().getString(InternetMenu.SESSION_TOKEN_KEY);
			String userId = connectionSuccessfulResponse.getJSONObject().getString(InternetMenu.USER_ID_KEY);
			String accountMessage = connectionSuccessfulResponse.getJSONObject().getString(InternetMenu.ACCOUNT_MESSAGE_KEY);
			
			if (mRememberSession) {
				mDbHelper.createLogin(InternetMenu.USER_ID_KEY, userId);
				mDbHelper.createLogin(InternetMenu.SESSION_TOKEN_KEY, sessionToken);
			}
			
			if (mPasswordOperation == PASSWORD_OPERATION_SAVE) {
        		mDbHelper.putLogin(InternetMenu.PASSWORD_KEY, password);
			}
			
			mReturnSession = new HashMap<String, String>();
			mReturnSession.put(InternetMenu.USER_ID_KEY, userId);
			mReturnSession.put(InternetMenu.SESSION_TOKEN_KEY, sessionToken);
			
			Bundle bundle = new Bundle();
			bundle.putSerializable(InternetMenu.LOGIN_KEY, mReturnSession);
			bundle.putString(InternetMenu.ACCOUNT_MESSAGE_KEY, accountMessage);
			
			Intent intent = new Intent();
			intent.putExtras(bundle);
			
			setResult(RESULT_OK, intent);
			finish();
		} else if (ConnectionUtils.checkConnectionId(connectionSuccessfulResponse, InternetMenu.mRecoverPasswordURL)) {
			Toast.makeText(Login.this, connectionSuccessfulResponse.getJSONObject().getString(ConnectionUtils.returnMessage), Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void onConnectionError(ConnectionErrorResponse connectionErrorResponse) {
		ConnectionUtils.connectionError(this, connectionErrorResponse, TAG);
		mWaitDialog.dismiss();
		
		if (ConnectionUtils.checkConnectionId(connectionErrorResponse, InternetMenu.mLogoutURL)) {
			Toast.makeText(Login.this, "Server couldn't logout", Toast.LENGTH_LONG).show();
			logout();
		}
	}

}
