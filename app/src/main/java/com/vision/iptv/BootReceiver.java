package com.vision.iptv;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
 if(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))return;
 android.content.SharedPreferences prefs=context.getSharedPreferences("vision",0);
 try{org.json.JSONArray a=new org.json.JSONArray(prefs.getString("scheduled","[]"));long now=System.currentTimeMillis();boolean pending=false;for(int i=a.length()-1;i>=0;i--){if(a.getJSONObject(i).getLong("end")<=now)a.remove(i);else pending=true;}prefs.edit().putString("scheduled",a.toString()).apply();if(pending){Intent service=new Intent(context,RecordingService.class).setAction("schedule");if(android.os.Build.VERSION.SDK_INT>=26)context.startForegroundService(service);else context.startService(service);}}catch(Exception ignored){}
 if(!prefs.getBoolean("bootLaunch",false))return;
 try{Intent launch=new Intent(context,MainActivity.class);launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(launch);}catch(RuntimeException ignored){}
 }
}