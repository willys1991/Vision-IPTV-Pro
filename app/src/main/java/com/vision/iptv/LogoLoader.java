package com.vision.iptv;
import android.graphics.*;import android.os.*;import android.util.LruCache;import android.widget.ImageView;import java.io.*;import java.net.*;import java.util.concurrent.*;
final class LogoLoader {
 private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(8*1024*1024){@Override protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
 private final ThreadPoolExecutor pool=new ThreadPoolExecutor(3,3,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.DiscardOldestPolicy());
 private volatile boolean closed=false;
 private volatile int maxPixels=256;
 void economical(boolean enabled){maxPixels=enabled?128:256;cache.resize((enabled?2:8)*1024*1024);}
 void trim(){cache.evictAll();pool.getQueue().clear();}
 private final Handler main=new Handler(Looper.getMainLooper());
 void bind(ImageView view,String url){view.setTag(url);view.setImageDrawable(null);if(closed||url==null||!(url.startsWith("http://")||url.startsWith("https://")))return;Bitmap cached=cache.get(url);if(cached!=null){view.setImageBitmap(cached);return;}java.lang.ref.WeakReference<ImageView> target=new java.lang.ref.WeakReference<>(view);pool.execute(()->{if(closed||target.get()==null)return;HttpURLConnection c=null;try{c=NetworkPolicy.open(new URL(url),5000,5000,"VisionIPTV/1.0",false,null);ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>2097152)return;bytes.write(b,0,n);}}byte[] data=bytes.toByteArray();BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);if(o.outWidth<=0||o.outHeight<=0)return;o.inJustDecodeBounds=false;o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>maxPixels)o.inSampleSize*=2;o.inPreferredConfig=Bitmap.Config.RGB_565;Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,o);if(bitmap==null||closed)return;cache.put(url,bitmap);main.post(()->{ImageView current=target.get();if(!closed&&current!=null&&url.equals(current.getTag()))current.setImageBitmap(bitmap);});}catch(Exception ignored){}finally{if(c!=null)c.disconnect();}});}
 void close(){closed=true;pool.shutdownNow();main.removeCallbacksAndMessages(null);cache.evictAll();}
}