// SPDX-License-Identifier: Apache-2.0
// Adapted for CR Replay Component from fixed main d815abb34bbb3847ca2fec95df3096059ea7dda0; see docs/provenance.md.
package cn.crhistory.player;
import java.io.*;import java.nio.file.*;import java.security.*;import java.util.*;import java.util.zip.*;import org.json.*;
/** 小压缩夹具验证实际展开总量；只写调用者指定的任务产物目录。 */
public final class PackBoundaryCheck {
 private static File pack(File root,long second,boolean extra)throws Exception{
  File file=new File(root,"boundary-"+second+"-"+extra+".pack");JSONArray items=new JSONArray();long total=0;byte[] zeros=new byte[65536];
  try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(file))){long[] lengths=extra?new long[]{1L<<30,second,1}:new long[]{1L<<30,second};
   for(int i=0;i<lengths.length;i++){String path="assets/fixture-"+i+".txt";long length=lengths[i];MessageDigest hash=MessageDigest.getInstance("SHA-256");zip.putNextEntry(new ZipEntry(path));for(long left=length;left>0;){int n=(int)Math.min(left,zeros.length);zip.write(zeros,0,n);hash.update(zeros,0,n);left-=n;}zip.closeEntry();StringBuilder checksum=new StringBuilder();for(byte b:hash.digest())checksum.append(String.format(Locale.ROOT,"%02x",b&255));items.put(new JSONObject().put("path",path).put("bytes",length).put("sha256",checksum.toString()));total+=length;}
   zip.putNextEntry(new ZipEntry("ready.json"));zip.write(new JSONObject().put("files",items).put("unpackedBytes",total).toString().getBytes("UTF-8"));zip.closeEntry();
  }return file;
 }
 public static void main(String[] args)throws Exception{
  File root=Path.of(args[0]).toFile();if(!root.isDirectory())throw new IOException("task directory required");
  for(String bad:new String[]{"../escape","/absolute","assets/../../escape","C:/escape","assets/code.dex","assets/code.so"}){try{ReplayResourcePack.path(bad);throw new AssertionError("unsafe path accepted");}catch(IOException expected){}}
  for(long second:new long[]{(1L<<30)-1,1L<<30}){File f=pack(root,second,false);ReplayResourcePack.validate(f,ReplayResourcePack.sha256(f),f.length());}
  File over=pack(root,1L<<30,true);try{ReplayResourcePack.validate(over,ReplayResourcePack.sha256(over),over.length());throw new AssertionError("2GiB+1 accepted");}catch(IOException expected){if(!expected.getMessage().contains("资源展开体积异常"))throw expected;}
  System.out.println("PASS: actual 2GiB-1 / 2GiB / 2GiB+1 expanded streams and rejected traversal/executable paths; JVM evidence only");
 }
}
