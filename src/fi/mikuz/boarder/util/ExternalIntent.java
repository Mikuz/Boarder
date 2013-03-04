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

package fi.mikuz.boarder.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

public abstract class ExternalIntent {
	private static final String TAG = ExternalIntent.class.getSimpleName();
	
	private static final String mExtLinkDonateFlattr = "https://flattr.com/thing/8aeacefc6a7b51a10f7777d01a3604d1";
	private static final String mExtLinkDonatePaypal = "https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=ZY98RYEQTS7TY";
	private static final String mExtLinkXDA = "https://forum.xda-developers.com/showthread.php?p=23224859#post23224859";
	public static final String mExtLinkMarket = "market://details?id=fi.mikuz.boarder";
	
	public static void openDonateFlattr(Context context) {
		try {
			Intent browserDonateIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mExtLinkDonateFlattr));
			context.startActivity(browserDonateIntent);
		} catch (ActivityNotFoundException e) {
			String error = "Unable to open web browser";
			Log.e(TAG, error, e);
			Toast.makeText(context, error, Toast.LENGTH_LONG).show();
		}
	}
	
	public static void openDonatePaypal(Context context) {
		try {
			Intent browserDonateIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mExtLinkDonatePaypal));
			context.startActivity(browserDonateIntent);
		} catch (ActivityNotFoundException e) {
			String error = "Unable to open web browser";
			Log.e(TAG, error, e);
			Toast.makeText(context, error, Toast.LENGTH_LONG).show();
		}
	}
	
	public static void openXdaForums(Context context) {
		try {
			Intent browserXdaIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mExtLinkXDA));
			context.startActivity(browserXdaIntent);
		} catch (ActivityNotFoundException e) {
			String error = "Unable to open web browser";
    		Log.e(TAG, error, e);
    		Toast.makeText(context, error, Toast.LENGTH_LONG).show();
    	}
	}
	
	public static void openGooglePlay(Context context) {
		Intent browserRateIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mExtLinkMarket));
    	try {
    		context.startActivity(browserRateIntent);
    	} catch (ActivityNotFoundException e) {
    		Log.e(TAG, "Unable to open external activity", e);
    		Toast.makeText(context, "Could not open Google Play. Opening XDA forums instead.", Toast.LENGTH_LONG).show();
    		openXdaForums(context);
    	}
	}
}
