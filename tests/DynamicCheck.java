import cn.crhistory.player.*;
import org.json.*;

public final class DynamicCheck {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static JSONObject catalog()throws Exception{return new JSONObject().put("schema",1).put("engine","test-engine").put("entries",new JSONArray().put(new JSONObject().put("source","38.4."+CompatibilityCheck.unknownNumber()).put("content",10).put("target","38.4.9").put("fingerprint","a".repeat(40)).put("inventorySha256","b".repeat(64))));}
 static void bad(JSONObject value)throws Exception{try{ReplayCompatibility.validate(value,"test-engine");throw new AssertionError("非法清单被接受");}catch(java.io.IOException expected){}}
 public static void main(String[] args)throws Exception{
  JSONObject catalog=catalog();ReplayCompatibility.validate(catalog,"test-engine");
  JSONObject replay=CompatibilityCheck.fixture().put("_sourceVersion","38.4."+CompatibilityCheck.unknownNumber()).put("contentVersion",10);String original=replay.toString();
  ReplayBootstrap.validate(replay,catalog);check(ReplayBootstrap.contentRules(replay,catalog).equals("38.4.9"));check(original.equals(replay.toString()));
  check(ReplayCompatibility.find(catalog,replay)!=null);replay.put("contentVersion",9);check(ReplayCompatibility.find(catalog,replay)==null);CompatibilityCheck.rejects(replay);
  replay.put("contentVersion",CompatibilityCheck.unknownNumber());try{ReplayBootstrap.validate(replay,catalog);throw new AssertionError("新格式被放行");}catch(java.io.IOException expected){}
  replay.put("contentVersion",10).put("_engineVersion","39.0.0");try{ReplayBootstrap.validate(replay,catalog);throw new AssertionError("新引擎被放行");}catch(java.io.IOException expected){}
  replay=CompatibilityCheck.fixture().put("_sourceVersion","38.4."+CompatibilityCheck.unknownNumber()).put("contentVersion",10).put("cmd",new JSONArray().put(new JSONObject().put("ct",999)));
  try{ReplayBootstrap.validate(replay,catalog);throw new AssertionError("新指令被放行");}catch(java.io.IOException expected){}
  for(String version:new String[]{"38.4.3","38.4.5","38.4.6","38.4.8","38.4.9","38.4.10"})check(ReplayBootstrap.hasRules(version));
  replay=CompatibilityCheck.fixture().put("_sourceVersion","38.4.8").put("contentVersion",8);check(ReplayBootstrap.contentRules(replay,catalog).equals("38.4.8"));
  bad(catalog().put("schema",2));bad(catalog().put("engine","other"));
  JSONObject duplicate=catalog();duplicate.getJSONArray("entries").put(duplicate.getJSONArray("entries").getJSONObject(0));bad(duplicate);
  for(String field:new String[]{"fingerprint","inventorySha256","source","target"}){JSONObject invalid=catalog();invalid.getJSONArray("entries").getJSONObject(0).put(field,"invalid");bad(invalid);}
  JSONObject future=catalog();future.getJSONArray("entries").getJSONObject(0).put("content",CompatibilityCheck.unknownNumber());bad(future);
  JSONObject large=catalog();large.getJSONArray("entries").getJSONObject(0).put("bundle",new JSONObject().put("sha256","c".repeat(64)).put("bytes",16777217));bad(large);
  System.out.println("PASS: dynamic exact pairs, preserved old rules, format/engine/command guards and malformed catalogs");
 }
}
