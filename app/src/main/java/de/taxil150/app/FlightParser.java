package de.taxil150.app;
import org.json.*;
import java.time.*;
import java.util.*;

/** Field mapping follows the public arrival list used on the BER website. */
public final class FlightParser {
 public static final ZoneId BERLIN=ZoneId.of("Europe/Berlin");
 public static final class Page {public final List<JSONObject> items=new ArrayList<>();public int page,totalPages,totalItems,rawCount;public String sourceAt="";}
 public static String text(JSONObject o,String key){if(o.isNull(key))return "";String s=o.optString(key,"").trim();return s.length()>200?s.substring(0,200):s;}
 public static long epoch(String value){if(value==null||value.isEmpty())return 0;try{return OffsetDateTime.parse(value).toInstant().toEpochMilli();}catch(Exception e){try{return LocalDateTime.parse(value).atZone(BERLIN).toInstant().toEpochMilli();}catch(Exception ignored){return 0;}}}
 public static Page parse(String response)throws JSONException {
  JSONObject root=new JSONObject(response),data=root.getJSONObject("data");JSONArray rows=data.getJSONArray("items");
  Page result=new Page();result.page=data.getInt("page");result.totalPages=data.getInt("total_pages");result.totalItems=data.getInt("total_items");result.rawCount=rows.length();result.sourceAt=text(data,"updated_at");
  if(result.page<1||result.totalPages<0||result.totalItems<0||(result.totalItems>0&&result.totalPages<1))throw new JSONException("Unvollständige Seitenangaben");
  for(int i=0;i<rows.length();i++){
   JSONObject raw=rows.getJSONObject(i);if(raw.optBoolean("is_departure",false))continue;
   String planned=text(raw,"scheduled_time");if(planned.isEmpty())planned=text(raw,"arr_scheduled_time");long p=epoch(planned);if(p==0)throw new JSONException("Ungültige geplante Ankunft");
   String expected=text(raw,"arr_estimated_time");long e=epoch(expected);if(!expected.isEmpty()&&e==0)throw new JSONException("Ungültige erwartete Ankunft");
   JSONObject f=new JSONObject();String number=text(raw,"flight_number");String id=text(raw,"id");
   f.put("id",id.isEmpty()?number+"_"+p:id).put("number",number).put("origin",text(raw,"dep_airport_name")).put("iata",text(raw,"dep_airport_iata")).put("airline",text(raw,"airline_name"));
   f.put("terminal",text(raw,"terminal")).put("arrival",text(raw,"arr_exit")).put("belt",text(raw,"arr_belt")).put("status",text(raw,"flight_status_label")).put("statusId",text(raw,"flight_status_id"));
   String color=text(raw,"flight_status_color");f.put("color",color.matches("#[0-9a-fA-F]{6}")?color:"#afbed3");
   f.put("updated",epoch(text(raw,"updated_at")));f.put("planned",p).put("expected",e).put("sort",e>0?e:p);if(e>0)f.put("delay",e/60000-p/60000);
   result.items.add(f);
  }
  return result;
 }
 public static void merge(Map<String,JSONObject> target,Collection<JSONObject> rows)throws JSONException {for(JSONObject row:rows){String id=row.getString("id");JSONObject old=target.get(id);if(old==null||row.optLong("updated")>old.optLong("updated")||(row.optLong("updated")==old.optLong("updated")&&row.optLong("expected")>0&&old.optLong("expected")==0))target.put(id,row);}}
 public static JSONArray sorted(Collection<JSONObject> flights){List<JSONObject> list=new ArrayList<>(flights);Collections.sort(list,(a,b)->Long.compare(a.optLong("sort"),b.optLong("sort")));return new JSONArray(list);}
 private FlightParser(){}
}
