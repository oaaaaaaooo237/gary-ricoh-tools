package local.grworld.studio;

import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;

/** Native, single-photo work surface; colors are product semantic roles. */
final class StudioScreen {
 static final int SURFACE=0xff282828,LOW=0xff181818,INK=0xfff5f5f3,MUTED=0xffb5b5b1,
  OUTLINE=0xff484848,PRIMARY=0xfff5f5f3,ON_PRIMARY=0xff161616,CONNECTED=0xff38c86b;
 final MainActivity a;TextView cameraModel,cameraState,resolutionHint;View connectionDot;ProgressBar progress;
 int displayedConnectionColor=0xff8b8b87;
 android.app.Dialog editorDialog;ImageView editorPreview;Bitmap editorShown;LinearLayout homeFooter,homeCamera;View emptyGroup;ScrollView homePage,settingsPage,launcherPage;TextView pageTitle,settingsIdentity,settingsFeedback,workflowHint;Button[] navigation=new Button[3];int currentPage;Button launcherPicture,launcherEnhance;
 TextView operationNote,imageNote;Button resumeWifi;LinearLayout editDock;Button choose,tools,adjustButton,saveButton,editorDoneButton;LinearLayout advanced;Button more,reset,crop;
 StudioScreen(MainActivity activity){a=activity;}
 int dp(int n){return a.dp(n);}
 GradientDrawable fill(int color,int radius,boolean stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke)d.setStroke(dp(1),OUTLINE);return d;}
 TextView label(String text,int size,int color){TextView t=new TextView(a);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");t.setIncludeFontPadding(false);return t;}
 TextView text(String value,int size){TextView t=label(value,size,INK);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(8));a.column.addView(t,p);return t;}
 void space(int height){a.column.addView(new View(a),new LinearLayout.LayoutParams(1,dp(height)));}
 void rule(){View v=new View(a);v.setBackgroundColor(OUTLINE);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.setMargins(0,dp(20),0,dp(20));a.column.addView(v,p);}
 Button make(String title,Runnable action,boolean primary){
  Button b=new Button(a);b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(16),dp(10),dp(16),dp(10));b.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));b.setStateListAnimator(null);
  int[][] states={new int[]{-android.R.attr.state_enabled},new int[]{}};
  b.setTextColor(new ColorStateList(states,new int[]{0xffa4a4a1,primary?ON_PRIMARY:INK}));
  StateListDrawable background=new StateListDrawable();background.addState(new int[]{-android.R.attr.state_enabled},fill(0xff3a3a3a,4,false));background.addState(new int[]{},fill(primary?PRIMARY:0x00000000,4,!primary));
  b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337f7f7f),background,null));
  a.controls.add(b);b.setOnClickListener(v->{if(!a.busy&&!MainActivity.NETWORK_BUSY.get())try{action.run();}catch(Exception e){a.message(e.getMessage());}});return b;
 }
 Button button(String title,Runnable action){Button b=make(title,action,false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(8));a.column.addView(b,p);return b;}
 Button textButton(String title,Runnable action){Button b=make(title,action,false);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337f7f7f),fill(0x00000000,8,false),null));return b;}
 SeekBar slider(String title,int max,int value){
  TextView heading=text(title,14);heading.setTextColor(MUTED);SeekBar s=new SeekBar(a);s.setMax(max);s.setProgress(value);s.setProgressTintList(ColorStateList.valueOf(INK));s.setThumbTintList(ColorStateList.valueOf(INK));s.setProgressBackgroundTintList(ColorStateList.valueOf(OUTLINE));s.setPadding(dp(8),0,dp(8),0);
  a.column.addView(s,new LinearLayout.LayoutParams(-1,dp(48)));a.controls.add(s);
  s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean user){if(!a.changing)a.schedule();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});return s;
 }

 StudioGlyph icon(String name,int size,int color){StudioGlyph d=new StudioGlyph(name,color);d.setBounds(0,0,dp(size),dp(size));return d;}
 LinearLayout stack(){LinearLayout v=new LinearLayout(a);v.setOrientation(1);return v;}
 void divider(LinearLayout p){View v=new View(a);v.setBackgroundColor(OUTLINE);p.addView(v,new LinearLayout.LayoutParams(-1,dp(1)));}
 Button row(LinearLayout p,String title,String glyph,Runnable action){Button b=textButton(title,action);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);b.setTextSize(15);b.setPadding(dp(4),dp(16),dp(4),dp(16));b.setCompoundDrawables(glyph==null?null:icon(glyph,22,INK),null,icon("next",18,MUTED),null);b.setCompoundDrawablePadding(dp(14));p.addView(b,new LinearLayout.LayoutParams(-1,-2));divider(p);return b;}
 LinearLayout pageBar(LinearLayout root,String title,Runnable back){LinearLayout bar=new LinearLayout(a);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),dp(4),dp(16),dp(4));bar.setBackgroundColor(Color.BLACK);Button b=textButton("返回",back);a.controls.remove(b);b.setCompoundDrawables(icon("back",22,INK),null,null,null);b.setCompoundDrawablePadding(dp(6));bar.addView(b,new LinearLayout.LayoutParams(-2,-2));TextView t=label(title,18,INK);t.setPadding(dp(8),0,0,0);bar.addView(t,new LinearLayout.LayoutParams(0,-2,1));root.addView(bar,new LinearLayout.LayoutParams(-1,-2));return bar;}
 LinearLayout dialogRoot(){LinearLayout root=stack();root.setBackgroundColor(Color.BLACK);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});return root;}
 void selectPage(int page){currentPage=page;homePage.setVisibility(page==0?View.VISIBLE:View.GONE);settingsPage.setVisibility(page==2?View.VISIBLE:View.GONE);if(launcherPage!=null)launcherPage.setVisibility(page==3?View.VISIBLE:View.GONE);homeFooter.setVisibility(page==0?View.VISIBLE:View.GONE);pageTitle.setText(page==2?"设置":page==3?"Gary趣味应用":"调整关机画面");tools.setVisibility(page==0?View.VISIBLE:View.GONE);setNavigation(page==3?0:page);if(page==0)enterFeature();}
 void enterFeature(){boolean first=!a.featureEntered;a.featureEntered=true;if(first&&a.foreground)a.check();}
 void setNavigation(int selected){for(int i=0;i<3;i++)if(navigation[i]!=null){int color=i==selected?INK:0xff999999;navigation[i].setTextColor(color);navigation[i].setCompoundDrawables(null,null,null,null);navigation[i].setSelected(i==selected);}}
 void feedback(String value){if(settingsFeedback!=null){settingsFeedback.setText(value);settingsFeedback.setVisibility(View.VISIBLE);}}
 void showEditor(){if(a.original==null)return;editorDialog.show();editorDialog.getWindow().setLayout(-1,-1);editorDialog.getWindow().getDecorView().requestApplyInsets();refresh();}
 void build(){
  LinearLayout root=stack();root.setBackgroundColor(SURFACE);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
  LinearLayout bar=new LinearLayout(a);bar.setBackgroundColor(Color.BLACK);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(24),dp(4),dp(12),dp(4));pageTitle=label("关机画面",18,INK);bar.addView(pageTitle,new LinearLayout.LayoutParams(0,-2,1));tools=textButton("工具",()->menu());bar.addView(tools,new LinearLayout.LayoutParams(-2,-2));root.addView(bar);
  FrameLayout pages=new FrameLayout(a);root.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
  launcherPage=new ScrollView(a);launcherPage.setFillViewport(true);
  LinearLayout launch=stack();launch.setGravity(Gravity.TOP);
  launch.setPadding(dp(24),dp(28),dp(24),dp(32));launcherPage.addView(launch);
  TextView description=label("更换关机图、恢复原厂画面，添加 XPan 宽幅与构图辅助线。",15,MUTED);
  description.setLineSpacing(dp(4),1);launch.addView(description);
  TextView welcome=label("选择功能",18,INK);welcome.setPadding(0,dp(32),0,dp(12));launch.addView(welcome);
  divider(launch);
  launcherPicture=row(launch,"调整关机画面","image",()->selectPage(0));
  launcherPicture.setMinHeight(dp(88));launcherPicture.setContentDescription("调整关机画面入口");
  launcherEnhance=row(launch,"拍摄增强","tune",()->{enterFeature();a.enhancements.show();});
  launcherEnhance.setMinHeight(dp(88));launcherEnhance.setContentDescription("拍摄增强入口");
  TextView intro=label("进入后自动识别机型与固件，显示适用功能。",13,MUTED);
  intro.setPadding(dp(4),dp(16),dp(4),0);launch.addView(intro);
  pages.addView(launcherPage,new FrameLayout.LayoutParams(-1,-1));
  homePage=new ScrollView(a);homePage.setFillViewport(true);a.column=stack();a.column.setPadding(dp(24),dp(16),dp(24),dp(8));homePage.addView(a.column);pages.addView(homePage,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout camera=new LinearLayout(a);homeCamera=camera;camera.setGravity(Gravity.CENTER_VERTICAL);LinearLayout copy=stack();LinearLayout identity=new LinearLayout(a);identity.setGravity(Gravity.CENTER_VERTICAL);connectionDot=new View(a);connectionDot.setBackground(fill(0xff8b8b87,5,false));LinearLayout.LayoutParams dot=new LinearLayout.LayoutParams(dp(8),dp(8));dot.setMargins(0,0,dp(10),0);identity.addView(connectionDot,dot);cameraModel=label("未连接相机",16,INK);identity.addView(cameraModel,new LinearLayout.LayoutParams(-2,-2));copy.addView(identity);cameraState=label("固件 —",12,MUTED);cameraState.setPadding(dp(18),dp(6),0,0);copy.addView(cameraState);camera.addView(copy,new LinearLayout.LayoutParams(0,-2,1));Button check=textButton("检测连接",()->a.check());check.setTextSize(12);check.setPadding(dp(8),0,dp(8),0);camera.addView(check);a.column.addView(camera);space(16);
  AspectPreview frame=new AspectPreview(true);homePage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(b-t!=ob-ot||r-l!=or-ol)frame.post(()->frame.requestLayout());});frame.setBackgroundColor(0xff111111);a.preview=new ImageView(a);a.preview.setScaleType(ImageView.ScaleType.FIT_CENTER);frame.addView(a.preview,new FrameLayout.LayoutParams(-1,-1));LinearLayout placeholder=new LinearLayout(a);placeholder.setOrientation(LinearLayout.HORIZONTAL);placeholder.setGravity(Gravity.CENTER);ImageView emptyIcon=new ImageView(a);emptyIcon.setImageDrawable(icon("image",28,MUTED));placeholder.addView(emptyIcon,new LinearLayout.LayoutParams(dp(28),dp(28)));a.empty=label("选择一张关机图片",14,MUTED);a.empty.setGravity(Gravity.CENTER);a.empty.setPadding(dp(12),0,0,0);placeholder.addView(a.empty,new LinearLayout.LayoutParams(-2,-2));emptyGroup=placeholder;frame.addView(placeholder,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));a.column.addView(frame,new LinearLayout.LayoutParams(-1,-2));
  a.details=text("720 × 480 · JPEG",12);a.details.setTextColor(MUTED);a.details.setGravity(Gravity.CENTER);space(8);choose=make("选择图片",()->new ImageLibrary(a,this).choose(),false);choose.setCompoundDrawables(icon("image",22,INK),null,null,null);choose.setCompoundDrawablePadding(dp(12));
  LinearLayout dock=new LinearLayout(a);editDock=dock;dock.setBackground(fill(SURFACE,0,true));Button adjust=textButton("画面调整",()->showEditor());adjust.setCompoundDrawables(null,null,null,null);adjust.setCompoundDrawablePadding(dp(8));Button save=textButton("保存成品",()->a.saveToLibrary());save.setCompoundDrawables(null,null,null,null);save.setCompoundDrawablePadding(dp(8));adjustButton=adjust;saveButton=save;dock.addView(adjust,new LinearLayout.LayoutParams(0,dp(48),1));View line=new View(a);line.setBackgroundColor(OUTLINE);dock.addView(line,new LinearLayout.LayoutParams(dp(1),-1));dock.addView(save,new LinearLayout.LayoutParams(0,dp(48),1));adjust.setTag("image-action");save.setTag("image-action");
  LinearLayout home=a.column;buildEditor();a.column=home;
  settingsPage=new ScrollView(a);settingsPage.setBackgroundColor(Color.BLACK);LinearLayout settings=stack();settings.setPadding(dp(24),dp(16),dp(24),dp(32));settingsPage.addView(settings);settingsPage.setVisibility(View.GONE);pages.addView(settingsPage,new FrameLayout.LayoutParams(-1,-1));TextView heading=label("相机",13,MUTED);heading.setPadding(0,dp(16),0,dp(12));settings.addView(heading);settingsIdentity=label("未连接相机",16,INK);settingsIdentity.setPadding(dp(4),dp(16),dp(4),dp(20));settings.addView(settingsIdentity);settingsFeedback=label("",12,MUTED);settingsFeedback.setPadding(dp(4),0,dp(4),dp(12));settingsFeedback.setVisibility(View.GONE);settings.addView(settingsFeedback);divider(settings);row(settings,"检测连接","refresh",()->a.check());TextView files=label("图片与记录",13,MUTED);files.setPadding(0,dp(8),0,dp(12));settings.addView(files);row(settings,"导出 JPEG","save",()->a.export(false));row(settings,"保存图片备份包",null,()->a.export(true));row(settings,"离线检查",null,()->a.offlineCheck());row(settings,"查看操作结果","task",()->new android.app.AlertDialog.Builder(a).setTitle("操作结果").setMessage(a.lastReport).setPositiveButton("关闭",null).show());row(settings,"关于与开源声明",null,()->a.enhancements.license());
  homeFooter=stack();homeFooter.setBackgroundColor(SURFACE);homeFooter.setPadding(dp(24),dp(8),dp(24),dp(8));workflowHint=label(CameraWorkflowPolicy.summary(null),12,MUTED);workflowHint.setGravity(Gravity.CENTER);workflowHint.setPadding(0,0,0,dp(8));a.column.addView(workflowHint,1);imageNote=label("第 2 步 · 选择图片\n从相册导入，或打开已处理图片；按需调整后检查最终预览。",13,MUTED);imageNote.setPadding(0,0,0,dp(8));homeFooter.addView(imageNote);homeFooter.addView(choose,new LinearLayout.LayoutParams(-1,dp(52)));View dockGap=new View(a);homeFooter.addView(dockGap,new LinearLayout.LayoutParams(1,dp(8)));homeFooter.addView(dock,new LinearLayout.LayoutParams(-1,dp(48)));progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setIndeterminateTintList(ColorStateList.valueOf(INK));homeFooter.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));a.status=label("选择图片后，连接相机 Wi-Fi 并检测连接。",12,MUTED);a.status.setTextIsSelectable(true);a.status.setPadding(0,dp(4),0,dp(10));a.status.setVisibility(View.GONE);a.column.addView(a.status);a.confirm=make("核对相机实际画面",()->{android.app.AlertDialog d=new android.app.AlertDialog.Builder(a).setTitle("相机关机画面是否正确？").setItems(new String[]{"画面正确","不正确或未变化","暂未核对"},(w,index)->{if(index<2)a.recordDisplay(index==0);else a.message("实际画面仍待核对；程序不会重复上传。");}).show();},false);operationNote=label("",13,MUTED);operationNote.setPadding(0,dp(10),0,dp(8));homeFooter.addView(operationNote);resumeWifi=make("重新查询相机状态",()->a.check(),true);homeFooter.addView(resumeWifi);homeFooter.addView(a.confirm);a.upload=make("检测相机，开始换图",()->a.cameraAction(false),true);LinearLayout.LayoutParams actionSpace=new LinearLayout.LayoutParams(-1,dp(52));actionSpace.setMargins(0,dp(8),0,0);homeFooter.addView(a.upload,actionSpace);a.restore=textButton("恢复原厂",()->a.cameraAction(true));a.restore.setContentDescription("恢复原厂关机图");a.restore.setTextSize(12);a.restore.setPadding(dp(4),0,dp(4),0);dock.addView(a.restore,new LinearLayout.LayoutParams(0,dp(48),1));adjust.setTextSize(12);save.setTextSize(12);adjust.setPadding(dp(4),0,dp(4),0);save.setPadding(dp(4),0,dp(4),0);root.addView(homeFooter);
  LinearLayout nav=new LinearLayout(a);nav.setBackgroundColor(Color.BLACK);String[] titles={"功能","","设置"};for(int i:new int[]{0,2}){final int index=i;Button b=textButton(titles[i],()->{if(index==1){a.sdScreen.show(false);}else selectPage(index==0?3:index);});b.setTextSize(12);b.setPadding(dp(4),dp(8),dp(4),dp(8));b.setGravity(Gravity.CENTER);b.setCompoundDrawablePadding(dp(6));nav.addView(b,new LinearLayout.LayoutParams(0,dp(56),1));navigation[i]=b;}root.addView(nav);setNavigation(0);a.setContentView(root);root.requestApplyInsets();
 }
 void buildEditor(){
  editorDialog=new android.app.Dialog(a,android.R.style.Theme_Material_NoActionBar);LinearLayout root=dialogRoot();pageBar(root,"画面调整",()->editorDialog.dismiss());ScrollView scroll=new ScrollView(a);LinearLayout content=stack();content.setPadding(dp(24),dp(20),dp(24),dp(24));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));a.column=content;
  AspectPreview frame=new AspectPreview();frame.setBackgroundColor(0xff111111);editorPreview=new ImageView(a);editorPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);frame.addView(editorPreview,new FrameLayout.LayoutParams(-1,-1));content.addView(frame,new LinearLayout.LayoutParams(-1,-2));resolutionHint=text("",12);resolutionHint.setTextColor(MUTED);rule();
  LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(label("显示方式",14,INK),new LinearLayout.LayoutParams(0,-2,1));
  a.mode=new Spinner(a);ArrayAdapter<String> adapter=new ArrayAdapter<String>(a,android.R.layout.simple_spinner_item,new String[]{"完整显示","裁切填满","拉伸填满","原尺寸加底"}){@Override public View getView(int p,View old,ViewGroup parent){TextView t=(TextView)super.getView(p,old,parent);t.setTextColor(INK);t.setTextSize(14);return t;}};
  adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);a.mode.setAdapter(adapter);a.mode.setBackgroundTintList(ColorStateList.valueOf(MUTED));a.mode.setContentDescription("显示方式");row.addView(a.mode,new LinearLayout.LayoutParams(dp(164),dp(48)));a.controls.add(a.mode);a.column.addView(row);
  a.mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View v,int p,long id){if(p==a.lastMode)return;a.lastMode=p;if(!a.changing)a.schedule();}public void onNothingSelected(AdapterView<?> p){}});
  crop=button("选择裁切区域",()->CropSelectionView.show(a));crop.setVisibility(View.GONE);
  LinearLayout bgRow=new LinearLayout(a);bgRow.setGravity(Gravity.CENTER_VERTICAL);bgRow.addView(label("背景颜色",14,INK),new LinearLayout.LayoutParams(0,-2,1));
  a.background=new Spinner(a);ArrayAdapter<String> backgrounds=new ArrayAdapter<String>(a,android.R.layout.simple_spinner_item,new String[]{"黑色","灰色","白色"}){@Override public View getView(int p,View old,ViewGroup parent){TextView t=(TextView)super.getView(p,old,parent);t.setTextColor(INK);t.setTextSize(14);return t;}};backgrounds.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);a.background.setAdapter(backgrounds);a.background.setBackgroundTintList(ColorStateList.valueOf(MUTED));a.background.setContentDescription("背景颜色");bgRow.addView(a.background,new LinearLayout.LayoutParams(dp(164),dp(48)));a.column.addView(bgRow);a.controls.add(a.background);
  a.background.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View v,int p,long id){if(p==a.lastBackground)return;a.lastBackground=p;if(!a.changing)a.schedule();}public void onNothingSelected(AdapterView<?> p){}});
  a.zoom=slider("缩放",300,0);
  more=textButton("位置与画质",()->{advanced.setVisibility(advanced.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);});LinearLayout.LayoutParams moreSpace=new LinearLayout.LayoutParams(-1,-2);moreSpace.setMargins(0,dp(8),0,dp(8));a.column.addView(more,moreSpace);
  LinearLayout main=a.column;advanced=new LinearLayout(a);advanced.setOrientation(1);main.addView(advanced);a.column=advanced;a.x=slider("左右位置",200,100);a.y=slider("上下位置",200,100);a.quality=slider("JPEG 质量",70,65);a.column=main;advanced.setVisibility(View.GONE);
  reset=textButton("重置调整",()->{a.reset();a.render();});a.column.addView(reset,new LinearLayout.LayoutParams(-1,-2));rule();

  Button done=make("完成调整",()->{editorDialog.dismiss();},true);editorDoneButton=done;LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(dp(24),dp(8),dp(24),dp(16));root.addView(done,p);editorDialog.setContentView(root);
 }
 void refresh(){
  boolean idle=!a.busy&&!MainActivity.NETWORK_BUSY.get();boolean editable=idle&&a.original!=null;emptyGroup.setVisibility(a.original==null?View.VISIBLE:View.GONE);
  for(View v:new View[]{a.mode,a.background,a.zoom,a.x,a.y,a.quality,more,reset,crop})v.setEnabled(editable);
  crop.setEnabled(editable&&a.prepared!=null);
  crop.setVisibility(a.original!=null&&a.mode.getSelectedItemPosition()==1?View.VISIBLE:View.GONE);
  if(a.original!=null){resolutionHint.setVisibility(View.VISIBLE);String size="原图 "+a.sourceWidth+" × "+a.sourceHeight+"。";boolean small=a.sourceWidth<720&&a.sourceHeight<480,large=a.sourceWidth>720||a.sourceHeight>480;String hint=small?"可等比放大、拉伸，或原尺寸加底。":large?"完整显示将等比缩小；裁切填满可自选范围。":a.sourceWidth==720&&a.sourceHeight==480?"尺寸符合要求，可直接使用或继续调整。":"可完整显示并加底，或裁切填满。";resolutionHint.setText(size+hint);}else resolutionHint.setVisibility(View.GONE);
  a.confirm.setVisibility(a.prefs.getBoolean("pending",false)?View.VISIBLE:View.GONE);
  progress.setVisibility(idle?View.GONE:View.VISIBLE);
  if(editorPreview!=null&&editorShown!=a.shown){editorPreview.setImageBitmap(a.shown);editorShown=a.shown;}
  for(int i=0;i<a.controls.size();i++){View v=a.controls.get(i);if("image-action".equals(v.getTag()))v.setEnabled(editable&&a.prepared!=null);}
  refreshConnection();workflowSteps();
 }
 void refreshConnection(){
  long now=android.os.SystemClock.elapsedRealtime();CameraIdentity actual=a.cameraState.current(now);CameraIdentity context=a.workflowIdentity();int route=CameraWorkflowPolicy.route(context);boolean sd=route==CameraWorkflowPolicy.SD,known=sd||route==CameraWorkflowPolicy.WIFI;
  String workflow=route==CameraWorkflowPolicy.UNVERIFIED?"连接相机并检测型号，自动准备对应的换图流程。":route==CameraWorkflowPolicy.UNSUPPORTED?"此机型或固件暂不支持换图；仍可处理和保存图片。":sd?(actual==null?"相机已断开；保留本次识别的 "+context.model+" · SD 流程。":"已为此相机准备 SD 流程，选图后按步骤继续。"):actual==null?"重新连接相机 Wi-Fi 后继续；图片处理仍可使用。":a.prepared==null?"选择一张图片，调整预览后更换关机画面。":"核对预览后更换关机图，或恢复原厂画面。";
  if(workflowHint!=null&&!android.text.TextUtils.equals(workflowHint.getText(),workflow))workflowHint.setText(workflow);
  if(navigation[1]!=null)navigation[1].setVisibility(View.GONE);
  boolean idle=!a.busy&&!MainActivity.NETWORK_BUSY.get(),pending=a.prefs.getBoolean("pending",false);
  String action=route==CameraWorkflowPolicy.UNVERIFIED?"检测相机，开始换图":route==CameraWorkflowPolicy.UNSUPPORTED?"重新检测相机":route==CameraWorkflowPolicy.WIFI&&actual==null?"重新连接相机":sd?"继续换图":"更换关机图";
  if(a.upload!=null){if(!android.text.TextUtils.equals(a.upload.getText(),action))a.upload.setText(action);a.upload.setEnabled(idle&&!pending&&(!known||route==CameraWorkflowPolicy.WIFI&&actual==null||a.prepared!=null||sd&&a.sdScreen!=null&&a.sdScreen.job!=null));}
  if(a.restore!=null){a.restore.setVisibility(known?View.VISIBLE:View.GONE);a.restore.setEnabled(idle&&!pending&&(sd||actual!=null&&a.stock!=null));}
  String model=a.cameraState.model(now),firmware=a.cameraState.firmware(now);  if(!android.text.TextUtils.equals(cameraModel.getText(),model))cameraModel.setText(model);
  if(!android.text.TextUtils.equals(cameraState.getText(),firmware))cameraState.setText(firmware);
  if(settingsIdentity!=null){String identity=model+"\n"+firmware;if(!android.text.TextUtils.equals(settingsIdentity.getText(),identity))settingsIdentity.setText(identity);}
  int state=actual==null?CameraConnectionState.DISCONNECTED:(actual.wifiSupported()||actual.sdSupported())?CameraConnectionState.SUPPORTED:CameraConnectionState.UNSUPPORTED,color=state==CameraConnectionState.SUPPORTED?CONNECTED:state==CameraConnectionState.UNSUPPORTED?0xfff06a6a:0xff8b8b87;
  if(color!=displayedConnectionColor){connectionDot.setBackground(fill(color,5,false));displayedConnectionColor=color;}
  connectionDot.setContentDescription(state==CameraConnectionState.SUPPORTED?"连接状态：支持":state==CameraConnectionState.UNSUPPORTED?"连接状态：不支持":"连接状态：未连接");
 }
 void pickPhoto(){Intent i;if(Build.VERSION.SDK_INT>=33){i=new Intent(MediaStore.ACTION_PICK_IMAGES);i.setType("image/*");}else{i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);}try{a.startActivityForResult(i,10);}catch(ActivityNotFoundException error){Intent fallback=new Intent(Intent.ACTION_OPEN_DOCUMENT);fallback.setType("image/*");fallback.addCategory(Intent.CATEGORY_OPENABLE);a.startActivityForResult(fallback,10);}}
 void workflowSteps(){if(operationNote==null)return;boolean pending=a.prefs.getBoolean("pending",false),idle=!a.busy&&!MainActivity.NETWORK_BUSY.get();CameraIdentity live=a.cameraState.current(android.os.SystemClock.elapsedRealtime());int step=WorkflowStages.wifi(live!=null&&(live.wifiSupported()||live.sdSupported()),a.prepared!=null,pending,!a.wifiResultAllowed());
  choose.setVisibility(pending?View.GONE:View.VISIBLE);editDock.setVisibility(pending||a.prepared==null?View.GONE:View.VISIBLE);imageNote.setVisibility(pending||a.prepared!=null?View.GONE:View.VISIBLE);operationNote.setVisibility(pending||a.prepared!=null?View.VISIBLE:View.GONE);workflowHint.setVisibility(View.GONE);a.upload.setVisibility(pending?View.GONE:View.VISIBLE);a.confirm.setVisibility(pending&&a.wifiResultAllowed()?View.VISIBLE:View.GONE);a.confirm.setEnabled(idle&&a.wifiResultAllowed());resumeWifi.setVisibility(pending&&!a.wifiResultAllowed()?View.VISIBLE:View.GONE);resumeWifi.setEnabled(idle);
  String note=pending?(a.wifiResultAllowed()?"第 4 步 · 检查相机实际画面\n机型和固件已由程序核实。联机接口无法读取当前关机图，请实际关机查看后记录结果；程序不会自动重发。":"续办检查 · 查询相机\n本地有未结束任务。请连接原相机 Wi-Fi，程序重新读取实际型号、固件后才能核对结果；不能按缓存跳过或重新上传。"):
   step==0?"第 1 步 · 自动识别相机\n连接相机 Wi-Fi 后程序会自动检查型号和固件。可以先处理图片，识别不合格不会执行相机操作。":a.workflowRoute()==CameraWorkflowPolicy.SD?"第 3 步 · 进入 SD 任务\n继续后按所选机型处理图片，检查卡内真实进度，再依次暂存、安装和核验。":a.prepared==null?"先选择并检查图片，图片未准备完成不会开放替换。":a.factoryIntent?"第 3 步 · 确认原厂画面\n上方是此机型的原厂图片。点击后程序再次核验相机、固件和连接，通过后恢复图片。":"第 3 步 · 确认图片并替换\n请检查上方最终图片。点击后程序自动复核机型、固件与连接，通过后上传并检查接口结果。";
  if(!android.text.TextUtils.equals(operationNote.getText(),note))operationNote.setText(note);if(!pending&&a.workflowRoute()==CameraWorkflowPolicy.WIFI&&live!=null&&a.prepared!=null)a.upload.setText(a.factoryIntent?"确认并恢复原厂画面":"确认并替换");
 }
 void menu(){
  PopupMenu popup=new PopupMenu(a,tools);String[] titles={"导出 JPEG","保存图片备份包","保存到图片库","离线检查","导出操作结果","图片校验信息","GR IIIx · SD 换图"};for(int i=0;i<titles.length;i++)if(i!=6||a.workflowRoute()==CameraWorkflowPolicy.SD)popup.getMenu().add(0,i,0,titles[i]);for(int i=0;i<3;i++)popup.getMenu().findItem(i).setEnabled(a.prepared!=null);
  popup.setOnMenuItemClickListener(item->{try{switch(item.getItemId()){
   case 0:a.export(false);break;case 1:a.export(true);break;case 2:a.saveToLibrary();break;
   case 3:a.offlineCheck();break;case 4:a.exportData=a.lastReport.getBytes(java.nio.charset.StandardCharsets.UTF_8);a.exportMime="application/json";a.saveDialog("GR-EndScreen-result.json");break;
   case 5:new android.app.AlertDialog.Builder(a).setTitle("图片校验信息").setMessage(a.prepared==null?"尚未选择图片":"720 × 480 · "+a.prepared.length+" 字节\nSHA-256\n"+EndScreenPolicy.sha256(a.prepared)).setPositiveButton("关闭",null).show();break;
   case 6:a.sdScreen.show(false);break;
  }}catch(Exception e){a.message(e.getMessage());}return true;});popup.show();
 }
 final class AspectPreview extends FrameLayout {final boolean responsive;AspectPreview(){this(false);}AspectPreview(boolean responsive){super(a);this.responsive=responsive;}@Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w),height=width*2/3;if(responsive&&homePage!=null&&homePage.getHeight()>0){int used=a.column.getPaddingTop()+a.column.getPaddingBottom();for(int i=0;i<a.column.getChildCount();i++){View child=a.column.getChildAt(i);if(child==this||child.getVisibility()==View.GONE)continue;used+=child.getMeasuredHeight();ViewGroup.LayoutParams p=child.getLayoutParams();if(p instanceof ViewGroup.MarginLayoutParams){used+=((ViewGroup.MarginLayoutParams)p).topMargin+((ViewGroup.MarginLayoutParams)p).bottomMargin;}}height=Math.max(dp(48),Math.min(height,homePage.getHeight()-used));}super.onMeasure(w,MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));}}
}
