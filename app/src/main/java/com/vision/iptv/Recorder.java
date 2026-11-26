package com.vision.iptv;
import java.io.*;
import java.net.*;
final class Recorder {
 static final long RESERVE=256L*1024*1024;
 volatile boolean running;
 volatile HttpURLConnection connection;
 volatile Thread worker;
 volatile String lastError="";
 synchronized boolean start(String address,File file,Runnable finished){
  if(worker!=null)return false;
  lastError="";running=true;
  worker=new Thread(()->{
   HttpURLConnection local=null;
   try{
    File parent=file.getParentFile();
    if(parent==null||parent.getUsableSpace()<RESERVE)throw new IOException("Недостаточно свободного места");
    URL url=new URL(address);
    if(!url.getProtocol().equals("http")&&!url.getProtocol().equals("https"))throw new IOException("Неподдерживаемый протокол");
    local=(HttpURLConnection)url.openConnection();
    connection=local;local.setConnectTimeout(15000);local.setReadTimeout(10000);
    if(!running)return;
    try(InputStream in=local.getInputStream()){
     String type=local.getContentType();
     if(type!=null&&(type.contains("mpegurl")||type.contains("text/")||type.contains("dash")))throw new IOException("Требуется MPEG-TS");
     byte[] packet=new byte[188];new DataInputStream(in).readFully(packet);
     if((packet[0]&255)!=71)throw new IOException("Требуется MPEG-TS");
     if(!running)return;
     try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){
      out.write(packet);byte[] buffer=new byte[65536];int n;long check=0;
      while(running&&(n=in.read(buffer))!=-1){
       if(!running)break;
       if(check<=0){if(parent.getUsableSpace()<RESERVE)throw new IOException("Запись остановлена: мало места");check=4L*1024*1024;}
       out.write(buffer,0,n);check-=n;
      }
     }
    }
   }catch(Exception e){if(running)lastError=e.getMessage()==null?"Ошибка записи":e.getMessage();}
   finally{
    if(local!=null)local.disconnect();
    if(file.length()==0)file.delete();
    synchronized(this){connection=null;running=false;worker=null;}
    finished.run();
   }
  },"VisionRecorder");worker.start();return true;
 }
 void stop(){running=false;HttpURLConnection c=connection;if(c!=null)new Thread(c::disconnect).start();}
}
