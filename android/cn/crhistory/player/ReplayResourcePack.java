// SPDX-License-Identifier: Apache-2.0
// Adapted for CR Replay Component from fixed main d815abb34bbb3847ca2fec95df3096059ea7dda0; see docs/provenance.md.
package cn.crhistory.player;
import java.io.*;import java.security.MessageDigest;import java.util.*;import java.util.zip.*;import org.json.*;
/** 检查完整资源归档，不从归档加载Java或原生执行代码。 */
public final class ReplayResourcePack {
 private ReplayResourcePack(){}
 public static String sha256(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:digest.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
 public static String path(String name)throws IOException{if(name==null||name.isEmpty()||name.length()>512||name.startsWith("/")||name.contains("\\")||name.contains(":")||name.indexOf(0)>=0)throw new IOException("资源路径异常");for(String part:name.split("/"))if(part.equals("..")||part.equals("."))throw new IOException("资源路径越界");String lower=name.toLowerCase(Locale.ROOT);for(String suffix:new String[]{".dex",".jar",".so",".apk",".js",".lua",".py",".exe",".dll"})if(lower.endsWith(suffix))throw new IOException("资源包含执行代码");return name;}
 public static JSONObject inventory(ZipFile zip)throws Exception{ZipEntry entry=zip.getEntry("ready.json");if(entry==null||entry.getSize()>4*1024*1024)throw new IOException("资源清单缺失或过大");try(InputStream in=zip.getInputStream(entry);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>4*1024*1024)throw new IOException("资源清单过大");out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));}}
 public static JSONObject validate(File archive,String expected,long bytes)throws Exception{
  if(archive.length()!=bytes||!expected.matches("[a-f0-9]{64}")||!expected.equals(sha256(archive)))throw new IOException("回放资源校验未通过，请重新下载");
  try(ZipFile zip=new ZipFile(archive)){JSONObject inventory=inventory(zip);JSONArray list=inventory.getJSONArray("files");if(list.length()<1||list.length()>20000)throw new IOException("资源数量异常");Set<String> paths=new HashSet<>();long total=0;
   for(int i=0;i<list.length();i++){JSONObject file=list.getJSONObject(i);String name=path(file.getString("path"));if(!name.startsWith("assets/")||!paths.add(name))throw new IOException("重复或未知资源路径");long length=file.getLong("bytes");if(length<0||length>1024L*1024*1024)throw new IOException("资源文件过大");total=Math.addExact(total,length);if(total>2L*1024*1024*1024)throw new IOException("资源展开体积异常");ZipEntry entry=zip.getEntry(name);if(entry==null||entry.isDirectory()||entry.getSize()!=length)throw new IOException("资源条目不完整");
    MessageDigest digest=MessageDigest.getInstance("SHA-256");long actual=0;try(InputStream in=zip.getInputStream(entry)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){actual+=n;if(actual>length)throw new IOException("资源展开大小异常");digest.update(buffer,0,n);}}StringBuilder checksum=new StringBuilder();for(byte b:digest.digest())checksum.append(String.format(Locale.ROOT,"%02x",b&255));if(actual!=length||!checksum.toString().equals(file.getString("sha256")))throw new IOException("资源条目校验不一致");}
   Set<String> seen=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){ZipEntry entry=entries.nextElement();String name=path(entry.getName());if(!seen.add(name)||entry.isDirectory()||!name.equals("ready.json")&&!paths.contains(name))throw new IOException("资源归档包含未登记条目");}
   if(total!=inventory.getLong("unpackedBytes"))throw new IOException("资源清单体积不一致");return inventory;
  }
 }
 private static InputStream updateStream(ZipFile zip)throws IOException{ZipEntry entry=zip.getEntry("assets/replay-resources.zip");if(entry==null)throw new IOException("更新资源缺失");return zip.getInputStream(entry);}
 public static void extractUpdate(File archive,File destination,long maximum)throws Exception{
  try(ZipFile zip=new ZipFile(archive);ZipInputStream updates=new ZipInputStream(updateStream(zip))){ZipEntry entry;Set<String> seen=new HashSet<>();long total=0;int count=0;byte[] b=new byte[65536];String prefix=destination.getCanonicalPath()+File.separator;
   while((entry=updates.getNextEntry())!=null){String name=path(entry.getName());if(!seen.add(name)||++count>20000)throw new IOException("更新资源条目异常");File target=new File(destination,name);if(!target.getCanonicalPath().startsWith(prefix))throw new IOException("更新资源越界");if(entry.isDirectory()){if(!target.isDirectory()&&!target.mkdirs())throw new IOException("资源目录创建失败");continue;}if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("资源目录创建失败");try(FileOutputStream out=new FileOutputStream(target)){int n;while((n=updates.read(b))!=-1){total=Math.addExact(total,n);if(total>maximum)throw new IOException("更新资源展开体积异常");out.write(b,0,n);}out.getFD().sync();}}
  }
 }
}
