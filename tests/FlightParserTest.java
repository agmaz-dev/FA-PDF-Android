import de.taxil150.app.FlightParser;import org.json.*;import java.util.*;import java.nio.file.*;
public class FlightParserTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static JSONObject flight(String id,String planned,String expected,String terminal)throws Exception{return new JSONObject().put("id",id).put("is_departure",false).put("scheduled_time",planned).put("arr_estimated_time",expected==null?JSONObject.NULL:expected).put("terminal",terminal).put("flight_number","AB 123").put("dep_airport_name","Korfu").put("dep_airport_iata","CFU").put("airline_name","Test").put("arr_exit","2").put("arr_belt","A2").put("flight_status_label","Anflug").put("flight_status_color","#48C75E");}
 static String page(JSONArray items)throws Exception{return new JSONObject().put("data",new JSONObject().put("items",items).put("page",1).put("total_pages",1).put("total_items",items.length())).toString();}
 public static void main(String[]args)throws Exception{
  JSONObject a=flight("1","2026-10-19T22:30:00+02:00","2026-10-19T23:01:49+02:00","T1");
  JSONObject b=flight("2","2026-10-19T23:50:00+02:00","2026-10-20T00:15:00+02:00","T2");
  JSONObject c=flight("3","2026-10-19T21:00:00+02:00",null,"T1");
  FlightParser.Page p=FlightParser.parse(page(new JSONArray().put(a).put(b).put(c)));
  check(p.items.size()==3);check(p.items.get(0).getLong("delay")==31);check(p.items.get(1).getLong("delay")==25);check(!p.items.get(2).has("delay"));check(p.items.get(2).getLong("expected")==0);
  check(p.items.get(0).getString("arrival").equals("2"));check(p.items.get(0).getString("belt").equals("A2"));check(p.items.get(1).getString("terminal").equals("T2"));
  check(FlightParser.sorted(p.items).getJSONObject(0).getString("id").equals("3"));
  check(FlightParser.epoch("2026-10-19T23:00:00+02:00")==FlightParser.epoch("2026-10-19T21:00:00Z"));
  a.put("flight_status_color","url(evil)");check(FlightParser.parse(page(new JSONArray().put(a))).items.get(0).getString("color").equals("#afbed3"));
  a.put("is_departure",true);check(FlightParser.parse(page(new JSONArray().put(a))).items.isEmpty());
  check(FlightParser.parse(page(new JSONArray())).items.isEmpty());
  for(String bad:new String[]{"{}","<html>403</html>","{\"data\":{\"items\":[]}}"}){boolean rejected=false;try{FlightParser.parse(bad);}catch(Exception e){rejected=true;}check(rejected);}
  Map<String,JSONObject> merged=new LinkedHashMap<>();JSONObject newest=p.items.get(0);newest.put("updated",100);JSONObject stale=new JSONObject(newest.toString()).put("updated",1).put("expected",0).put("status","Planmäßig");FlightParser.merge(merged,Arrays.asList(newest,stale));check(merged.size()==1&&merged.get("1").getLong("expected")>0);
  System.out.println("PASS: BER-Feldzuordnung, 31/25 Min. Verspätung, Mitternacht, Zeitzone, fehlende Prognose, T1/T2, Ankunft/Gepäck, Sortierung, leere und ungültige Daten");
  if(args.length>0){FlightParser.Page live=FlightParser.parse(Files.readString(Path.of(args[0])));check(live.items.size()>0);Map<String,JSONObject> unique=new LinkedHashMap<>();FlightParser.merge(unique,live.items);JSONArray sorted=FlightParser.sorted(unique.values());Files.writeString(Path.of(args[1]),new JSONObject().put("date","2026-10-19").put("items",sorted).put("sourceAt",live.sourceAt).put("fetchedAt",System.currentTimeMillis()).toString());System.out.println("LIVE-DATEN: "+live.items.size()+" Datensätze ("+unique.size()+" eindeutige Flüge) von Seite "+live.page+"/"+live.totalPages+" erfolgreich geparst.");}
 }
}
