// SPDX-License-Identifier: Apache-2.0
// Adapted for CR Replay Component from fixed main d815abb34bbb3847ca2fec95df3096059ea7dda0; see docs/provenance.md.
import cn.crhistory.player.ReplayBootstrap;
import java.nio.file.*;import org.json.*;
/** 同包实际入口的兼容边界；真实样本仅从本地验收目录读取。 */
public final class CompatibilityCheck {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void rejects(int id,boolean tower)throws Exception{try{if(tower)ReplayBootstrap.engineTower(id);else ReplayBootstrap.engineLocation(id);throw new AssertionError("未知定义必须在 native 前拒绝");}catch(java.io.IOException expected){}}
 static void rejects(JSONObject replay)throws Exception{try{ReplayBootstrap.validate(replay);throw new AssertionError("非法回放必须在 native 前拒绝");}catch(java.io.IOException expected){}}
 static JSONObject fixture()throws Exception{
  JSONObject replay=new JSONObject("{\"endTick\":1,\"rndSeed\":1,\"_engineVersion\":\"38.4.2\",\"cmd\":[],\"battle\":{\"location\":15000220,\"arena\":54000161}}");
  JSONObject battle=replay.getJSONObject("battle");
  for(int side=0;side<2;side++){
   battle.put("avatar"+side,new JSONObject().put("accountID.hi",27).put("accountID.lo",side+1));
   battle.put("deck"+side,new JSONObject().put("sc",new JSONArray().put(new JSONObject().put("d",169000004))).put("sp",new JSONArray().put(new JSONObject().put("d",26000085).put("el",1))));
  }
  return replay;
 }
 static int unknownNumber()throws Exception{
  JSONObject registry=new JSONObject(Files.readString(Path.of("metadata/rules_registry.json")));
  JSONArray aliases=new JSONArray(Files.readString(Path.of("metadata/rules_aliases.json")));
  int maximum=5;java.util.Iterator<String> keys=registry.keys();while(keys.hasNext())maximum=Math.max(maximum,Integer.parseInt(keys.next().substring(5)));
  for(int i=0;i<aliases.length();i++){JSONObject alias=aliases.getJSONObject(i);maximum=Math.max(maximum,Math.max(alias.getInt("content"),Integer.parseInt(alias.getString("source").substring(5))));}
  return maximum+1;
 }
 public static void main(String[] args)throws Exception{
  check(ReplayBootstrap.engineLocation(15000013)==15000013,"已有冠军竞技场不得改变");
  check(ReplayBootstrap.engineLocation(15000220)==15000220,"惊魂十月保留原始 ID");
  check(ReplayBootstrap.engineLocation(15000171)==15000171,"同引擎场景不得改写");
  check(ReplayBootstrap.engineArena(54000036)==54000036,"已有传奇竞技场");
  check(ReplayBootstrap.engineArena(54000161)==54000161,"新赛季竞技场保留原始 ID");
  for(int id:new int[]{54000000,54000162,179000000,179000205,179000206,179000208})check(ReplayBootstrap.engineArena(id)==id,"已归档竞技场不得改写 ID");
  for(int id:new int[]{0,53999999,54000163,178999999,179000209}){JSONObject invalid=fixture();invalid.getJSONObject("battle").put("arena",id);rejects(invalid);}
  sampleArenas();
  for(int i:new int[]{0,1,2,4})check(ReplayBootstrap.engineTower(169000000+i)==169000000+i,"塔兵原始 ID");
  for(int i:new int[]{169000003,169000005,159000000})rejects(i,true);
  for(int i:new int[]{15000222,15000300,0})rejects(i,false);
  JSONObject sample=fixture();String unchanged=sample.toString();ReplayBootstrap.validate(sample);check(sample.toString().equals(unchanged),"进化卡牌和新塔兵不得改写");
  sample=fixture();sample.put("_engineVersion","39.0.0");rejects(sample);
  sample=fixture();sample.put("_sourceVersion","38.4.7");rejects(sample);
  sample=fixture();sample.put("contentVersion",7);rejects(sample);
  sample=fixture();sample.put("contentVersion",7).put("_sourceVersion","38.4.7");ReplayBootstrap.validate(sample);check(ReplayBootstrap.contentRules(sample).equals("38.4.8"),"仅明确的 38.4.7 来源可临时使用已归档规则");
  sample.put("_sourceVersion","38.4.8");rejects(sample);
  sample.put("_sourceVersion","38.4.7").put("contentVersion",8);String aliasOriginal=sample.toString();ReplayBootstrap.validate(sample);check(ReplayBootstrap.contentRules(sample).equals("38.4.8"),"真实 38.4.7 / 格式8选择已归档规则");check(sample.toString().equals(aliasOriginal),"临时映射不得改写正文");
  check(ReplayBootstrap.contentRules(sample,new JSONObject().put("schema",1).put("entries",new JSONArray())).equals("38.4.8"),"已知来源无需远程清单即可修复");
  JSONObject unsupported=new JSONObject(sample.toString());unsupported.put("cmd",new JSONArray().put(new JSONObject().put("ct",163)));rejects(unsupported);
  sample.put("contentVersion",9);rejects(sample);
  sample.put("_sourceVersion","38.4.8").put("contentVersion",8);ReplayBootstrap.validate(sample);check(ReplayBootstrap.contentRules(sample).equals("38.4.8"),"格式8的精确来源不被新增映射遮蔽");
  sample.remove("_sourceVersion");ReplayBootstrap.validate(sample);check(ReplayBootstrap.contentRules(sample).equals("38.4.8"),"无来源格式8继续按精确规则回退");
  sample=fixture();sample.put("contentVersion",9).put("_sourceVersion","38.4.9");ReplayBootstrap.validate(sample);check(ReplayBootstrap.contentRules(sample).equals("38.4.9"),"38.4.9 必须选择精确规则");
  sample.put("contentVersion",10);ReplayBootstrap.validate(sample);check(ReplayBootstrap.contentRules(sample).equals("38.4.9"),"真实格式10必须绑定来源38.4.9");
  sample.put("_sourceVersion","38.4.10");ReplayBootstrap.validate(sample);check(ReplayBootstrap.contentRules(sample).equals("38.4.10"),"38.4.10 必须选择精确规则");
  sample.put("_sourceVersion","38.4."+unknownNumber());rejects(sample);
  sample=fixture();sample.put("contentVersion",unknownNumber());rejects(sample);
  sample=fixture();sample.put("contentVersion",6);ReplayBootstrap.validate(sample);check(ReplayBootstrap.contentRules(sample).equals("38.4.6"),"格式6必须选择真实新规则");
  sample.put("_sourceVersion","38.4.6");ReplayBootstrap.validate(sample);check(ReplayBootstrap.contentRules(sample).equals("38.4.6"),"明确的新来源选择真实规则");
  sample=fixture();sample.put("contentVersion",4);check(ReplayBootstrap.contentRules(sample).equals("38.4.3"),"历史回放必须选择原始规则");
  sample=fixture();sample.put("contentVersion",5);check(ReplayBootstrap.contentRules(sample).equals("38.4.5"),"当前回放必须选择最新规则");
  sample.put("_sourceVersion","38.4.3");check(ReplayBootstrap.contentRules(sample).equals("38.4.3"),"明确来源优先");
  sample=fixture();sample.put("_sourceVersion","38.4.4");rejects(sample);
  sample=fixture();sample.put("_viewerAccount","27:999");rejects(sample);
  sample=fixture();sample.getJSONObject("battle").getJSONObject("avatar0").put("accountID.hi",0x100000000L);rejects(sample);
  sample=fixture();sample.getJSONObject("battle").getJSONObject("deck0").getJSONArray("sp").getJSONObject(0).put("d",26000108);rejects(sample);
  sample=fixture();sample.put("cmd",new JSONArray().put(new JSONObject().put("ct",999)));rejects(sample);
  System.out.println("PASS: 新场景/竞技场/塔兵及进化卡牌保留，未知定义/版本/身份/指令拒绝");
  if(args.length==0)return;
  JSONArray cases=new JSONArray(Files.readString(Path.of(args[0])));int accepted=0;
  for(int i=0;i<cases.length();i++){
   JSONObject row=cases.getJSONObject(i);if(!row.has("file"))continue;
   JSONObject replay=new JSONObject(Files.readString(Path.of(args[0]).getParent().resolve(row.getString("file"))));String original=replay.toString();
   ReplayBootstrap.validate(replay);accepted++;
   check(replay.toString().equals(original),"验证不得改写原始回放");
  }
  check(accepted==74,"全量分母缺失");System.out.println("PASS: 场景/塔兵原始 ID、未知定义拒绝、原件保留；74份正文，放行="+accepted);
 }
 static void sampleArenas()throws Exception{
  JSONObject replay=fixture();JSONObject battle=replay.getJSONObject("battle");
  battle.put("arena",179000205).getJSONObject("avatar0").put("arena",179000206);
  battle.put("hbd",new JSONArray().put(new JSONObject().put("sa",179000208)));
  String original=replay.toString();ReplayBootstrap.validate(replay);check(replay.toString().equals(original),"战场、头像和皮肤共用竞技场边界并保留原件");
  battle.getJSONObject("avatar0").put("arena",179000209);rejects(replay);
  battle.getJSONObject("avatar0").put("arena",179000206);battle.getJSONArray("hbd").getJSONObject(0).put("sa",179000209);rejects(replay);
 }
}
