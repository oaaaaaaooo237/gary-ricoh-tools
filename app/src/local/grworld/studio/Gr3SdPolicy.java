package local.grworld.studio;

/** A known live camera cannot inherit the offline SD preparation path. */
final class Gr3SdPolicy {
 static boolean allowed(CameraIdentity identity){return CameraWorkflowPolicy.sdPreparationAllowed(identity);}
 static boolean same(CameraIdentity a,CameraIdentity b){return a==null?b==null:a.same(b);}
 static String explanation(CameraIdentity identity){
  if(identity==null)return "离线准备 · 尚未核实实机\n仅适用 GR IIIx 普通版、Urban、HDF 的固件 1.70。";
  String actual=identity.model+" · 固件 "+identity.firmware;
  if(identity.sdSupported())return "已识别："+actual+"\n1.70 实机写入、显示及恢复仍待验证。";
  if(identity.wifiSupported())return actual+"\n不适用 SD 换图。请回到“画面”，使用 Wi-Fi 替换关机图。";
  return actual+"\n不适用当前 SD 流程；目前仅适用 GR IIIx 普通版、Urban、HDF 的固件 1.70。";
 }
}
