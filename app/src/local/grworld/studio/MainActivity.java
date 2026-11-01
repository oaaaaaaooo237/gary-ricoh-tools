package local.grworld.studio;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.net.NetworkCapabilities;
import android.widget.*;
import android.view.View;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import org.json.JSONObject;

public final class MainActivity extends Activity {
 static final ExecutorService WORK=Executors.newSingleThreadExecutor();
 static final ExecutorService READS=Executors.newSingleThreadExecutor();
 static final AtomicBoolean NETWORK_BUSY=new AtomicBoolean();
 static final AtomicBoolean PROBE_BUSY=new AtomicBoolean();
 static final String STOCK_HASH="d4b8554a7b71094bd6f63d87d5113b8cfe9f4db5cd5ec5317b48244a3b0cbf96";
 LinearLayout column;TextView status,details,empty;ImageView preview;StudioScreen screen;Gr3SdScreen sdScreen;EnhancementScreen enhancements;AlertDialog workflowDialog;String exportContext="";boolean featureEntered;
 Spinner mode,background;SeekBar zoom,x,y,quality;Button upload,restore,confirm;int sourceWidth,sourceHeight;
 final ArrayList<View> controls=new ArrayList<>();
 byte[] source,prepared,stock,exportData;Bitmap original,shown;
 SharedPreferences prefs;boolean changing,busy,factoryIntent,wifiRecoveryRequired;String wifiCheckedTask="";int revision,lastMode,lastBackground;
 volatile boolean foreground;boolean checking,callbackRegistered;
 final CameraConnectionState cameraState=new CameraConnectionState();
 final WorkflowSession workflowSession=new WorkflowSession();
 CameraIdentity workflowIdentity(){return workflowSession.context(cameraState.current(SystemClock.elapsedRealtime()));}
 int workflowRoute(){return CameraWorkflowPolicy.route(workflowIdentity());}
 void cameraAction(boolean factory){
  int route=workflowRoute();
  if(route==CameraWorkflowPolicy.SD){sdScreen.begin(factory);return;}
  if(route==CameraWorkflowPolicy.WIFI){CameraIdentity id=cameraState.current(SystemClock.elapsedRealtime());if(id==null){check();return;}if(factory){try{setImage(factoryBytes(id),"对应机型的原厂画面",true);factoryIntent=true;refresh();}catch(Exception e){message(e.getMessage());}}else send(factoryIntent);return;}
  check();
 }
 boolean manualCheckRequested;
 Network verifiedNetwork;final AtomicInteger connectionEpoch=new AtomicInteger();
 ConnectivityManager networkManager;
 String name="已选择图片",lastReport="尚未检查",exportMime;
 final Handler handler=new Handler(Looper.getMainLooper());
 final Runnable renderTask=()->render();
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);prefs=getSharedPreferences("studio",MODE_PRIVATE);wifiRecoveryRequired=prefs.getBoolean("pending",false);
  screen=new StudioScreen(this);screen.build();sdScreen=new Gr3SdScreen(this);enhancements=new EnhancementScreen(this);screen.selectPage(3);
  try{
   byte[] factory=CameraClient.read(getAssets().open("GB_HDF.jpg"),EndScreenPolicy.MAX_BYTES);EndScreenPolicy.image(factory);
   if(!STOCK_HASH.equals(EndScreenPolicy.sha256(factory)))throw new IOException("原厂资源校验失败");
   stock=factory;
   lastReport=prefs.getString("report","尚未检查");
   if(prefs.getBoolean("has_user_image",false)&&new File(getFilesDir(),"current.jpg").isFile())setImage(CameraClient.read(openFileInput("current.jpg"),EndScreenPolicy.MAX_BYTES),"上次准备的画面",true);

   if(prefs.getBoolean("pending",false))message("上次操作尚未确认实际显示，请先查看相机关机画面；程序不会自动重发。");
  }catch(Exception e){message("载入失败："+e.getMessage());}refresh();
 }
 void message(String s){status.setText(s==null?"操作未完成":s);status.setVisibility(View.VISIBLE);if(screen!=null)screen.feedback(status.getText().toString());}
 @Override public void onBackPressed(){if(screen!=null&&screen.currentPage!=3){screen.selectPage(3);return;}super.onBackPressed();}
 void refresh(){for(View c:controls)c.setEnabled(!busy&&!NETWORK_BUSY.get());boolean ready=!busy&&!NETWORK_BUSY.get()&&foreground&&cameraState.supported(SystemClock.elapsedRealtime())&&!prefs.getBoolean("pending",false);upload.setEnabled(ready&&prepared!=null);restore.setEnabled(ready&&stock!=null);confirm.setEnabled(!busy&&!NETWORK_BUSY.get()&&prefs.getBoolean("pending",false));if(screen!=null)screen.refresh();if(sdScreen!=null)sdScreen.refreshApplicability();if(enhancements!=null)enhancements.refresh();}
 void reset(){changing=true;lastMode=0;mode.setSelection(0);lastBackground=0;background.setSelection(0);zoom.setProgress(0);x.setProgress(100);y.setProgress(100);quality.setProgress(65);changing=false;}
 int backgroundColor(){return new int[]{Color.BLACK,0xff808080,Color.WHITE}[background.getSelectedItemPosition()];}
 void applyCrop(float scale,float panX,float panY){changing=true;lastMode=1;mode.setSelection(1);zoom.setProgress(Math.round((scale-1)*100));x.setProgress(Math.round((panX+1)*100));y.setProgress(Math.round((panY+1)*100));changing=false;render();}
 void schedule(){if(zoom==null||busy)return;factoryIntent=false;prepared=null;revision++;refresh();handler.removeCallbacks(renderTask);handler.postDelayed(renderTask,180);}

 void setImage(byte[] bytes,String title,boolean exact){setImage(bytes,title,exact,null,null);}
 void setImage(byte[] bytes,String title,boolean exact,byte[] projectPrepared,JSONObject settings){
  factoryIntent=false;handler.removeCallbacks(renderTask);final int token=++revision;busy=true;refresh();reset();
  WORK.execute(()->{try{
   Bitmap bitmap=ImageEditor.decode(bytes);final int[] dims=ImageEditor.dimensions(bytes);byte[] jpeg=bytes;
   try{if(!exact)throw new IllegalArgumentException();EndScreenPolicy.image(jpeg);}catch(IllegalArgumentException e){jpeg=ImageEditor.render(bitmap,0,1,0,0,95);}
   final byte[] ready=projectPrepared==null?jpeg:projectPrepared;runOnUiThread(()->{if(isDestroyed()||token!=revision){bitmap.recycle();return;}if(original!=null)original.recycle();original=bitmap;source=bytes;name=title;sourceWidth=dims[0];sourceHeight=dims[1];
    if(settings!=null){changing=true;lastMode=settings.optInt("mode",0);mode.setSelection(lastMode);zoom.setProgress(settings.optInt("zoom",0));x.setProgress(settings.optInt("x",100));y.setProgress(settings.optInt("y",100));quality.setProgress(settings.optInt("quality",65));changing=false;}
    if(settings!=null){changing=true;lastBackground=settings.optInt("background",0);background.setSelection(lastBackground);changing=false;}busy=false;display(ready);});
  }catch(Exception e){runOnUiThread(()->{busy=false;message("图片载入失败："+e.getMessage());refresh();});}});
 }
 void render(){if(original==null||busy)return;final int token=++revision;final Bitmap bitmap=original;final int m=mode.getSelectedItemPosition(),q=quality.getProgress()+30,bg=backgroundColor();final float z=1+zoom.getProgress()/100f,px=(x.getProgress()-100)/100f,py=(y.getProgress()-100)/100f;
  busy=true;refresh();WORK.execute(()->{try{byte[] jpeg=ImageEditor.render(bitmap,m,z,px,py,q,bg);runOnUiThread(()->{if(isDestroyed()||token!=revision)return;busy=false;display(jpeg);});}catch(Exception e){runOnUiThread(()->{busy=false;message("预览失败："+e.getMessage());refresh();});}});
 }
 void display(byte[] jpeg){try{
  EndScreenPolicy.image(jpeg);Bitmap next=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);if(next==null)throw new IOException("最终图片无法解码");
  preview.setImageBitmap(next);if(shown!=null)shown.recycle();shown=next;prepared=jpeg;
  empty.setVisibility(View.GONE);details.setText("720 × 480 · "+(jpeg.length/1024)+" KB · JPEG");
  writeFile("current.jpg",jpeg);prefs.edit().putBoolean("has_user_image",true).apply();refresh();
 }catch(Exception e){prepared=null;message("图片准备未完成："+e.getMessage());refresh();}}
 void writeFile(String name,byte[] data)throws IOException{File tmp=new File(getFilesDir(),name+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(data);out.getFD().sync();}if(!tmp.renameTo(new File(getFilesDir(),name)))throw new IOException("无法保存本地记录");}
 CameraClient camera()throws Exception{return new CameraClient((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE));}
 void invalidateConnection(String reason){boolean changed=cameraState.clear();verifiedNetwork=null;connectionEpoch.incrementAndGet();if(changed)refresh();}
 void check(){featureEntered=true;probe(true);}
 boolean observeWifiTask(CameraIdentity actual){if(actual.supported()&&status.getText().toString().startsWith("连接检查失败："))message("连接已确认。");if(!prefs.getBoolean("pending",false))return false;String previous=wifiCheckedTask;boolean before=wifiRecoveryRequired;try{JSONObject r=new JSONObject(prefs.getString("report","{}"));if(actual.model.equals(r.optString("model"))&&actual.firmware.equals(r.optString("firmware"))&&!r.optString("operation_id").isEmpty()){wifiCheckedTask=r.getString("operation_id");wifiRecoveryRequired=false;}else{wifiCheckedTask="";wifiRecoveryRequired=true;}}catch(Exception invalid){wifiCheckedTask="";wifiRecoveryRequired=true;}if(!wifiRecoveryRequired&&status.getText().toString().startsWith("记录失败：请先重新连接相机"))message("相机身份已重新核实，请核对实际关机画面；上传结果仍以实际显示为准。");return before!=wifiRecoveryRequired||!previous.equals(wifiCheckedTask);}
 boolean wifiResultAllowed(){try{return prefs.getBoolean("pending",false)&&!wifiRecoveryRequired&&!wifiCheckedTask.isEmpty()&&wifiCheckedTask.equals(new JSONObject(prefs.getString("report","{}")).optString("operation_id"));}catch(Exception e){return false;}}
 String sdExportContext(String filename){Gr3SdSession j=sdScreen==null?null:sdScreen.job;if(j==null)return "";for(int phase=1;phase<=3;phase++){String expected="GR3x-"+j.id+"-"+(phase==1?"暂存":phase==2?"安装":"备份恢复")+".zip";if(filename.equals(expected))return j.id+":"+phase;}return "";}
 void probe(boolean manual){
  if(!foreground||!featureEntered||NETWORK_BUSY.get())return;
  manualCheckRequested|=manual;
  if(checking||!PROBE_BUSY.compareAndSet(false,true))return;
  if(NETWORK_BUSY.get()){PROBE_BUSY.set(false);return;}
  checking=true;final int epoch=connectionEpoch.get();
  READS.execute(()->{CameraClient client=null;CameraClient.IdentityRead read=null;String error=null;
   try{client=camera();read=client.readIdentity();ConnectionPolicy.reply(read.elapsedMs);}catch(Exception e){error=connectionError(e);}
   final CameraClient checked=client;final CameraClient.IdentityRead observed=read;final long completed=SystemClock.elapsedRealtime();final String failure=error;
   PROBE_BUSY.set(false);runOnUiThread(()->{
    checking=false;if(isDestroyed())return;boolean showMessage=manualCheckRequested;manualCheckRequested=false;
    if(foreground&&epoch==connectionEpoch.get()){
     if(failure==null){
      CameraIdentity before=cameraState.current(completed);boolean changed=cameraState.observe(observed.identity,completed);
      if(before!=null&&(!before.same(observed.identity)||!checked.network.equals(verifiedNetwork)))connectionEpoch.incrementAndGet();
      verifiedNetwork=checked.network;boolean taskChanged=observeWifiTask(observed.identity);if(changed||taskChanged)refresh();
     }
     else{invalidateConnection("未通过检测 · "+failure);}
     if(showMessage)message(failure==null?(observed.identity.supported()?"连接已确认。": "当前机型或固件暂不支持换图。"):"连接检查失败："+failure);
    }
    if(foreground){handler.removeCallbacks(pollConnection);handler.postDelayed(pollConnection,ConnectionPolicy.POLL_MS);}
   });
  });
 }
 String connectionError(Exception error){
  if(error instanceof java.net.SocketTimeoutException)return "响应超时，请检查相机 Wi-Fi";
  if(error instanceof java.net.ConnectException)return "无法连接，请检查相机 Wi-Fi";
  if(error instanceof org.json.JSONException)return "返回信息无法识别，请确认连接的是相机";
  String value=error.getMessage();if(value==null)return "连接检查未完成，请重新检测";
  return value.length()>96?value.substring(0,96)+"…":value;
 }
 byte[] factoryBytes(CameraIdentity id)throws Exception{
  EndScreenPolicy.camera(id.model,id.firmware);byte[] bytes=CameraClient.read(getAssets().open(EnhancementPolicy.stockAsset(id)),EndScreenPolicy.MAX_BYTES);EndScreenPolicy.image(bytes);if(!EnhancementPolicy.stockHash(id).equals(EndScreenPolicy.sha256(bytes)))throw new IOException("对应机型原厂资源校验失败");return bytes;
 }
 void send(boolean factory){
  if(!foreground||busy||NETWORK_BUSY.get())return;
  CameraIdentity actual=cameraState.current(SystemClock.elapsedRealtime());
  if(!CameraWorkflowPolicy.wifiAllowed(actual)){workflowDialog=new AlertDialog.Builder(this).setTitle(actual==null?"Wi-Fi 工作流尚未核实":"此相机不适用 Wi-Fi 换图").setMessage(CameraWorkflowPolicy.wifiExplanation(actual)).setPositiveButton("知道了",null).show();return;}
  byte[] factoryImage=null;try{if(factory)factoryImage=factoryBytes(actual);}catch(Exception e){message(e.getMessage());return;}
  if(!foreground||!CameraWorkflowPolicy.wifiAllowed(actual)||(factory?factoryImage==null:prepared==null)||prefs.getBoolean("pending",false)||!NETWORK_BUSY.compareAndSet(false,true))return;
  final int epoch=connectionEpoch.get();final Network expectedNetwork=verifiedNetwork;
  final CameraIdentity expectedIdentity=cameraState.current(SystemClock.elapsedRealtime());
  handler.removeCallbacks(renderTask);final byte[] jpeg=(factory?factoryImage:prepared).clone();busy=true;refresh();message("正在连续检查连接、机型和固件，尚未上传……");
  // Same serial queue as read-only probes: a click waits for any in-flight GET.
  READS.execute(()->{boolean attempted=false,accepted=false;JSONObject report=new JSONObject();String message;
   try{
    EndScreenPolicy.image(jpeg);CameraClient client=camera();
    if(!client.network.equals(expectedNetwork))throw new IOException("Wi-Fi 已切换，请重新检测");
    ConnectionPolicy.preflight(()->{requireActivePreflight(epoch);return client.identity(expectedIdentity);},millis->{Thread.sleep(millis);requireActivePreflight(epoch);});
    String hash=EndScreenPolicy.sha256(jpeg);
    String id=UUID.randomUUID().toString();writeFile("operation-"+id+".jpg",jpeg);
    report.put("tool","GR EndScreen Studio Android 0.11.2-preview");report.put("operation_id",id);report.put("action",factory?"upload_factory_picture":"replace_picture");report.put("model",expectedIdentity.model);report.put("firmware",expectedIdentity.firmware);report.put("preflight_samples",3);report.put("image_sha256",hash);report.put("image_bytes",jpeg.length);report.put("device_readback",false);report.put("display_verified",false);report.put("status","outcome_unknown");
    requireActivePreflight(epoch);client.requireSameNetwork();
    if(!prefs.edit().putBoolean("pending",true).putString("report",report.toString(2)).commit())throw new IOException("本地操作记录保存失败，未发送");
    attempted=true;client.upload(jpeg);accepted=true;report.put("status","camera_accepted");report.put("http_status",200);report.put("errCode",200);
    message=factory?"相机已接受原厂图片。请关机核对画面，再记录结果。":"相机已接受图片。请关机核对画面，再记录结果。";
   }catch(Exception e){message=attempted?"发送结果未确认，请先查看实际关机画面；不要直接重发。":"发送前检查失败，尚未上传："+e.getMessage();try{report.put("status",attempted?"outcome_unknown":"not_sent");report.put("error",e.getClass().getSimpleName());}catch(Exception ignored){}}
   try{report.put("write_requests",attempted?1:0);lastReport=report.toString(2);prefs.edit().putString("report",lastReport).commit();writeFile("last-operation.json",lastReport.getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
   final String info=message;final boolean succeeded=accepted;NETWORK_BUSY.set(false);runOnUiThread(()->{if(isDestroyed())return;busy=false;if(report.optInt("write_requests",0)>0){wifiCheckedTask=report.optString("operation_id");wifiRecoveryRequired=false;}if(!succeeded)invalidateConnection("操作未确认");message(info);refresh();if(foreground){handler.removeCallbacks(pollConnection);handler.post(pollConnection);}});
  });
 }
 void requireActivePreflight(int epoch)throws IOException {if(!foreground||connectionEpoch.get()!=epoch||screen.currentPage!=0)throw new IOException("连接状态或页面已变化，已取消上传前检查");}
 void saveDialog(String filename){exportContext=filename.startsWith("GRKit-1.3.5-")?"enhancement|"+enhancements.currentKey():(sdExportContext(filename));Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(exportMime);i.putExtra(Intent.EXTRA_TITLE,filename);startActivityForResult(i,11);}
 void export(boolean project){try{
  if(prepared==null)throw new IOException("请先准备图片");
  exportMime=project?"application/zip":"image/jpeg";exportData=project?backup():prepared.clone();saveDialog(project?"GR-EndScreen.grend":"GR-EndScreen-720x480.jpg");
 }catch(Exception e){message(e.getMessage());}}
 byte[] backup()throws Exception{return backup(source,prepared);}
 byte[] backup(byte[] originalSource,byte[] finalJpeg)throws Exception{
  if(originalSource==null||finalJpeg==null)throw new IOException("请先选择图片");
  JSONObject p=new JSONObject();p.put("version",1);p.put("source_sha256",EndScreenPolicy.sha256(originalSource));p.put("prepared_sha256",EndScreenPolicy.sha256(finalJpeg));p.put("scope","local_image_project_not_camera_backup");p.put("name",name);
  p.put("mode",mode.getSelectedItemPosition());p.put("zoom",zoom.getProgress());p.put("x",x.getProgress());p.put("y",y.getProgress());p.put("quality",quality.getProgress());
  p.put("background",background.getSelectedItemPosition());
  ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(out)){
   String[] names={"project.json","source.image","prepared.jpg"};byte[][] data={p.toString(2).getBytes(StandardCharsets.UTF_8),originalSource,finalJpeg};
   for(int i=0;i<names.length;i++){z.putNextEntry(new ZipEntry(names[i]));z.write(data[i]);z.closeEntry();}
  }return out.toByteArray();
 }
 static final class Project{byte[] source,prepared;JSONObject settings;}
 Project projectImage(byte[] bytes)throws Exception{
  HashMap<String,byte[]> entries=new HashMap<>();try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry e;while((e=z.getNextEntry())!=null){String n=e.getName();if(entries.containsKey(n)||!Arrays.asList("project.json","source.image","prepared.jpg").contains(n))throw new IOException("备份包条目不符");ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int count,max=n.equals("source.image")?32*1024*1024:n.equals("prepared.jpg")?EndScreenPolicy.MAX_BYTES:8192;while((count=z.read(buf))!=-1){if(b.size()+count>max)throw new IOException("备份条目超过限制");b.write(buf,0,count);}entries.put(n,b.toByteArray());}}
  if(entries.size()!=3)throw new IOException("备份包内容不完整");JSONObject p=new JSONObject(new String(entries.get("project.json"),StandardCharsets.UTF_8));
  if(p.getInt("version")!=1||!EndScreenPolicy.sha256(entries.get("source.image")).equals(p.getString("source_sha256"))||!EndScreenPolicy.sha256(entries.get("prepared.jpg")).equals(p.getString("prepared_sha256")))throw new IOException("备份包哈希不一致");
  String[] keys={"mode","zoom","x","y","quality"};int[] max={3,300,200,200,70};for(int i=0;i<keys.length;i++){int v=p.getInt(keys[i]);if(v<0||v>max[i])throw new IOException("备份包调整参数不符");}if(p.optInt("background",0)<0||p.optInt("background",0)>2)throw new IOException("备份包背景参数不符");
  EndScreenPolicy.image(entries.get("prepared.jpg"));Project result=new Project();result.source=entries.get("source.image");result.prepared=entries.get("prepared.jpg");result.settings=p;return result;
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==47||request==48){if(result==RESULT_OK&&data!=null&&data.getData()!=null)sdScreen.reconcileResult(data.getData(),data.getFlags(),request==48);return;}if(request==46){if(result==RESULT_OK&&data!=null&&data.getData()!=null)enhancements.manualBaselineResult(data.getData());return;}if(request==43||request==44){if(result!=RESULT_OK||data==null||data.getData()==null){enhancements.request=null;return;}if(request==43)enhancements.result(data.getData(),data.getFlags());else enhancements.readResult(data.getData(),data.getFlags());return;}if(request==42){if(result!=RESULT_OK||data==null||data.getData()==null)sdScreen.card.cancelled();else sdScreen.card.result(data.getData(),data.getFlags());return;}if(request==11&&result!=RESULT_OK)exportContext="";if(result!=RESULT_OK||data==null||data.getData()==null)return;
  if(request==40){sdScreen.reconcileResult(data.getData(),data.getFlags(),false);return;}if(request==41){sdScreen.result(request,data.getData());return;}
  if(request==11){final String savedContext=exportContext;exportContext="";final byte[] bytes=exportData;exportData=null;if(bytes==null){message("导出会话已失效，请重新导出");return;}WORK.execute(()->{try{try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new IOException("无法打开保存位置");out.write(bytes);}runOnUiThread(()->{message("文件已保存。");if(sdScreen!=null&&sdScreen.job!=null&&savedContext.startsWith(sdScreen.job.id+":")){prefs.edit().putString("sd_manual_prepared",savedContext).apply();sdScreen.cleanupConfirmed.setChecked(false);sdScreen.update();}if(savedContext.startsWith("enhancement|")&&enhancements!=null&&savedContext.equals("enhancement|"+enhancements.currentKey()))enhancements.manualSaved();});}catch(Exception e){runOnUiThread(()->message("保存失败："+e.getMessage()));}});}
  if(request==10||request==12||request==13){busy=true;refresh();WORK.execute(()->{try{
   final byte[] bytes=CameraClient.read(getContentResolver().openInputStream(data.getData()),34*1024*1024);
   final boolean zip=bytes.length>3&&bytes[0]=='P'&&bytes[1]=='K';
   final Project project=request==12||(request==13&&zip)?projectImage(bytes):null;
   if(project==null&&bytes.length>32*1024*1024)throw new IOException("请选择小于 32 MB 的图片");
   if(request==13&&project==null)EndScreenPolicy.image(bytes);
   runOnUiThread(()->{busy=false;if(project==null)setImage(bytes,"已选择图片",request==13);else setImage(project.source,"已选择图片",true,project.prepared,project.settings);});
  }catch(Exception e){runOnUiThread(()->{busy=false;message("导入失败："+e.getMessage());refresh();});}});}

 }
 void saveToLibrary(){try{
  if(prepared==null)throw new IOException("请先选择图片");final byte[] jpeg=prepared.clone();EndScreenPolicy.image(jpeg);busy=true;refresh();
  WORK.execute(()->{String result;boolean saved=false;try{writeFile("library-"+EndScreenPolicy.sha256(jpeg)+".jpg",jpeg);saved=true;result="已保存到图片库。下次在“选择图片 → 已处理的关机图”中打开。";}catch(Exception e){result="保存失败："+e.getMessage();}final String info=result;final boolean savedOk=saved;runOnUiThread(()->{busy=false;message(info);refresh();});});
 }catch(Exception e){message(e.getMessage());}}
 void recordDisplay(boolean correct){try{if(!wifiResultAllowed())throw new IOException("请先重新连接相机核实中断任务，不能按本地记录确认完成");
  JSONObject report=new JSONObject(prefs.getString("report","{}"));report.put("display_result",correct?"correct":"incorrect");report.put("display_evidence","user_reported");report.put("display_verified",correct);String value=report.toString(2);
  if(!prefs.edit().putString("report",value).putBoolean("pending",false).commit())throw new IOException("记录保存失败");lastReport=value;invalidateConnection("实际画面已记录");
  message(correct?"已记录实际显示正确。再次操作前请开机并检查连接。":"已记录显示不正确。请导出结果供分析，再决定是否重新操作。");
 }catch(Exception e){message("记录失败："+e.getMessage());}refresh();}
 void offlineCheck(){try{
  if(stock==null)throw new IOException("原厂资源尚未准备好");
  final byte[] expectedSource=source==null?stock:source,expectedPrepared=prepared==null?stock:prepared;
  final byte[] archive=backup(expectedSource,expectedPrepared);
  busy=true;refresh();message("正在使用本手机检查图片处理和备份，不访问相机……");
  WORK.execute(()->{String result;
   try{
    int n=OfflineChecks.imagePipeline();Project p=projectImage(archive);
    OfflineChecks.require(Arrays.equals(expectedSource,p.source)&&Arrays.equals(expectedPrepared,p.prepared),"备份恢复原始字节");n++;
    OfflineChecks.require(STOCK_HASH.equals(EndScreenPolicy.sha256(stock)),"原厂资源哈希");n++;
    result="离线检查通过（"+n+" 项）：本手机的图片解码、尺寸、黑边、裁切、位置、缩放、透明底和备份字节一致。没有访问或写入相机。";
   }catch(Exception e){result="离线检查未通过："+e.getMessage()+"。没有访问或写入相机。";}
   final String info=result;runOnUiThread(()->{busy=false;message(info);refresh();});
  });
 }catch(Exception e){message(e.getMessage());}}
 final Runnable observeNetwork=new Runnable(){public void run(){if(isDestroyed())return;if(NETWORK_BUSY.get()){handler.postDelayed(this,500);return;}lastReport=prefs.getString("report",lastReport);refresh();}};
 final Runnable pollConnection=new Runnable(){public void run(){if(!foreground||isDestroyed())return;probe(false);if(!checking)handler.postDelayed(this,ConnectionPolicy.POLL_MS);}};
 final Runnable connectionClock=new Runnable(){public void run(){if(!foreground||isDestroyed())return;if(cameraState.expired(SystemClock.elapsedRealtime()))invalidateConnection("检测结果过期");handler.postDelayed(this,ConnectionPolicy.POLL_MS);}};
 final ConnectivityManager.NetworkCallback networkEvents=new ConnectivityManager.NetworkCallback(){
  @Override public void onLost(Network n){handler.post(()->{if(foreground&&n.equals(verifiedNetwork)){invalidateConnection("Wi-Fi 已断开 · 禁止上传");handler.removeCallbacks(pollConnection);handler.post(pollConnection);}});}
  @Override public void onAvailable(Network n){handler.post(()->{if(foreground){handler.removeCallbacks(pollConnection);handler.post(pollConnection);}});}
 };
 @Override protected void onResume(){super.onResume();foreground=true;if(prefs.getBoolean("pending",false)){wifiRecoveryRequired=true;wifiCheckedTask="";}if(status!=null){
  invalidateConnection("正在重新检测……");networkManager=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
  try{networkManager.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),networkEvents);callbackRegistered=true;}catch(RuntimeException e){callbackRegistered=false;}
  handler.post(pollConnection);handler.post(connectionClock);if(NETWORK_BUSY.get())handler.post(observeNetwork);
 }}
 @Override protected void onPause(){foreground=false;if(sdScreen!=null){sdScreen.cardVerified=false;}if(enhancements!=null&&enhancements.opened()&&enhancements.hasPrepared()){enhancements.mustReconcile=true;enhancements.cardChecked=false;}handler.removeCallbacks(pollConnection);handler.removeCallbacks(connectionClock);if(callbackRegistered){try{networkManager.unregisterNetworkCallback(networkEvents);}catch(RuntimeException ignored){}callbackRegistered=false;}invalidateConnection("返回后重新检测");super.onPause();}
 @Override protected void onDestroy(){handler.removeCallbacks(renderTask);handler.removeCallbacks(observeNetwork);handler.removeCallbacks(pollConnection);handler.removeCallbacks(connectionClock);revision++;super.onDestroy();}
}
