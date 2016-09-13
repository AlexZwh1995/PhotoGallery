package com.zjut.alex.photogallery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by Alex on 2016/7/29 0029.
 */
public class StartupReceiver extends BroadcastReceiver {
	private static final String TAG = "StartupReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i(TAG, "Received a broadcast intent: " + intent.getAction());

		boolean isOn = QueryPreferences.isAlarmOn(context);
		PollService.setServiceAlarm(context, isOn);
	}
}
