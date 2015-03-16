package com.android.launcher3;
import android.view.MenuItem;
import android.app.ActionBar;
import android.app.Activity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.AdapterView.OnItemClickListener;
import android.os.Bundle;
import android.widget.AdapterView;
import android.app.WallpaperManager;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
/**
 *add 20140902 for HOSIN_CUST_LAUNCHER_MESSI
*/
public class ThemePickerActivity extends Activity {
	private static final String NAME_KEY = "theme_key";
	private static final String BITMAP_KEY = "bitmap_key";
	private List<HashMap<String, Object>> mThemes;
	HashMap<String, String> xHashMap;
	private static final String SHARED_PREFRENCE_KEY = "com.android.launcher3.prefs";
	private ImageAdapter adapter;
	private GridView gridView;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.theme_picker);
		ActionBar actionBar = getActionBar();
		//actionBar.setBackgroundDrawable(new ColorDrawable(R.color.white));
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setTitle(R.string.theme_title);
		actionBar.setIcon(getResources().getDrawable(R.drawable.ic_themes));
		getThemes();
		 gridView = (GridView) findViewById(R.id.gridview);
		 adapter = new ImageAdapter(this);
		gridView.setAdapter(adapter);
		gridView.setSelector(new ColorDrawable(Color.TRANSPARENT));
		gridView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				WallpaperManager wallpaperManager = WallpaperManager
						.getInstance(ThemePickerActivity.this);
				if (position == mThemes.size()) {
				} else  {
					Intent intent = new Intent(ThemePickerActivity.this,ThemeEffectPreview.class);
					intent.putExtra(LauncherApplication.NAME_KEY, (String) mThemes.get(position).get(LauncherApplication.NAME_KEY));
					startActivity(intent);
				} /*else {
					// down load this theme
				}*/
			}
		});
	}

	@Override
	public  boolean onOptionsItemSelected(MenuItem item){
		switch(item.getItemId()){
			case android.R.id.home:
				this.finish();
				break;
			default:
				break;
			}
		return super.onOptionsItemSelected(item);
	}
	private class ImageAdapter extends BaseAdapter {
		private Context mContext;

		public ImageAdapter(Context context) {
			this.mContext = context;
		}

		@Override
		public int getCount() {
			return mThemes.size();
		}

		@Override
		public Object getItem(int position) {
				return mThemes.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ImageView previewImage = null;
			TextView themeName = null;
			ImageView check = null;
			if(null == convertView){
				convertView = LayoutInflater.from(mContext).inflate(R.layout.theme_grid_item, null);
			}
			 previewImage = (ImageView) convertView.findViewById(R.id.grid_item_image);
			 themeName = (TextView) convertView.findViewById(R.id.grid_item_theme_name);
			 check = (ImageView) convertView.findViewById(R.id.grid_item_check);
			HashMap<String,Object> map = mThemes.get(position);
			if(((String)map.get(LauncherApplication.NAME_KEY)).equals("default")){
				themeName.setText(ThemePickerActivity.this.getString(R.string.default_theme));
			}
			else
				themeName.setText((CharSequence) map.get(LauncherApplication.NAME_KEY));
			previewImage.setImageBitmap((Bitmap) map.get(LauncherApplication.BITMAP_KEY));
			String name = getSharedPreferences(
					LauncherApplication.SHARED_PREFRENCE,
					Context.MODE_PRIVATE).getString(LauncherApplication.NAME_KEY, "default");
			if(name.equals((String)map.get(LauncherApplication.NAME_KEY))){
				check.setVisibility(View.VISIBLE);
			}
			else{
				check.setVisibility(View.INVISIBLE);
			}
			return convertView;
			/*ImageView imageView;
			if (convertView == null) {
				imageView = new ImageView(mContext);
				imageView.setLayoutParams(new GridView.LayoutParams(280, 478));
				imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
				imageView.setPadding(8, 8, 8, 8);
			} else {
				imageView = (ImageView) convertView;
			}
			if (position == mThemesNames.size())
				imageView.setImageBitmap(BitmapFactory.decodeResource(
						ThemePickerActivity.this.getResources(),
						R.drawable.wallpaper_architecture));
			else
				imageView.setImageBitmap(mThemesBitmaps.get(position));
			return imageView;*/
		}

	}

	private void getThemes() {
		mThemes = new ArrayList<HashMap<String,Object>>();
		try {
			String[] themes = getAssets().list(getString(R.string.theme));
			for (String theme : themes) {
				String path = getString(R.string.theme)+"/"+theme+"/"
						+getString(R.string.preview)+"/"+"thumbnail.jpg";
				Bitmap bitmap = BitmapFactory
						.decodeStream(getAssets().open(path));
						HashMap<String,Object> map = new HashMap<String, Object>();
						map.put(LauncherApplication.NAME_KEY, theme);
						map.put(LauncherApplication.BITMAP_KEY, bitmap);
						if(theme.equals("default"))
							mThemes.add(0,map);
						else
							mThemes.add(map);
				}
	
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Log.i("messi",e.toString());
			e.printStackTrace();
		}
	}

	private String trimExtension(String filename) {
		if ((filename != null) && (filename.length() > 0)) {
			int i = filename.lastIndexOf('.');
			if ((i > -1) && (i < (filename.length()))) {
				return filename.substring(0, i);
			}
		}
		return null;
	}

	private boolean isFileEffect(String name) {
		File file = new File(name);
		if (file.exists() && file.isDirectory() && (file.list().length > 0))
			return true;
		else
			return false;

	}
}
