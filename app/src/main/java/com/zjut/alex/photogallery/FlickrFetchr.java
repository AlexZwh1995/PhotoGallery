package com.zjut.alex.photogallery;

import android.net.Uri;
import android.util.Log;

import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Alex on 2016/7/20 0020.
 */
public class FlickrFetchr {

	private static final String TAG = "FlickrFetchr";

	private static final String API_KEY = "d4cfac32cc4df4c5be874d12ae5969f2";
	private static final String FETCH_RECENT_METHOD = "flickr.photos.getRecent";
	private static final String SEARCH_METHOD = "flickr.photos.search";
	private static final Uri ENDPOINT = Uri
			.parse("https://api.flickr.com/services/rest")
			.buildUpon()
			.appendQueryParameter("api_key", API_KEY)
			.appendQueryParameter("format", "json")
			.appendQueryParameter("nojsoncallback", "1")
			.appendQueryParameter("extras", "url_s")
			.build();

	//基本网络连接代码
	public byte[] getUrlBytes(String urlSpec) throws IOException {
		URL url = new URL(urlSpec);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			InputStream in = connection.getInputStream();

			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				throw new IOException(connection.getResponseMessage() + ": with " + urlSpec);
			}

			int byteRead = 0;
			byte[] buffer = new byte[1024];
			while ((byteRead = in.read(buffer)) > 0) {
				out.write(buffer, 0, byteRead);
			}
			out.close();
			return out.toByteArray();
		} finally {
			connection.disconnect();
		}
	}

	public String getUrlString(String urlSpec) throws IOException {
		return new String(getUrlBytes(urlSpec));
	}

	public List<GalleryItem> fetchRecentPhotos() {
		String url = buildUrl(FETCH_RECENT_METHOD, null);
		return downloadGalleryItems(url);
	}

	public List<GalleryItem> searchPhotos(String query) {
		String url = buildUrl(SEARCH_METHOD, query);
		return downloadGalleryItems(url);
	}

	private List<GalleryItem> downloadGalleryItems(String url) {

		List<GalleryItem> items = new ArrayList<>();

		try {
			String jsonString = getUrlString(url);
			Log.i(TAG, "Received JSON: " + jsonString);
			//解析JSON数据
			parseItems(items, jsonString);
		} catch (JsonParseException jpe) {
			Log.e(TAG, "Failed to parse JSON", jpe);
		} catch (IOException ioe) {
			Log.e(TAG, "Failed to fetch items", ioe);
		}

		return items;
	}

	private String buildUrl(String method, String query) {
		Uri.Builder uriBuilder = ENDPOINT.buildUpon()
				.appendQueryParameter("method", method);

		if (method.equals(SEARCH_METHOD)) {
			uriBuilder.appendQueryParameter("text", query);
		}

		return uriBuilder.build().toString();
	}

	//解析Flickr图片
	private void parseItems(List<GalleryItem> items, String jsonString) {

		Gson gson = new GsonBuilder()
				.registerTypeAdapter(GalleryItem[].class, new PhotoJsonDeserializer())
				.create();
		GalleryItem[] photoList = gson.fromJson(jsonString, GalleryItem[].class);
		//scan photo list
		for (GalleryItem item : photoList) {
			if (item.getUrl() != null) {
				items.add(item);
			}
		}
	}

	private class PhotoJsonDeserializer implements JsonDeserializer<GalleryItem[]> {

		@Override
		public GalleryItem[] deserialize(JsonElement json, Type typeOfT,
		                                 JsonDeserializationContext context) throws
				JsonParseException {
			// Get the "photos" element from the parsed JSON
			JsonElement photos = json.getAsJsonObject().get("photos");
			JsonElement photoArray = photos.getAsJsonObject().get("photo");
			// Deserialize it. You use a new instance of Gson to avoid infinite recursion
			// to this deserializer
			Gson gson = new GsonBuilder()
					.setFieldNamingStrategy(new PhotoFieldNamingStrategy())
					.create();
			return gson.fromJson(photoArray, GalleryItem[].class);
		}
	}

	private class PhotoFieldNamingStrategy implements FieldNamingStrategy {

		@Override
		public String translateName(Field f) {
			switch (f.getName()) {
				case "mId":
					return "id";
				case "mCaption":
					return "title";
				case "mUrl":
					return "url_s";
				case "mOwner":
					return "owner";
				default:
					return f.getName();
			}
		}
	}
}
