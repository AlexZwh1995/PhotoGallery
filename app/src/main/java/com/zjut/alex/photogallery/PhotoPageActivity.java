package com.zjut.alex.photogallery;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.Fragment;

/**
 * Created by Alex on 2016/8/7 0007.
 */
public class PhotoPageActivity extends SingleFragmentActivity {
	private PhotoPageFragment mPhotoPageFragment;

	public static Intent newIntent(Context context, Uri photoPageUri) {
		Intent i = new Intent(context, PhotoPageActivity.class);
		i.setData(photoPageUri);
		return i;
	}

	@Override
	protected Fragment createFragment() {
		mPhotoPageFragment =  PhotoPageFragment.newInstance(getIntent().getData());
		return mPhotoPageFragment;
	}

	@Override
	public void onBackPressed() {
		if (mPhotoPageFragment.onBackPressed()) {
			return;
		}
		super.onBackPressed();
	}
}
