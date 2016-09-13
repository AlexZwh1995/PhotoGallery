package com.zjut.alex.photogallery;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;

/**
 * Created by Alex on 2016/7/25 0025.
 */
public class PollService extends IntentService {
	private static final String TAG = "PollService";

	//private static final long POLL_INTERVAL = AlarmManager.INTERVAL_FIFTEEN_MINUTES;
	private static final long POLL_INTERVAL = 1000 * 5;

	public static final String ACTION_SHOW_NOTIFICATION = "com.zjut.alex.photogallery.SHOW_NOTIFICATION";
	public static final String PERM_PRIVATE = "com.zjut.alex.photogallery.PRIVATE";
	public static final String REQUEST_CODE = "REQUEST_CODE";
	public static final String NOTIFICATION = "NOTIFICATION";

	public static Intent newIntent(Context context) {
		return new Intent(context, PollService.class);
	}

	public static void setServiceAlarm(Context context, boolean isOn) {
		Intent i = PollService.newIntent(context);
		PendingIntent pi = PendingIntent.getService(context, 0, i, 0);

		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

		if (isOn) {
			alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock
					.elapsedRealtime(), POLL_INTERVAL, pi);
		} else {
			alarmManager.cancel(pi);
			pi.cancel();
		}

		QueryPreferences.setAlarmOn(context, isOn);
	}

	public static boolean isServiceAlarmOn(Context context) {
		Intent i = PollService.newIntent(context);
		PendingIntent pi = PendingIntent.getService(context, 0, i, PendingIntent.FLAG_NO_CREATE);
		return pi != null;
	}

	public PollService() {
		super(TAG);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.i(TAG, "Received an intent: " + intent);

		if (!isNetworkAvailableAndConnected()) {
			return;
		}

		String query = QueryPreferences.getStoredQuery(this);
		String lastResultId = QueryPreferences.getLastResultId(this);
		List<GalleryItem> items;

		if (query == null) {
			items = new FlickrFetchr().fetchRecentPhotos();
		} else {
			items = new FlickrFetchr().searchPhotos(query);
		}

		if (items.size() == 0) {
			return;
		}

		String resultId = items.get(0).getId();
		if (resultId.equals(lastResultId)) {
			Log.i(TAG, "Got an old result: " + resultId);
		} else {
			Log.i(TAG, "Got a new result: " + resultId);
		}

		Resources resources = getResources();
		Intent i = PhotoGalleryFragment.newIntent(this);
		PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);

		Notification notification = new Notification.Builder(this)
				.setTicker(resources.getString(R.string.new_pictures_title))
				.setSmallIcon(android.R.drawable.ic_menu_report_image)
				.setContentTitle(resources.getString(R.string.new_pictures_title))
				.setContentText(resources.getString(R.string.new_pictures_text))
				.setContentIntent(pi)
				.setAutoCancel(true)
				.build();

		showBackgroundNotification(0, notification);

		QueryPreferences.setLastResultId(this, resultId);
	}

	private void showBackgroundNotification(int requestCode, Notification notification) {
		Intent i = new Intent(ACTION_SHOW_NOTIFICATION);
		i.putExtra(REQUEST_CODE, requestCode);
		i.putExtra(NOTIFICATION, notification);
		sendOrderedBroadcast(i, PERM_PRIVATE, null, null, Activity.RESULT_OK, null, null);
	}

	private boolean isNetworkAvailableAndConnected() {
		ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

		boolean isNetworkAvailable = manager.getActiveNetworkInfo() != null;
		boolean isNetworkConnected = isNetworkAvailable && manager.getActiveNetworkInfo()
				.isConnected();

		return isNetworkConnected;
	}
}
