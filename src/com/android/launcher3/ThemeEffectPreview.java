package com.android.launcher3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import android.os.AsyncTask;
import android.R.integer;
import android.app.ActionBar;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageParser.NewPermissionInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.view.WindowManager;
import java.lang.reflect.Method;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.content.ContentValues;
import android.database.Cursor;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
/**
*add 20140902 for HOSIN_CUST_LAUNCHER_MESSI
*/
public class ThemeEffectPreview extends Activity implements OnClickListener{
	private ViewPager viewPager;
	private List<View> views;
	private ImageView[] imageViews;
	private LinearLayout imageLayout;
	private Button applyBtn;
	private SharedPreferences sharedPreferences;
	private MyViewPageAdapter pageAdapter;
	private String theme;
	private MenuItem refreshItem;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.theme_effect_preview);
		initLayout();
		initViews();
	}
	private void initLayout(){
		viewPager = (ViewPager) findViewById(R.id.theme_effect_view_pager);
		imageLayout = (LinearLayout) findViewById(R.id.theme_effect_image_linear);
		applyBtn = (Button) findViewById(R.id.theme_effect_btn);
		applyBtn.setOnClickListener(this);
		imageViews = new ImageView[2];
		imageViews[0]= (ImageView) imageLayout.getChildAt(0);
		imageViews[1]= (ImageView) imageLayout.getChildAt(1);
		
	}
	private void initViews(){
		views = new ArrayList<View>();
		sharedPreferences = getSharedPreferences(LauncherApplication.SHARED_PREFRENCE, Context.MODE_PRIVATE);
		theme  = getIntent().getStringExtra(LauncherApplication.NAME_KEY);
		ActionBar actionBar = getActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setTitle(theme);
		actionBar.setDisplayUseLogoEnabled(false);
		actionBar.setDisplayShowHomeEnabled(false);
		try {
			Bitmap previewBitmap_01 = BitmapFactory.decodeStream(getAssets().open(getString(R.string.theme)+"/"+theme+"/"+getString(R.string.preview)
					+"/"+"preview_01.jpg"));
			Bitmap previewBitmap_02 = BitmapFactory.decodeStream(getAssets().open(getString(R.string.theme)+"/"+theme+"/"+getString(R.string.preview)
					+"/"+"preview_02.jpg"));
			ImageView imageView_01 = new ImageView(this);
			imageView_01.setImageBitmap(previewBitmap_01);
			ImageView imageView_02 = new ImageView(this);
			imageView_02.setImageBitmap(previewBitmap_02);
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
			params.gravity = Gravity.CENTER;
			views.add(imageView_01);
			views.add(imageView_02);
			pageAdapter = new MyViewPageAdapter(views);
			viewPager.setAdapter(pageAdapter);
			viewPager.setCurrentItem(0);
			viewPager.setOnPageChangeListener(new MyOnPageChangeListener());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	private void setRefreshActionButtonState(boolean refreshing){
		if(refreshItem!=null){
			if(refreshing){
				refreshItem.setVisible(true);
				refreshItem.setActionView(R.layout.actionbar_indeterminate_progress);
			}
			else{
				refreshItem.setVisible(false);
				refreshItem.setActionView(null);
			}
		}
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		// TODO Auto-generated method stub
		getMenuInflater().inflate(R.menu.main_content,menu);
		refreshItem = menu.findItem(R.id.menu_refresh);
		setRefreshActionButtonState(false);
		return super.onCreateOptionsMenu(menu);
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// TODO Auto-generated method stub
		switch (item.getItemId()) {
		case android.R.id.home:
			this.finish();
			break;

		default:
			break;
		}
		
		return super.onOptionsItemSelected(item);
	}
	private class MyViewPageAdapter extends PagerAdapter{
		 public List<View> mListViews;
		 
         public MyViewPageAdapter(List<View> listViews) {
             this.mListViews = listViews;
         }

		@Override
		public int getCount() {
			// TODO Auto-generated method stub
			return mListViews.size();
		}

		@Override
		public boolean isViewFromObject(View arg0, Object arg1) {
			// TODO Auto-generated method stub
			return  arg0 == (arg1);
		}
		@Override
         public void destroyItem(View arg0, int arg1, Object arg2) {
             ((ViewPager) arg0).removeView(mListViews.get(arg1));
        }
 
         @Override
         public Object instantiateItem(View arg0, int arg1) {
             ((ViewPager) arg0).addView(mListViews.get(arg1), 0);
              return mListViews.get(arg1);
          }
	}
	private class MyOnPageChangeListener implements OnPageChangeListener{

		@Override
		public void onPageScrollStateChanged(int arg0) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onPageScrolled(int arg0, float arg1, int arg2) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onPageSelected(int arg0) {
			// TODO Auto-generated method stub
			for(int i=0;i<imageViews.length;i++){
				if(arg0==i){
					imageViews[i].setImageResource(R.drawable.dot_select);
				}
				else {
					imageViews[i].setImageResource(R.drawable.dot_unselect);
				}
			}
		}
		
	}
	    protected  class WallPaperSetTask extends AsyncTask<Void, Void, Boolean> {
			private String theme;
			private Context mContext;
			public WallPaperSetTask(String theme,Context context){
				this.theme = theme;
				mContext = context;
				}
			  @Override
        	protected Boolean doInBackground(Void... params) {
        	WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
		try {
			String path = mContext.getString(R.string.theme)+"/"+theme+"/"
					+mContext.getString(R.string.wallpaper)+"/"+mContext.getString(R.string.wallpaper)+".jpg";
			wallpaperManager.setStream(mContext.getAssets().open(path));
			/*Bitmap bitmap = BitmapFactory.decodeStream(getAssets().open(path));
			int width = Utilities.getScreenWidth(ThemeEffectPreview.this);
			int height = Utilities.getScreenHeight(ThemeEffectPreview.this);
			int x = (bitmap.getWidth() -width)/2;
			int y = (bitmap.getHeight() - height)/2;
			Bitmap bitmapCrop = Bitmap.createBitmap(bitmap,x,y,width,height);
;			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			bitmapCrop.compress(CompressFormat.JPEG,85,bos);
			String[] array = mContext.getResources().getStringArray(R.array.wallpaper_chooser);*/
			ContentValues value_lock_screen = new ContentValues();
			value_lock_screen.put("_id",1);
			value_lock_screen.put("flag",0);
			ContentValues value_main_menu = new ContentValues();
			value_main_menu.put("_id",2);
			value_main_menu.put("flag",0);
			Uri url = Uri.parse("content://"+"com.android.launcher3.settings"+"/"+"wallpaper");	
			Log.i("messi","insert start");
			Uri newUrl_lock_screen = ThemeEffectPreview.this.getContentResolver().insert(url,value_lock_screen);
			Uri newUrl_main_menu = ThemeEffectPreview.this.getContentResolver().insert(url,value_main_menu);
			if(newUrl_lock_screen == null){
				Log.i("messi","update start");
				mContext.getContentResolver().update(url,value_lock_screen,"_id =  ?",new String[]{"1"});
			}
			if(newUrl_main_menu == null){
				Log.i("messi","update start");
				mContext.getContentResolver().update(url,value_main_menu,"_id =  ?",new String[]{"2"});
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
			return true;
        	}
		@Override
			protected void onPreExecute(){
				setRefreshActionButtonState(true);
				applyBtn.setClickable(false);
		sharedPreferences.edit()
		.putString(LauncherApplication.NAME_KEY,  getIntent().getStringExtra(LauncherApplication.NAME_KEY)).commit();
			LauncherApplication.themeChanged = true;
			}
        	@Override
        	protected void onPostExecute(Boolean result) {
        	String path = mContext.getString(R.string.theme)+"/"+theme+"/"
					+mContext.getString(R.string.wallpaper)+"/"+mContext.getString(R.string.wallpaper)+".jpg";
			Bitmap bitmap = null;
			try{
				 bitmap = BitmapFactory.decodeStream(mContext.getAssets().open(path));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Drawable d = new BitmapDrawable(bitmap);
        		setKeyguardBg(d);
        		Intent intent = new Intent(ThemeEffectPreview.this,Launcher.class);
			startActivity(intent);
			finish();
			android.os.Process.killProcess(android.os.Process.myPid());
        	}

		}
 //Hosin_messi add start
public void setKeyguardBg(Drawable d) {
		// TODO Auto-generated method stub
		try {
			Log.i("messi","setKeyguardViewBg theme ");
			Object service = getSystemService("keyguard");
			Class<?> keyguardViewManager= Class
					.forName("com.android.keyguard.KeyguardViewManager");
			Method setbg = null;
			if (service != null) {
					Log.i("messi","setKeyguardViewBg->>service != null");
					setbg = keyguardViewManager.getMethod("setKeyguardViewBg",Drawable.class);
				setbg.setAccessible(true);
				setbg.invoke(service,d);
			}

		} catch (Exception e) {
		}

	}
	// Hosin_messi add end
	private  void saveWallPaper(){
		
	
	}
	@Override
	public void onClick(View v) {
	/*try{
	WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
	String path = getString(R.string.theme)+"/"+theme+"/"
					+getString(R.string.wallpaper)+"/"+getString(R.string.wallpaper)+".jpg";
			wallpaperManager.setStream(getAssets().open(path));
			Bitmap bitmap = BitmapFactory.decodeStream(getAssets().open(path));
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			bitmap.compress(CompressFormat.JPEG,85,bos);
	ContentValues values = new ContentValues();
			values.put("_id",1);
			values.put("type","wallpaper");
			values.put("wallpaper",bos.toByteArray());
			Uri url = Uri.parse("content://"+"com.android.launcher3.settings"+"/"+"wallpaper");	
			Log.i("messi","insert start");
			Uri newUrl =getContentResolver().insert(url,values);
			Log.i("messi","insert end: "+newUrl);
			if(newUrl == null){
				Log.i("messi","update start");
				getContentResolver().update(url,values,null,null);
			}
			} catch (Exception e) {
		}
	*/
	WallPaperSetTask task = new WallPaperSetTask(theme,this);
	task.execute();
		// TODO Auto-generated method stub
	//	setRefreshActionButtonState(true);
		//WindowManager manager = getWindowManager();
		//WindowManager.LayoutParams params = new WindowManager.LayoutParams();
	//	params.gravity =Gravity.CENTER;
	//	View view = getLayoutInflater().inflate(R.layout.actionbar_indeterminate_progress, null);
		//manager.addView(view, params );
	//	applyBtn.setClickable(false);
	//	sharedPreferences.edit()
		//.putString(LauncherApplication.NAME_KEY,  getIntent().getStringExtra(LauncherApplication.NAME_KEY)).commit();
		//	LauncherApplication.themeChanged = true;
	//	Intent intent = new Intent(this,Launcher.class);
	//	intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		//startActivity(intent);
	//	android.os.Process.killProcess(android.os.Process.myPid());
	//	Intent i = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
	//	i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		//startActivity(i);
	}
}
