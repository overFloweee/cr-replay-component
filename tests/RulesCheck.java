package cn.crhistory.player;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;import java.security.*;import org.json.*;
public class RulesCheck {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void run(File bundle,File update,String version)throws Exception{try(InputStream in=new FileInputStream(bundle)){ReplayRules.install(in,update,version);}}
 static void same(File update,File bundle,String version)throws Exception{try(ZipFile z=new ZipFile(bundle)){Enumeration<? extends ZipEntry> entries=z.entries();while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();if(e.getName().startsWith(version+"/")){String name=e.getName().substring(version.length()+1);check(Arrays.equals(Files.readAllBytes(new File(update,name).toPath()),z.getInputStream(e).readAllBytes()),name);}}}}
 public static void main(String[] args)throws Exception{File base=new File(args[0]),update=new File(base,"update");update.mkdirs();File good=new File(base,"good.zip");int count=0;
  Files.write(new File(update,"public-asset").toPath(),new byte[]{9});
  List<String> versions;try(ZipFile z=new ZipFile(good)){JSONObject index=new JSONObject(new String(z.getInputStream(z.getEntry("index.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));versions=new ArrayList<>(index.keySet());Collections.sort(versions);}List<String> sequence=new ArrayList<>(versions);sequence.add(versions.get(0));sequence.add(versions.get(versions.size()-1));
  for(String version:sequence){run(good,update,version);same(update,good,version);File stale=new File(update,"csv_logic/stale.csv");Files.write(stale.toPath(),new byte[]{1});run(good,update,version);check(!stale.exists(),"旧规则残留");count++;}
  for(File bad:base.listFiles(f->f.getName().startsWith("bad-")&&f.getName().endsWith(".zip"))){run(good,update,"38.4.5");boolean failed=false;try{run(bad,update,"38.4.3");}catch(Exception e){failed=true;}check(failed,bad.getName());check(!new File(update,"csv_logic").exists()&&!new File(update,"data_manifest.toml").exists()&&!new File(update,".apk-rules").exists(),"失败残留："+bad);check(new File(update,"public-asset").exists(),"公共素材丢失");count++;}
  try{run(good,update,"999.0.0");throw new AssertionError("误支持未登记版本");}catch(IOException expected){count++;}
  File visualUpdate=new File(base,"visual-update");visualUpdate.mkdirs();Files.write(new File(visualUpdate,"data_manifest.toml").toPath(),new byte[]{42});
  try(InputStream in=new FileInputStream(new File(base,"visuals-good.zip"))){ReplayRules.installVisuals(in,visualUpdate,"38.4.11");}same(visualUpdate,new File(base,"visuals-good.zip"),"38.4.11");check(Files.readAllBytes(new File(visualUpdate,"data_manifest.toml").toPath())[0]==42,"动画覆盖改写规则");count++;
  for(File bad:base.listFiles(f->f.getName().startsWith("visuals-bad-"))){boolean failed=false;try(InputStream in=new FileInputStream(bad)){ReplayRules.installVisuals(in,visualUpdate,"38.4.11");}catch(Exception e){failed=true;}check(failed,"动画覆盖未拒绝："+bad);check(!new File(visualUpdate,"assets.scdb").exists()&&!new File(visualUpdate,".apk-visuals").exists(),"动画失败残留");check(Files.readAllBytes(new File(visualUpdate,"data_manifest.toml").toPath())[0]==42,"失败影响逻辑规则");count++;}
  try(InputStream in=new FileInputStream(new File(base,"visuals-good.zip"))){ReplayRules.installVisuals(in,visualUpdate,"38.4.10");throw new AssertionError("错误版本使用新动画");}catch(IOException expected){count++;}
  System.out.println("规则加载检查通过："+count+" 项（含全部登记版本连续切换及失败清理）");
 }
}
