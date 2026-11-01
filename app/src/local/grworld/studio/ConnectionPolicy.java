package local.grworld.studio;

import java.io.IOException;

/** Timing gates are conservative observations, never a guarantee against future loss. */
final class ConnectionPolicy {
 static final long POLL_MS=5000, FRESH_MS=15000, MAX_REPLY_MS=2000;
 interface Probe { long read() throws Exception; }
 interface Pause { void waitFor(long millis) throws Exception; }
 static boolean fresh(long checkedAt,long now){return checkedAt>=0&&now>=checkedAt&&now-checkedAt<FRESH_MS;}
 static void reply(long millis)throws IOException {
  if(millis<0||millis>MAX_REPLY_MS)throw new IOException("相机响应较慢，请靠近相机后重新检测");
 }
 static void preflight(Probe probe,Pause pause)throws Exception {
  for(int sample=0;sample<3;sample++){
   reply(probe.read());
   if(sample<2)pause.waitFor(500);
  }
 }
}
