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