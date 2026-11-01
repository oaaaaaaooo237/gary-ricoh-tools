package local.grworld.studio;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** All image choices live below one entry point, never as main-screen presets. */
final class ImageLibrary {
 final MainActivity a;final StudioScreen ui;Dialog dialog;
 ImageLibrary(MainActivity activity,StudioScreen screen){a=activity;ui=screen;}
 void choose(){Dialog sheet=new Dialog(a);LinearLayout content=ui.stack();content.setBackgroundColor(StudioScreen.LOW);content.setPadding(a.dp(24),a.dp(24),a.dp(24),a.dp(16));TextView title=ui.label("选择图片",20,StudioScreen.INK);title.setPadding(0,0,0,a.dp(20));content.addView(title);Button fresh=ui.row(content,"从相册选择","image",()->{sheet.dismiss();ui.pickPhoto();});a.controls.remove(fresh);TextView first=ui.label("导入新图片，调整尺寸、构图与背景。",13,StudioScreen.MUTED);first.setPadding(a.dp(4),a.dp(8),0,a.dp(16));content.addView(first);Button saved=ui.row(content,"已处理的关机图","task",()->{sheet.dismiss();prepared();});a.controls.remove(saved);TextView second=ui.label("选择内置成品、已保存图片或图片工程。",13,StudioScreen.MUTED);second.setPadding(a.dp(4),a.dp(8),0,a.dp(20));content.addView(second);Button cancel=ui.textButton("取消",()->sheet.dismiss());a.controls.remove(cancel);content.addView(cancel,new LinearLayout.LayoutParams(-1,-2));content.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(a.dp(24)+i.getSystemWindowInsetLeft(),a.dp(24),a.dp(24)+i.getSystemWindowInsetRight(),a.dp(16)+i.getSystemWindowInsetBottom());return i;});sheet.setContentView(content);sheet.show();sheet.getWindow().setBackgroundDrawable(ui.fill(StudioScreen.LOW,4,false));sheet.getWindow().setGravity(Gravity.BOTTOM);sheet.getWindow().setLayout(-1,-2);content.requestApplyInsets();}
 void prepared(){
  LinearLayout content=new LinearLayout(a);content.setOrientation(1);content.setPadding(a.dp(20),a.dp(8),a.dp(20),a.dp(16));
  category(content,"内置图片");
  ArrayList<Entry> presets=new ArrayList<>();for(String name:new String[]{"GB_HDF.jpg","GB_GR4.jpg"})presets.add(new Entry(name,null));
  thumbnails(content,presets,"内置图片");category(content,"已保存图片");
  File[] files=a.getFilesDir().listFiles((dir,name)->name.matches("library-[0-9a-f]{64}\\.jpg"));
  ArrayList<Entry> saved=new ArrayList<>();if(files!=null){Arrays.sort(files,(x,y)->Long.compare(y.lastModified(),x.lastModified()));for(File file:files){if(saved.size()==100)break;saved.add(new Entry(null,file));}}
  if(saved.isEmpty()){TextView empty=ui.label("处理完成后，可在工具中保存到图片库。",13,StudioScreen.MUTED);empty.setPadding(0,0,0,a.dp(16));content.addView(empty);}else thumbnails(content,saved,"已保存图片");
  dialog=new Dialog(a,android.R.style.Theme_Material_NoActionBar);LinearLayout root=ui.dialogRoot();ui.pageBar(root,"已处理的关机图",()->dialog.dismiss());ScrollView scroll=new ScrollView(a);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout footer=ui.stack();footer.setPadding(a.dp(24),a.dp(8),a.dp(24),a.dp(16));Button open=ui.make("打开已保存文件",()->{dialog.dismiss();openFile();},true);a.controls.remove(open);footer.addView(open,new LinearLayout.LayoutParams(-1,-2));Button cancel=ui.textButton("取消",()->dialog.dismiss());a.controls.remove(cancel);footer.addView(cancel,new LinearLayout.LayoutParams(-1,-2));root.addView(footer);dialog.setContentView(root);dialog.show();dialog.getWindow().setLayout(-1,-1);root.requestApplyInsets();
 }
 void tone(AlertDialog d){if(d.getWindow()!=null)d.getWindow().setBackgroundDrawable(ui.fill(StudioScreen.LOW,4,false));for(int id:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){Button b=d.getButton(id);if(b!=null)b.setTextColor(StudioScreen.INK);}}
 void category(LinearLayout parent,String title){TextView t=ui.label(title,14,StudioScreen.MUTED);t.setPadding(0,a.dp(12),0,a.dp(12));parent.addView(t);}
 void thumbnails(LinearLayout parent,ArrayList<Entry> entries,String prefix){
  LinearLayout row=null;int index=0;
  for(Entry item:entries){
   if(index%2==0){row=new LinearLayout(a);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,0,0,a.dp(8));parent.addView(row,rp);}
   ImageButton image=new ImageButton(a);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackgroundColor(0xff080808);image.setPadding(a.dp(4),a.dp(4),a.dp(4),a.dp(4));image.setContentDescription(prefix+" "+(index+1));
   LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,a.dp(100),1);if(index%2==0)ip.setMargins(0,0,a.dp(8),0);row.addView(image,ip);
   try{byte[] data=item.read();BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;image.setImageBitmap(BitmapFactory.decodeByteArray(data,0,data.length,options));
    image.setOnClickListener(v->{dialog.dismiss();a.setImage(data,"已选择图片",true);});
   }catch(Exception error){image.setEnabled(false);image.setContentDescription("图片校验失败");}
   index++;
  }
  if(index%2!=0&&row!=null)row.addView(new View(a),new LinearLayout.LayoutParams(0,a.dp(100),1));
 }
 void openFile(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","application/zip","application/octet-stream"});a.startActivityForResult(intent,13);}
 final class Entry {
  final String asset;final File file;Entry(String asset,File file){this.asset=asset;this.file=file;}
  byte[] read()throws Exception{
   InputStream stream=asset==null?new FileInputStream(file):a.getAssets().open(asset);byte[] data=CameraClient.read(stream,EndScreenPolicy.MAX_BYTES);EndScreenPolicy.image(data);
   if(file!=null&&!file.getName().equals("library-"+EndScreenPolicy.sha256(data)+".jpg"))throw new IOException("图片库校验失败");return data;
  }
 }
}
