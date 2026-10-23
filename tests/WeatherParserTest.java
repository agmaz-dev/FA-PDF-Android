import de.taxil150.app.WeatherParser;import org.json.*;import java.nio.file.*;
public class WeatherParserTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static String payload(Object temperature,String icon)throws Exception{return new JSONObject().put("weather",new JSONObject().put("temperature",temperature).put("icon",icon).put("timestamp","2026-10-19T22:00:00+00:00").put("source_id",7)).put("sources",new JSONArray().put(new JSONObject().put("id",7).put("station_name","Berlin Brandenburg"))).toString();}
 public static void main(String[]args)throws Exception{
  JSONObject n=WeatherParser.parse(payload(-2.4,"clear-night"));check(n.getDouble("temperature")==-2.4&&n.getString("label").equals("Klar")&&n.getString("icon").equals("moon")&&n.getString("station").equals("Berlin Brandenburg"));
  check(WeatherParser.parse(payload(0,"snow")).getDouble("temperature")==0);check(WeatherParser.parse(payload(10,"rain")).getString("label").equals("Regen"));check(WeatherParser.parse(payload(10,"cloudy")).getString("label").equals("Bewölkt"));
  for(String bad:new String[]{"{}","<html>Fehler</html>",payload(JSONObject.NULL,"cloudy"),payload(1000,"clear-day")}){boolean rejected=false;try{WeatherParser.parse(bad);}catch(Exception e){rejected=true;}check(rejected);}
  if(args.length>0){n=WeatherParser.parse(Files.readString(Path.of(args[0])));System.out.println("Live-Wetter geparst: Station "+n.getString("station")+", "+n.getDouble("temperature")+" °C, "+n.getString("label"));}
  System.out.println("PASS: DWD-Temperatur einschließlich Null/Minus, Zustand, Nacht, Stationszuordnung, fehlende/ungültige Werte");
 }
}
