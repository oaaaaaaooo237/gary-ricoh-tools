package local.grworld.studio;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Select -> inspect -> concrete review -> write -> complete SD readback, never camera execution. */
final class SdCardController {
 final MainActivity a;final Gr3SdScreen s;Request request;AlertDialog review;TextView resultView;volatile boolean valid;boolean writing;String issue="";
 static final class Request {final Gr3SdSession snapshot,original;final CameraIdentity camera;final int phase;EnhancementBundle enhancement;EnhancementDeployment.Inspection enhancementReview;boolean pauseEnhancement,resumeEnhancement;Request(Gr3SdSession snapshot,Gr3SdSession original,CameraIdentity camera,int phase){this.snapshot=snapshot;this.original=original;this.camera=camera;this.phase=phase;}}
 SdCardController(MainActivity a,Gr3SdScreen s){this.a=a;this.s=s;}
 void refresh(){if(writing&&(!a.foreground||request==null||s.job!=request.original||!request.camera.same(a.workflowIdentity())||s.editions.getSelectedItemPosition()!=request.snapshot.profile.ordinal()))valid=false;}
 void choose(int phase){try{
  if(a.busy)return;issue="";if(!s.applicable()){s.warnInapplicable();return;}if(phase!=0&&!s.cardVerified)throw new IOException("先读取 SD 卡核实任务实际进度");if(!s.authorized()||s.job==null)throw new IOException("请先核对当前任务的机型与固件");
  Gr3SdSession snapshot=s.copy();if(phase!=0)new SdCardDeployment.Plan(snapshot,phase);request=new Request(snapshot,s.job,a.workflowIdentity(),phase);
  Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
  try{String last=a.prefs.getString("sd_card_tree","");if(!last.isEmpty())intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,Uri.parse(last));}catch(Exception ignored){}
  a.startActivityForResult(intent,42);
 }catch(Exception e){s.error(e);}}
 void cancelled(){request=null;a.message("已取消选择 SD 卡，没有写入文件。");}
 boolean same(Request r){return r!=null&&s.job==r.original&&r.camera.same(a.workflowIdentity())&&s.applicable()&&s.editions.getSelectedItemPosition()==r.snapshot.profile.ordinal();}
 void result(Uri tree,int flags){Request selected=request;if(selected==null){s.error(new IOException("选卡会话已失效，请重新选择；没有写入 SD 卡。"));return;}try{
  SdDocumentCard card=SdDocumentCard.open(a,tree,flags);int grant=flags&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);try{a.getContentResolver().takePersistableUriPermission(tree,grant);}catch(SecurityException ignored){}
  if(!same(selected))throw new IOException("任务或机型已变化，请重新选择 SD 卡");a.prefs.edit().putString("sd_card_tree",tree.toString()).apply();inspect(card,selected);
 }catch(Exception e){request=null;String fallback=selected.phase==0?"自动清理不可用时，请用文件管理器移除本任务启动脚本，并在相机关闭 Script；不要删除其他内容。":"本次没有写入 SD 卡。可使用“手动导出 ZIP”，解压后把 card 内内容复制到卡根目录。";s.error(new IOException(e.getMessage()+"\n"+fallback,e));}}
 void inspect(SdCardDeployment.Card card,Request selected){issue="";a.busy=true;a.refresh();s.update();s.cardStatus.setText("正在检查 SD 卡与现有启动文件，尚未写入……");MainActivity.WORK.execute(()->{try{
  if(selected.phase!=0){SdReconciler.Result evidence=SdReconciler.inspect(card,selected.snapshot);if(evidence.interrupted)throw new IOException(evidence.message);if(evidence.preparedPhase==selected.phase){a.runOnUiThread(()->{a.busy=false;s.applyReconciled(evidence,true);a.refresh();});return;}if(selected.phase==1&&evidence.job.staged||selected.phase==2&&(evidence.job.installed||evidence.job.restored)||selected.phase==3&&evidence.job.restored){a.runOnUiThread(()->{a.busy=false;s.applyReconciled(evidence,true);a.refresh();});return;}}SdCardDeployment.Plan plan=selected.phase==0?null:new SdCardDeployment.Plan(selected.snapshot,selected.phase);SdCardDeployment.Card inspectionCard=card;
  if(EnhancementDeployment.exists(card,EnhancementDeployment.RECORD)||EnhancementDeployment.exists(card,EnhancementDeployment.PAUSED)){
   selected.enhancement=a.enhancements.bundle(selected.camera);selected.resumeEnhancement=EnhancementDeployment.paused(card,selected.enhancement);
   if(plan!=null){plan.files.put(SdCardDeployment.ENTRY,selected.enhancement.all.get(selected.enhancement.entry()));if(!selected.resumeEnhancement){selected.enhancementReview=EnhancementDeployment.inspect(card,selected.enhancement,true,true,false);selected.pauseEnhancement=true;inspectionCard=EnhancementDeployment.withoutStartup(card);}}
  }
  SdCardDeployment.Inspection found=plan==null?SdCardDeployment.inspectCleanup(card,selected.snapshot):SdCardDeployment.inspect(inspectionCard,plan);
  a.runOnUiThread(()->{a.busy=false;a.refresh();s.update();if(a.isDestroyed()||a.isFinishing()||!a.foreground||!same(selected)){s.error(new IOException("页面、任务或机型已变化，未写入 SD 卡；请重新选择。"));return;}showReview(card,selected,plan,found);});
 }catch(Exception e){a.runOnUiThread(()->{a.busy=false;a.refresh();s.update();s.error(e);});}});}
 void showReview(SdCardDeployment.Card card,Request selected,SdCardDeployment.Plan plan,SdCardDeployment.Inspection found){
  LinearLayout content=a.screen.stack();content.setPadding(a.dp(20),a.dp(8),a.dp(20),a.dp(8));TextView info=a.screen.label("存储卡："+card.key()+"\n"+selected.snapshot.profile.label+" · 固件 1.70\n任务："+selected.snapshot.id,14,StudioScreen.INK);content.addView(info);
  TextView detail=a.screen.label(plan==null?"只移除经核对属于本任务的启动脚本和入口文件。保留照片、读回文件和其他内容。相机的 Script 开关仍需手动关闭。":"阶段："+plan.title()+"\n准备启动脚本与相机入口文件，并完整读回核验。保留照片和已有读回文件。\n"+(selected.phase==2?"插回相机执行此阶段后，会替换关机图。":selected.phase==3?"插回相机执行此阶段后，会恢复旧图。":"此步只准备 SD 卡；插回相机执行后才会暂存图片。"),13,StudioScreen.MUTED);detail.setPadding(0,a.dp(12),0,a.dp(12));content.addView(detail);
  if(selected.pauseEnhancement||selected.resumeEnhancement){TextView coexist=a.screen.label(plan==null?"本次清理后将恢复已核验的增强启动入口；继续使用增强时不要关闭 Script。":"此卡有本工具可验证的增强。先暂停其启动入口，再执行本次换图；最终清理时恢复增强，其他模块和设置保留。",13,StudioScreen.INK);content.addView(coexist);}
  CheckBox confirm=new CheckBox(a);confirm.setText("已核对机身版本和 1.70 固件，确认这是要使用的 SD 卡");confirm.setTextSize(13);confirm.setTextColor(StudioScreen.INK);confirm.setMinHeight(a.dp(48));content.addView(confirm);
  ScrollView scroll=new ScrollView(a);scroll.addView(content);int max=(int)(a.getResources().getDisplayMetrics().heightPixels*.55f);scroll.setLayoutParams(new ViewGroup.LayoutParams(-1,-2));scroll.setFillViewport(false);
  review=new AlertDialog.Builder(a).setTitle(plan==null?"清理本任务 SD 启动文件":"写入 SD 卡 · "+plan.title()).setView(scroll).setNegativeButton("取消",(d,w)->request=null).setPositiveButton(plan==null?"移除并核验":"写入并核验",null).create();review.setOnCancelListener(d->request=null);review.show();scroll.post(()->{if(scroll.getHeight()>max){ViewGroup.LayoutParams p=scroll.getLayoutParams();p.height=max;scroll.setLayoutParams(p);}});new ImageLibrary(a,a.screen).tone(review);review.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff777777,StudioScreen.INK}));review.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);confirm.setOnCheckedChangeListener((b,checked)->review.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(checked));
  review.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!confirm.isChecked()||!same(selected)||!a.foreground){s.error(new IOException("确认状态已失效，未写入 SD 卡。"));review.dismiss();return;}s.confirmed.setChecked(true);review.dismiss();write(card,selected,plan,found);});
 }
 void write(SdCardDeployment.Card card,Request selected,SdCardDeployment.Plan plan,SdCardDeployment.Inspection found){
  if(!same(selected)||!s.authorized()||!a.foreground){s.error(new IOException("任务确认已失效"));return;}if(plan!=null&&s.cleanupConfirmed!=null)s.cleanupConfirmed.setChecked(false);valid=true;writing=true;request=selected;a.busy=true;a.refresh();s.update();String progress=plan==null?"正在移除本任务 SD 启动文件并核验……":"正在写入 SD 卡并完整核验，请勿拔出读卡器……";a.message(progress);s.cardStatus.setText(progress);
  MainActivity.WORK.execute(()->{try{SdCardDeployment.Gate gate=()->{if(!valid||!a.foreground||a.isDestroyed())throw new IOException("操作已中止，请重新核对 SD 卡");};
   SdCardDeployment.Inspection actual=found;if(selected.pauseEnhancement){gate.check();EnhancementDeployment.compare(selected.enhancementReview,EnhancementDeployment.inspect(card,selected.enhancement,true,true,false));EnhancementDeployment.suspend(card,selected.enhancement,gate);selected.resumeEnhancement=true;actual=SdCardDeployment.inspect(card,plan);}
   if(plan==null){SdCardDeployment.cleanup(card,selected.snapshot,actual,gate);if(selected.enhancement!=null&&selected.resumeEnhancement)EnhancementDeployment.resume(card,selected.enhancement,gate);}else SdCardDeployment.deploy(card,plan,actual,gate);
   SdReconciler.Result checked=null;try{checked=SdReconciler.inspect(card,selected.snapshot);}catch(Exception ignored){}final SdReconciler.Result actualState=checked;final boolean resumed=plan==null&&selected.enhancement!=null&&selected.resumeEnhancement;if(resumed){a.prefs.edit().putString("sd_enhancement_resumed",selected.snapshot.id).commit();java.util.Set<String> modules=EnhancementBundle.list(EnhancementDeployment.bytes(card,EnhancementBundle.LIST));byte[] boot=EnhancementDeployment.exists(card,"GRKIT/BOOT.TXT")?card.read("GRKIT/BOOT.TXT",512*1024):new byte[0];a.enhancements.recordPrepared(new EnhancementScreen.Request(selected.camera,modules.contains("FRAMING.GRM"),modules.contains("GUIDES.GRM"),false),card.key(),boot,false);}
   a.runOnUiThread(()->{writing=false;a.busy=false;if(actualState!=null)s.applyReconciled(actualState,false);else{s.cardVerified=false;}a.refresh();s.update();if(plan==null){a.prefs.edit().putString("sd_cleaned",selected.snapshot.id).apply();a.message(resumed?"换图启动任务已清理，增强启动入口已恢复并核验。请重新开机核对增强；继续使用时保持 Script 开启。":"本任务 SD 启动文件已移除并核验。请在相机关闭 Script，再勾选清理确认。");}else{String tag=selected.snapshot.id+":"+selected.phase;a.prefs.edit().remove("sd_cleaned").remove("sd_enhancement_resumed").putString("sd_deployed",tag).putString("sd_deployed_card",card.key()).apply();a.message("SD 卡文件完整核验通过。请在系统中安全弹出，再插回相机按本阶段说明执行；尚未验证相机执行结果。");}s.update();request=null;});
  }catch(Exception e){a.runOnUiThread(()->{writing=false;a.busy=false;request=null;a.refresh();s.update();s.error(selected.resumeEnhancement?new IOException(e.getMessage()+"\n若增强仍暂停，请在拍摄增强的管理中恢复已暂停的增强入口；不要自行覆盖启动文件。",e):e);});}});
 }
}
