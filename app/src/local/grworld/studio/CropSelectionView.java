package local.grworld.studio;

import android.app.AlertDialog;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** A draggable 3:2 rectangle over the source; output remains the existing JPEG pipeline. */
final class CropSelectionView extends View {
 final Bitmap source;final MainActivity a;final RectF image=new RectF(),frame=new RectF();
 final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 float zoom,panX,panY,lastX,lastY;
 CropSelectionView(MainActivity activity){super(activity);a=activity;source=a.original;zoom=1+a.zoom.getProgress()/100f;panX=(a.x.getProgress()-100)/100f;panY=(a.y.getProgress()-100)/100f;setContentDescription("拖动三比二裁切框选择保留区域，下方滑块调整范围");setBackgroundColor(0xff080808);}
 void geometry(){float scale=Math.min(getWidth()/(float)source.getWidth(),getHeight()/(float)source.getHeight());float w=source.getWidth()*scale,h=source.getHeight()*scale;image.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);float fw=Math.min(w,h*1.5f)/zoom,fh=fw/1.5f;float cx=image.centerX()-panX*(w-fw)/2,cy=image.centerY()-panY*(h-fh)/2;frame.set(cx-fw/2,cy-fh/2,cx+fw/2,cy+fh/2);}
 @Override protected void onDraw(Canvas c){super.onDraw(c);geometry();paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);c.drawBitmap(source,null,image,paint);paint.setColor(0x99000000);c.drawRect(image.left,image.top,image.right,frame.top,paint);c.drawRect(image.left,frame.bottom,image.right,image.bottom,paint);c.drawRect(image.left,frame.top,frame.left,frame.bottom,paint);c.drawRect(frame.right,frame.top,image.right,frame.bottom,paint);paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(2));c.drawRect(frame,paint);paint.setStrokeWidth(a.dp(1));paint.setColor(0x88ffffff);for(int i=1;i<3;i++){float x=frame.left+frame.width()*i/3,y=frame.top+frame.height()*i/3;c.drawLine(x,frame.top,x,frame.bottom,paint);c.drawLine(frame.left,y,frame.right,y,paint);}paint.setStyle(Paint.Style.FILL);}
 @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=event.getX();lastY=event.getY();getParent().requestDisallowInterceptTouchEvent(true);return true;}if(event.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=event.getX()-lastX,dy=event.getY()-lastY;lastX=event.getX();lastY=event.getY();float freeX=image.width()-frame.width(),freeY=image.height()-frame.height();if(freeX>1)panX=Math.max(-1,Math.min(1,panX-2*dx/freeX));if(freeY>1)panY=Math.max(-1,Math.min(1,panY-2*dy/freeY));invalidate();return true;}if(event.getActionMasked()==MotionEvent.ACTION_UP){performClick();return true;}return event.getActionMasked()==MotionEvent.ACTION_CANCEL;}
 @Override public boolean performClick(){super.performClick();return true;}
 static void show(MainActivity a){
  CropSelectionView crop=new CropSelectionView(a);LinearLayout content=new LinearLayout(a);content.setOrientation(1);content.setPadding(a.dp(20),a.dp(8),a.dp(20),a.dp(8));
  TextView hint=a.screen.label("拖动裁切框选择保留区域，或使用居中裁切。",13,StudioScreen.MUTED);hint.setPadding(0,0,0,a.dp(12));content.addView(hint);int height=Math.min(a.dp(300),(int)(a.getResources().getDisplayMetrics().heightPixels*.38f));content.addView(crop,new LinearLayout.LayoutParams(-1,height));
  TextView range=a.screen.label("裁切范围",14,StudioScreen.INK);range.setPadding(0,a.dp(12),0,a.dp(4));content.addView(range);SeekBar scale=new SeekBar(a);scale.setMax(300);scale.setProgress(a.zoom.getProgress());scale.setContentDescription("裁切范围");scale.setProgressTintList(android.content.res.ColorStateList.valueOf(StudioScreen.INK));scale.setThumbTintList(android.content.res.ColorStateList.valueOf(StudioScreen.INK));content.addView(scale,new LinearLayout.LayoutParams(-1,a.dp(48)));scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){crop.zoom=1+p/100f;crop.invalidate();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
  AlertDialog d=new AlertDialog.Builder(a).setTitle("选择裁切区域").setView(content).setPositiveButton("应用",(v,w)->{a.applyCrop(crop.zoom,crop.panX,crop.panY);}).setNeutralButton("居中裁切",null).setNegativeButton("取消",null).create();d.show();new ImageLibrary(a,a.screen).tone(d);d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{scale.setProgress(0);crop.zoom=1;crop.panX=0;crop.panY=0;crop.invalidate();});
 }
}
