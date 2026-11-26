package com.vision.iptv;
import java.net.*;
import java.io.*;

final class NetworkPolicy {
 interface Observer {void opened(HttpURLConnection c);}
 static HttpURLConnection open(URL initial,int connect,int read,String agent,boolean sameOrigin,Observer observer)throws IOException{
  URL url=initial;
  for(int hop=0;hop<=5;hop++){
   String protocol=url.getProtocol();
   if(!("http".equals(protocol)||"https".equals(protocol))||url.getHost().isEmpty()||url.getUserInfo()!=null)throw new IOException("Invalid HTTP URL");
   HttpURLConnection c=(HttpURLConnection)url.openConnection();
   c.setInstanceFollowRedirects(false);c.setConnectTimeout(connect);c.setReadTimeout(read);c.setUseCaches(false);
   if(agent!=null&&!agent.isEmpty())c.setRequestProperty("User-Agent",agent);
   if(observer!=null)observer.opened(c);
   boolean keep=false;
   try{
    int status=c.getResponseCode();
    if(status==301||status==302||status==303||status==307||status==308){
     String location=c.getHeaderField("Location");if(location==null||location.isEmpty()||hop==5)throw new IOException("Redirect limit or missing location");
     URL next=new URL(url,location);
     if("https".equals(protocol)&&!"https".equals(next.getProtocol()))throw new IOException("Insecure redirect blocked");
     if(sameOrigin&&(!url.getHost().equalsIgnoreCase(next.getHost())||port(url)!=port(next)||!protocol.equals(next.getProtocol())))throw new IOException("Cross-origin credential redirect blocked");
     url=next;continue;
    }
    if(status<200||status>=300)throw new IOException("HTTP status "+status);
    keep=true;return c;
   }finally{if(!keep)c.disconnect();}
  }
  throw new IOException("Redirect limit");
 }
 private static int port(URL u){return u.getPort()<0?u.getDefaultPort():u.getPort();}
}
