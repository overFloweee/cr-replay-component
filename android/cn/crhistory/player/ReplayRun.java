// SPDX-License-Identifier: Apache-2.0
// Adapted for CR Replay Component from fixed main d815abb34bbb3847ca2fec95df3096059ea7dda0; see docs/provenance.md.
package cn.crhistory.player;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.util.AtomicFile;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.json.JSONObject;




/** 公共资源只读复用；每次引擎启动先登记独立私有运行空间。 */
public final class ReplayRun {
 private static final String PROCESS=UUID.randomUUID().toString();
 private ReplayRun(){}
 private static File data(Context c)throws Exception{return new File(c.getPackageManager().getApplicationInfo(c.getPackageName(),0).dataDir);}
 private static File base(Context c)throws Exception{return new File(data(c),"files/replay/runs");}
 private static File pointer(Context c)throws Exception{return new File(data(c),"files/replay/active-run.json");}
 private static JSONObject read(File f)throws Exception{try(InputStream in=new AtomicFile(f).openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1){if(out.size()+n>16384)throw new IOException("回放运行索引过大");out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));}}
 private static void write(File f,JSONObject value)throws Exception{AtomicFile a=new AtomicFile(f);FileOutputStream out=null;try{out=a.startWrite();out.write(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));a.finishWrite(out);}catch(Exception e){if(out!=null)a.failWrite(out);throw e;}}
 private static boolean exists(File f){return f.exists()||new File(f+".bak").exists();}
 private static boolean valid(String id){return id!=null&&id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}");}
 private static List<ActivityManager.RunningAppProcessInfo> processes(Context c)throws IOException{ActivityManager m=c.getSystemService(ActivityManager.class);List<ActivityManager.RunningAppProcessInfo> ps=m==null?null:m.getRunningAppProcesses();if(ps==null)throw new IOException("回放进程状态未知，请稍后重试");return ps;}
 private static boolean running(Context c)throws IOException{for(ActivityManager.RunningAppProcessInfo p:processes(c))if(p.uid==android.os.Process.myUid()&&p.processName.equals(c.getPackageName()+":replay"))return true;return false;}
 public static synchronized File current(Context c){try{JSONObject active=read(pointer(c));String id=active.getString("id");if(!valid(id))throw new IOException("回放运行编号无效");File root=new File(base(c),id);JSONObject manifest=read(new File(root,"manifest.json"));if(!id.equals(manifest.getString("id"))||!manifest.getString("state").equals("active"))throw new IOException("回放运行已关闭");if(Files.isSymbolicLink(root.toPath()))throw new IOException("回放运行路径无效");return root;}catch(Exception e){throw new IllegalStateException("回放运行空间不可用",e);}}
 private static void remove(File f,File allowed)throws Exception{if(!f.getAbsolutePath().startsWith(allowed.getAbsolutePath()+File.separator))throw new IOException("拒绝清理回放空间之外的文件");if(Files.isSymbolicLink(f.toPath())){if(!f.delete())throw new IOException("回放链接清理失败");return;}if(f.isDirectory()){File[] children=f.listFiles();if(children==null)throw new IOException("回放目录读取失败");for(File child:children)remove(child,allowed);}if(f.exists()&&!f.delete())throw new IOException("回放残留清理失败，请重试");}
 public static synchronized void reclaim(Context c)throws Exception {
  if(running(c))return;
  JSONObject active=exists(pointer(c))?read(pointer(c)):null;
  if(active!=null&&PROCESS.equals(active.optString("creator"))&&(!active.has("launched_at")||android.os.SystemClock.elapsedRealtime()-active.optLong("launched_at")<10000))return; // 登记后的准备阶段也占用，不能只看子进程。
  File space=base(c);if(Files.isSymbolicLink(space.toPath()))throw new IOException("回放空间路径无效");File[] runs=space.listFiles();if(space.exists()&&runs==null)throw new IOException("回放空间读取失败");
  if(runs!=null)for(File run:runs){collectDiagnostics(c,run);remove(run,space);}
  File prefs=new File(data(c),"shared_prefs");File[] entries=prefs.listFiles();if(prefs.exists()&&entries==null)throw new IOException("回放偏好目录读取失败");
  if(entries!=null)for(File f:entries)if(f.getName().startsWith("replay-run-")&&(f.getName().endsWith(".xml")||f.getName().endsWith(".xml.bak"))){String name=f.getName().replaceFirst("\\.xml(\\.bak)?$","");if(!c.getSharedPreferences(name,0).edit().clear().commit()||!c.deleteSharedPreferences(name))throw new IOException("回放偏好清理待重试");}
  new AtomicFile(pointer(c)).delete();
 }
 private static void collectDiagnostics(Context c,File run)throws Exception {
  if(Files.isSymbolicLink(run.toPath())||!run.isDirectory())throw new IOException("Invalid run directory");
  JSONObject outcome=playbackOutcome(run);if("interrupted_unknown".equals(outcome.optString("outcome")))observeExit(c,run,outcome);
  File evidence=new File(data(c),"files/replay/evidence");if(!evidence.isDirectory()&&!evidence.mkdirs())throw new IOException("Cannot retain playback evidence");
  File saved=new File(evidence,run.getName()+".json");if(!saved.exists())write(saved,new JSONObject().put("manifest",read(new File(run,"manifest.json"))).put("outcome",outcome));
 }
 static JSONObject playbackOutcome(File run)throws Exception {
  File result=new File(run,"files/playback-result.json"),failure=new File(run,"files/playback-failure.json");JSONObject out=new JSONObject();
  if(result.isFile()){try{JSONObject value=read(result);if(value.has("tick")&&value.optLong("tick",-1)>=0)return out.put("outcome","complete");}catch(Exception ignored){}return out.put("outcome","failed").put("error_code","invalid_playback_result");}
  if(failure.isFile()){String code="playback_failed";try{JSONObject value=read(failure);for(String key:new String[]{"time_ms","last_failure_stage","exception_type","cause_types","stack_frames","error_fingerprint","error_domain"})if(value.has(key))out.put(key,value.get(key));String saved=value.optString("error_code");if(saved.matches("[a-z_]{1,80}"))code=saved;}catch(Exception ignored){}return out.put("outcome","failed").put("error_code",code);}
  File exit=new File(run,"files/playback-exit.json");if(exists(exit))try{JSONObject value=read(exit);String reason=value.optString("finish_reason");return out.put("outcome","user_exit").put("finish_reason",reason.equals("in_game_exit")||reason.equals("system_back_exit")?reason:"unknown");}catch(Exception ignored){/* 损坏文件不能证明主动退出。 */}
  return out.put("outcome","interrupted_unknown").put("error_code","playback_result_unobserved").put("finish_reason","exit_info_unobserved");
 }
 // 只在首次冻结前读取一次；系统记录晚到时保留未知，不重写已发送载荷。
 private static void observeExit(Context c,File run,JSONObject out){
  if(android.os.Build.VERSION.SDK_INT<30)return;
  try{String id=run.getName();if(!valid(id)||!id.equals(read(new File(run,"manifest.json")).optString("id")))return;int pid=read(new File(run,"entered.json")).optInt("pid");if(pid<=0)return;
   ActivityManager manager=c.getSystemService(ActivityManager.class);if(manager==null)return;
   android.app.ApplicationExitInfo found=matchingExit(manager.getHistoricalProcessExitReasons(c.getPackageName(),pid,0),c.getPackageName(),pid,id);
   if(found!=null)out.put("finish_reason","process_exit_observed").put("exit_reason",found.getReason()).put("exited_pid",pid).put("exit_timestamp_ms",found.getTimestamp());
  }catch(Exception ignored){/* 缺失、歧义或平台拒绝均不猜测结束原因。 */}
 }
 static android.app.ApplicationExitInfo matchingExit(List<android.app.ApplicationExitInfo> exits,String pkg,int pid,String id){
  if(exits==null||pid<=0||!valid(id))return null;android.app.ApplicationExitInfo found=null;byte[] expected=id.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  for(android.app.ApplicationExitInfo item:exits)if(item.getPid()==pid&&(pkg+":replay").equals(item.getProcessName())&&Arrays.equals(expected,item.getProcessStateSummary())&&item.getTimestamp()>0&&item.getReason()>=0){if(found!=null)return null;found=item;}
  return found;
 }
 private static void copy(File from,File to)throws Exception{if(Files.isSymbolicLink(from.toPath()))throw new IOException("公共资源路径无效");if(from.isDirectory()){if(!to.mkdirs()&&!to.isDirectory())throw new IOException("回放空间不足");File[] children=from.listFiles();if(children==null)throw new IOException("资源目录读取失败");for(File f:children)copy(f,new File(to,f.getName()));}else if(from.isFile()){try(InputStream in=new FileInputStream(from);FileOutputStream out=new FileOutputStream(to)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.getFD().sync();}}else throw new IOException("资源文件缺失");}
 public static synchronized void prepare(Context c,String scope,String game)throws Exception {
  if(exists(pointer(c))&&"closing".equals(read(pointer(c)).optString("state"))){long deadline=android.os.SystemClock.elapsedRealtime()+2000;do{try{stop(c);break;}catch(IOException e){if(!running(c))throw e;if(android.os.SystemClock.elapsedRealtime()>=deadline)break;android.os.SystemClock.sleep(50);}}while(true);}
  reclaim(c);if(running(c)||exists(pointer(c)))throw new IOException("Another replay is preparing or closing");
  JSONObject resource=LocalResources.ready(c);if(resource==null)throw new IOException("回放资源尚未准备完成");if(!scope.matches("[a-f0-9]{64}|local")||game==null||!game.matches("[0-9]+:[0-9]+"))throw new IOException("回放归属无效");
  File space=base(c);if(!space.isDirectory()&&!space.mkdirs())throw new IOException("回放空间不可用");String id=UUID.randomUUID().toString();File root=new File(space,id);if(!root.mkdir())throw new IOException("回放空间不可用");
  String operation=UUID.randomUUID().toString();
  JSONObject manifest=new JSONObject().put("operation_id",operation).put("id",id).put("creator",PROCESS).put("scope",scope).put("game",game).put("resource",resource.getString("id")).put("state","active");
  write(new File(root,"manifest.json"),manifest);write(pointer(c),manifest);
  try{copy(new File(LocalResources.runtimeDirectory(c),"update"),new File(root,"update"));directory(root,"files");}catch(Exception e){abandon(c);throw e;}
 }
 public static void launch(Context c,Intent intent)throws Exception{
  File root;JSONObject active;synchronized(ReplayRun.class){root=current(c);active=read(new File(root,"manifest.json"));if(active.has("launched_at"))throw new IOException("回放启动票据已使用");active.put("launched_at",android.os.SystemClock.elapsedRealtime());write(pointer(c),active);write(new File(root,"manifest.json"),active);intent.putExtra("replay_run",root.getName());}
  try{c.startActivity(intent);}catch(Exception e){abandon(c,active.optString("operation_id"));throw e;}
  // Android 已接受启动；证据写入失败也不能重放或撤销真实启动。
  try{synchronized(ReplayRun.class){active.put("launch_returned",true);write(new File(root,"manifest.json"),active);}}catch(Exception e){android.util.Log.w("CRReplay","launch evidence unavailable");}
 }

 public static synchronized boolean launchEvidence(Context c,String operation)throws Exception{File space=base(c);File[] runs=space.listFiles();if(runs==null){if(space.exists())throw new IOException("回放运行目录读取失败");return false;}for(File run:runs){if(Files.isSymbolicLink(run.toPath())||!run.isDirectory())continue;JSONObject m=read(new File(run,"manifest.json"));if(operation.equals(m.optString("operation_id"))&&(m.optBoolean("launch_returned")||exists(new File(run,"entered.json"))))return true;}return false;}
 public static synchronized void entered(Context c,String id)throws Exception{if(!valid(id))throw new IOException("回放运行编号无效");File run=new File(base(c),id);if(Files.isSymbolicLink(run.toPath())||!id.equals(read(new File(run,"manifest.json")).optString("id")))throw new IOException("回放运行记录不一致");File entered=new File(run,"entered.json");boolean first=true;if(exists(entered))try{first=read(entered).optInt("pid")!=android.os.Process.myPid();}catch(Exception ignored){}write(entered,new JSONObject().put("entered",true).put("pid",android.os.Process.myPid()));
  if(first&&android.os.Build.VERSION.SDK_INT>=30&&android.app.Application.getProcessName().equals(c.getPackageName()+":replay"))try{ActivityManager manager=c.getSystemService(ActivityManager.class);if(manager!=null)manager.setProcessStateSummary(id.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}catch(RuntimeException ignored){/* 系统观测失败不能阻止播放。 */}
 }
 public static synchronized void abandon(Context c,String operation)throws Exception{if(!exists(pointer(c)))return;JSONObject active=read(pointer(c));if(!operation.equals(active.optString("operation_id")))return;abandon(c);}
 public static synchronized void abandon(Context c)throws Exception{if(exists(pointer(c))){JSONObject a=read(pointer(c));a.put("creator","");write(pointer(c),a);}reclaim(c);}
 public static synchronized void stop(Context c)throws Exception {
  if(exists(pointer(c))){String id=read(pointer(c)).getString("id");if(!valid(id))throw new IOException("回放运行编号无效");File root=new File(base(c),id);if(Files.isSymbolicLink(root.toPath()))throw new IOException("回放运行路径无效");JSONObject manifest=read(new File(root,"manifest.json"));if(!id.equals(manifest.getString("id")))throw new IOException("回放运行记录不一致");manifest.put("state","closing");write(new File(root,"manifest.json"),manifest);}
  boolean found=false;for(ActivityManager.RunningAppProcessInfo p:processes(c))if(p.uid==android.os.Process.myUid()&&p.pid!=android.os.Process.myPid()&&p.processName.equals(c.getPackageName()+":replay")){found=true;android.os.Process.killProcess(p.pid);}
  if(exists(pointer(c))){JSONObject a=read(pointer(c));if(!found&&!"closing".equals(a.optString("state"))&&a.has("launched_at")&&android.os.SystemClock.elapsedRealtime()-a.getLong("launched_at")<10000)throw new IOException("回放启动尚未确认，清理待重试");a.put("creator","").put("state","closing");write(pointer(c),a);}
  if(running(c))throw new IOException("回放进程正在停止，清理待重试");reclaim(c);
 }
 public static synchronized void closing(Context c)throws Exception {if(exists(pointer(c))){JSONObject a=read(pointer(c));String id=a.getString("id");if(!valid(id))throw new IOException("回放运行编号无效");File root=new File(base(c),id);if(Files.isSymbolicLink(root.toPath()))throw new IOException("回放运行路径无效");JSONObject manifest=read(new File(root,"manifest.json"));if(!id.equals(manifest.getString("id")))throw new IOException("回放运行记录不一致");manifest.put("creator","").put("state","closing");a.put("creator","").put("state","closing");write(new File(root,"manifest.json"),manifest);write(pointer(c),a);}}
 public static synchronized void inputEvidence(Context c,String operation,JSONObject evidence)throws Exception{File root=current(c);JSONObject manifest=read(new File(root,"manifest.json"));if(!operation.equals(manifest.optString("operation_id")))throw new IOException("回放操作已变化");manifest.put("input_evidence",evidence);write(new File(root,"manifest.json"),manifest);}
 public static File directory(File root,String name){if(!name.matches("[A-Za-z0-9_.-]+")||name.contains(".."))throw new IllegalArgumentException("回放目录名无效");File f=new File(root,name);if(!f.isDirectory()&&!f.mkdirs())throw new IllegalStateException("回放目录不可用");return f;}
 public static String preference(Context c,String name){try{if(!name.matches("[A-Za-z0-9_.-]{1,100}")||name.contains(".."))throw new IOException("回放偏好名无效");return "replay-run-"+current(c).getName()+"-"+name;}catch(Exception e){throw new IllegalStateException(e);}}
}
