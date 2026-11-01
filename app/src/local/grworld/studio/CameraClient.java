package local.grworld.studio;

import android.net.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class CameraClient {
 final Network network;
 final ConnectivityManager manager;
 CameraClient(ConnectivityManager manager)throws IOException {
  this.manager=manager;
  Network found=null;
  for(Network n:manager.getAllNetworks()){
   NetworkCapabilities c=manager.getNetworkCapabilities(n);
   if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)){
    if(found!=null)throw new IOException("发现多个 Wi-Fi 网络，请只连接相机 Wi-Fi");found=n;
   }
  }
  if(found==null)throw new IOException("请先在手机设置中连接相机 Wi-Fi，并选择保持连接");network=found;
 }
 static byte[] read(InputStream input,int max)throws IOException {
  if(input==null)throw new IOException("没有返回内容");
  try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>max)throw new IOException("内容超过大小限制");out.write(buf,0,n);}return out.toByteArray();
  }
 }
 HttpURLConnection connection(String path,String method)throws Exception {
  HttpURLConnection c=(HttpURLConnection)network.openConnection(new URL("http://192.168.0.1"+path),java.net.Proxy.NO_PROXY);
  c.setConnectTimeout(7000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestMethod(method);return c;
 }
 JSONObject response(HttpURLConnection c)throws Exception {
  int http=c.getResponseCode();InputStream in=http>=400?c.getErrorStream():c.getInputStream();
  JSONObject p=new JSONObject(new String(read(in,256*1024),StandardCharsets.UTF_8));
  if(!EndScreenPolicy.accepted(http,p.optInt("errCode",-1)))throw new IOException("相机未返回成功结果：HTTP "+http+" / errCode "+p.optInt("errCode",-1));return p;
 }
 void requireSameNetwork()throws IOException {
  if(!network.equals(new CameraClient(manager).network))throw new IOException("Wi-Fi 已切换，请重新检测相机");
 }
 static final class IdentityRead {
  final CameraIdentity identity;final long elapsedMs;
  IdentityRead(CameraIdentity identity,long elapsedMs){this.identity=identity;this.elapsedMs=elapsedMs;}
 }
 IdentityRead readIdentity()throws Exception {
  requireSameNetwork();long start=android.os.SystemClock.elapsedRealtime();
  HttpURLConnection c=connection("/v1/props","GET");c.setConnectTimeout(3000);c.setReadTimeout(3000);
  try{JSONObject p=response(c);CameraIdentity actual=new CameraIdentity(p.optString("model"),p.optString("firmwareVersion"));requireSameNetwork();return new IdentityRead(actual,android.os.SystemClock.elapsedRealtime()-start);}finally{c.disconnect();}
 }
 long identity()throws Exception {
  IdentityRead read=readIdentity();EndScreenPolicy.camera(read.identity.model,read.identity.firmware);return read.elapsedMs;
 }
 long identity(CameraIdentity expected)throws Exception {
  IdentityRead read=readIdentity();
  if(!read.identity.same(expected))throw new IOException("相机型号或固件已变化，请重新检测");
  EndScreenPolicy.camera(read.identity.model,read.identity.firmware);return read.elapsedMs;
 }
 // Caller must durably record the uncertain operation BEFORE invoking this method.
 void upload(byte[] jpeg)throws Exception {
  requireSameNetwork();
  HttpURLConnection c=connection(EndScreenPolicy.path(jpeg),"PUT");
  try{
   c.setDoOutput(true);c.setRequestProperty("Content-Type","image/jpeg");c.setFixedLengthStreamingMode(jpeg.length);
   try(OutputStream out=c.getOutputStream()){out.write(jpeg);}response(c);
  }finally{c.disconnect();}
 }
}
