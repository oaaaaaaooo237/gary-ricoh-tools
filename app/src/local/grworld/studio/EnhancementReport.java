package local.grworld.studio;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** A matching old report is not evidence of a new camera start. */
final class EnhancementReport {
 static void verify(CameraIdentity id,boolean framing,boolean guides,byte[] report,byte[] boot,int baselineLength,String baselineHash)throws Exception{
  String text=new String(report,StandardCharsets.UTF_8);Matcher m=Pattern.compile("^GRKit 1\\.3\\.5 \\(([^)]+)\\)").matcher(text);if(!m.find())throw new IOException("报告版本无法匹配本次增强包");String target=m.group(1),family=EnhancementPolicy.family(id);
  boolean match="gr4".equals(family)?id.firmware.equals("1.11")?target.equals("gr4-1.11"):target.equals("gr4-1.20")||target.equals("gr4-1.20-0528"):family!=null&&target.equals("gr3-2.10");if(!match)throw new IOException("报告的运行时与本次机型、固件不匹配");
  if(text.contains("did not install")||text.contains("Safe mode:"))throw new IOException("相机报告增强未正常加载，请查看日志");
  for(String module:new String[]{framing?"FRAMING":"",guides?"GUIDES":""})if(!module.isEmpty()&&!Pattern.compile("(?m)^"+module+"(?:\\.GRM)?(?: \\(GRKIT\\\\MODULES\\))?  loaded\\r?$").matcher(text).find())throw new IOException(module+" 未正常启用；请在相机检查模块开关并重新核对");
  if(baselineLength<0||boot.length<=baselineLength||!baselineHash.equals(EndScreenPolicy.sha256(Arrays.copyOf(boot,baselineLength))))throw new IOException("启动日志没有可核实的本轮新增记录，暂不认为本次执行成功");
  String suffix=new String(boot,baselineLength,boot.length-baselineLength,StandardCharsets.UTF_8);Matcher starts=Pattern.compile("(?m)^\\S+ install (-?\\d+)\\r?$").matcher(suffix);String last=null;while(starts.find())last=starts.group(1);if(!"0".equals(last))throw new IOException("本轮最后一次启动没有成功加载记录");
 }
}
