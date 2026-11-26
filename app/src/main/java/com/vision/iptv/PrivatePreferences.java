package com.vision.iptv;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class PrivatePreferences implements SharedPreferences {
 private static final String PREFIX="vision:gcm:1:";
 private static SecretKey dataKey;
 private final SharedPreferences raw;
 private final Context context;
 static PrivatePreferences open(Context c){return new PrivatePreferences(c.getApplicationContext());}
 private PrivatePreferences(Context c){context=c;raw=c.getSharedPreferences("vision",Context.MODE_PRIVATE);}
 private static synchronized SecretKey key(Context c)throws Exception{
  if(dataKey!=null)return dataKey;
  SharedPreferences meta=c.getSharedPreferences("vision_crypto",Context.MODE_PRIVATE);
  if(meta.getBoolean("initialized",false)){
   String[] saved=NasSecrets.read(c,"playlist_data_key_v1");
   dataKey=new SecretKeySpec(Base64.decode(saved[0],Base64.NO_WRAP),"AES");
  }else{
   // If the wrapped key already exists after an interrupted initialization, reuse it.
   String[] saved=null;
   try{saved=NasSecrets.read(c,"playlist_data_key_v1");}catch(Exception ignored){}
   if(saved!=null)dataKey=new SecretKeySpec(Base64.decode(saved[0],Base64.NO_WRAP),"AES");
   else{KeyGenerator gen=KeyGenerator.getInstance("AES");gen.init(256);SecretKey generated=gen.generateKey();NasSecrets.save(c,"playlist_data_key_v1",Base64.encodeToString(generated.getEncoded(),Base64.NO_WRAP),"");dataKey=generated;}
   if(!meta.edit().putBoolean("initialized",true).commit()){dataKey=null;throw new java.io.IOException("Key initialization failed");}
  }
  return dataKey;
 }
 static boolean sensitive(String k){return k.equals("playlist")||k.startsWith("p_")||k.startsWith("url_")||k.startsWith("archiveTemplate_")||k.startsWith("backupStream_")||k.equals("epgUrl")||k.equals("history")||k.equals("lastChannel")||k.equals("scheduled")||k.equals("reminders")||k.equals("favorites");}
 private String seal(String k,String value){try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(context));cipher.updateAAD(k.getBytes(StandardCharsets.UTF_8));return PREFIX+Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);}catch(Exception e){throw new IllegalStateException("Encrypted storage unavailable",e);}}
 private String unseal(String k,String value){try{String[] parts=value.substring(PREFIX.length()).split(":",2);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(context),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));cipher.updateAAD(k.getBytes(StandardCharsets.UTF_8));return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("Encrypted storage unavailable",e);}}
 @Override public String getString(String k,String fallback){String value=raw.getString(k,fallback);if(value==null||!sensitive(k)||!raw.contains(k))return value;if(value.startsWith(PREFIX))return unseal(k,value);try{String encrypted=seal(k,value);raw.edit().putString(k,encrypted).commit();}catch(Exception ignored){}return value;}
 @Override public Set<String> getStringSet(String k,Set<String> fallback){if(!sensitive(k))return raw.getStringSet(k,fallback);if(!raw.contains(k))return fallback;try{Object old=raw.getAll().get(k);if(old instanceof Set){Set<String> result=new HashSet<>();for(Object value:(Set<?>)old)result.add((String)value);try{edit().putStringSet(k,result).commit();}catch(Exception ignored){}return result;}org.json.JSONArray a=new org.json.JSONArray(getString(k,"[]"));Set<String> result=new HashSet<>();for(int i=0;i<a.length();i++)result.add(a.getString(i));return result;}catch(Exception e){throw new IllegalStateException("Encrypted set unavailable",e);}}
 @Override public Map<String,?> getAll(){Map<String,Object> safe=new HashMap<>();for(Map.Entry<String,?> e:raw.getAll().entrySet())if(!sensitive(e.getKey()))safe.put(e.getKey(),e.getValue());return safe;}
 @Override public boolean contains(String k){return raw.contains(k);}
 @Override public int getInt(String k,int d){return raw.getInt(k,d);}
 @Override public long getLong(String k,long d){return raw.getLong(k,d);}
 @Override public float getFloat(String k,float d){return raw.getFloat(k,d);}
 @Override public boolean getBoolean(String k,boolean d){return raw.getBoolean(k,d);}
 @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){raw.registerOnSharedPreferenceChangeListener(l);}
 @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){raw.unregisterOnSharedPreferenceChangeListener(l);}
 @Override public Editor edit(){return new PrivateEditor(raw.edit());}
 private final class PrivateEditor implements Editor{
  private final Editor e;PrivateEditor(Editor e){this.e=e;}
  public Editor putString(String k,String v){e.putString(k,v==null||!sensitive(k)?v:seal(k,v));return this;}
  public Editor putStringSet(String k,Set<String> v){if(sensitive(k))return putString(k,v==null?null:new org.json.JSONArray(v).toString());e.putStringSet(k,v);return this;}
  public Editor putInt(String k,int v){e.putInt(k,v);return this;}
  public Editor putLong(String k,long v){e.putLong(k,v);return this;}
  public Editor putFloat(String k,float v){e.putFloat(k,v);return this;}
  public Editor putBoolean(String k,boolean v){e.putBoolean(k,v);return this;}
  public Editor remove(String k){e.remove(k);return this;}
  public Editor clear(){e.clear();return this;}
  public boolean commit(){return e.commit();}
  public void apply(){e.apply();}
 }
}
