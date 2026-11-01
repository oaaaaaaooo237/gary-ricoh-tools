package local.grworld.studio;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** One small, consistent line-icon family drawn at a 24-unit grid. */
final class StudioGlyph extends Drawable {
 final String name;final int color;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 StudioGlyph(String name,int color){this.name=name;this.color=color;}
 @Override public void draw(Canvas c){Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);p.setColor(color);p.setStrokeWidth(1.6f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
  if(name.equals("image")){c.drawRoundRect(3,4,21,20,1,1,p);c.drawCircle(8,9,1.4f,p);Path path=new Path();path.moveTo(4,18);path.lineTo(10,12);path.lineTo(14,16);path.lineTo(17,13);path.lineTo(21,17);c.drawPath(path,p);}
  else if(name.equals("tune")){for(int i=0;i<3;i++){float y=6+i*6,x=i==1?8:16;c.drawLine(3,y,x-2,y,p);c.drawLine(x+2,y,21,y,p);c.drawCircle(x,y,2,p);}}
  else if(name.equals("task")){c.drawRoundRect(5,4,19,22,1,1,p);c.drawRoundRect(9,2,15,6,1,1,p);c.drawLine(9,11,16,11,p);c.drawLine(9,15,16,15,p);c.drawLine(9,19,14,19,p);}
  else if(name.equals("save")){c.drawLine(12,3,12,15,p);c.drawLine(7,10,12,15,p);c.drawLine(17,10,12,15,p);Path path=new Path();path.moveTo(4,15);path.lineTo(4,21);path.lineTo(20,21);path.lineTo(20,15);c.drawPath(path,p);}
  else if(name.equals("upload")){c.drawLine(12,21,12,8,p);c.drawLine(7,13,12,8,p);c.drawLine(17,13,12,8,p);c.drawLine(4,3,20,3,p);}
  else if(name.equals("back")){c.drawLine(20,12,4,12,p);c.drawLine(4,12,11,5,p);c.drawLine(4,12,11,19,p);}
  else if(name.equals("next")){c.drawLine(9,5,16,12,p);c.drawLine(16,12,9,19,p);}
  else if(name.equals("refresh")){c.drawArc(4,4,20,20,35,290,false,p);c.drawLine(20,4,20,10,p);c.drawLine(20,10,14,10,p);}
  c.restore();
 }
 @Override public void setAlpha(int alpha){p.setAlpha(alpha);invalidateSelf();}
 @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);invalidateSelf();}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
