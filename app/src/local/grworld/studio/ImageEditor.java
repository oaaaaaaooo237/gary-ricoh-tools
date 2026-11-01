package local.grworld.studio;

import android.graphics.*;
import android.media.ExifInterface;
import java.io.*;

final class ImageEditor {
 static int[] dimensions(byte[] source){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(source,0,source.length,o);int orientation=1;try{orientation=new ExifInterface(new ByteArrayInputStream(source)).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}return orientation>=5&&orientation<=8?new int[]{o.outHeight,o.outWidth}:new int[]{o.outWidth,o.outHeight};}
 static Bitmap decode(byte[] source)throws Exception {
  BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
  BitmapFactory.decodeByteArray(source,0,source.length,o);
  if(o.outWidth<=0||o.outHeight<=0||(long)o.outWidth*o.outHeight>80000000)throw new IOException("图片无法解码或超过 8000 万像素");
  o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>2400)o.inSampleSize*=2;
  o.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeByteArray(source,0,source.length,o);
  if(bitmap==null)throw new IOException("图片解码失败");
  int orientation=1;
  try{ExifInterface exif=new ExifInterface(new ByteArrayInputStream(source));orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException unsupportedMetadata){/* BitmapFactory also supports formats without EXIF, including BMP. */}
  Matrix m=new Matrix();
  switch(orientation){
   case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;
   case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;
   case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;
  }
  if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}
  return bitmap;
 }
 static byte[] render(Bitmap source,int mode,float zoom,float x,float y,int quality)throws Exception {
  return render(source,mode,zoom,x,y,quality,Color.BLACK);
 }
 static byte[] render(Bitmap source,int mode,float zoom,float x,float y,int quality,int background)throws Exception {
  Bitmap output=Bitmap.createBitmap(720,480,Bitmap.Config.ARGB_8888);
  try {
   Canvas c=new Canvas(output);c.drawColor(background);
   float sx=720f/source.getWidth(),sy=480f/source.getHeight();
   if(mode!=2)sx=sy=(mode==3?Math.min(1f,Math.min(sx,sy)):mode==0?Math.min(sx,sy):Math.max(sx,sy));
   float w=source.getWidth()*sx*zoom,h=source.getHeight()*sy*zoom;
   float left=(720-w)/2+x*Math.abs(720-w)/2,top=(480-h)/2+y*Math.abs(480-h)/2;
   c.drawBitmap(source,null,new RectF(left,top,left+w,top+h),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
   byte[] data;
   do {ByteArrayOutputStream out=new ByteArrayOutputStream();if(!output.compress(Bitmap.CompressFormat.JPEG,quality,out))throw new IOException("JPEG 编码失败");data=out.toByteArray();quality-=5;}while(data.length>EndScreenPolicy.MAX_BYTES&&quality>=30);
   EndScreenPolicy.image(data);return data;
  }finally{output.recycle();}
 }
}
