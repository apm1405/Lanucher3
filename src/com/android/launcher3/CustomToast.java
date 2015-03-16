package com.android.launcher3;


import java.util.Timer;
import java.util.TimerTask;

import com.android.internal.R.color;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Message;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AlphaAnimation;
import android.widget.LinearLayout;
import android.widget.TextView;


public class CustomToast {
	private Context mContext;
	private View mView;
	private int mDuration;
	private boolean isHide=true;
	private WindowManager.LayoutParams mParams = new WindowManager.LayoutParams();
	private WindowManager manager;
	
	
	private Handler handler=new Handler(){
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 0:
				   hide();
				   isHide=true;
				break;
			}
		}    	
    };
    
     public CustomToast(Context context){
    	 this.mContext=context;
     }
     
     
     
     public static CustomToast makeText(Context context,String text, int duration){
    	CustomToast ct=new CustomToast(context);
    	 ct.mDuration=duration;
    	 ct.mView=View.inflate(context, R.layout.custom_toast, null);
    	 TextView tv=(TextView) ct.mView.findViewById(R.id.tv_toast);
    	 tv.setText(text);
    	 return ct;
     }
     
     
     public void show(){
       if( isHide==true){
    	 manager=(WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    	 Display display= manager.getDefaultDisplay();
         int screenHeight=display.getHeight();
         int height=mView.getHeight();
    	 WindowManager.LayoutParams params = mParams;
         params.height = WindowManager.LayoutParams.WRAP_CONTENT;
         params.width = WindowManager.LayoutParams.WRAP_CONTENT;
         params.format = PixelFormat.TRANSLUCENT;
         params.windowAnimations=com.android.internal.R.style.Animation_Toast;
         params.type = WindowManager.LayoutParams.TYPE_TOAST;
         params.setTitle("Toast");
         params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                 | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                 | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
         params.privateFlags = WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
         params.y=250; 
         manager.addView(mView, params);
         isHide=false;
         }else {
 	    return;
 	}
         TimerTask task=new TimerTask() {
 			public void run() {
 				Message msg=Message.obtain();
 				msg.what=0;
 				handler.sendMessage(msg);
 			}
 		};
 		Timer timer=new Timer();
 		timer.schedule(task,mDuration);
     }
     
     
     
     public void hide(){
        if(mView!=null){
 	   manager.removeView(mView);
        }
        mView=null;
     }
}
