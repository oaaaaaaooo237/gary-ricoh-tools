package local.grworld.studio;

import java.io.*;
import java.util.*;

/** Independent baseline JPEG encoder. No metadata/entropy padding; exact-size search. */
final class Gr3JpegEncoder {
 static final class TooDetailed extends IOException {TooDetailed(){super("图片细节过多，需降低细节后再处理");}}
 static final int W=720,H=480,BLOCKS=W*H/256*6;
 static final int[] Z={0,1,8,16,9,2,3,10,17,24,32,25,18,11,4,5,12,19,26,33,40,48,41,34,27,20,13,6,7,14,21,28,35,42,49,56,57,50,43,36,29,22,15,23,30,37,44,51,58,59,52,45,38,31,39,46,53,60,61,54,47,55,62,63};
 static final double[][] BASIS=new double[8][8];
 static{for(int u=0;u<8;u++)for(int x=0;x<8;x++)BASIS[u][x]=(u==0?1/Math.sqrt(2):1)*Math.cos((2*x+1)*u*Math.PI/16)/2;}
 static final class Headers {
  byte[] app,frame,scan;final int[][] quant=new int[2][64];
  Headers(byte[] jpeg)throws IOException{
   int p=2;while(p+4<=jpeg.length){if((jpeg[p++]&255)!=255)throw new IOException("JPEG 标记异常");int marker=jpeg[p++]&255;int len=((jpeg[p]&255)<<8)|(jpeg[p+1]&255);if(len<2||p+len>jpeg.length)throw new IOException("JPEG 段越界");byte[] raw=Arrays.copyOfRange(jpeg,p-2,p+len);
    if(marker==0xe0)app=raw;else if(marker==0xc0)frame=raw;else if(marker==0xda){scan=raw;break;}
    else if(marker==0xdb){for(int at=p+2;at<p+len;){int spec=jpeg[at++]&255;if(spec>1||at+64>p+len)throw new IOException("不支持的量化表");for(int k=0;k<64;k++)quant[spec][Z[k]]=jpeg[at++]&255;}}
    else if(marker!=0xc4)throw new IOException("JPEG 含未支持的附加段");p+=len;
   }
   if(app==null||frame==null||scan==null||quant[0][0]==0||quant[1][0]==0)throw new IOException("JPEG 基线声明不完整");
   byte[] expectedFrame={-1,-64,0,17,8,1,-32,2,-48,3,1,34,0,2,17,1,3,17,1};
   byte[] expectedScan={-1,-38,0,12,3,1,0,2,17,3,17,0,63,0};
   if(!Arrays.equals(frame,expectedFrame)||!Arrays.equals(scan,expectedScan))throw new IOException("仅支持原厂 720×480、4:2:0 基线格式");
  }
 }
 static final class Result {final byte[] jpeg;final int trials;Result(byte[] jpeg,int trials){this.jpeg=jpeg;this.trials=trials;}}
 final Headers headers;final float[] coefficients=new float[BLOCKS*64];final int[] values=new int[BLOCKS*64];
 final long deadline;int trials;
 Gr3JpegEncoder(int[] pixels,byte[] original,long deadline)throws IOException{
  if(pixels.length!=W*H)throw new IOException("输入尺寸必须为 720×480");headers=new Headers(original);this.deadline=deadline;
  double[] block=new double[64],tmp=new double[64];int number=0;
  for(int my=0;my<H;my+=16)for(int mx=0;mx<W;mx+=16)for(int sub=0;sub<6;sub++){
   for(int y=0;y<8;y++)for(int x=0;x<8;x++){
    if(sub<4){int rgb=pixels[(my+(sub/2)*8+y)*W+mx+(sub%2)*8+x];block[y*8+x]=.299*((rgb>>16)&255)+.587*((rgb>>8)&255)+.114*(rgb&255)-128;}
    else{double sum=0;for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++){int rgb=pixels[(my+2*y+dy)*W+mx+2*x+dx],r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;sum+=sub==4?-.168736*r-.331264*g+.5*b:.5*r-.418688*g-.081312*b;}block[y*8+x]=sum/4;}
   }
   for(int y=0;y<8;y++)for(int u=0;u<8;u++){double sum=0;for(int x=0;x<8;x++)sum+=block[y*8+x]*BASIS[u][x];tmp[y*8+u]=sum;}
   for(int v=0;v<8;v++)for(int u=0;u<8;u++){double sum=0;for(int y=0;y<8;y++)sum+=tmp[y*8+u]*BASIS[v][y];coefficients[number*64+v*8+u]=(float)sum;}
   number++;
  }
 }
 int[][] tables(int scale){int[][] q=new int[2][64];for(int t=0;t<2;t++)for(int k=0;k<64;k++)q[t][k]=Math.max(1,Math.min(255,(headers.quant[t][k]*scale+128)/256));return q;}
 void time()throws IOException{if(System.nanoTime()>deadline||trials>=1800)throw new IOException("精确长度搜索未完成，请调整图片细节或换一张图片");}
 Result exact(int target)throws IOException{
  int low=1,high=65536,bestScale=256,bestError=Integer.MAX_VALUE;byte[] best=null;
  byte[] min=encode(tables(high)),max=encode(tables(low));
  if(min.length>target)throw new TooDetailed();
  if(max.length<target)throw new IOException("图片细节不足，无法生成该机型的精确长度图片");
  for(int n=0;n<20&&low<=high;n++){int scale=(low+high)>>>1;byte[] jpeg=encode(tables(scale));int err=Math.abs(jpeg.length-target);if(err<bestError){best=jpeg;bestError=err;bestScale=scale;}if(err==0)return new Result(jpeg,trials);if(jpeg.length>target)low=scale+1;else high=scale-1;}
  int[][] left=tables(Math.max(1,bestScale*95/100)),right=tables(Math.min(65536,bestScale*105/100+2));ArrayList<Integer> positions=new ArrayList<>();for(int t=0;t<2;t++)for(int k=0;k<64;k++)if(left[t][k]!=right[t][k])positions.add(t*64+k);
  if(positions.isEmpty())throw new IOException("量化表没有可搜索范围，请调整图片");
  Random random=new Random(17020261031L);int[][] current=tables(bestScale),bestTables=copy(current);int currentError=bestError;
  while(true){time();int[][] candidate=copy(current);int flips=trials%11==0?Math.min(5,positions.size()):1;for(int i=0;i<flips;i++){int pos=positions.get(random.nextInt(positions.size())),t=pos/64,k=pos%64;candidate[t][k]=random.nextBoolean()?left[t][k]:right[t][k];}
   byte[] jpeg=encode(candidate);int err=Math.abs(jpeg.length-target);if(err==0)return new Result(jpeg,trials);
   if(err<bestError){best=jpeg;bestError=err;bestTables=copy(candidate);}if(err<=currentError||(random.nextInt(100)<2&&err<currentError+8)){current=candidate;currentError=err;}
   if(trials%120==0){current=copy(bestTables);currentError=bestError;}
  }
 }
 static int[][] copy(int[][] a){return new int[][]{a[0].clone(),a[1].clone()};}
 static int category(int value){int n=Math.abs(value),bits=0;while(n!=0){n>>>=1;bits++;}return bits;}
 byte[] encode(int[][] q)throws IOException{
  time();trials++;int[][] frequency=new int[4][256];int[] last={0,0,0};
  for(int b=0;b<BLOCKS;b++){
   int sub=b%6,component=sub<4?0:sub-3,group=component==0?0:1,base=b*64;
   for(int k=0;k<64;k++){int value=Math.round(coefficients[base+k]/q[group][k]);values[base+k]=k==0?value:Math.max(-1023,Math.min(1023,value));}
   int dc=values[base]-last[component];last[component]=values[base];frequency[group*2][category(dc)]++;
   int zero=0;for(int k=1;k<64;k++){int ac=values[base+Z[k]];if(ac==0){zero++;continue;}while(zero>=16){frequency[group*2+1][240]++;zero-=16;}frequency[group*2+1][zero*16+category(ac)]++;zero=0;}if(zero>0)frequency[group*2+1][0]++;
  }
  Huffman[] h=new Huffman[4];for(int i=0;i<4;i++)h[i]=new Huffman(frequency[i],i%2==0);
  ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(255);out.write(216);out.write(headers.app);
  for(int t=0;t<2;t++){out.write(255);out.write(219);out.write(0);out.write(67);out.write(t);for(int k=0;k<64;k++)out.write(q[t][Z[k]]);}
  out.write(headers.frame);for(int i=0;i<4;i++){out.write(255);out.write(196);int len=19+h[i].symbols.length;out.write(len>>8);out.write(len);out.write((i%2)*16+i/2);for(int n=1;n<=16;n++)out.write(h[i].counts[n]);for(int symbol:h[i].symbols)out.write(symbol);}
  out.write(headers.scan);Bits bits=new Bits(out);Arrays.fill(last,0);
  for(int b=0;b<BLOCKS;b++){int sub=b%6,component=sub<4?0:sub-3,group=component==0?0:1,base=b*64;int value=values[base]-last[component];last[component]=values[base];int size=category(value);h[group*2].write(bits,size);bits.value(value,size);
   int zero=0;for(int k=1;k<64;k++){value=values[base+Z[k]];if(value==0){zero++;continue;}while(zero>=16){h[group*2+1].write(bits,240);zero-=16;}size=category(value);h[group*2+1].write(bits,zero*16+size);bits.value(value,size);zero=0;}if(zero>0)h[group*2+1].write(bits,0);
  }
  bits.finish();out.write(255);out.write(217);return out.toByteArray();
 }
 static final class Node {final long weight;final int symbol;final Node left,right;Node(long weight,int symbol,Node left,Node right){this.weight=weight;this.symbol=symbol;this.left=left;this.right=right;}}
 static final class Huffman {
  final int[] counts=new int[17],codes=new int[256],lengths=new int[256],symbols;
  Huffman(int[] frequency,boolean dc){
   int[] depth=new int[257];int floor=1;
   while(true){PriorityQueue<Node> queue=new PriorityQueue<>((a,b)->a.weight!=b.weight?Long.compare(a.weight,b.weight):Integer.compare(a.symbol,b.symbol));
    for(int s=0;s<256;s++)if(dc?s<12:(s==0||s==240||(s%16>=1&&s%16<=10)))queue.add(new Node(Math.max(floor,frequency[s]),s,null,null));
    queue.add(new Node(1,256,null,null));int serial=257;while(queue.size()>1){Node a=queue.remove(),b=queue.remove();queue.add(new Node(a.weight+b.weight,serial++,a,b));}Arrays.fill(depth,0);depth(queue.remove(),0,depth);int max=0,index=0;for(int s=0;s<257;s++)if(depth[s]>max){max=depth[s];index=s;}if(max<=16){int temp=depth[256];depth[256]=max;depth[index]=temp;break;}floor*=2;
   }
   ArrayList<Integer> list=new ArrayList<>();for(int s=0;s<256;s++)if(depth[s]>0)list.add(s);list.sort((a,b)->depth[a]!=depth[b]?Integer.compare(depth[a],depth[b]):Integer.compare(a,b));symbols=new int[list.size()];int code=0,previous=0,index=0;
   for(int symbol:list){int len=depth[symbol];code<<=len-previous;codes[symbol]=code++;lengths[symbol]=len;counts[len]++;symbols[index++]=symbol;previous=len;}
  }
  static void depth(Node node,int level,int[] result){if(node.left==null){result[node.symbol]=Math.max(1,level);return;}depth(node.left,level+1,result);depth(node.right,level+1,result);}
  void write(Bits bits,int symbol)throws IOException{if(lengths[symbol]==0)throw new IOException("JPEG Huffman 符号异常");bits.write(codes[symbol],lengths[symbol]);}
 }
 static final class Bits {final ByteArrayOutputStream out;int value,count;Bits(ByteArrayOutputStream out){this.out=out;}void write(int number,int size){for(int bit=size-1;bit>=0;bit--){value=(value<<1)|((number>>>bit)&1);if(++count==8){out.write(value);if((value&255)==255)out.write(0);value=0;count=0;}}}void value(int number,int size){if(size>0)write(number<0?number+(1<<size)-1:number,size);}void finish(){if(count>0)write((1<<(8-count))-1,8-count);}}
}
