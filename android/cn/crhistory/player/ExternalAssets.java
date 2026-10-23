// SPDX-License-Identifier: Apache-2.0
// Adapted for CR Replay Component from fixed main d815abb34bbb3847ca2fec95df3096059ea7dda0; see docs/provenance.md.
package cn.crhistory.player;
import android.content.*;import android.content.pm.ApplicationInfo;import android.content.res.loader.*;import android.os.*;import java.io.*;
public final class ExternalAssets {
 private static Object loader;
 private ExternalAssets(){}
 public static String archivePath(Context c)throws Exception{return LocalResources.readyArchive(c).getAbsolutePath();}
 public static synchronized void selectContent(Context c,String version,org.json.JSONObject row)throws Exception{
  if(!ReplayBootstrap.hasRules(version)){
   throw new IOException("Only bundled registered rules are supported");
  }
  selectContent(c,version);
 }
 public static synchronized void selectContent(Context c,String version)throws Exception{
  try(java.util.zip.ZipFile apk=new java.util.zip.ZipFile(c.getApplicationInfo().sourceDir)){
   java.util.zip.ZipEntry bundle=apk.getEntry("assets/crhistory/replay-rules.zip");
   if(bundle==null)throw new IOException("安装包回放规则缺失，请更新或重新安装应用");
   File update=new File(ReplayRun.current(c),"update");
   try(InputStream rules=apk.getInputStream(bundle)){ReplayRules.install(rules,update,version);}
   if(version.equals("38.4.11")){java.util.zip.ZipEntry visuals=apk.getEntry("assets/crhistory/replay-visuals.zip");if(visuals==null)throw new IOException("安装包回放动画缺失，请重新安装应用");try(InputStream input=apk.getInputStream(visuals)){ReplayRules.installVisuals(input,update,version);}}
  }
 }

 public static File files(Context c){return ReplayRun.directory(ReplayRun.current(c),"files");}
 public static Context applicationContext(Context c){android.app.ActivityManager m=c.getSystemService(android.app.ActivityManager.class);java.util.List<android.app.ActivityManager.RunningAppProcessInfo> ps=m==null?null:m.getRunningAppProcesses();if(ps==null)throw new IllegalStateException("应用进程状态未知");for(android.app.ActivityManager.RunningAppProcessInfo p:ps)if(p.pid==android.os.Process.myPid())return p.processName.equals(c.getPackageName()+":replay")?engineContext(c):c;throw new IllegalStateException("应用进程归属未知");}
 public static Context engineContext(Context c){return c instanceof EngineContext?c:new EngineContext(c);}
 private static final class EngineContext extends ContextWrapper {
  final File runtime;EngineContext(Context c){super(c);runtime=ReplayRun.current(c);}
  public Context getApplicationContext(){return this;}
  public ApplicationInfo getApplicationInfo(){ApplicationInfo a=new ApplicationInfo(super.getApplicationInfo());a.dataDir=runtime.getAbsolutePath();a.deviceProtectedDataDir=a.dataDir;return a;}
  public File getDataDir(){return runtime;}
  public File getFilesDir(){return ReplayRun.directory(runtime,"files");}
  public File getCacheDir(){return ReplayRun.directory(runtime,"cache");}
  public File getCodeCacheDir(){return ReplayRun.directory(runtime,"code-cache");}
  public File getNoBackupFilesDir(){return ReplayRun.directory(runtime,"no-backup");}
  public File getDir(String n,int mode){return ReplayRun.directory(runtime,"app_"+n);}
  private File child(File folder,String n){if(!n.matches("[A-Za-z0-9_.-]{1,200}")||n.contains(".."))throw new IllegalArgumentException("回放文件名无效");return new File(folder,n);}
  public File getFileStreamPath(String n){return child(getFilesDir(),n);}
  public FileInputStream openFileInput(String n)throws FileNotFoundException{return new FileInputStream(getFileStreamPath(n));}
  public FileOutputStream openFileOutput(String n,int mode)throws FileNotFoundException{if(mode!=MODE_PRIVATE&&mode!=MODE_APPEND)throw new IllegalArgumentException("仅允许私有回放文件");return new FileOutputStream(getFileStreamPath(n),mode==MODE_APPEND);}
  public boolean deleteFile(String n){return getFileStreamPath(n).delete();}
  public String[] fileList(){String[] names=getFilesDir().list();return names==null?new String[0]:names;}
  public File getDatabasePath(String n){return child(ReplayRun.directory(runtime,"databases"),n);}
  public android.database.sqlite.SQLiteDatabase openOrCreateDatabase(String n,int mode,android.database.sqlite.SQLiteDatabase.CursorFactory factory){return openOrCreateDatabase(n,mode,factory,null);}
  public android.database.sqlite.SQLiteDatabase openOrCreateDatabase(String n,int mode,android.database.sqlite.SQLiteDatabase.CursorFactory factory,android.database.DatabaseErrorHandler error){if(mode!=MODE_PRIVATE)throw new IllegalArgumentException("仅允许私有回放数据库");return android.database.sqlite.SQLiteDatabase.openDatabase(getDatabasePath(n).getPath(),factory,android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY,error);}
  public boolean deleteDatabase(String n){return android.database.sqlite.SQLiteDatabase.deleteDatabase(getDatabasePath(n));}
  public String[] databaseList(){String[] names=ReplayRun.directory(runtime,"databases").list();return names==null?new String[0]:names;}
  public File getExternalFilesDir(String type){return ReplayRun.directory(runtime,type==null?"external-files":"external-files-"+type);}
  public File[] getExternalFilesDirs(String type){return new File[]{getExternalFilesDir(type)};}
  public File getExternalCacheDir(){return ReplayRun.directory(runtime,"external-cache");}
  public File[] getExternalCacheDirs(){return new File[]{getExternalCacheDir()};}
  public File[] getExternalMediaDirs(){return new File[]{ReplayRun.directory(runtime,"external-media")};}
  public SharedPreferences getSharedPreferences(String n,int mode){if(mode!=MODE_PRIVATE)throw new IllegalArgumentException("仅允许私有回放偏好");return super.getSharedPreferences(ReplayRun.preference(this,n),mode);}
 }
 public static synchronized void attach(Context c)throws Exception{if(Build.VERSION.SDK_INT>=30){Api30.attach(c);return;}java.lang.reflect.Method m=android.content.res.AssetManager.class.getDeclaredMethod("addAssetPath",String.class);m.setAccessible(true);if((Integer)m.invoke(c.getAssets(),archivePath(c))==0)throw new IOException("此系统暂不能加载回放资源");}
 private static final class Api30 {static void attach(Context c)throws Exception{if(loader==null){ResourcesLoader l=new ResourcesLoader();try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(LocalResources.readyArchive(c),ParcelFileDescriptor.MODE_READ_ONLY)){l.addProvider(ResourcesProvider.loadFromApk(fd));}loader=l;}c.getResources().addLoaders((ResourcesLoader)loader);if(c.getResources()!=c.getApplicationContext().getResources())c.getApplicationContext().getResources().addLoaders((ResourcesLoader)loader);}}
}
