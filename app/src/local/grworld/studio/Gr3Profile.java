package local.grworld.studio;

import java.io.IOException;

/** Edition-specific resources recovered from official GR IIIx 1.70.20.18. */
enum Gr3Profile {
 STANDARD("GR IIIx 普通版","GoodBye.jpg","gr3x-goodbye.jpg",7264,"dff9ba0ad2c7ea2697b19470cf6c695337651ee0ef415a6d3359a7f318ff3c79"),
 URBAN("GR IIIx Urban Edition","GB_Urban.jpg","gr3x-urban.jpg",107902,"7a2154a6d24ed5e460d2a918783d593171e399562923da582801186098ffc89f"),
 HDF("GR IIIx HDF","GoodBye.jpg","gr3x-goodbye.jpg",7264,"dff9ba0ad2c7ea2697b19470cf6c695337651ee0ef415a6d3359a7f318ff3c79");
 final String label,filename,asset,stockHash;final int bytes;
 Gr3Profile(String label,String filename,String asset,int bytes,String stockHash){this.label=label;this.filename=filename;this.asset=asset;this.bytes=bytes;this.stockHash=stockHash;}
 String target(){return "B:\\Resource\\Jpeg\\"+filename;}
 void stock(byte[] data)throws Exception{EndScreenPolicy.image(data);if(data.length!=bytes||!EndScreenPolicy.sha256(data).equals(stockHash))throw new IOException("原厂资源校验失败");}
 void candidate(byte[] original,byte[] data)throws Exception{
  stock(original);EndScreenPolicy.image(data);if(data.length!=bytes)throw new IOException("图片未达到该机型的精确长度");
  Gr3JpegEncoder.Headers a=new Gr3JpegEncoder.Headers(original),b=new Gr3JpegEncoder.Headers(data);
  if(!java.util.Arrays.equals(a.app,b.app)||!java.util.Arrays.equals(a.frame,b.frame)||!java.util.Arrays.equals(a.scan,b.scan))throw new IOException("JPEG 声明与原厂格式不一致");
 }
}
