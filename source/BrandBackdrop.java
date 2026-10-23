package uk.michael.calmhome;
import android.graphics.*;import android.graphics.drawable.Drawable;
/** Resolution-independent artwork for apps without a published programme or bundled brand plate. */
final class BrandBackdrop extends Drawable {
 final String name,pkg;final Typeface font;final int color;final Paint paint=new Paint(3);
 BrandBackdrop(String pkg,String name,Typeface font,int color){this.pkg=pkg;this.name=name;this.font=font;this.color=color;}
 public int getIntrinsicWidth(){return 1920;}public int getIntrinsicHeight(){return 1080;}
 public void draw(Canvas c){int save=c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/1920f,b.height()/1080f);paint.setStyle(Paint.Style.FILL);
  boolean sounds=pkg.contains("sounds"),frame=pkg.equals("uk.michael.framefire"),settings=pkg.contains("settings");int base=sounds?0xffed4f16:frame?0xff16242d:settings?0xff1a202a:darken(color,.38f);int accent=sounds?0xffff772d:frame?0xff4c6c78:settings?0xff435367:color;
  paint.setShader(new LinearGradient(0,0,1920,1080,new int[]{base,darken(base,.48f)},null,Shader.TileMode.CLAMP));c.drawRect(0,0,1920,1080,paint);paint.setShader(new RadialGradient(1320,200,1350,new int[]{accent,Color.TRANSPARENT},null,Shader.TileMode.CLAMP));c.drawRect(0,0,1920,1080,paint);paint.setShader(null);
  paint.setColor(0x0affffff);c.drawCircle(1540,-240,860,paint);paint.setColor(0x06ffffff);c.drawCircle(1540,-240,1040,paint);
  if(sounds){paint.setColor(Color.WHITE);paint.setStrokeWidth(22);paint.setStrokeCap(Paint.Cap.ROUND);for(int i=0;i<5;i++){float h=new float[]{70,135,220,135,70}[i];c.drawLine(830+i*65,360-h/2,830+i*65,360+h/2,paint);}word(c,"BBC Sounds",960,620,142,Color.WHITE);}
  else if(frame){Path p=new Path();p.moveTo(817,263);p.lineTo(817,537);p.quadTo(817,568,847,550);p.lineTo(1085,413);p.quadTo(1117,395,1085,377);p.lineTo(847,240);p.quadTo(817,222,817,263);paint.setColor(0xffeeeae4);c.drawPath(p,paint);Path fold=new Path();fold.moveTo(817,263);fold.lineTo(1085,413);fold.lineTo(965,480);fold.close();paint.setColor(0xffacb3ac);c.drawPath(fold,paint);Path tip=new Path();tip.moveTo(965,480);tip.lineTo(1085,413);tip.lineTo(1085,447);tip.close();paint.setColor(0xffb55743);c.drawPath(tip,paint);word(c,"Frame",960,690,116,Color.WHITE);}
  else if(settings){paint.setColor(0xffeef2f8);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(18);c.drawCircle(960,405,90,paint);c.drawCircle(960,405,34,paint);for(int i=0;i<12;i++){c.save();c.rotate(i*30,960,405);c.drawLine(960,287,960,308,paint);c.restore();}paint.setStyle(Paint.Style.FILL);word(c,"Settings",960,665,106,Color.WHITE);}
  else word(c,name,960,530,170,Color.WHITE);
  c.restore();
 }
 void word(Canvas c,String text,float x,float y,float size,int color){paint.setShader(null);paint.setColor(color);paint.setTypeface(Typeface.create(font,Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(size);while(paint.measureText(text)>1450&&paint.getTextSize()>56)paint.setTextSize(paint.getTextSize()-2);c.drawText(text,x,y,paint);}
 static int darken(int color,float factor){return Color.rgb((int)(Color.red(color)*factor),(int)(Color.green(color)*factor),(int)(Color.blue(color)*factor));}
 public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}public int getOpacity(){return PixelFormat.OPAQUE;}
}
