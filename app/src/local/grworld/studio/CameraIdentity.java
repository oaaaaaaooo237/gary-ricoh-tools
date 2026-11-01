package local.grworld.studio;

/** Actual identity returned by the camera; unknown versions never inherit support. */
final class CameraIdentity {
 final String model,firmware;
 CameraIdentity(String model,String firmware){
  if(model==null||firmware==null||model.trim().isEmpty()||firmware.trim().isEmpty()||model.length()>96||firmware.length()>32||hasControl(model)||hasControl(firmware))throw new IllegalArgumentException("相机身份信息不完整");
  this.model=model;this.firmware=firmware;
 }
 static boolean hasControl(String text){for(int i=0;i<text.length();i++)if(Character.isISOControl(text.charAt(i)))return true;return false;}
 boolean wifiSupported(){try{EndScreenPolicy.camera(model,firmware);return true;}catch(IllegalArgumentException unsupported){return false;}}
 boolean sdSupported(){return firmware.equals("1.70")&&(model.equals("RICOH GR IIIx")||model.equals("RICOH GR IIIx Urban Edition")||model.equals("RICOH GR IIIx HDF"));}
 boolean enhancementSupported(){return EnhancementPolicy.family(this)!=null;}
 boolean supported(){return wifiSupported()||sdSupported()||enhancementSupported();}
 boolean same(CameraIdentity other){return other!=null&&model.equals(other.model)&&firmware.equals(other.firmware);}
}
