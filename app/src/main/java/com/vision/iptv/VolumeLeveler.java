package com.vision.iptv;
import java.nio.*;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;
@UnstableApi
final class VolumeLeveler extends BaseAudioProcessor {
 volatile int mode=1;
 volatile float trimDb=0;
 volatile int bassDb=0,midDb=0,trebleDb=0,balance=0; volatile boolean voice=false,mono=false;
 private double[] low,high,frame;
 private double energy=0.01585,gain=1,peakGain=1;
 @Override protected AudioFormat onConfigure(AudioFormat format) throws UnhandledAudioFormatException {
  if(format.encoding!=C.ENCODING_PCM_16BIT)return AudioFormat.NOT_SET;
  return format;
 }
 @Override protected void onFlush(){energy=0.01585;gain=1;peakGain=1;low=null;high=null;frame=null;}
 @Override public void queueInput(ByteBuffer input){
  int bytes=input.remaining();ByteBuffer output=replaceOutputBuffer(bytes).order(ByteOrder.nativeOrder());
  input.order(ByteOrder.nativeOrder());
  int channels=Math.max(1,inputAudioFormat.channelCount),rate=Math.max(1,inputAudioFormat.sampleRate);
  if(frame==null||frame.length!=channels){frame=new double[channels];low=new double[channels];high=new double[channels];}
  double lAlpha=1-Math.exp(-2*Math.PI*180/rate),hAlpha=1-Math.exp(-2*Math.PI*3500/rate);double bass=Math.pow(10,bassDb/20.0),mid=Math.pow(10,(midDb+(voice?3:0))/20.0),treble=Math.pow(10,trebleDb/20.0);boolean mix=mono&&channels==2;int bal=balance;
  int m=mode;double trim=Math.pow(10,trimDb/20.0),target=m==2?0.16:0.125;
  double energyAlpha=1-Math.exp(-1.0/(rate*0.4)),attack=1-Math.exp(-1.0/(rate*0.045)),release=1-Math.exp(-1.0/(rate*1.5));
  while(input.remaining()>=channels*2){
   int pos=input.position();double square=0,peak=0;
   for(int i=0;i<channels;i++){double x=input.getShort()/32768.0;low[i]+=lAlpha*(x-low[i]);high[i]+=hAlpha*(x-high[i]);frame[i]=low[i]*bass+(high[i]-low[i])*mid+(x-high[i])*treble;}if(mix){double sum=(frame[0]+frame[1])*0.5;frame[0]=sum;frame[1]=sum;}if(channels==2){frame[0]*=1-Math.max(0,bal)/100.0;frame[1]*=1-Math.max(0,-bal)/100.0;}for(double x:frame){square+=x*x;peak=Math.max(peak,Math.abs(x));}
   energy+=energyAlpha*(square/channels-energy);
   double desired=energy<0.0001?Math.min(gain,1):Math.max(0.25,Math.min(m==2?3.16:2.0,target/Math.sqrt(energy)));
   if(m==0)desired=1;
   gain+=(desired<gain?attack:release)*(desired-gain);
   double applied=m==0?1:gain;double limit=peak>0?Math.min(1,0.89/(peak*applied*trim)):1;
   peakGain=limit<peakGain?limit:peakGain+release*(limit-peakGain);
   for(int i=0;i<channels;i++){double y=frame[i]*32768*applied*trim*(m==0&&trim==1&&bassDb==0&&midDb==0&&trebleDb==0&&!voice?1:peakGain);output.putShort((short)Math.max(-32768,Math.min(32767,Math.round(y))));}
  }
  while(input.hasRemaining())output.put(input.get());output.flip();
 }
}