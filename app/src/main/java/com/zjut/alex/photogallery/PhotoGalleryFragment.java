package com.zjut.alex.photogallery;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Alex on 2016/7/20 0020.
 */
public class PhotoGalleryFragment extends VisibleFragment {

	private static final String TAG = "PhotoGalleryFragment";
	private static final int STANDARD_COLS = 3;

	private RecyclerView mPhotoRecyclerView;
	private List<GalleryItem> mItems = new ArrayList<>();
	private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;
	private int lastFetchedPage = 1;

	public static Intent newIntent(Context context) {
		return new Intent(context, PhotoGalleryActivity.class);
	}

	public static PhotoGalleryFragment newInstance() {
		return new PhotoGalleryFragment();
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
		setHasOptionsMenu(true);

		updateItems();

		//关联使用反馈Handler
		Handler responseHandler = new Handler();
		mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
		mThumbnailDownloader.setThumbnailDownloadListener(new ThumbnailDownloader
				.ThumbnailDownloadListener<PhotoHolder>() {
			@Override
			public void onThumbnailDownloaded(PhotoHolder target, Bitmap thumbnail) {
				Drawable drawable = new BitmapDrawable(getResources(), thumbnail);
				target.bindDrawable(drawable);
			}
		});
		mThumbnailDownloader.start();
		mThumbnailDownloader.getLooper();
		Log.i(TAG, "Background thread started");
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable
	Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_photo_gallery, container, false);

		mPhotoRecyclerView = (RecyclerView) view.findViewById(R.id
				.fragment_photo_gallery_recycler_view);
		mPhotoRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), STANDARD_COLS));

		//分页
//		mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
//			@Override
//			public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
//				PhotoAdapter adapter = (PhotoAdapter) recyclerView.getAdapter();
//				int lastPosition = adapter.getLastBoundPosition();
//				GridLayoutManager layoutManager = (GridLayoutManager) recyclerView
//						.getLayoutManager();
//				int loadBufferPosition = 1;
//				if (lastPosition >= (adapter.getItemCount() - layoutManager.getSpanCount() -
//						loadBufferPosition)) {
//					new FetchItemsTask(null).execute(lastPosition + 1);
//				}
//			}
//
//			@Override
//			public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
//				super.onScrolled(recyclerView, dx, dy);
//			}
//		});

		//动态调整网格列
		mPhotoRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver
				.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				Point size = new Point();
				getActivity().getWindowManager().getDefaultDisplay().getSize(size);
				int newColumns = (int) Math.floor(size.x * STANDARD_COLS / 1080);
				if (newColumns != STANDARD_COLS) {
					GridLayoutManager layoutManager = (GridLayoutManager) mPhotoRecyclerView
							.getLayoutManager();
					layoutManager.setSpanCount(newColumns);
				}
			}
		});

		setupAdapter();

		return view;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		mThumbnailDownloader.clearQueue();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		mThumbnailDownloader.quit();
		Log.i(TAG, "Background thread destroyed");
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);

		inflater.inflate(R.menu.fragment_photo_gallery, menu);

		MenuItem searchItem = menu.findItem(R.id.menu_item_search);
		final SearchView searchView = (SearchView) searchItem.getActionView();

		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				Log.d(TAG, "QueryTextSubmit: " + query);
				QueryPreferences.setStoredQuery(getActivity(), query);
				updateItems();
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				Log.d(TAG, "QueryTextChange: " + newText);
				return false;
			}
		});

		searchView.setOnSearchClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				String query = QueryPreferences.getStoredQuery(getActivity());
				searchView.setQuery(query, false);
			}
		});

		MenuItem toggleItem = menu.findItem(R.id.menu_item_toggle_polling);
		if (PollService.isServiceAlarmOn(getActivity())) {
			toggleItem.setTitle(R.string.stop_polling);
		} else {
			toggleItem.setTitle(R.string.start_polling);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_item_clear:
				QueryPreferences.setStoredQuery(getActivity(), null);
				updateItems();
				return true;
			case R.id.menu_item_toggle_polling:
				boolean shouldStartAlarm = !PollService.isServiceAlarmOn(getActivity());
				PollService.setServiceAlarm(getActivity(), shouldStartAlarm);
				getActivity().invalidateOptionsMenu();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void updateItems() {
		String query = QueryPreferences.getStoredQuery(getActivity());
		new FetchItemsTask(query).execute();
	}

	private void setupAdapter() {
		if (isAdded()) {
			mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
		}
	}

	private class PhotoHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
		private ImageView mItemImageView;
		private GalleryItem mGalleryItem;

		public PhotoHolder(View itemView) {
			super(itemView);

			mItemImageView = (ImageView) itemView.findViewById(R.id.fragment_photo_gallery_image_view);
			itemView.setOnClickListener(this);
		}

		public void bindDrawable(Drawable drawable) {
			mItemImageView.setImageDrawable(drawable);
		}

		public void bindGalleryItem(GalleryItem galleryItem) {
			mGalleryItem = galleryItem;
		}

		@Override
		public void onClick(View view) {
			//Intent i = new Intent(Intent.ACTION_VIEW, mGalleryItem.getPhotoPageUri());
			Intent i = PhotoPageActivity.newIntent(getActivity(), mGalleryItem.getPhotoPageUri());
			startActivity(i);
		}
	}

	private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {
		private List<GalleryItem> mGalleryItems;
		private int lastBoundPosition;

		public int getLastBoundPosition() {
			return lastBoundPosition;
		}

		private PhotoAdapter(List<GalleryItem> galleryItems) {
			mGalleryItems = galleryItems;
		}

		@Override
		public PhotoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			LayoutInflater inflater = LayoutInflater.from(getActivity());
			View view = inflater.inflate(R.layout.gallery_item, parent, false);

			return new PhotoHolder(view);
		}

		@Override
		public void onBindViewHolder(PhotoHolder holder, int position) {
			GalleryItem galleryItem = mGalleryItems.get(position);
			holder.bindGalleryItem(galleryItem);
			Drawable placeholder = getResources().getDrawable(R.drawable.loading);
			holder.bindDrawable(placeholder);
			mThumbnailDownloader.queueThumbnail(holder, galleryItem.getUrl());

			lastBoundPosition = position;
			Log.i(TAG, "Last bound position is " + Integer.toString(lastBoundPosition));
		}

		@Override
		public int getItemCount() {
			return mGalleryItems.size();
		}
	}

	private class FetchItemsTask extends AsyncTask<Void, Void, List<GalleryItem>> {
		private String mQuery;

		public FetchItemsTask(String query) {
			mQuery = query;
		}

		@Override
		protected List<GalleryItem> doInBackground(Void... params) {
			if (mQuery == null) {
				return new FlickrFetchr().fetchRecentPhotos();
			} else {
				return new FlickrFetchr().searchPhotos(mQuery);
			}
		}

		@Override
		protected void onPostExecute(List<GalleryItem> items) {
//			if (lastFetchedPage > 1) {
//				mItems.addAll(items);
//				mPhotoRecyclerView.getAdapter().notifyDataSetChanged();
//			} else {
//				mItems = items;
//				setupAdapter();
//			}
//			lastFetchedPage++;
//			QueryPreferences.setLastFetchedPage(getActivity(), lastFetchedPage);

			mItems = items;
			setupAdapter();
		}
	}
}
