package local.grworld.studio;

/** Per-feature capability, independent of the shutdown-image transport. */
final class EnhancementPolicy {
 static String family(CameraIdentity id){
  if(id==null)return null;
  String m=id.model,f=id.firmware;
  if((m.equals("RICOH GR III")||m.equals("RICOH GR III HDF"))&&(f.equals("2.10")||f.equals("2.20")))return "gr3";
  if((m.equals("RICOH GR IIIx")||m.equals("RICOH GR IIIx HDF")||m.equals("RICOH GR IIIx Urban Edition"))&&(f.equals("1.60")||f.equals("1.70")))return "gr3x";
  if((m.equals("RICOH GR IV")||m.equals("RICOH GR IV HDF"))&&(f.equals("1.11")||f.equals("1.20")))return "gr4";
  return null;
 }
 static String description(CameraIdentity id){return id==null?"请让手机连接相机 Wi-Fi，程序会自动识别型号与固件。":family(id)==null?id.model+" · "+id.firmware+"\n当前拍摄增强不适用此机型或固件。":id.model+" · 固件 "+id.firmware+"\n支持准备 XPan 宽幅与构图辅助线；实际加载及显示仍须核对。";}
 static String stockAsset(CameraIdentity id){return id!=null&&id.model.equals("RICOH GR IV")?"GB_GR4.jpg":"GB_HDF.jpg";}
 static String stockHash(CameraIdentity id){return id!=null&&id.model.equals("RICOH GR IV")?"dff9ba0ad2c7ea2697b19470cf6c695337651ee0ef415a6d3359a7f318ff3c79":"d4b8554a7b71094bd6f63d87d5113b8cfe9f4db5cd5ec5317b48244a3b0cbf96";}
}
