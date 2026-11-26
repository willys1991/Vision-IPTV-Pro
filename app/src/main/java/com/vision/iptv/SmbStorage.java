package com.vision.iptv;
import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;
import java.io.*;
import java.util.Properties;
final class SmbStorage {
 static CIFSContext context(String user,String password)throws Exception {
 Properties p=new Properties();p.setProperty("jcifs.smb.client.minVersion","SMB202");p.setProperty("jcifs.smb.client.maxVersion","SMB311");p.setProperty("jcifs.smb.client.connTimeout","10000");p.setProperty("jcifs.smb.client.responseTimeout","20000");
 CIFSContext base=new BaseContext(new PropertyConfiguration(p));return user.isEmpty()?base.withAnonymousCredentials():base.withCredentials(new NtlmPasswordAuthenticator(user,password));
 }
 static final class Entry {final String name,address;final boolean directory;Entry(String n,String a,boolean d){name=n;address=a;directory=d;}}
 static java.util.List<Entry> list(String address,String user,String password)throws Exception{
 if(!address.startsWith("smb://"))throw new IOException("Invalid SMB address");
 CIFSContext ctx=context(user,password);java.util.List<Entry> result=new java.util.ArrayList<>();
 try(SmbFile dir=new SmbFile(address,ctx)){if(!dir.isDirectory())throw new IOException("Not a directory");SmbFile[] children=dir.listFiles();if(children==null)return result;try{for(SmbFile child:children){if(Thread.currentThread().isInterrupted())throw new IOException("Cancelled");boolean folder=child.isDirectory();String name=child.getName();if(folder||name.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(mp4|mkv|ts|m2ts|avi|mov|webm|mpg|mpeg|m4v|mp3|flac|aac|wav)$"))result.add(new Entry(name,child.getPath(),folder));if(result.size()>=3000)break;}}finally{for(SmbFile child:children)try{child.close();}catch(Exception ignored){}}}finally{ctx.close();}
 result.sort((a,b)->a.directory!=b.directory?(a.directory?-1:1):a.name.compareToIgnoreCase(b.name));return result;
 }
 static void download(String address,String user,String password,File target,long limit)throws Exception {
 if(!address.startsWith("smb://"))throw new IOException("Invalid SMB address");
 CIFSContext ctx=context(user,password);try(SmbFile remote=new SmbFile(address,ctx)){long length=remote.length();if(length<=0||length>limit)throw new IOException("File size limit");try(InputStream in=remote.getInputStream();OutputStream out=new FileOutputStream(target)){copy(in,out,limit);}}finally{ctx.close();}
 }
 static void upload(String address,String user,String password,File source)throws Exception {
 if(!address.startsWith("smb://"))throw new IOException("Invalid SMB address");
 CIFSContext ctx=context(user,password);try(SmbFile remote=new SmbFile(address,ctx)){if(remote.exists())throw new IOException("File already exists");try(InputStream in=new FileInputStream(source);OutputStream out=remote.getOutputStream()){copy(in,out,source.length());}}finally{ctx.close();}
 }
 static OutputStream openRecording(String address)throws Exception {
 if(!address.startsWith("smb://"))throw new IOException("Invalid SMB address");
 CIFSContext ctx=context("","");SmbFile remote=null;
 try{remote=new SmbFile(address,ctx);if(remote.exists())throw new IOException("File already exists");OutputStream out=remote.getOutputStream();final SmbFile target=remote;return new FilterOutputStream(out){@Override public void write(byte[] b,int off,int len)throws IOException{out.write(b,off,len);}@Override public void close()throws IOException{try{super.close();}finally{try{target.close();}finally{try{ctx.close();}catch(Exception e){throw new IOException(e);}}}}};}catch(Exception e){if(remote!=null)try{remote.close();}catch(Exception ignored){}try{ctx.close();}catch(Exception ignored){}throw e;}
 }
 static void copy(InputStream in,OutputStream out,long limit)throws IOException {byte[] buffer=new byte[65536];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>limit)throw new IOException("Size limit");out.write(buffer,0,n);}}
}