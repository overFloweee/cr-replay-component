package cn.crhistory.player;

import java.io.IOException;
import org.json.*;

/** 清单仅补充明确的来源与格式组合，不能扩大引擎的协议能力。 */
public final class ReplayCompatibility {
    private ReplayCompatibility() {}
    public static void validate(JSONObject catalog, String engine) throws Exception {
        if(catalog.getInt("schema")!=1||!catalog.getString("engine").equals(engine))throw new IOException("回放规则与当前引擎不匹配");
        JSONArray rows=catalog.getJSONArray("entries");
        if(rows.length()>256)throw new IOException("回放兼容登记过多");
        java.util.Set<String> seen=new java.util.HashSet<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String source=row.getString("source"),target=row.getString("target");int content=row.getInt("content");
            if(!source.matches("38\\.4\\.[0-9]+")||!target.matches("38\\.4\\.[0-9]+")||content<4||content>10||!seen.add(source+":"+content))throw new IOException("回放兼容登记无效");
            if(!row.getString("fingerprint").matches("[a-f0-9]{40}")||!row.getString("inventorySha256").matches("[a-f0-9]{64}"))throw new IOException("回放规则证据缺失");
            JSONObject bundle=row.optJSONObject("bundle");
            if(bundle!=null&&(!bundle.getString("sha256").matches("[a-f0-9]{64}")||bundle.getLong("bytes")<=0||bundle.getLong("bytes")>16*1024*1024))throw new IOException("回放规则包登记无效");
        }
    }
    public static JSONObject find(JSONObject catalog, JSONObject replay) throws Exception {
        if(catalog==null)return null;
        String source=replay.optString("_sourceVersion","");int content=replay.optInt("contentVersion",5);
        JSONArray rows=catalog.getJSONArray("entries");
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(source.equals(row.getString("source"))&&content==row.getInt("content"))return row;}
        return null;
    }
}
