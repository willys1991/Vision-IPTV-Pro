package com.vision.iptv;
import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
final class Recorder {
 static final long RESERVE=256L*1024*1024;
 volatile boolean running;
 volatile HttpURLConnection connection;
 volatile Thread worker;
 volatile String lastError="";
 volatile Runnable retentionCleanup; volatile int maxHeight=0; volatile long quotaBytes=0; private long written=0,existing=0;
 volatile String userAgent="VisionIPTV/1.0";
 private HttpURLConnection open(URL url)throws IOException{
  if(!"http".equals(url.getProtocol())&&!"https".equals(url.getProtocol()))throw new IOException("HTTP(S) only");
  HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent",userAgent);connection=c;return c;
 }
 private void quota(long next)throws IOException{if(quotaBytes>0&&existing+written+next>quotaBytes)throw new IOException("Достигнут лимит записей");}
 private void space(File parent)throws IOException{if(parent==null||parent.getUsableSpace()<RESERVE)throw new IOException("Недостаточно свободного места");}
 synchronized boolean start(String address,File file,Runnable finished){
  if(worker!=null)return false;
  lastError="";running=true;written=0;existing=0;
  worker=new Thread(()->{
   try{Runnable cleanup=retentionCleanup;if(cleanup!=null)cleanup.run();File[] previous=file.getParentFile().listFiles((d,n)->n.endsWith(".ts"));if(previous!=null)for(File old:previous)existing+=old.length();space(file.getParentFile());if(!running)return;URL url=new URL(address);HttpURLConnection c=open(url);
    try(BufferedInputStream in=new BufferedInputStream(c.getInputStream())){
     URL resolved=c.getURL();in.mark(16);byte[] head=new byte[7];int n=0;while(n<head.length){int k=in.read(head,n,head.length-n);if(k<0)break;n+=k;}in.reset();
     if(n==7&&"#EXTM3U".equals(new String(head,StandardCharsets.US_ASCII))){String playlist=readText(in);c.disconnect();connection=null;recordHls(resolved,playlist,file);}
     else if(n>0&&(head[0]&255)==71){try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){byte[] b=new byte[65536];int k;long check=0;while(running&&(k=in.read(b))!=-1){if(!running)break;if(check<=0){space(file.getParentFile());check=4L*1024*1024;}quota(k);out.write(b,0,k);written+=k;check-=k;}}}
     else throw new IOException("Поддерживаются MPEG-TS и HLS MPEG-TS");
    }finally{c.disconnect();}
   }catch(Exception e){if(running)lastError=e.getMessage()==null?"Ошибка записи":e.getMessage();}
   finally{HttpURLConnection c=connection;if(c!=null)c.disconnect();if(file.length()==0)file.delete();synchronized(this){connection=null;running=false;worker=null;}finished.run();}
  },"VisionRecorder");worker.start();return true;
 }
 private String readText(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while(running&&(n=in.read(b))!=-1){if(out.size()+n>2097152)throw new IOException("Слишком большой HLS плейлист");out.write(b,0,n);}return out.toString("UTF-8");}
 private static final class Playlist{URL url;String text;Playlist(URL u,String t){url=u;text=t;}}
 private Playlist fetch(URL url)throws IOException{HttpURLConnection c=open(url);try(InputStream in=c.getInputStream()){return new Playlist(c.getURL(),readText(in));}finally{c.disconnect();connection=null;}}
 private long bandwidth(String line){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:^|[:,])BANDWIDTH=([0-9]+)").matcher(line);try{return m.find()?Long.parseLong(m.group(1)):0;}catch(Exception e){return 0;}}
 private int height(String line){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:^|[:,])RESOLUTION=[0-9]+x([0-9]+)").matcher(line);try{return m.find()?Integer.parseInt(m.group(1)):0;}catch(Exception e){return 0;}}
 private Playlist media(Playlist p)throws IOException{
  for(int depth=0;depth<3&&p.text.contains("#EXT-X-STREAM-INF");depth++){long best=-1;URL chosen=null;String info=null;for(String raw:p.text.split("\\r?\\n")){String line=raw.trim();if(line.startsWith("#EXT-X-STREAM-INF:"))info=line;else if(!line.isEmpty()&&!line.startsWith("#")&&info!=null){long bw=bandwidth(info);if(!info.contains("AUDIO=")&&(maxHeight==0||(height(info)>0&&height(info)<=maxHeight))&&bw>best){best=bw;chosen=new URL(p.url,line);}info=null;}}if(chosen==null)throw new IOException("Нет совместимого HLS варианта выбранного качества");p=fetch(chosen);}
  if(p.text.contains("#EXT-X-STREAM-INF"))throw new IOException("Вложенный HLS");return p;
 }
 private void recordHls(URL url,String text,File file)throws Exception{
  Playlist p=media(new Playlist(url,text));long last=-1;int idle=0;
  try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){
   while(running){
    if(!p.text.startsWith("#EXTM3U")||p.text.contains("#EXT-X-MAP")||p.text.contains("#EXT-X-BYTERANGE")||p.text.contains("#EXT-X-DISCONTINUITY")||p.text.contains("#EXT-X-GAP"))throw new IOException("Неподдерживаемый тип HLS");
    long seq=0;boolean hasSeq=false,segment=false;int target=6,added=0;
    for(String raw:p.text.split("\\r?\\n")){
     if(!running)break;String line=raw.trim();
     if(line.startsWith("#EXT-X-KEY:")&&!line.contains("METHOD=NONE"))throw new IOException("Шифрованный HLS не поддерживается");
     if(line.startsWith("#EXT-X-MEDIA-SEQUENCE:")){seq=Long.parseLong(line.substring(22).trim());hasSeq=true;}
     else if(line.startsWith("#EXT-X-TARGETDURATION:"))target=Math.max(1,Math.min(60,Integer.parseInt(line.substring(22).trim())));
     else if(line.startsWith("#EXTINF:"))segment=true;
     else if(segment&&!line.isEmpty()&&!line.startsWith("#")){if(!hasSeq&&!p.text.contains("#EXT-X-ENDLIST"))throw new IOException("Нет номера сегмента");if(last>=0&&seq>last+1)throw new IOException("Пропущены сегменты HLS");if(seq>last){space(file.getParentFile());if(!copySegment(new URL(p.url,line),file,out))break;last=seq;added++;}seq++;segment=false;}
    }
    out.flush();if(!running||p.text.contains("#EXT-X-ENDLIST"))break;if(added==0){if(++idle>8)throw new IOException("HLS перестал обновляться");}else idle=0;
    Thread.sleep(Math.max(1000,target*500L));if(running)p=fetch(p.url);
   }
  }
 }
 private boolean copySegment(URL url,File file,OutputStream out)throws IOException{
  File tmp=File.createTempFile("segment_",".tmp",file.getParentFile());HttpURLConnection c=null;
  try{c=open(url);long total=0;try(InputStream in=c.getInputStream();OutputStream temp=new BufferedOutputStream(new FileOutputStream(tmp))){byte[] b=new byte[65536];int n;while(running&&(n=in.read(b))!=-1){if(total==0&&(b[0]&255)!=71)throw new IOException("Сегмент не MPEG-TS");if(total+n>64L*1024*1024)throw new IOException("Слишком большой сегмент");space(file.getParentFile());temp.write(b,0,n);total+=n;}}
   if(!running)return false;if(total==0||total%188!=0)throw new IOException("Повреждённый MPEG-TS сегмент");if(file.getParentFile().getUsableSpace()<RESERVE+total)throw new IOException("Недостаточно места");
   quota(total);try(InputStream in=new FileInputStream(tmp)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}written+=total;return true;
  }finally{if(c!=null)c.disconnect();connection=null;tmp.delete();}
 }
 void stop(){running=false;HttpURLConnection c=connection;if(c!=null)new Thread(c::disconnect).start();Thread t=worker;if(t!=null)t.interrupt();}
}
