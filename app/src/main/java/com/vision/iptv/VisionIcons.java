package com.vision.iptv;
import android.graphics.*;
import android.graphics.drawable.Drawable;

final class VisionIcons extends Drawable {
 private final String kind;
 private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 VisionIcons(String kind){this.kind=kind;p.setColor(0xff9ac7f2);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);}
 private void line(Canvas c,float x,float y,float a,float b){c.drawLine(x,y,a,b,p);}
 private void box(Canvas c,float l,float t,float r,float b){c.drawRoundRect(l,t,r,b,2,2,p);}
 private void path(Canvas c,float... xy){Path path=new Path();path.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);c.drawPath(path,p);}
 @Override public void draw(Canvas c){int save=c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);switch(kind){
 case "tv":box(c,2,3,22,17);line(c,8,21,16,21);line(c,12,17,12,21);break;
 case "calendar":box(c,3,5,21,22);line(c,3,10,21,10);line(c,8,2,8,7);line(c,16,2,16,7);line(c,7,14,10,14);line(c,14,14,17,14);line(c,7,18,10,18);break;
 case "film":box(c,3,7,21,21);path(c,3,7,2,3,20,1,21,5,3,7);line(c,8,2.4f,11,6);line(c,15,1.6f,18,5.4f);path(c,10,11,16,14,10,18,10,11);break;
 case "record":c.drawCircle(12,12,9,p);p.setStyle(Paint.Style.FILL);c.drawCircle(12,12,4,p);p.setStyle(Paint.Style.STROKE);break;
 case "grid":for(int y=0;y<2;y++)for(int x=0;x<2;x++)box(c,3+x*10,3+y*10,11+x*10,11+y*10);break;
 case "list":for(int y=5;y<=19;y+=7){c.drawCircle(4,y,1,p);line(c,8,y,21,y);}break;
 case "settings":c.drawCircle(12,12,7,p);c.drawCircle(12,12,3,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;line(c,12+7*(float)Math.cos(a),12+7*(float)Math.sin(a),12+10*(float)Math.cos(a),12+10*(float)Math.sin(a));}break;
 case "mic":box(c,9,2,15,14);c.drawArc(5,6,19,18,0,180,false,p);line(c,12,18,12,22);line(c,8,22,16,22);break;
 case "server":for(int y=3;y<=15;y+=6){box(c,3,y,21,y+5);line(c,6,y+2.5f,7,y+2.5f);line(c,11,y+2.5f,18,y+2.5f);}break;
 case "star":Path star=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float radius=i%2==0?10:4.5f,x=12+radius*(float)Math.cos(a),y=12+radius*(float)Math.sin(a);if(i==0)star.moveTo(x,y);else star.lineTo(x,y);}star.close();c.drawPath(star,p);break;
 case "uhd":box(c,1,4,23,20);path(c,4,8,4,13,9,13,9,8);line(c,9,13,9,17);line(c,14,8,14,17);path(c,20,8,14,12,20,17);break;
 case "play":c.drawCircle(12,12,10,p);path(c,9,7,17,12,9,17,9,7);break;
 case "search":c.drawCircle(10,10,6,p);line(c,14.5f,14.5f,21,21);break;
 case "history":c.drawArc(3,3,21,21,-140,320,false,p);path(c,2,3,2,9,8,9);path(c,12,6,12,12,16,14);break;
 case "sound":path(c,3,9,7,9,12,5,12,19,7,15,3,15,3,9);c.drawArc(11,7,19,17,-65,130,false,p);c.drawArc(10,3,23,21,-55,110,false,p);break;
 case "power":c.drawArc(3,3,21,21,-45,270,false,p);line(c,12,2,12,11);break;
 case "pause":box(c,6,4,10,20);box(c,14,4,18,20);break;
 case "folder":path(c,2,20,2,5,10,5,12,8,22,8,22,20,2,20);break;
 default:c.drawCircle(12,12,10,p);line(c,12,10,12,17);c.drawCircle(12,6,0.6f,p);break;
 }c.restoreToCount(save);}
 @Override public void setAlpha(int alpha){p.setAlpha(alpha);invalidateSelf();}
 @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);invalidateSelf();}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
