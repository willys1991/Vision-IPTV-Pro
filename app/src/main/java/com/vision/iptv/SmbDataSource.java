package com.vision.iptv;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;
import jcifs.CIFSContext;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbRandomAccessFile;
import java.io.IOException;

final class SmbDataSource extends BaseDataSource {
 private final String address,user,password;
 private CIFSContext context;
 private SmbFile file;
 private SmbRandomAccessFile reader;
 private Uri uri;
 private long remaining;
 private boolean opened;
 SmbDataSource(String address,String user,String password){super(true);this.address=address;this.user=user;this.password=password;}
 @Override public long open(DataSpec spec)throws IOException{
  transferInitializing(spec);
  try{uri=spec.uri;context=SmbStorage.context(user,password);file=new SmbFile(address,context);reader=new SmbRandomAccessFile(file,"r");long length=reader.length();if(spec.position>length)throw new IOException("Position outside file");reader.seek(spec.position);remaining=spec.length==C.LENGTH_UNSET?length-spec.position:Math.min(spec.length,length-spec.position);opened=true;transferStarted(spec);return remaining;}
  catch(Exception e){try{close();}catch(Exception ignored){}throw new IOException("Не удалось открыть файл SMB",e);}
 }
 @Override public int read(byte[] buffer,int offset,int length)throws IOException{
  if(length==0)return 0;if(remaining==0)return C.RESULT_END_OF_INPUT;
  int n=reader.read(buffer,offset,(int)Math.min(length,remaining));if(n<0)throw new IOException("Unexpected SMB EOF");remaining-=n;bytesTransferred(n);return n;
 }
 @Override public Uri getUri(){return uri;}
 @Override public void close()throws IOException{
  try{if(reader!=null)reader.close();}finally{reader=null;try{if(file!=null)file.close();}finally{file=null;try{if(context!=null)context.close();}catch(Exception e){throw new IOException(e);}finally{context=null;uri=null;if(opened){opened=false;transferEnded();}}}}
 }
}
