package local.grworld.studio;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.os.storage.*;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.*;

/** SAF adapter; production entry validates a real removable volume on every operation. */
final class SdDocumentCard implements SdCardDeployment.Card {
 interface RootCheck {void check()throws Exception;}
 final ContentResolver resolver;final Uri tree;final String rootId,cardKey;final RootCheck rootCheck;final boolean writable;
 SdDocumentCard(ContentResolver resolver,Uri tree,String key,RootCheck check){this(resolver,tree,key,check,true);}
 SdDocumentCard(ContentResolver resolver,Uri tree,String key,RootCheck check,boolean writable){this.resolver=resolver;this.tree=tree;rootId=DocumentsContract.getTreeDocumentId(tree);cardKey=key;rootCheck=check;this.writable=writable;}
 static SdDocumentCard open(Context context,Uri tree,int grantFlags)throws Exception{
  final ContentResolver resolver=context.getContentResolver();final String rootId=DocumentsContract.getTreeDocumentId(tree);final String authority=tree.getAuthority();
  final StorageManager manager=(StorageManager)context.getSystemService(Context.STORAGE_SERVICE);
  RootCheck check=()->{boolean mounted=false;for(StorageVolume volume:manager.getStorageVolumes())if(volume.getUuid()!=null&&(volume.getUuid()+":").equalsIgnoreCase(rootId)&&volume.isRemovable()&&!volume.isPrimary()&&Environment.MEDIA_MOUNTED.equals(volume.getState()))mounted=true;
   int mode=Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION;boolean permission=(grantFlags&mode)==mode&&context.checkUriPermission(tree,android.os.Process.myPid(),android.os.Process.myUid(),mode)==android.content.pm.PackageManager.PERMISSION_GRANTED;
   SdCardRootPolicy.require(authority,rootId,mounted,permission);
  };
  check.check();return new SdDocumentCard(resolver,tree,rootId.substring(0,rootId.length()-1).toUpperCase(Locale.US),check);
 }
 public String key(){return cardKey;}
 static SdDocumentCard openRead(Context context,Uri tree,int flags)throws Exception{
  final String id=DocumentsContract.getTreeDocumentId(tree);RootCheck check=()->{boolean mounted=false;StorageManager manager=(StorageManager)context.getSystemService(Context.STORAGE_SERVICE);for(StorageVolume v:manager.getStorageVolumes())if(v.getUuid()!=null&&(v.getUuid()+":").equalsIgnoreCase(id)&&v.isRemovable()&&!v.isPrimary()&&Environment.MEDIA_MOUNTED.equals(v.getState()))mounted=true;boolean grant=(flags&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0&&context.checkUriPermission(tree,android.os.Process.myPid(),android.os.Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)==android.content.pm.PackageManager.PERMISSION_GRANTED;SdCardRootPolicy.require(tree.getAuthority(),id,mounted,grant);};check.check();return new SdDocumentCard(context.getContentResolver(),tree,id.substring(0,id.length()-1).toUpperCase(Locale.US),check,false);
 }
 public void check()throws Exception{rootCheck.check();Node root=query(DocumentsContract.buildDocumentUriUsingTree(tree,rootId));if(!root.directory||writable&&(root.flags&DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE)==0)throw new IOException("此 SD 根目录不支持当前操作，请使用手动方式。");}
 static final class Node {final Uri uri;final String id,name;final boolean directory;final int flags;Node(Uri uri,String id,String name,String mime,int flags){this.uri=uri;this.id=id;this.name=name;directory=DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);this.flags=flags;}}
 static final String[] COLUMNS={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_FLAGS};
 Node query(Uri uri)throws Exception{try(Cursor c=resolver.query(uri,COLUMNS,null,null,null)){if(c==null||!c.moveToFirst())throw new IOException("SD 文件已不可读取");Node n=new Node(uri,c.getString(0),c.getString(1),c.getString(2),c.getInt(3));if(c.moveToNext())throw new IOException("SD 文件定位不唯一");return n;}}
 List<Node> children(Node parent)throws Exception{
  if(!parent.directory)throw new IOException("SD 路径不是文件夹");List<Node> nodes=new ArrayList<>();Set<String> names=new HashSet<>();try(Cursor c=resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent.id),COLUMNS,null,null,null)){
   if(c==null)throw new IOException("SD 目录不可读取");while(c.moveToNext()){if(nodes.size()>=4096)throw new IOException("SD 目录文件过多，请手动导出");String name=c.getString(1);if(name==null||name.contains("/")||name.contains("\\")||name.equals(".")||name.equals("..")||!names.add(name.toLowerCase(Locale.US)))throw new IOException("SD 目录存在不明确的文件名");String id=c.getString(0);nodes.add(new Node(DocumentsContract.buildDocumentUriUsingTree(tree,id),id,name,c.getString(2),c.getInt(3)));}}
  return nodes;
 }
 Node find(String path)throws Exception{
  if(path.startsWith("/")||path.contains("..")||path.contains("\\"))throw new IOException("SD 路径无效");Node n=query(DocumentsContract.buildDocumentUriUsingTree(tree,rootId));if(path.isEmpty())return n;
  for(String part:path.split("/")){Node match=null;for(Node child:children(n))if(child.name.equalsIgnoreCase(part)){if(match!=null)throw new IOException("SD 路径重名");match=child;}if(match==null)throw new FileNotFoundException(path);n=match;}return n;
 }
 public List<String> list(String directory)throws Exception{check();List<String> names=new ArrayList<>();for(Node n:children(find(directory)))names.add(n.name);return names;}
 public boolean directory(String path)throws Exception{check();return find(path).directory;}
 public byte[] read(String path,int limit)throws Exception{check();Node n=find(path);if(n.directory)throw new IOException("不能将目录读取为文件");return CameraClient.read(resolver.openInputStream(n.uri),limit);}
 Node createNode(String path,String mime)throws Exception{
  if(!writable)throw new IOException("此授权仅供只读核验");check();int slash=path.lastIndexOf('/');String name=path.substring(slash+1),parentPath=slash<0?"":path.substring(0,slash);Node parent=find(parentPath);
  for(Node n:children(parent))if(n.name.equalsIgnoreCase(name))throw new IOException("不会覆盖已有 SD 文件："+path);
  Uri created=DocumentsContract.createDocument(resolver,parent.uri,mime,name);if(created==null)throw new IOException("SD 文件创建失败");Node n=query(created);if(!name.equalsIgnoreCase(n.name)||!mime.equals(DocumentsContract.Document.MIME_TYPE_DIR)&&n.directory){try{DocumentsContract.deleteDocument(resolver,created);}catch(Exception ignored){}throw new IOException("系统更改了文件名，已停止写入");}return n;
 }
 public void mkdir(String path)throws Exception{createNode(path,DocumentsContract.Document.MIME_TYPE_DIR);}
 public void create(String path,byte[] bytes)throws Exception{
  Node n=createNode(path,"application/octet-stream");try{ParcelFileDescriptor descriptor=resolver.openFileDescriptor(n.uri,"rwt");if(descriptor==null)throw new IOException("SD 文件不可写");try(ParcelFileDescriptor.AutoCloseOutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(descriptor)){out.write(bytes);out.flush();out.getFD().sync();}}catch(Exception e){try{DocumentsContract.deleteDocument(resolver,n.uri);}catch(Exception ignored){}throw e;}
 }
 public void delete(String path)throws Exception{if(!writable)throw new IOException("此授权仅供只读核验");check();Node n=find(path);if(n.directory)throw new IOException("不会自动删除 SD 文件夹");if(!DocumentsContract.deleteDocument(resolver,n.uri))throw new IOException("SD 文件删除失败："+path);}
 public void rename(String path,String name)throws Exception{
  if(!writable)throw new IOException("此授权仅供只读核验");check();Node n=find(path);int slash=path.lastIndexOf('/');String parent=slash<0?"":path.substring(0,slash);for(Node item:children(find(parent)))if(item.name.equalsIgnoreCase(name))throw new IOException("启动脚本位置已被占用");
  Uri moved=DocumentsContract.renameDocument(resolver,n.uri,name);if(moved==null||!query(moved).name.equalsIgnoreCase(name))throw new IOException("SD 启动脚本启用失败");
 }
}
