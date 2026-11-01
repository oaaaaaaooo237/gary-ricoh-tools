package local.grworld.studio;
import java.security.MessageDigest;
import java.util.Locale;

/** Shared GR IV 1.20 handler; factory resources are selected separately by actual model. */
public final class EndScreenPolicy {
 public static final int INDEX=51;
 public static final int MAX_BYTES=512*1024;
 public static void camera(String model,String firmware) {
  if(!("RICOH GR IV HDF".equals(model)||"RICOH GR IV".equals(model))||!"1.20".equals(firmware))throw new IllegalArgumentException("Wi-Fi 关机图适用于 GR IV／GR IV HDF 1.20");
 }
 public static void image(byte[] data) {
  if(data==null||data.length<4||data.length>MAX_BYTES||data[0]!=(byte)255||data[1]!=(byte)216)throw new IllegalArgumentException("图片格式或大小不符");
  int pos=2;boolean frame=false,quantization=false,huffman=false;
  while(pos<data.length){
   if((data[pos++]&255)!=255)break;
   while(pos<data.length&&(data[pos]&255)==255)pos++;
   if(pos>=data.length)break;
   int marker=data[pos++]&255;
   if(marker==0xd9||marker==0xd8||marker==0x00||marker==0x01||(marker>=0xd0&&marker<=0xd7))break;
   if(pos+2>data.length)break;
   int len=((data[pos]&255)<<8)|(data[pos+1]&255);
   if(len<2||pos+len>data.length)break;
   if(marker>=0xc0&&marker<=0xcf&&marker!=0xc4&&marker!=0xc8&&marker!=0xcc){
    if(marker!=0xc0||frame||len!=17)break;
    int h=((data[pos+3]&255)<<8)|(data[pos+4]&255),w=((data[pos+5]&255)<<8)|(data[pos+6]&255);
    if(w!=720||h!=480||(data[pos+2]&255)!=8||(data[pos+7]&255)!=3)break;
    frame=true;
   }
   if(marker==0xdb)quantization=true;if(marker==0xc4)huffman=true;
   if(marker==0xda){
    if(!frame||!quantization||!huffman||len!=12||(data[pos+2]&255)!=3||data[pos+9]!=0||(data[pos+10]&255)!=63||data[pos+11]!=0)break;
    pos+=len;int entropy=0;
    while(pos<data.length){
     int b=data[pos++]&255;if(b!=255){entropy++;continue;}
     while(pos<data.length&&(data[pos]&255)==255)pos++;
     if(pos>=data.length)break;int next=data[pos++]&255;
     if(next==0||(next>=0xd0&&next<=0xd7)){entropy++;continue;}
     if(next==0xd9&&pos==data.length&&entropy>0)return;
     break;
    }
    break;
   }
   pos+=len;
  }
  throw new IllegalArgumentException("图片必须是 720×480、8 位三分量的基线 JPEG");
 }
 public static String sha256(byte[] data)throws Exception {
  StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
 }
 public static String path(byte[] data)throws Exception {image(data);return "/v1/device/image/endscreen?index="+INDEX+"&sha256="+sha256(data);}
 public static boolean accepted(int http,int code){return http==200&&code==200;}
}
