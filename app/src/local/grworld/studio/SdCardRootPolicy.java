package local.grworld.studio;
import java.io.IOException;

/** Accept only the root of a mounted, writable, identified removable volume. */
final class SdCardRootPolicy {
 static String require(String authority,String documentId,boolean removableMounted,boolean writable)throws IOException{
  if(!"com.android.externalstorage.documents".equals(authority)||documentId==null||!documentId.matches("(?i)[0-9a-f]{4}-[0-9a-f]{4}:"))throw new IOException("请选择读卡器中的 SD 卡根目录；不能使用手机存储、DCIM 或子文件夹。系统不允许选择根目录时，请使用手动方式。");
  if(!removableMounted)throw new IOException("未确认该目录属于已挂载的可移除存储，请重新接入读卡器。");
  if(!writable)throw new IOException("SD 卡没有读写权限，请解除卡片写保护或重新授权；也可以手动导出。");
  return documentId.substring(0,documentId.length()-1).toUpperCase(java.util.Locale.US);
 }
}
