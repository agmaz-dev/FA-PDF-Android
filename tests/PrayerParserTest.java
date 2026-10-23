import de.taxil150.app.PrayerParser;import org.json.*;import java.nio.file.*;import java.time.*;
public class PrayerParserTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 static long at(String time){return ZonedDateTime.parse(time+"[Europe/Berlin]").toInstant().toEpochMilli();}
 public static void main(String[]args)throws Exception{
  JSONObject snapshot=new JSONObject(Files.readString(Path.of("app/src/main/assets/prayer_berlin.json")));
  JSONObject state=PrayerParser.state(snapshot,"",at("2026-10-20T00:30:00+02:00"));check(state.getInt("nextIndex")==0&&state.getJSONObject("today").getJSONArray("times").getString(0).equals("05:46"));
  check(PrayerParser.state(snapshot,"",at("2026-10-20T05:46:00+02:00")).getInt("nextIndex")==2); // sunrise is not a prayer
  state=PrayerParser.state(snapshot,"",at("2026-10-20T20:00:00+02:00"));check(state.getInt("nextIndex")==0&&state.getString("nextDate").equals("2026-10-21"));
  check(state.getLong("nextAt")==at("2026-10-21T05:48:00+02:00"));
  state=PrayerParser.state(snapshot,"",at("2026-10-25T02:30:00+02:00"));check(state.getLong("nextAt")==at("2026-10-25T04:55:00+01:00")); // DST fallback
  state=PrayerParser.state(snapshot,"offline",at("2026-12-20T00:30:00+01:00"));check(!state.has("today")&&!state.has("nextAt"));
  for(String bad:new String[]{"<html>Error</html>","Berl 11002 <tr><td>broken</td></tr>"}){boolean failed=false;try{PrayerParser.parse(bad);}catch(Exception e){failed=true;}check(failed);}
  if(args.length>0){JSONObject parsed=PrayerParser.parse(Files.readString(Path.of(args[0])));check(parsed.getJSONObject("days").length()>=30);check(parsed.getJSONObject("days").getJSONObject("2026-10-20").getJSONArray("times").getString(5).equals("19:43"));System.out.println("Official HTML parsed: "+parsed.getJSONObject("days").length()+" days");}
  System.out.println("PASS: official Berlin dates, next prayer, sunrise skipped, next-day Imsak, DST, expired cache, invalid HTML");
 }
}
