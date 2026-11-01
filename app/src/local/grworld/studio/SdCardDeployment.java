package local.grworld.studio;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** SD files only. Activation is last; camera execution/readback is a separate transaction. */
final class SdCardDeployment {
 static final String RECORD="GR3DEP.TXT",STARTUP="script/startup.ttl",ENTRY="00078490.609",MOD="DEVELOP.MOD";
 interface Card {
  String key();void check()throws Exception;List<String> list(String directory)throws Exception;
  boolean directory(String path)throws Exception;byte[] read(String path,int limit)throws Exception;
  void mkdir(String path)throws Exception;void create(String path,byte[] bytes)throws Exception;
  void delete(String path)throws Exception;void rename(String path,String newName)throws Exception;
 }
 interface Gate {void check()throws Exception;}
 static final class Plan {
  final Gr3SdSession job;final int phase;final LinkedHashMap<String,byte[]> files=new LinkedHashMap<>();final String temp;
  Plan(Gr3SdSession job,int phase)throws Exception{
   if(job==null||phase<1||phase>3)throw new IOException("SD 阶段无效");
   if(phase==1&&(job.staged||job.installed||job.restored)||phase==2&&(!job.staged||job.installed||job.restored)||phase==3&&(!job.canRestore()||job.restored))throw new IOException("该阶段已完成或前置校验未通过，请重新核对任务。");
   this.job=job;this.phase=phase;temp="script/G"+job.id+"W.TMP";
   Map<String,byte[]> pack=new LinkedHashMap<>();pack.put("card/"+ENTRY,Gr3SdSession.factoryEntry());pack.put("card/"+MOD,Gr3SdSession.developMode());pack.put("card/"+STARTUP,job.script(phase));
   for(String path:new String[]{ENTRY,MOD,STARTUP}){byte[] bytes=pack.get("card/"+path);if(bytes==null)throw new IOException("SD 操作文件缺失");files.put(path,bytes.clone());}
  }
  String title(){return phase==1?"暂存":phase==2?"安装":"恢复旧图";}
 }
 static final class Inspection {
  final String key;final Map<String,String> state=new LinkedHashMap<>();final boolean entryOwned,modOwned;
  Inspection(String key,boolean entryOwned,boolean modOwned){this.key=key;this.entryOwned=entryOwned;this.modOwned=modOwned;}
 }
 static boolean exists(Card card,String path)throws Exception{
  int slash=path.lastIndexOf('/');String parent=slash<0?"":path.substring(0,slash),name=path.substring(slash+1);if(!parent.isEmpty()&&!exists(card,parent))return false;
  int matches=0;for(String item:card.list(parent))if(item.equalsIgnoreCase(name))matches++;if(matches>1)throw new IOException("SD 卡存在大小写重名文件，请先处理冲突。");return matches==1;
 }
 static byte[] bytes(Card c,String p)throws Exception{if(c.directory(p))throw new IOException("应为文件的位置已有文件夹："+p);return c.read(p,1024*1024);}
 static String fingerprint(Card c,String p)throws Exception{return !exists(c,p)?"absent":c.directory(p)?"directory":EndScreenPolicy.sha256(bytes(c,p));}
 static Map<String,String> record(Card c,Gr3SdSession job)throws Exception{
  if(!exists(c,RECORD))return null;String text=new String(c.read(RECORD,2048),StandardCharsets.US_ASCII);String[] lines=text.split("\r?\n");if(lines.length!=8||!lines[0].equals("GR3SDDEPLOY 1"))throw new IOException("卡上的任务记录无法验证，请先手动检查，不会覆盖。");
  Map<String,String> p=new LinkedHashMap<>();for(int i=1;i<lines.length;i++){int split=lines[i].indexOf('=');if(split<1||p.put(lines[i].substring(0,split),lines[i].substring(split+1))!=null)throw new IOException("SD 任务记录无效");}
  if(!job.id.equals(p.get("id"))||!job.profile.name().equals(p.get("profile"))||!EndScreenPolicy.sha256(job.candidate).equals(p.get("candidate")))throw new IOException("卡里留有其他任务，请先打开对应历史任务并清理启动文件。");
  int phase;try{phase=Integer.parseInt(p.get("phase"));}catch(Exception e){throw new IOException("SD 任务阶段无效");}if(phase<1||phase>3||!EndScreenPolicy.sha256(job.script(phase)).equals(p.get("script"))||!Arrays.asList("0","1").contains(p.get("entry"))||!Arrays.asList("0","1").contains(p.get("mod")))throw new IOException("SD 任务记录与本轮脚本不一致");return p;
 }
 static byte[] recordBytes(Plan p,boolean entry,boolean mod)throws Exception{return ("GR3SDDEPLOY 1\r\nid="+p.job.id+"\r\nprofile="+p.job.profile.name()+"\r\ncandidate="+EndScreenPolicy.sha256(p.job.candidate)+"\r\nphase="+p.phase+"\r\nscript="+EndScreenPolicy.sha256(p.files.get(STARTUP))+"\r\nentry="+(entry?1:0)+"\r\nmod="+(mod?1:0)+"\r\n").getBytes(StandardCharsets.US_ASCII);}
 static Inspection inspect(Card c,Plan p)throws Exception{
  c.check();for(String name:c.list("")){if(name.toLowerCase(Locale.US).endsWith(".bin"))throw new IOException("卡根目录存在固件 BIN 文件，请先移走，避免相机误进固件更新。");if(name.matches("[0-9]{8}\\.[0-9]{3}")&&!name.equalsIgnoreCase(ENTRY))throw new IOException("卡里有其他机型的工厂入口文件，不会混用。");}
  if(exists(c,"script")&&!c.directory("script"))throw new IOException("script 位置不是文件夹");Map<String,String> old=record(c,p.job);
  if(exists(c,STARTUP)&&(old==null||!fingerprint(c,STARTUP).equals(old.get("script"))))throw new IOException("卡里已有其他或被改动的启动脚本，不会覆盖。请先停用原脚本或换一张卡。");
  for(String file:new String[]{ENTRY,MOD})if(exists(c,file)&&!Arrays.equals(bytes(c,file),p.files.get(file)))throw new IOException("卡上已有不同的 "+file+"，不会覆盖。");
  if(exists(c,p.temp))throw new IOException("卡上有未完成的临时文件 "+p.temp+"，请先检查并手动移除该临时文件。");
  char start=p.phase==1?'A':p.phase==2?'C':'E';if(exists(c,p.job.name(start,"TXT")))throw new IOException("本阶段已有执行记录，请先读回核验，不要重复部署。");for(String file:p.job.expected(p.phase))if(exists(c,file))throw new IOException("本阶段已有读回文件，请先核验，不要重复部署。");
  Inspection result=new Inspection(c.key(),!exists(c,ENTRY)||old!=null&&"1".equals(old.get("entry")),!exists(c,MOD)||old!=null&&"1".equals(old.get("mod")));
  for(String path:new String[]{ENTRY,MOD,STARTUP,RECORD,p.temp,"script",p.job.name(start,"TXT")})result.state.put(path,fingerprint(c,path));for(String path:p.job.expected(p.phase))result.state.put(path,fingerprint(c,path));return result;
 }
 static void compare(Inspection a,Inspection b)throws IOException{if(!a.key.equals(b.key)||a.entryOwned!=b.entryOwned||a.modOwned!=b.modOwned||!a.state.equals(b.state))throw new IOException("SD 卡或文件在确认后发生变化，已停止；请重新检查。");}
 static void writeChecked(Card c,String path,byte[] bytes,Gate gate)throws Exception{gate.check();c.check();c.create(path,bytes);if(!Arrays.equals(bytes,bytes(c,path)))throw new IOException("SD 文件完整读回不一致："+path);}
 static void deploy(Card c,Plan p,Inspection reviewed,Gate gate)throws Exception{
  gate.check();compare(reviewed,inspect(c,p));boolean temp=false,active=false,recordCreated=false;
  try{
   // Deactivate the verified previous script before preparing anything new.
   if(exists(c,STARTUP)){gate.check();c.delete(STARTUP);if(exists(c,STARTUP))throw new IOException("旧启动脚本未移除");}
   if(!exists(c,"script")){gate.check();c.mkdir("script");}
   temp=true;writeChecked(c,p.temp,p.files.get(STARTUP),gate);
   for(String path:new String[]{ENTRY,MOD})if(!exists(c,path))writeChecked(c,path,p.files.get(path),gate);
   byte[] record=recordBytes(p,reviewed.entryOwned,reviewed.modOwned);gate.check();if(exists(c,RECORD))c.delete(RECORD);recordCreated=true;writeChecked(c,RECORD,record,gate);
   gate.check();c.check();if(exists(c,STARTUP))throw new IOException("准备期间出现新的启动脚本，已停止");
   // This rename is the only activation. Never stream partial bytes into startup.ttl.
   active=true;c.rename(p.temp,"startup.ttl");temp=false;
   for(Map.Entry<String,byte[]> f:p.files.entrySet())if(!Arrays.equals(f.getValue(),bytes(c,f.getKey())))throw new IOException("SD 部署读回不一致："+f.getKey());
   if(!Arrays.equals(record,bytes(c,RECORD)))throw new IOException("SD 任务记录读回不一致");gate.check();c.check();
  }catch(Exception error){
   boolean clean=true;try{if(active&&exists(c,STARTUP)&&fingerprint(c,STARTUP).equals(EndScreenPolicy.sha256(p.files.get(STARTUP))))c.delete(STARTUP);if(temp&&exists(c,p.temp))c.delete(p.temp);if(recordCreated&&exists(c,RECORD)&&!exists(c,STARTUP))c.delete(RECORD);if(exists(c,STARTUP))clean=false;}catch(Exception failed){clean=false;}
   throw new IOException("SD 写入未完成；不要将此卡插回相机运行。"+(clean?"本次启动脚本未启用或已停用。":"卡已断开或清理未完成，请先检查 script/startup.ttl。")+"\n"+error.getMessage(),error);
  }
 }
 static Inspection inspectCleanup(Card c,Gr3SdSession job)throws Exception{
  c.check();Map<String,String> old=record(c,job);if(old==null)throw new IOException("卡上没有可验证的本任务部署记录；手动复制的文件请按说明手动清理。");
  if(exists(c,STARTUP)&&!fingerprint(c,STARTUP).equals(old.get("script")))throw new IOException("启动脚本已改变，不能作为本任务自动删除。");
  Inspection i=new Inspection(c.key(),"1".equals(old.get("entry")),"1".equals(old.get("mod")));
  for(String path:new String[]{STARTUP,RECORD,ENTRY,MOD})i.state.put(path,fingerprint(c,path));
  Map<String,byte[]> expected=new LinkedHashMap<>();expected.put("card/"+ENTRY,Gr3SdSession.factoryEntry());expected.put("card/"+MOD,Gr3SdSession.developMode());for(String p:new String[]{ENTRY,MOD})if(exists(c,p)&&!Arrays.equals(bytes(c,p),expected.get("card/"+p))){boolean shared=p.equals(ENTRY)&&!i.entryOwned&&Arrays.equals(bytes(c,p),"[SCRIPT_EN_MODE EN]\r\n".getBytes(StandardCharsets.US_ASCII));if(!shared)throw new IOException("入口文件已改变，不能自动清理："+p);}return i;
 }
 static void cleanup(Card c,Gr3SdSession job,Inspection reviewed,Gate gate)throws Exception{
  gate.check();compare(reviewed,inspectCleanup(c,job));for(String path:new String[]{STARTUP,ENTRY,MOD,RECORD}){
   if(path.equals(ENTRY)&&!reviewed.entryOwned||path.equals(MOD)&&!reviewed.modOwned)continue;
   gate.check();c.check();if(exists(c,path)){c.delete(path);if(exists(c,path))throw new IOException("SD 文件未能移除："+path);}
  }
 }
}
