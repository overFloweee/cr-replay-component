// SPDX-License-Identifier: Apache-2.0
// Adapted for CR Replay Component from fixed main d815abb34bbb3847ca2fec95df3096059ea7dda0; see docs/provenance.md.
package cn.crhistory.player;

import android.content.Context;
import java.io.*;
import java.util.zip.*;

public final class ReplayBootstrap {
    private ReplayBootstrap() {}
    // 同一国服引擎直接读取原始 ID；范围来自已归档 38.4.10 的 arenas / arenas_alt 表。
    public static int engineArena(int source) throws IOException {
        if((source<54000000||source>=54000163)&&(source<179000000||source>=179000209))throw new IOException("当前回放资源尚未支持此竞技场，请更新回放组件");
        return source;
    }
    public static int engineLocation(int source) throws IOException {
        if(source<15000000||source>=15000222)throw new IOException("当前回放资源尚未支持此场景，请更新回放组件");
        return source;
    }
    public static int engineTower(int source) throws IOException {
        if(source<169000000||source>169000004||source==169000003)throw new IOException("当前回放组件尚未支持此塔兵类型");
        return source;
    }
    private static void validateCard(int source) throws IOException {
        if((source>=26000000&&source<26000108)||(source>=27000000&&source<27000018)||(source>=28000000&&source<28000027))return;
        throw new IOException("当前回放资源尚未支持此卡牌，请更新回放组件");
    }
    public static boolean isReplayProcess(Context context) {
        String name=null;
        if(android.os.Build.VERSION.SDK_INT>=28)name=android.app.Application.getProcessName();
        else {
            android.app.ActivityManager manager=(android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes=manager==null?null:manager.getRunningAppProcesses();
            if(processes!=null)for(android.app.ActivityManager.RunningAppProcessInfo process:processes)if(process.pid==android.os.Process.myPid()){name=process.processName;break;}
        }
        return (context.getPackageName()+":replay").equals(name);
    }
    public static native boolean submit(byte[] replay, String dataDirectory, long viewerHi, long viewerLo);
    // BEGIN GENERATED REPLAY RULES
    private static final String[] RULE_SOURCES={"38.4.10","38.4.11","38.4.3","38.4.5","38.4.6","38.4.8","38.4.9"};
    private static final int[] RULE_CONTENTS={10,11,4,5,6,8,9};
    private static final String[][] RULE_ALIASES={{"38.4.7","7","38.4.8"},{"38.4.7","8","38.4.8"}};
    // END GENERATED REPLAY RULES
    private static boolean registeredContent(int content) {
        for(int value:RULE_CONTENTS)if(value==content)return true;
        return false;
    }
    private static boolean supportedContent(int content) {
        if(registeredContent(content))return true;
        for(String[] alias:RULE_ALIASES)if(Integer.parseInt(alias[1])==content)return true;
        return false;
    }
    public static boolean hasSource(String source) {
        for(String value:RULE_SOURCES)if(value.equals(source))return true;
        for(String[] alias:RULE_ALIASES)if(alias[0].equals(source))return true;
        return source.isEmpty();
    }
    public static boolean hasRules(String version) {for(String value:RULE_SOURCES)if(value.equals(version))return true;return false;}
    public static String contentRules(org.json.JSONObject replay,org.json.JSONObject catalog) throws Exception {
        if(!hasSource(replay.optString("_sourceVersion",""))){org.json.JSONObject row=ReplayCompatibility.find(catalog,replay);if(row!=null)return row.getString("target");}
        return contentRules(replay);
    }
    public static String contentRules(org.json.JSONObject replay) throws IOException {
        String source=replay.optString("_sourceVersion","");
        int content=replay.optInt("contentVersion",5);
        for(String[] alias:RULE_ALIASES)if(source.equals(alias[0])&&content==Integer.parseInt(alias[1]))return alias[2];
        for(String[] alias:RULE_ALIASES)if(source.equals(alias[0])||(content==Integer.parseInt(alias[1])&&!registeredContent(content)))throw new IOException("这份回放的规则版本尚未支持，请更新回放组件");
        for(int i=0;i<RULE_SOURCES.length;i++)if(source.isEmpty()?content==RULE_CONTENTS[i]:source.equals(RULE_SOURCES[i]))return RULE_SOURCES[i];
        throw new IOException("这份回放的规则版本尚未支持，请更新回放组件");
    }
    public static byte[] encode(org.json.JSONObject replay) throws IOException {
        byte[] data=replay.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(data.length>16*1024*1024)throw new IOException("回放超过组件支持的 16 MiB，原始记录保留");
        return data;
    }
    public static void validate(org.json.JSONObject replay) throws Exception {validate(replay,null);}
    public static void validate(org.json.JSONObject replay,org.json.JSONObject catalog) throws Exception {
        validatePayload(replay);contentRules(replay,catalog);
    }
    public static void validatePayload(org.json.JSONObject replay) throws Exception {
        encode(replay);
        if(replay.getInt("endTick")<=0)throw new IOException("回放结束时间无效");
        replay.getInt("rndSeed");
        if(replay.has("_engineVersion")&&!"38.4.2".equals(replay.getString("_engineVersion")))throw new IOException("这份回放的来源版本尚未支持，请更新回放组件");
        if(replay.has("contentVersion")&&!supportedContent(replay.getInt("contentVersion"))&&!(replay.getInt("contentVersion")==10&&"38.4.9".equals(replay.optString("_sourceVersion",""))))throw new IOException("这份回放的数据格式尚未支持，请更新回放组件");
        org.json.JSONObject battle=replay.getJSONObject("battle");
        engineLocation(battle.getInt("location"));
        engineArena(battle.getInt("arena"));
        if(battle.has("hbd")){org.json.JSONArray skins=battle.getJSONArray("hbd");for(int i=0;i<skins.length();i++)if(skins.getJSONObject(i).has("sa"))engineArena(skins.getJSONObject(i).getInt("sa"));}
        if(battle.has("avatar2"))throw new IOException("当前回放组件尚未支持此多人模式");
        String requested=replay.optString("_viewerAccount","");boolean owner=requested.isEmpty();
        for(int side=0;side<2;side++){
            org.json.JSONObject avatar=battle.getJSONObject("avatar"+side);
            long hi=avatar.getLong("accountID.hi"),lo=avatar.getLong("accountID.lo");
            if(hi<0||hi>0xffffffffL||lo<0||lo>0xffffffffL)throw new IOException("回放玩家身份无效");
            if(avatar.has("arena"))engineArena(avatar.getInt("arena"));
            if((avatar.getLong("accountID.hi")+":"+avatar.getLong("accountID.lo")).equals(requested))owner=true;
            org.json.JSONObject deck=battle.getJSONObject("deck"+side);
            org.json.JSONArray cards=deck.getJSONArray("sp");
            for(int i=0;i<cards.length();i++)validateCard(cards.getJSONObject(i).getInt("d"));
            org.json.JSONArray supports=deck.getJSONArray("sc");
            for(int i=0;i<supports.length();i++)engineTower(supports.getJSONObject(i).getInt("d"));
        }
        if(!owner)throw new IOException("观看账号与回放身份不符");
        org.json.JSONArray commands=replay.getJSONArray("cmd");
        for(int i=0;i<commands.length();i++){
            org.json.JSONObject command=commands.getJSONObject(i);int type=command.getInt("ct");
            if(type!=153&&type!=189)throw new IOException("回放指令 "+type+" 尚未适配，请更新回放组件");
            org.json.JSONObject payload=command.getJSONObject("c");
            int tick=payload.getInt("t"),sent=payload.getInt("t2");
            if(tick<0||tick>replay.getInt("endTick")||sent<0)throw new IOException("回放指令时间无效");
            String actor=payload.getLong("idHi")+":"+payload.getLong("idLo");boolean participant=false;
            for(int side=0;side<2;side++){org.json.JSONObject avatar=battle.getJSONObject("avatar"+side);if(actor.equals(avatar.getLong("accountID.hi")+":"+avatar.getLong("accountID.lo")))participant=true;}
            if(!participant)throw new IOException("回放指令的玩家身份不符");
            if(type==153){payload.getInt("px");payload.getInt("py");payload.getInt("sid");org.json.JSONObject selected=payload.getJSONObject("sel");validateCard(selected.getInt("os"));selected.getInt("pd");}
            else payload.getInt("cgid");
        }
    }
    private static Thread.UncaughtExceptionHandler startupGuard, startupPrevious;private static boolean lifecycleInstalled;
    // Application 的库加载早于 Activity；记下失败后仍交回系统终止原进程。
    public static void watchStartup(Context activity) {
        endStartup();
        if(activity instanceof android.app.Application&&isReplayProcess(activity)&&!lifecycleInstalled){lifecycleInstalled=true;((android.app.Application)activity).registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks(){
            public void onActivityDestroyed(android.app.Activity a){if(a.getClass().getName().equals("com.supercell.clashroyale.GameApp")&&!a.isChangingConfigurations())finishProcess(a);}
            public void onActivityCreated(android.app.Activity a,android.os.Bundle b){}public void onActivityStarted(android.app.Activity a){}public void onActivityResumed(android.app.Activity a){}public void onActivityPaused(android.app.Activity a){}public void onActivityStopped(android.app.Activity a){}public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle b){}
        });}
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();startupPrevious=previous;
        startupGuard=(thread,error)->{
            try{if(startupGuard!=null&&thread==android.os.Looper.getMainLooper().getThread())recordFailure(activity,"engine_start_failed","startup",error);}
            finally{endStartup();if(previous!=null)previous.uncaughtException(thread,error);else thread.getThreadGroup().uncaughtException(thread,error);}
        };
        Thread.setDefaultUncaughtExceptionHandler(startupGuard);
    }
    private static void endStartup() {
        if(startupGuard!=null&&Thread.getDefaultUncaughtExceptionHandler()==startupGuard)Thread.setDefaultUncaughtExceptionHandler(startupPrevious);
        startupGuard=null;startupPrevious=null;
    }
    public static void begin(android.app.Activity activity) {
        try {
            java.util.Locale.setDefault(java.util.Locale.SIMPLIFIED_CHINESE);
            byte[] bytes=java.nio.file.Files.readAllBytes(new File(activity.getFilesDir(),"replay.json").toPath());
            org.json.JSONObject replay=new org.json.JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
            File catalogFile=new File(activity.getFilesDir(),"replay-catalog.json");
            org.json.JSONObject catalog=catalogFile.isFile()?new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(catalogFile.toPath()),java.nio.charset.StandardCharsets.UTF_8)):null;
            validate(replay,catalog);
            org.json.JSONObject battle=replay.getJSONObject("battle");
            org.json.JSONObject viewer=battle.getJSONObject("avatar1");
            String requested=replay.optString("_viewerAccount","");
            if(!requested.isEmpty()) {
                viewer=null;
                for(int i=0;i<2;i++){org.json.JSONObject candidate=battle.getJSONObject("avatar"+i);if((candidate.getLong("accountID.hi")+":"+candidate.getLong("accountID.lo")).equals(requested))viewer=candidate;}
                if(viewer==null)throw new IOException("观看账号与回放不符");
            }
            // 接入元数据已经校验，不交给游戏引擎解析。
            replay.remove("_replaySha256");replay.remove("_viewerAccount");replay.remove("_engineVersion");replay.remove("_sourceVersion");
            byte[] input=encode(replay);
            if(!submit(input,activity.getFilesDir().getParent(),viewer.getLong("accountID.hi"),viewer.getLong("accountID.lo")))throw new IOException("播放引擎未就绪");
            watch(activity);
            android.util.Log.i("CRReplay","queued_input_bytes="+input.length);
        }catch(Exception|LinkageError error){recordFailure(activity,"engine_start_failed","begin",error);new android.app.AlertDialog.Builder(activity).setTitle("回放未启动").setMessage(error.getMessage()).setPositiveButton("返回",(d,w)->close(activity)).show();}finally{endStartup();}
    }
    static synchronized void recordFailure(Context activity,String code,String stage,Throwable failure){try{android.util.AtomicFile file=new android.util.AtomicFile(new File(activity.getFilesDir(),"playback-failure.json"));if(file.getBaseFile().exists()||new File(file.getBaseFile()+".bak").exists())return;org.json.JSONObject value=new org.json.JSONObject().put("exception_type",failure.getClass().getName());value.put("error_code",code).put("last_failure_stage",stage).put("time_ms",System.currentTimeMillis());FileOutputStream out=null;try{out=file.startWrite();out.write(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}}catch(Exception ignored){android.util.Log.w("CRReplay","failure state unavailable");}}
    private static native int presentedFrames();
    private static native long firstFrameElapsed();
    private static native boolean hasFinished();
    private static native boolean shouldExit();
    private static native String result();
    private static void watch(android.app.Activity activity){
        android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(new Runnable(){boolean saved,frameSaved;public void run(){if(activity.isFinishing()||activity.isDestroyed())return;try{if(shouldExit()){userExit(activity,"in_game_exit");return;}if(!frameSaved){int frames=presentedFrames();if(frames!=0){File file=new File(activity.getFilesDir(),"playback-telemetry.json");android.util.AtomicFile atomic=new android.util.AtomicFile(file);FileOutputStream out=null;try{out=atomic.startWrite();out.write(new org.json.JSONObject().put("native_frame_count",Math.max(0,frames)).put("first_frame_elapsed_ms",firstFrameElapsed()).put("probe_available",frames>=0).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(Exception e){if(out!=null)atomic.failWrite(out);throw e;}frameSaved=true;}}String outcome=saved?null:result();if(outcome!=null){File file=new File(activity.getFilesDir(),"playback-result.json"),tmp=new File(activity.getFilesDir(),"playback-result.json.tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(outcome.getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}if(!tmp.renameTo(file))throw new IOException("回放结果保存未完成");saved=true;}}catch(Exception|LinkageError e){recordFailure(activity,"result_write_failed","playback_watch",e);android.util.Log.w("CRReplay","completion state unavailable");}handler.postDelayed(this,500);}},500);
    }
    // 结果页的原生按钮会返回在线大厅；离线组件在结果页接管触摸并退出。
    public static boolean onResultTouch(android.app.Activity activity, android.view.MotionEvent event) {
        if (!hasFinished()) return false;
        if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP) close(activity);
        return true;
    }
    // 仅明确的游戏内退出或系统返回记主动退出；统一清理入口不推断用户意图。
    public static void userExit(android.app.Activity activity) {userExit(activity,"system_back_exit");}
    private static void userExit(android.app.Activity activity,String reason) {
        try{if(!hasFinished())recordExit(activity,reason);}catch(Exception|LinkageError ignored){android.util.Log.w("CRReplay","exit state unavailable");}
        close(activity);
    }
    static void recordExit(Context context,String reason)throws Exception {
        if(!reason.equals("in_game_exit")&&!reason.equals("system_back_exit"))throw new IllegalArgumentException("退出原因无效");
        android.util.AtomicFile file=new android.util.AtomicFile(new File(context.getFilesDir(),"playback-exit.json"));FileOutputStream out=null;
        try{out=file.startWrite();out.write(new org.json.JSONObject().put("finish_reason",reason).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }
    public static void close(android.app.Activity activity) {
        finishProcess(activity);
    }
    private static boolean ending;
    static void finishProcess(android.app.Activity activity){
        if(ending)return;ending=true;
        // 结果页返回可能早于轮询；关闭前保存同一引擎生成的完成结果。
        try{String outcome=activity.getClass().getName().equals("com.supercell.clashroyale.GameApp")?result():null;if(outcome!=null){android.util.AtomicFile file=new android.util.AtomicFile(new File(activity.getFilesDir(),"playback-result.json"));FileOutputStream out=null;try{out=file.startWrite();out.write(outcome.getBytes(java.nio.charset.StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}}}catch(Exception|LinkageError e){recordFailure(activity,"result_write_failed","result_write",e);}
        try{ReplayRun.closing(activity);}catch(Exception e){android.util.Log.w("CRReplay","run cleanup pending");}
        activity.setResult(android.app.Activity.RESULT_OK);activity.finish();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 300);
    }
    public static synchronized void prepare(Context context) throws IOException {
        try{ExternalAssets.attach(context);}catch(Exception e){throw new IOException("回放资源加载失败",e);}
    }
}
