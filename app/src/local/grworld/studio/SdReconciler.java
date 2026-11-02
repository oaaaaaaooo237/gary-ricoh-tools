package local.grworld.studio;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Derives the next permitted step from the selected card, never from saved completion booleans. */
final class SdReconciler {
 static final class Result {
  final Gr3SdSession job;final String card;final int preparedPhase;final boolean interrupted;final String message;boolean cleaned,paused,resumed;
  Result(Gr3SdSession job,String card,int prepared,boolean interrupted,String message){this.job=job;this.card=card;preparedPhase=prepared;this.interrupted=interrupted;this.message=message;}
 }
 static Map<String,byte[]> proof(SdCardDeployment.Card c,Gr3SdSession j,int phase)throws Exception{
  Map<String,byte[]> found=new HashMap<>();int count=0;for(String name:j.expected(phase))if(SdCardDeployment.exists(c,name)){found.put(name,c.read(name,name.endsWith(".TXT")?256:EndScreenPolicy.MAX_BYTES));count++;}
  if(count==0)return null;if(count!=j.expected(phase).length)throw new IOException("本轮第 "+phase+" 阶段读回不完整，不能继续或重复执行；请保留卡片排查");return found;
 }
 static Result inspect(SdCardDeployment.Card c,Gr3SdSession saved)throws Exception{
  c.check();Gr3SdSession actual=new Gr3SdSession(saved.profile,saved.id,saved.original,saved.candidate,saved.factoryRecovery,saved.detail);
  Map<String,String> receipt=SdCardDeployment.record(c,actual);int prepared=receipt==null?0:Integer.parseInt(receipt.get("phase"));
  // Manually copied ZIPs have no application ownership receipt. Recognize only
  // this transaction's exact script; this grants no automatic deletion rights.
  if(receipt==null&&SdCardDeployment.exists(c,SdCardDeployment.STARTUP)){
   byte[] script=c.read(SdCardDeployment.STARTUP,1024*1024);
   for(int phase=1;phase<=3;phase++)if(Arrays.equals(script,actual.script(phase)))prepared=phase;
   if(prepared!=0){
    byte[] entry=c.read(SdCardDeployment.ENTRY,128),mod=c.read(SdCardDeployment.MOD,128);
    boolean entryValid=Arrays.equals(entry,Gr3SdSession.factoryEntry())||Arrays.equals(entry,"[SCRIPT_EN_MODE EN]\r\n".getBytes(StandardCharsets.US_ASCII));
    if(!entryValid||!Arrays.equals(mod,Gr3SdSession.developMode()))throw new IOException("手动包的入口文件不完整或已改变，不能认定本阶段文件已准备");
   }
  }
  if(receipt!=null){if(!SdCardDeployment.exists(c,SdCardDeployment.STARTUP)||!EndScreenPolicy.sha256(c.read(SdCardDeployment.STARTUP,1024*1024)).equals(receipt.get("script")))throw new IOException("卡内启动任务与记录不一致，先检查启动入口；不沿用本地进度");byte[] entry=c.read(SdCardDeployment.ENTRY,128),mod=c.read(SdCardDeployment.MOD,128);boolean entryValid=Arrays.equals(entry,Gr3SdSession.factoryEntry())||"0".equals(receipt.get("entry"))&&Arrays.equals(entry,"[SCRIPT_EN_MODE EN]\r\n".getBytes(StandardCharsets.US_ASCII));if(!entryValid||!Arrays.equals(mod,Gr3SdSession.developMode()))throw new IOException("卡内入口文件不完整或已改变，不能认定本阶段文件已准备");}
  Map<String,byte[]> stage=proof(c,actual,1),install=proof(c,actual,2),restore=proof(c,actual,3);
  if(stage!=null)actual.verify(1,stage);if(install!=null){if(!actual.staged)throw new IOException("卡内有安装读回但缺少暂存与备份证据");actual.verify(2,install);}if(restore!=null){if(!actual.staged)throw new IOException("卡内有恢复读回但缺少本轮旧图备份");actual.verify(3,restore);}
  if(prepared==2&&!actual.staged)throw new IOException("卡内已有安装脚本，但缺少本轮暂存完整读回；不能运行或继续");
  if(prepared==3&&!actual.canRestore())throw new IOException("卡内已有恢复脚本，但缺少本轮已验证的旧图备份；不能运行或继续");
  boolean interrupted=false;for(int phase=1;phase<=3;phase++){char start=phase==1?'A':phase==2?'C':'E';boolean done=phase==1?stage!=null:phase==2?install!=null:restore!=null;if(SdCardDeployment.exists(c,actual.name(start,"TXT"))&&!done)interrupted=true;}
  if((saved.staged&&!actual.staged)||(saved.installed&&!actual.installed&&!actual.restored)||(saved.restored&&!actual.restored))throw new IOException("本地进度与当前卡片不符；缺少真实读回证据，不能按缓存继续");
  String msg=interrupted?"卡内有本轮执行开始痕迹，但未取得完整完成证据。停止重复运行，先排查或清理本次启动任务。":actual.restored?"卡内本轮恢复标记和完整图片读回已核实；接下来查看实际关机画面。":prepared==3?"本轮旧图备份已核实，恢复脚本已准备；请在相机执行后再次读卡核验。":actual.installed?"卡内本轮安装标记和完整目标读回已核实；接下来查看实际关机画面。":actual.staged?prepared==2?"暂存阶段已核实，安装文件已准备；请在相机执行后再次读卡核验。":"暂存阶段的完整读回已核实，可以准备安装阶段。":prepared==1?"卡内暂存启动文件已核实，尚无完整执行结果；请在相机执行后再次读卡核验。":"当前卡内尚无本轮执行证据，可以准备暂存阶段。";
  actual.display=0;Result r=new Result(actual,c.key(),prepared,interrupted,msg);r.cleaned=receipt==null&&!SdCardDeployment.exists(c,SdCardDeployment.STARTUP);r.paused=SdCardDeployment.exists(c,EnhancementDeployment.PAUSED);return r;
 }
}
