package local.grworld.studio;

/** Mutually exclusive shutdown-image routes; enhancement support is independent; unknown identity is preparation only. */
final class CameraWorkflowPolicy {
 static final int UNVERIFIED=0,WIFI=1,SD=2,UNSUPPORTED=3;
 static int route(CameraIdentity camera){return camera==null?UNVERIFIED:camera.wifiSupported()?WIFI:camera.sdSupported()?SD:UNSUPPORTED;}
 static boolean wifiAllowed(CameraIdentity camera){return route(camera)==WIFI;}
 static boolean sdPreparationAllowed(CameraIdentity camera){int route=route(camera);return route==SD||route==UNVERIFIED;}
 static String summary(CameraIdentity camera){switch(route(camera)){
  case WIFI:return "Wi-Fi 换图可用 · SD 换图不适用";
  case SD:return "SD 换图可用 · Wi-Fi 换图不适用";
  case UNSUPPORTED:return "当前机型的关机图工作流不适用";
  default:return "Wi-Fi 未连接 · SD 仅可离线准备";
 }}
 static String wifiExplanation(CameraIdentity camera){
  if(camera==null)return "尚未连接并核实相机，只能离线准备图片。";
  String actual=camera.model+" · 固件 "+camera.firmware;
  if(route(camera)==SD)return actual+"\n此相机不适用 Wi-Fi 换图，请使用“SD任务”工作流。";
  if(route(camera)==UNSUPPORTED)return actual+"\n此机型或固件不适用当前 Wi-Fi、SD 换图工作流。";
  return actual+"\n适用 Wi-Fi 换图；正式发送前仍需通过连接复核。";
 }
}
