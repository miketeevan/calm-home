package uk.michael.calmhome;
import android.graphics.*;import android.graphics.drawable.*;
/** Fits original installed artwork without stretching logos or introducing side panels. */
final class TileArtwork {
 static Bitmap fitted(Drawable drawable,boolean whiteOnColour){
  int iw=Math.max(1,drawable.getIntrinsicWidth()),ih=Math.max(1,drawable.getIntrinsicHeight());
  if(drawable instanceof BitmapDrawable){BitmapDrawable bd=(BitmapDrawable)drawable;bd.setFilterBitmap(true);iw=bd.getBitmap().getWidth();ih=bd.getBitmap().getHeight();}
  float scale=Math.min(1f,1024f/Math.max(iw,ih));int w=Math.max(1,Math.round(iw*scale)),h=Math.max(1,Math.round(ih*scale));
  Bitmap original=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);drawable=drawable.mutate();drawable.setBounds(0,0,w,h);drawable.draw(new Canvas(original));
  int background=original.getPixel(w/2,Math.min(h-1,Math.max(1,h/16)));if(Color.alpha(background)<220)background=original.getPixel(w/2,h/4);if(Color.alpha(background)<220)background=0xff161a22;background|=0xff000000;
  int[] pixels=new int[w*h];original.getPixels(pixels,0,w,0,0,w,h);int l=w,t=h,r=-1,b=-1;
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){int at=y*w+x,c=pixels[at];if(whiteOnColour){float alpha=Color.alpha(c)/255f*Color.red(c)/255f;c=Color.rgb(Math.round(Color.red(background)*(1-alpha)+255*alpha),Math.round(Color.green(background)*(1-alpha)+255*alpha),Math.round(Color.blue(background)*(1-alpha)+255*alpha));pixels[at]=c;}int delta=Math.max(Math.abs(Color.red(c)-Color.red(background)),Math.max(Math.abs(Color.green(c)-Color.green(background)),Math.abs(Color.blue(c)-Color.blue(background))));if(Color.alpha(c)>220&&delta>48){l=Math.min(l,x);t=Math.min(t,y);r=Math.max(r,x);b=Math.max(b,y);}}
  if(whiteOnColour)original.setPixels(pixels,0,w,0,0,w,h);
  // Rounded transparent corners are background, not logo content.
  if(r<=l||b<=t){l=0;t=0;r=w-1;b=h-1;}
  Bitmap out=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(out);canvas.drawColor(background);
  float fit=Math.min(448f/(r-l+1),210f/(b-t+1));float dw=(r-l+1)*fit,dh=(b-t+1)*fit;
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
  canvas.drawBitmap(original,new Rect(l,t,r+1,b+1),new RectF((640-dw)/2,(360-dh)/2,(640+dw)/2,(360+dh)/2),p);return out;
 }
 static Bitmap settings(){Bitmap out=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setShader(new LinearGradient(0,0,640,360,0xff454b56,0xff171b22,Shader.TileMode.CLAMP));canvas.drawRect(0,0,640,360,p);p.setShader(null);
  Path gear=new Path();for(int i=0;i<96;i++){double angle=Math.PI*2*i/96-Math.PI/2;float radius=(i%8<2||i%8>=6)?92:106;float x=320+(float)Math.cos(angle)*radius,y=180+(float)Math.sin(angle)*radius;if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);}gear.close();gear.addCircle(320,180,43,Path.Direction.CCW);gear.setFillType(Path.FillType.EVEN_ODD);p.setShader(new LinearGradient(0,74,0,286,0xfff5f7fb,0xffa5adba,Shader.TileMode.CLAMP));canvas.drawPath(gear,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0x88ffffff);canvas.drawCircle(320,180,58,p);return out;}
}
