package cn.crhistory.player;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.security.MessageDigest;
import org.json.JSONObject;

/** 使用已校验的 APK 或独立规则包；校验完成后替换本场目录中的旧规则。 */
public final class ReplayRules {
 private static final String VISUAL="sc3d/characters/hero/hero_electrowizard/animation/asm_hero_electrowizard_battle.json";
 private ReplayRules(){}
 private static boolean valid(String name,boolean visuals){if(visuals)return name.equals("assets.scdb")||name.equals(VISUAL);return (name.equals("data_manifest.toml")||name.startsWith("csv_logic/"))&&!name.contains("\\")&&!name.contains("//")&&!name.endsWith("/")&&Arrays.stream(name.split("/",-1)).noneMatch(p->p.isEmpty()||p.equals(".")||p.equals(".."));}
 private static void remove(File file)throws IOException{if(java.nio.file.Files.isSymbolicLink(file.toPath()))throw new IOException("回放规则路径无效");if(file.isDirectory()){File[] children=file.listFiles();if(children==null)throw new IOException("回放规则目录不可读");for(File child:children)remove(child);}if(file.exists()&&!file.delete())throw new IOException("回放规则清理失败");}
 private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
 public static void install(InputStream input,File update,String version)throws Exception{
  install(input,update,version,false);
 }
 public static void installVisuals(InputStream input,File update,String version)throws Exception{install(input,update,version,true);}
 private static void install(InputStream input,File update,String version,boolean visuals)throws Exception{
  File stage=new File(update,visuals?".apk-visuals":".apk-rules");boolean success=false;
  try(ZipInputStream zip=new ZipInputStream(input)){
   remove(stage);if(!stage.mkdirs())throw new IOException("回放规则目录不可用");
   ZipEntry entry=zip.getNextEntry();if(entry==null||!entry.getName().equals("index.json"))throw new IOException("安装包回放规则索引缺失");
   ByteArrayOutputStream index=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;while((n=zip.read(block))!=-1){if(index.size()+n>1024*1024)throw new IOException("回放规则索引过大");index.write(block,0,n);}
   JSONObject versions=new JSONObject(index.toString("UTF-8"));JSONObject selected=versions.optJSONObject(version);if(selected==null)throw new IOException("回放规则版本未登记");
   Set<String> expected=new HashSet<>();long budget=0;
   for(Iterator<String> vs=versions.keys();vs.hasNext();){String v=vs.next();if(!v.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IOException("回放规则版本无效");JSONObject files=versions.getJSONObject(v);if(visuals?(!files.has("assets.scdb")||!files.has(VISUAL)||files.length()!=2):(!files.has("data_manifest.toml")||files.length()<2))throw new IOException("回放规则集合不完整");for(Iterator<String> names=files.keys();names.hasNext();){String name=names.next();JSONObject spec=files.getJSONObject(name);long bytes=spec.getLong("bytes");if(!valid(name,visuals)||bytes<0||bytes>(visuals?32:1)*1024*1024||!spec.getString("sha256").matches("[a-f0-9]{64}"))throw new IOException("回放规则登记无效");budget+=bytes;if(budget>(visuals?64:16)*1024*1024)throw new IOException("回放规则总量过大");expected.add(v+"/"+name);}}
   Set<String> seen=new HashSet<>();
   while((entry=zip.getNextEntry())!=null){String full=entry.getName();if(entry.isDirectory()||!expected.contains(full)||!seen.add(full))throw new IOException("回放规则重复或路径未登记");int slash=full.indexOf('/');String v=full.substring(0,slash),name=full.substring(slash+1);JSONObject spec=versions.getJSONObject(v).getJSONObject(name);long size=0;MessageDigest digest=MessageDigest.getInstance("SHA-256");File target=new File(stage,name);boolean chosen=v.equals(version);
    if(chosen&&!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("回放规则目录不可用");
    try(FileOutputStream out=chosen?new FileOutputStream(target):null){while((n=zip.read(block))!=-1){size+=n;if(size>spec.getLong("bytes"))throw new IOException("回放规则解压大小不符");digest.update(block,0,n);if(out!=null)out.write(block,0,n);}if(out!=null)out.getFD().sync();}
    if(size!=spec.getLong("bytes")||!hex(digest.digest()).equals(spec.getString("sha256")))throw new IOException("安装包回放规则损坏");
   }
   if(!seen.equals(expected))throw new IOException("安装包回放规则不完整");
   for(String name:visuals?new String[]{"assets.scdb",VISUAL}:new String[]{"csv_logic","data_manifest.toml"}){File target=new File(update,name);remove(target);if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("回放资源目录不可用");if(!new File(stage,name).renameTo(target))throw new IOException("回放规则保存未完成");}success=true;
  }finally{
   try{remove(stage);}finally{if(!success)for(String name:visuals?new String[]{"assets.scdb",VISUAL}:new String[]{"csv_logic","data_manifest.toml"})remove(new File(update,name));}
  }
 }
}
