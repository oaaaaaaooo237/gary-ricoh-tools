package local.grworld.studio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Persisted SD transaction. Stages advance only from complete, operation-bound readbacks. */
final class Gr3SdSession {
 interface Stocks{byte[] load(Gr3Profile profile)throws Exception;}
 final Gr3Profile profile;final String id,detail;final boolean factoryRecovery;final byte[] original,candidate;
 final LinkedHashMap<String,byte[]> proof=new LinkedHashMap<>();boolean staged,installed,restored;int display;
 Gr3SdSession(Gr3Profile profile,String id,byte[] original,byte[] candidate,boolean recovery,String detail)throws Exception{
  if(!id.matches("[A-F0-9]{6}"))throw new IOException("任务编号无效");profile.candidate(original,candidate);
  if(recovery&&!Arrays.equals(original,candidate))throw new IOException("原厂恢复任务的图片不符");
  this.profile=profile;this.id=id;this.original=original.clone();this.candidate=candidate.clone();factoryRecovery=recovery;this.detail=detail==null?"":detail;
 }
 String name(char code,String extension){return "G"+id+code+"."+extension;}
 String internal(char code){return "B:\\Resource\\Jpeg\\"+name(code,"JPG");}
 String card(char code,String extension){return "C:\\"+name(code,extension);}
 byte[] marker(int phase){return ("GR3SD "+id+" "+phase+" DONE").getBytes(StandardCharsets.US_ASCII);}
 String[] expected(int phase){return phase==1?(factoryRecovery?new String[]{name('N',"JPG"),name('B',"TXT")}:new String[]{name('N',"JPG"),name('O',"JPG"),name('P',"JPG"),name('B',"TXT")}):phase==2?new String[]{name('I',"JPG"),name('D',"TXT")}:new String[]{name('R',"JPG"),name('F',"TXT")};}
 byte[] required(Map<String,byte[]> files,String key)throws IOException{byte[] b=files.get(key);if(b==null)throw new IOException("缺少本轮读回："+key);return b;}
 void equal(byte[] a,byte[] b,String why)throws IOException{if(!Arrays.equals(a,b))throw new IOException(why);}
 void verify(int phase,Map<String,byte[]> files)throws Exception{
  for(String key:expected(phase))required(files,key);
  if(phase==1){
   equal(required(files,name('B',"TXT")),marker(1),"暂存完成标记不属于本轮任务");equal(required(files,name('N',"JPG")),candidate,"临时图完整内容不一致");
   if(!factoryRecovery){byte[] backup=required(files,name('O',"JPG")),before=required(files,name('P',"JPG"));EndScreenPolicy.image(backup);if(backup.length!=profile.bytes)throw new IOException("当前画面备份长度不符");equal(backup,before,"机内备份与操作前画面不一致");}
   staged=true;
  }else if(phase==2){if(!staged||restored)throw new IOException("任务未暂存或已经恢复，请新建任务后安装");equal(required(files,name('D',"TXT")),marker(2),"安装标记不属于本轮任务");equal(required(files,name('I',"JPG")),candidate,"安装读回与候选图片不一致");installed=true;display=0;
  }else if(phase==3){if(!canRestore())throw new IOException("本任务尚无已验证的操作前备份");equal(required(files,name('F',"TXT")),marker(3),"恢复标记不属于本轮任务");equal(required(files,name('R',"JPG")),proof.get(name('O',"JPG")),"恢复读回与本轮备份不一致");restored=true;display=0;
  }else throw new IOException("未知阶段");
  for(String key:expected(phase))proof.put(key,files.get(key).clone());
 }
 boolean canRestore(){return staged&&!factoryRecovery&&proof.containsKey(name('O',"JPG"));}
 static byte[] factoryEntry(){return "[OPEN_FACTORY_DEBUG_MENU]\r\n".getBytes(StandardCharsets.US_ASCII);}
 static byte[] developMode(){return new byte[]{7,1,44,31,16,3,30,22,5,45};}
 byte[] pack(int phase)throws Exception{
  if(phase==2&&(!staged||restored))throw new IOException("任务未通过暂存校验或已经恢复，请新建任务后安装");
  if(phase==3&&!canRestore())throw new IOException("缺少已验证备份");
  LinkedHashMap<String,byte[]> files=new LinkedHashMap<>();files.put("card/00078490.609",factoryEntry());files.put("card/DEVELOP.MOD",developMode());
  files.put("card/script/startup.ttl",script(phase));files.put("操作说明.txt",instructions(phase).getBytes(StandardCharsets.UTF_8));files.put("任务.properties",metadata());return zip(files);
 }
 String instructions(int phase){return profile.label+" / 固件 1.70 / 任务 "+id+"\n\n"+
  "这是 SD 脚本流程预览版，1.70 实机写入、JPEG显示和恢复仍待验证；不是刷固件。请先在相机菜单核对型号与1.70版本。\n"+
  "只把card内的文件放到卡根目录。已有其他startup.ttl时先另存并停用，不覆盖其他任务或照片。按MENU开机进入工厂菜单，只启用Script；不改变其他选项。\n"+
  "本阶段："+(phase==1?"准备机内临时图片"+(factoryRecovery?"（官方原厂图）":"及当前画面备份")+"，不写入关机图目标。":phase==2?"将已验证临时图安装为关机画面。":"从本任务已验证的机内备份恢复操作前画面；目标即使已截短也不阻止恢复。")+"\n"+
  "正常开机运行，等存储活动停止后关机。将卡接手机/电脑，在App里选择本轮读回所在文件夹。需要的文件："+Arrays.toString(expected(phase))+"。\n"+
  "只有完整内容校验通过才能继续。标记文件、文件长度或程序退出都不能代替完整校验及实际关机显示。出现异常保留本轮文件，不要清空标记重复执行。\n"+
  "成功后移除本任务startup.ttl，关闭Script，再清理本任务入口文件，保留任务记录和备份。勿删除DCIM或已有脚本。\n"+
  "本操作包包含所选图片数据，按个人文件保管。\n";
 }
 byte[] script(int phase)throws Exception{
  if(phase<1||phase>3)throw new IOException("未知阶段");TTL t=new TTL();char start=phase==1?'A':phase==2?'C':'E',done=phase==1?'B':phase==2?'D':'F';
  t.absent(card(start,"TXT"));for(String filename:expected(phase))t.absent("C:\\"+filename);
  if(phase==1){t.absent(internal('N'));if(!factoryRecovery){t.absent(internal('O'));t.size(profile.target(),profile.bytes);}}
  else if(phase==2){t.size(internal('N'),profile.bytes);if(!factoryRecovery)t.size(internal('O'),profile.bytes);}
  else t.size(internal('O'),profile.bytes); // Deliberately no size/existence guard on a damaged target.
  t.marker(card(start,"TXT"),"GR3SD "+id+" "+phase+" START");
  if(phase==1){
   if(!factoryRecovery){t.copy(profile.target(),internal('O'));t.size(internal('O'),profile.bytes);}
   t.lines.add("filecreate image '"+internal('N')+"'");t.guard("image < 0");t.lines.add("fileclose image");t.lines.add("filetruncate '"+internal('N')+"' "+candidate.length);t.size(internal('N'),candidate.length);
   t.lines.add("fileopen image '"+internal('N')+"' 0");t.guard("image < 0");
   for(int index=0;index<candidate.length;){if(candidate[index]==0){index++;continue;}int begin=index;while(index<candidate.length&&candidate[index]!=0&&index-begin<42)index++;
    t.lines.add("fileseek image "+begin+" 0");StringBuilder literal=new StringBuilder("filewrite image ");for(int i=begin;i<index;i++)literal.append('#').append(candidate[i]&255);t.lines.add(literal.toString());}
   t.lines.add("fileclose image");t.copy(internal('N'),card('N',"JPG"));t.size(card('N',"JPG"),profile.bytes);
   if(!factoryRecovery){t.copy(internal('O'),card('O',"JPG"));t.size(card('O',"JPG"),profile.bytes);t.copy(profile.target(),card('P',"JPG"));t.size(card('P',"JPG"),profile.bytes);}
  }else{t.copy(internal(phase==2?'N':'O'),profile.target());t.size(profile.target(),profile.bytes);t.copy(profile.target(),card(phase==2?'I':'R',"JPG"));t.size(card(phase==2?'I':'R',"JPG"),profile.bytes);}
  t.marker(card(done,"TXT"),new String(marker(phase),StandardCharsets.US_ASCII));return t.finish();
 }
 static final class TTL {
  final ArrayList<String> lines=new ArrayList<>();
  void guard(String condition){Collections.addAll(lines,"if "+condition+" then","exit","endif");}
  void absent(String path){lines.add("getfileattr '"+path+"'");guard("result <> -1");}
  void size(String path,int bytes){Collections.addAll(lines,"sz = -1","filestat '"+path+"' sz","stat = result");guard("stat <> 0");guard("sz <> "+bytes);}
  void marker(String path,String value){lines.add("filecreate log '"+path+"'");guard("log < 0");Collections.addAll(lines,"filewrite log '"+value+"'","fileclose log");}
  void copy(String from,String to){if(!from.startsWith("B:\\Resource\\Jpeg\\"))throw new IllegalArgumentException("SD不能作为机内复制来源");lines.add("filecopy '"+from+"' '"+to+"'");}
  byte[] finish()throws IOException{lines.add("exit");for(String line:lines){if(line.length()>=240)throw new IOException("脚本行超长");for(char c:line.toCharArray())if(c>127)throw new IOException("脚本必须为ASCII");}return (String.join("\r\n",lines)+"\r\n").getBytes(StandardCharsets.US_ASCII);}
 }
 byte[] metadata()throws Exception{Properties p=new Properties();p.setProperty("format","gr3sd-1");p.setProperty("profile",profile.name());p.setProperty("firmware","1.70");p.setProperty("id",id);p.setProperty("recovery",Boolean.toString(factoryRecovery));p.setProperty("detail",detail);p.setProperty("candidate_sha256",EndScreenPolicy.sha256(candidate));p.setProperty("stock_sha256",profile.stockHash);p.setProperty("display",Integer.toString(display));ByteArrayOutputStream out=new ByteArrayOutputStream();p.store(out,"GR IIIx SD image task");return out.toByteArray();}
 byte[] save()throws Exception{LinkedHashMap<String,byte[]> entries=new LinkedHashMap<>();entries.put("session.properties",metadata());entries.put("candidate.jpg",candidate);for(Map.Entry<String,byte[]> entry:proof.entrySet())entries.put("proof/"+entry.getKey(),entry.getValue());return zip(entries);}
 static Gr3SdSession load(byte[] archive,Stocks stocks)throws Exception{
  Map<String,byte[]> entries=unzip(archive);Properties p=new Properties();p.load(new ByteArrayInputStream(need(entries,"session.properties")));
  if(!"gr3sd-1".equals(p.getProperty("format"))||!"1.70".equals(p.getProperty("firmware")))throw new IOException("任务版本不支持");
  Gr3Profile profile=Gr3Profile.valueOf(p.getProperty("profile"));byte[] candidate=need(entries,"candidate.jpg");if(!profile.stockHash.equals(p.getProperty("stock_sha256"))||!EndScreenPolicy.sha256(candidate).equals(p.getProperty("candidate_sha256")))throw new IOException("任务哈希不一致");
  String recovery=p.getProperty("recovery");if(!"true".equals(recovery)&&!"false".equals(recovery))throw new IOException("任务类型无效");
  Gr3SdSession job=new Gr3SdSession(profile,p.getProperty("id",""),stocks.load(profile),candidate,Boolean.parseBoolean(recovery),p.getProperty("detail",""));Set<String> allowed=new HashSet<>(Arrays.asList("session.properties","candidate.jpg"));Map<String,byte[]> files=new HashMap<>();
  for(int phase=1;phase<=3;phase++)for(String name:job.expected(phase)){allowed.add("proof/"+name);if(entries.containsKey("proof/"+name))files.put(name,entries.get("proof/"+name));}
  for(String key:entries.keySet())if(!allowed.contains(key))throw new IOException("任务含未知文件");
  for(int phase=1;phase<=3;phase++){boolean any=false;for(String name:job.expected(phase))any|=files.containsKey(name);if(any)job.verify(phase,files);}
  int display=Integer.parseInt(p.getProperty("display","0"));if(display<0||display>2||display!=0&&!job.installed&&!job.restored)throw new IOException("显示记录无效");job.display=display;return job;
 }
 static byte[] need(Map<String,byte[]> entries,String name)throws IOException{byte[] b=entries.get(name);if(b==null)throw new IOException("任务缺少 "+name);return b;}
 static byte[] zip(Map<String,byte[]> entries)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){for(Map.Entry<String,byte[]> entry:entries.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}return out.toByteArray();}
 static Map<String,byte[]> unzip(byte[] archive)throws IOException{if(archive.length>2*1024*1024)throw new IOException("任务过大");Map<String,byte[]> entries=new LinkedHashMap<>();int total=0;try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){String name=entry.getName();if(entries.size()>=16||entries.containsKey(name)||entry.isDirectory()||name.contains("..")||name.startsWith("/")||name.contains("\\"))throw new IOException("任务文件不安全");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=zip.read(buf))!=-1){total+=n;if(total>2*1024*1024||out.size()+n>512*1024)throw new IOException("任务解压超限");out.write(buf,0,n);}entries.put(name,out.toByteArray());}}return entries;}
}
