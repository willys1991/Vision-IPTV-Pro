package com.vision.iptv;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
 if(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))return;
 if(!context.getSharedPreferences("vision",0).getBoolean("bootLaunch",false))return;
 try{Intent launch=new Intent(context,MainActivity.class);launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(launch);}catch(RuntimeException ignored){}
 }
}