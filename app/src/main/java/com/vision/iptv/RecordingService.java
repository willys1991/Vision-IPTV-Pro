package com.vision.iptv;
import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import org.json.*;

public final class RecordingService extends Service {
 static volatile boolean active=false;
 static final String CHANNEL="vision_recording";
 private final Recorder recorder=new Recorder();
 private final Handler handler=new Handler(Looper.getMainLooper());
 private SharedPreferences prefs;
 private PowerManager.WakeLock wake;
 private long end=0;
 private String title="";
 private boolean stopping=false;
 private final Runnable tick=new Runnable(){public void run(){check();if(!stopping)handler.postDelayed(this,5000);}};
 @Override public void onCreate(){super.onCreate();prefs=getSharedPreferences("vision",MODE_PRIVATE);if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Запись IPTV",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"VisionIPTV:recording");wake.setReferenceCounted(false);}
 private Notification notification(String message){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);Intent stop=new Intent(this,RecordingService.class).setAction("stop");PendingIntent si=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return b.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Vision IPTV Pro").setContentText(message).setContentIntent(pi).setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Остановить запись",si).build();}
 private void show(String message){getSystemService(NotificationManager.class).notify(81,notification(message));}
 @Override public int onStartCommand(Intent intent,int flags,int id){startForeground(81,notification("Подготовка записи"));if(intent!=null&&"stop".equals(intent.getAction())){recorder.stop();end=0;}else if(intent!=null&&"record".equals(intent.getAction())&&recorder.worker==null){String url=intent.getStringExtra("url");if(url!=null)begin(url,intent.getStringExtra("title"),0);}handler.removeCallbacks(tick);handler.post(tick);return START_NOT_STICKY;}
 private void begin(String url,String name,long until){File dir=new File(getFilesDir(),"recordings");dir.mkdirs();File file=new File(dir,System.currentTimeMillis()+".ts");title=name==null?"Канал":name;end=until;recorder.userAgent=prefs.getString("streamUserAgent","VisionIPTV/1.0");if(recorder.start(url,file,()->handler.post(()->{active=false;end=0;prefs.edit().putString("lastRecordingResult",recorder.lastError.isEmpty()?(file.length()>0?"Запись сохранена":"Запись не создана"):"Ошибка записи: "+recorder.lastError).apply();if(!stopping)check();}))){active=true;if(!wake.isHeld())wake.acquire(12L*60*60*1000);show("Запись • "+title);}}
 private void check(){if(stopping)return;long now=System.currentTimeMillis();if(recorder.worker!=null){if(end>0&&now>=end)recorder.stop();return;}boolean pending=false;try{JSONArray a=new JSONArray(prefs.getString("scheduled","[]"));boolean changed=false;for(int i=a.length()-1;i>=0;i--){JSONObject o=a.getJSONObject(i);if(o.getLong("end")<=now){a.remove(i);changed=true;continue;}pending=true;if(o.getLong("start")<=now&&recorder.worker==null){begin(o.getString("url"),o.optString("title"),o.getLong("end"));a.remove(i);changed=true;}}if(changed)prefs.edit().putString("scheduled",a.toString()).apply();pending=a.length()>0;}catch(Exception ignored){}if(recorder.worker==null&&!pending){stopping=true;stopForeground(true);stopSelf();return;}if(!wake.isHeld())wake.acquire(12L*60*60*1000);if(recorder.worker==null)show("Ожидание записи по расписанию");}
 @Override public void onDestroy(){stopping=true;handler.removeCallbacksAndMessages(null);recorder.stop();active=false;if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}
