package com.vision.iptv;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;

// Deliberately supports only clear MPEG-TS HLS media playlists.
final class LocalTimeshift implements Closeable {
 private static final long RESERVE=256L*1024*1024, SEGMENT_LIMIT=32L*1024*1024;
 private final File dir;
 private final long limit;
 private final String agent,token=UUID.randomUUID().toString();
 private final ArrayDeque<Segment> segments=new ArrayDeque<>();
 private final ServerSocket server;
 private final ExecutorService clients=Executors.newFixedThreadPool(2);
 private volatile boolean closed;
 private volatile HttpURLConnection upstream;
 private long bytes=0,last=-1;
 private int target=10;
 private static final class Segment {long seq;double duration;File file;Segment(long s,double d,File f){seq=s;duration=d;file=f;}}
 interface Listener {void ready(String url);void failed(String reason);}
 LocalTimeshift(File cache,long max,String userAgent)throws IOException{
  dir=new File(cache,"shift_"+token);if(!dir.mkdir())throw new IOException("Не удалось создать кеш");
  limit=max;agent=userAgent;
  try{server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));}catch(IOException e){dir.delete();throw e;}
 }
 void start(String address,Listener listener){
  new Thread(()->{while(!closed){try{Socket socket=server.accept();socket.setSoTimeout(5000);clients.execute(()->serve(socket));}catch(Exception e){if(!closed)close();}}},"ShiftHTTP").start();
  new Thread(()->{boolean ready=false;try{URL source=new URL(address);while(!closed){
   String playlist=readPlaylist(source);if(!playlist.startsWith("#EXTM3U")||playlist.contains("#EXT-X-STREAM-INF")||playlist.contains("#EXT-X-MAP")||playlist.contains("#EXT-X-BYTERANGE")||playlist.contains("#EXT-X-DISCONTINUITY")||playlist.contains("#EXT-X-ENDLIST")||playlist.contains("#EXT-X-GAP"))throw new IOException("Нужен прямой HLS MPEG-TS без разрывов");
   long seq=0;double duration=0;boolean sequence=false;int count=0;
   for(String raw:playlist.split("\\r?\\n")){String line=raw.trim();if(line.startsWith("#EXT-X-KEY:")&&!line.contains("METHOD=NONE"))throw new IOException("Шифрованный HLS не поддерживается");
    if(line.startsWith("#EXT-X-MEDIA-SEQUENCE:")){seq=Long.parseLong(line.substring(22).trim());sequence=true;}
    else if(line.startsWith("#EXT-X-TARGETDURATION:")){synchronized(this){target=Math.max(1,Math.min(60,Integer.parseInt(line.substring(22).trim())));}}
    else if(line.startsWith("#EXTINF:"))duration=Double.parseDouble(line.substring(8).split(",",2)[0]);
    else if(!line.isEmpty()&&!line.startsWith("#")&&duration>0){
     if(!sequence)throw new IOException("Нет номера сегмента");
     if(last>=0&&seq>last+1)throw new IOException("Пропущены сегменты");
     if(seq>last){append(seq,duration,new URL(source,line));last=seq;}seq++;count++;duration=0;
    }
   }
   if(count==0)throw new IOException("Нет сегментов");
   synchronized(this){if(!ready&&segments.size()>=3){ready=true;listener.ready("http://127.0.0.1:"+server.getLocalPort()+"/"+token+"/live.m3u8");}}
   Thread.sleep(Math.max(1000,target*500L));
  }}catch(Exception e){if(!closed)listener.failed(e.getMessage()==null?"Ошибка Timeshift":e.getMessage());}finally{close();}},"ShiftCapture").start();
 }
 private HttpURLConnection open(URL url)throws IOException{if(!"http".equals(url.getProtocol())&&!"https".equals(url.getProtocol()))throw new IOException("HTTP(S) only");return NetworkPolicy.open(url,10000,15000,agent,false,c->upstream=c);}
 private String readPlaylist(URL url)throws IOException{HttpURLConnection c=open(url);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(closed||out.size()+n>1048576)throw new IOException("Playlist limit");out.write(buf,0,n);}return out.toString("UTF-8");}finally{c.disconnect();upstream=null;}}
 private void append(long seq,double duration,URL url)throws IOException{
  if(closed||dir.getUsableSpace()<RESERVE+SEGMENT_LIMIT)throw new IOException("Недостаточно места");
  File file=new File(dir,seq+".ts");HttpURLConnection c=open(url);long size=0;
  try(InputStream in=c.getInputStream();OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1){if(closed||size+n>SEGMENT_LIMIT)throw new IOException("Segment limit");if(size==0&&(buf[0]&255)!=71)throw new IOException("Требуется MPEG-TS");out.write(buf,0,n);size+=n;}}finally{c.disconnect();upstream=null;}
  synchronized(this){if(closed){file.delete();return;}segments.add(new Segment(seq,duration,file));bytes+=size;while(bytes>limit&&segments.size()>1){Segment old=segments.removeFirst();bytes-=old.file.length();old.file.delete();}}
 }
 private void serve(Socket socket){try(Socket s=socket;BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.US_ASCII));OutputStream out=s.getOutputStream()){
  String request=in.readLine();if(request==null)return;String[] parts=request.split(" ");if(parts.length<3||!parts[0].equals("GET"))return;for(int i=0;i<64;i++){String line=in.readLine();if(line==null||line.isEmpty())break;}
  String path=parts[1];if(path.equals("/"+token+"/live.m3u8")){byte[] data; synchronized(this){StringBuilder p=new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:"+target+"\n#EXT-X-MEDIA-SEQUENCE:"+(segments.isEmpty()?0:segments.peekFirst().seq)+"\n");for(Segment seg:segments)p.append("#EXTINF:").append(seg.duration).append(",\n").append(seg.seq).append(".ts\n");data=p.toString().getBytes(StandardCharsets.UTF_8);}header(out,200,"application/vnd.apple.mpegurl",data.length);out.write(data);}
  else if(path.startsWith("/"+token+"/")){String name=path.substring(token.length()+2);FileInputStream file=null;long length=0;synchronized(this){for(Segment seg:segments)if(seg.file.getName().equals(name)){file=new FileInputStream(seg.file);length=seg.file.length();break;}}if(file==null){header(out,404,"text/plain",0);return;}try(FileInputStream stream=file){header(out,200,"video/mp2t",length);byte[] buf=new byte[65536];int n;while((n=stream.read(buf))!=-1)out.write(buf,0,n);}}
  else header(out,404,"text/plain",0);
 }catch(Exception ignored){}}
 private void header(OutputStream out,int status,String type,long length)throws IOException{out.write(("HTTP/1.1 "+status+(status==200?" OK":" Not Found")+"\r\nContent-Type: "+type+"\r\nContent-Length: "+length+"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));}
 @Override public synchronized void close(){if(closed)return;closed=true;try{server.close();}catch(Exception ignored){}clients.shutdownNow();HttpURLConnection c=upstream;if(c!=null)new Thread(c::disconnect).start();File[] files=dir.listFiles();if(files!=null)for(File file:files)file.delete();dir.delete();segments.clear();}
}
