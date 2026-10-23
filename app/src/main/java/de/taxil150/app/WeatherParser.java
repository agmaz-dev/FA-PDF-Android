package de.taxil150.app;
import org.json.*;
public final class WeatherParser {
 public static JSONObject parse(String response)throws JSONException {
  JSONObject root=new JSONObject(response),raw=root.getJSONObject("weather");if(raw.isNull("temperature"))throw new JSONException("Temperatur fehlt");double temperature=raw.getDouble("temperature");if(Double.isNaN(temperature)||Double.isInfinite(temperature)||temperature< -90||temperature>65)throw new JSONException("Temperatur ungültig");
  long at=FlightParser.epoch(raw.optString("timestamp",""));if(at==0)throw new JSONException("Wetterzeit fehlt");
  String icon=raw.optString("icon","");String label="Wetter unbekannt",symbol="cloud";
  switch(icon){case "clear-day":label="Sonnig";symbol="sun";break;case "clear-night":label="Klar";symbol="moon";break;case "partly-cloudy-day":case "partly-cloudy-night":label="Leicht bewölkt";break;case "cloudy":label="Bewölkt";break;case "rain":label="Regen";symbol="rain";break;case "sleet":label="Schneeregen";symbol="rain";break;case "snow":label="Schnee";symbol="snow";break;case "fog":label="Nebel";symbol="fog";break;case "wind":label="Windig";break;case "hail":label="Hagel";symbol="rain";break;case "thunderstorm":label="Gewitter";symbol="rain";break;default:if("dry".equals(raw.optString("condition")))label="Trocken";}
  String station="";JSONArray sources=root.optJSONArray("sources");if(sources!=null)for(int i=0;i<sources.length();i++){JSONObject source=sources.getJSONObject(i);if(source.optLong("id")==raw.optLong("source_id"))station=source.optString("station_name","");}
  return new JSONObject().put("temperature",temperature).put("label",label).put("icon",symbol).put("sourceAt",at).put("station",station);
 }
 private WeatherParser(){}
}
