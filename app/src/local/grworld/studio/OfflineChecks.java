package local.grworld.studio;

import android.graphics.*;
import java.io.IOException;

/** Runs on Android's actual bitmap codecs. No networking or camera state access. */
final class OfflineChecks {
 static int imagePipeline()throws Exception {
  int checks=0;Bitmap input=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);
  try{
   input.eraseColor(Color.RED);
   Bitmap fit=decode(ImageEditor.render(input,0,1,0,0,95));
   try{
    require(fit.getWidth()==720&&fit.getHeight()==480,"最终尺寸");checks++;
    require(dark(fit.getPixel(40,240))&&dark(fit.getPixel(680,240)),"完整显示的黑边");checks++;
    require(red(fit.getPixel(360,240)),"完整显示的主体");checks++;
   }finally{fit.recycle();}
   Bitmap crop=decode(ImageEditor.render(input,1,1,0,0,95));
   try{require(red(crop.getPixel(10,10))&&red(crop.getPixel(710,470)),"铺满裁切");checks++;}finally{crop.recycle();}
   Bitmap pan=decode(ImageEditor.render(input,0,1,1,0,95));
   try{require(dark(pan.getPixel(100,240))&&red(pan.getPixel(680,240)),"左右位置");checks++;}finally{pan.recycle();}
   Bitmap zoom=decode(ImageEditor.render(input,0,2,0,0,95));
   try{require(red(zoom.getPixel(10,10))&&red(zoom.getPixel(710,470)),"放大后的主体");checks++;}finally{zoom.recycle();}
   input.eraseColor(Color.TRANSPARENT);
   Bitmap alpha=decode(ImageEditor.render(input,2,1,0,0,95));
   try{require(dark(alpha.getPixel(360,240)),"透明图片合成黑底");checks++;}finally{alpha.recycle();}
   input.eraseColor(Color.RED);Bitmap original=decode(ImageEditor.render(input,3,1,0,0,95,0xff808080));
   try{require(red(original.getPixel(360,240)),"小图原尺寸主体");checks++;int gray=original.getPixel(300,240);require(Math.abs(Color.red(gray)-128)<6&&Math.abs(Color.green(gray)-128)<6&&Math.abs(Color.blue(gray)-128)<6,"小图原尺寸灰底不放大");checks++;}finally{original.recycle();}
  }finally{input.recycle();}
  Bitmap halves=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888);try{Canvas c=new Canvas(halves);c.drawColor(Color.BLUE);Paint p=new Paint();p.setColor(Color.RED);c.drawRect(0,0,100,100,p);
   Bitmap left=decode(ImageEditor.render(halves,1,1,1,0,95)),right=decode(ImageEditor.render(halves,1,1,-1,0,95));try{require(red(left.getPixel(360,240)),"裁切框左侧选区");checks++;int blue=right.getPixel(360,240);require(Color.blue(blue)>220&&Color.red(blue)<25,"裁切框右侧选区");checks++;}finally{left.recycle();right.recycle();}
  }finally{halves.recycle();}
  try{Bitmap invalid=ImageEditor.decode(new byte[]{1,2,3});invalid.recycle();throw new IOException("错误图片未被拒绝");}catch(IOException expected){if("错误图片未被拒绝".equals(expected.getMessage()))throw expected;checks++;}
  return checks;
 }
 static Bitmap decode(byte[] data)throws Exception{EndScreenPolicy.image(data);Bitmap image=BitmapFactory.decodeByteArray(data,0,data.length);if(image==null)throw new IOException("生成的 JPEG 无法解码");return image;}
 static void require(boolean pass,String name)throws IOException{if(!pass)throw new IOException("检查未通过："+name);}
 static boolean red(int color){return Color.red(color)>220&&Color.green(color)<25&&Color.blue(color)<25;}
 static boolean dark(int color){return Color.red(color)<20&&Color.green(color)<20&&Color.blue(color)<20;}
}
