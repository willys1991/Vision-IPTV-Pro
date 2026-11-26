package com.vision.iptv;
import java.nio.*;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;
@UnstableApi
final class VolumeLeveler extends BaseAudioProcessor {
 volatile int mode=1;
 volatile int delayMs=0;
 private short[] delayRing;private int delayIndex=0,configuredDelay=-1,delayRate=0,delayChannels=0;
 volatile float trimDb=0;
 volatile int bassDb=0,midDb=0,trebleDb=0,balance=0; volatile boolean voice=false,mono=false;
 private double[] low,high,frame;
 private double energy=0.01585,gain=1,peakGain=1;
 private int lastMode=-1;private volatile int resetGeneration=0;private int appliedGeneration=-1;
 volatile boolean processing=false;
 void resetChannel(){resetGeneration++;}
 private void resetSignal(){energy=0.01585;gain=1;peakGain=1;if(low!=null)java.util.Arrays.fill(low,0);if(high!=null)java.util.Arrays.fill(high,0);if(delayRing!=null)java.util.Arrays.fill(delayRing,(short)0);delayIndex=0;}
 @Override protected AudioFormat onConfigure(AudioFormat format) throws UnhandledAudioFormatException {
  if(format.encoding!=C.ENCODING_PCM_16BIT){processing=false;return AudioFormat.NOT_SET;}
  return format;
 }
 @Override protected void onFlush(){processing=false;resetSignal();low=null;high=null;frame=null;delayRing=null;configuredDelay=-1;lastMode=-1;}
 @Override protected void onReset(){processing=false;resetSignal();low=null;high=null;frame=null;delayRing=null;configuredDelay=-1;}
 @Override public void queueInput(ByteBuffer input){
  int bytes=input.remaining();if(bytes==0)return;processing=true;ByteBuffer output=replaceOutputBuffer(bytes).order(ByteOrder.nativeOrder());
  input.order(ByteOrder.nativeOrder());
  int channels=Math.max(1,inputAudioFormat.channelCount),rate=Math.max(1,inputAudioFormat.sampleRate);
  int delay=Math.max(0,Math.min(500,delayMs));if(delay!=configuredDelay||rate!=delayRate||channels!=delayChannels){configuredDelay=delay;delayRate=rate;delayChannels=channels;delayIndex=0;long samples=(long)rate*delay/1000*channels;delayRing=samples>0&&samples<=1048576?new short[(int)samples]:null;}
  if(frame==null||frame.length!=channels){frame=new double[channels];low=new double[channels];high=new double[channels];}
  int m=Math.max(0,Math.min(2,mode)),generation=resetGeneration;if(generation!=appliedGeneration||lastMode!=m){resetSignal();appliedGeneration=generation;lastMode=m;}int b=Math.max(-6,Math.min(6,bassDb)),md=Math.max(-6,Math.min(6,midDb)),t=Math.max(-6,Math.min(6,trebleDb));boolean speech=voice,mix=mono&&channels==2;int bal=Math.max(-100,Math.min(100,balance));double trim=Math.pow(10,Math.max(-12,Math.min(6,trimDb))/20.0);boolean flat=b==0&&md==0&&t==0&&!speech;boolean bypass=m==0&&flat&&trim==1&&!mix&&(channels!=2||bal==0)&&delayRing==null;if(bypass){output.put(input);output.flip();return;}double lAlpha=1-Math.exp(-2*Math.PI*180/rate),hAlpha=1-Math.exp(-2*Math.PI*3500/rate);double bass=Math.pow(10,b/20.0),mid=Math.pow(10,(md+(speech?3:0))/20.0),treble=Math.pow(10,t/20.0);double target=m==2?0.16:0.125;
  double energyAlpha=1-Math.exp(-1.0/(rate*0.4)),attack=1-Math.exp(-1.0/(rate*0.045)),release=1-Math.exp(-1.0/(rate*1.5));
  while(input.remaining()>=channels*2){
   double square=0,peak=0;
   for(int i=0;i<channels;i++){double x=input.getShort()/32768.0;if(flat)frame[i]=x;else{low[i]+=lAlpha*(x-low[i]);high[i]+=hAlpha*(x-high[i]);frame[i]=low[i]*bass+(high[i]-low[i])*mid+(x-high[i])*treble;}}if(mix){double sum=(frame[0]+frame[1])*0.5;frame[0]=sum;frame[1]=sum;}if(channels==2){frame[0]*=1-Math.max(0,bal)/100.0;frame[1]*=1-Math.max(0,-bal)/100.0;}for(double x:frame){square+=x*x;peak=Math.max(peak,Math.abs(x));}
   if(m!=0)energy+=energyAlpha*(square/channels-energy);
   double desired=m==0?1:energy<0.0001?Math.min(gain,1):Math.max(0.25,Math.min(m==2?3.16:2.0,target/Math.sqrt(energy)));
   if(m==0)desired=1;
   gain+=(desired<gain?attack:release)*(desired-gain);
   double applied=m==0?1:gain;double limit=peak>0?Math.min(1,0.89/(peak*applied*trim)):1;
   peakGain=limit<peakGain?limit:peakGain+release*(limit-peakGain);
   for(int i=0;i<channels;i++){double y=frame[i]*32768*applied*trim*(m==0&&trim==1&&flat?1:peakGain);short sample=(short)Math.max(-32768,Math.min(32767,Math.round(y)));if(delayRing!=null){short previous=delayRing[delayIndex];delayRing[delayIndex]=sample;delayIndex++;if(delayIndex==delayRing.length)delayIndex=0;sample=previous;}output.putShort(sample);}
  }
  while(input.hasRemaining())output.put(input.get());output.flip();
 }
}