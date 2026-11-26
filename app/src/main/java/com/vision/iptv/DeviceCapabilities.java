package com.vision.iptv;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.view.Display;
import android.app.ActivityManager;
import android.content.Context;

final class DeviceCapabilities {
 static String report(Context context,Display display){
  StringBuilder s=new StringBuilder(Build.MANUFACTURER+" "+Build.MODEL+"\nAndroid "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\n");
  ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
  ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();am.getMemoryInfo(mem);
  s.append("ОЗУ: ").append(mem.totalMem/1048576).append(" МБ, доступно ").append(mem.availMem/1048576).append(" МБ\nКласс heap: ").append(am.getMemoryClass()).append(" МБ\n");
  if(display!=null){Display.Mode mode=display.getMode();s.append("Экран: ").append(mode.getPhysicalWidth()).append(" × ").append(mode.getPhysicalHeight()).append(" / ").append(mode.getRefreshRate()).append(" Гц\n");int[] hdr=display.getHdrCapabilities().getSupportedHdrTypes();s.append("HDR экрана: ");if(hdr.length==0)s.append("не заявлен");for(int type:hdr)s.append(type==1?"Dolby Vision ":type==2?"HDR10 ":type==3?"HLG ":type==4?"HDR10+ ":"тип "+type+" ");s.append("\n");}
  String[] mimes={"video/avc","video/hevc","video/x-vnd.on2.vp9","video/av01","video/dolby-vision","video/mpeg2"};
  String[] labels={"H.264 / AVC","H.265 / HEVC","VP9","AV1","Dolby Vision","MPEG-2"};
  MediaCodecInfo[] codecs=new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
  for(int i=0;i<mimes.length;i++){s.append("\n").append(labels[i]).append(":\n");boolean found=false;for(MediaCodecInfo info:codecs){if(info.isEncoder())continue;try{MediaCodecInfo.CodecCapabilities caps=info.getCapabilitiesForType(mimes[i]);MediaCodecInfo.VideoCapabilities video=caps.getVideoCapabilities();if(video==null)continue;found=true;String name=info.getName();String hw=Build.VERSION.SDK_INT>=29?(info.isHardwareAccelerated()?"аппаратный":info.isSoftwareOnly()?"программный":"тип не указан"):"тип не определён";s.append(name).append(" • ").append(hw).append("\n");int[][] sizes={{1280,720},{1920,1080},{3840,2160}};for(int[] size:sizes){if(!video.isSizeSupported(size[0],size[1]))continue;s.append(size[1]).append("p ");if(video.areSizeAndRateSupported(size[0],size[1],60))s.append("60 fps; ");else if(video.areSizeAndRateSupported(size[0],size[1],30))s.append("30 fps; ");else s.append("(частота не подтверждена); ");}s.append("\n");}catch(IllegalArgumentException ignored){}}if(!found)s.append("Не заявлен\n");}
  return s.toString();
 }
}
