package com.vision.iptv;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;

final class NasSecrets {
 private static final String ALIAS="vision_nas_v1";
 private static synchronized SecretKey key()throws Exception{
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  if(ks.containsAlias(ALIAS))return (SecretKey)ks.getKey(ALIAS,null);
  KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
  gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());return gen.generateKey();
 }
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences("nas_secrets",Context.MODE_PRIVATE);}
 static void save(Context c,String id,String user,String password)throws Exception{
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
  org.json.JSONArray data=new org.json.JSONArray();data.put(user);data.put(password);
  String value=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
  if(!prefs(c).edit().putString(id,value).commit())throw new java.io.IOException("Credential storage failed");
 }
 static String[] read(Context c,String id)throws Exception{
  String value=prefs(c).getString(id,null);if(value==null)throw new java.io.IOException("Credentials missing");String[] parts=value.split(":",2);
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
  org.json.JSONArray a=new org.json.JSONArray(new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8));return new String[]{a.getString(0),a.getString(1)};
 }
 static void remove(Context c,String id){prefs(c).edit().remove(id).apply();}
}
