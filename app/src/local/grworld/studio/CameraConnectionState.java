package local.grworld.studio;

/** Visible state changes only on an identity/status change, never on a polling tick. */
final class CameraConnectionState {
 static final int DISCONNECTED=0,SUPPORTED=1,UNSUPPORTED=2;
 private CameraIdentity identity;private long checkedAt=-1;
 boolean observe(CameraIdentity next,long now){boolean changed=!next.same(current(now));identity=next;checkedAt=now;return changed;}
 boolean clear(){boolean changed=identity!=null;identity=null;checkedAt=-1;return changed;}
 CameraIdentity current(long now){return ConnectionPolicy.fresh(checkedAt,now)?identity:null;}
 int status(long now){CameraIdentity actual=current(now);return actual==null?DISCONNECTED:actual.supported()?SUPPORTED:UNSUPPORTED;}
 boolean supported(long now){return status(now)==SUPPORTED;}
 boolean expired(long now){return identity!=null&&!ConnectionPolicy.fresh(checkedAt,now);}
 String model(long now){CameraIdentity actual=current(now);return actual==null?"未连接相机":actual.model;}
 String firmware(long now){CameraIdentity actual=current(now);return actual==null?"固件 —":"固件 "+actual.firmware+(actual.supported()?"":" · 不支持");}
}
