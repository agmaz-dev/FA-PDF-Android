package de.taxil150.app;
import org.json.*;import java.time.*;import java.util.*;import java.util.regex.*;
public final class PrayerParser {
 public static final ZoneId BERLIN=ZoneId.of("Europe/Berlin");
 static final String[] MONTHS={"Ocak","Şubat","Mart","Nisan","Mayıs","Haziran","Temmuz","Ağustos","Eylül","Ekim","Kasım","Aralık"};
 static String plain(String s){Matcher entities=Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(s);StringBuffer decoded=new StringBuffer();while(entities.find()){try{String code=entities.group(1);int cp=code.startsWith("x")?Integer.parseInt(code.substring(1),16):Integer.parseInt(code);entities.appendReplacement(decoded,Matcher.quoteReplacement(new String(Character.toChars(cp))));}catch(Exception e){entities.appendReplacement(decoded,Matcher.quoteReplacement(entities.group()));}}entities.appendTail(decoded);return decoded.toString().replaceAll("(?s)<[^>]*>"," ").replace("&nbsp;"," ").replace("&#305;","ı").replace("&#351;","ş").replace("&#220;","Ü").replace("&#252;","ü").replace("&#199;","Ç").replace("&#231;","ç").replace("&#214;","Ö").replace("&#246;","ö").replace("&#287;","ğ").replaceAll("\\s+"," ").trim();}
 public static JSONObject parse(String body)throws Exception{
  if(!body.contains("Berl")||!body.contains("11002"))throw new IllegalArgumentException("Keine Berliner Gebetszeiten");
  JSONObject days=new JSONObject();Matcher rows=Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>").matcher(body);
  while(rows.find()){ArrayList<String> cells=new ArrayList<>();Matcher td=Pattern.compile("(?is)<td\\b[^>]*>(.*?)</td>").matcher(rows.group(1));while(td.find())cells.add(plain(td.group(1)));if(cells.size()!=8)continue;
   Matcher date=Pattern.compile("^(\\d{1,2}) ([^ ]+) (\\d{4}) .+").matcher(cells.get(0));if(!date.matches())continue;int month=Arrays.asList(MONTHS).indexOf(date.group(2))+1;if(month<1)continue;
   try{LocalDate d=LocalDate.of(Integer.parseInt(date.group(3)),month,Integer.parseInt(date.group(1)));JSONArray times=new JSONArray(),epochs=new JSONArray();int previous=-1;boolean valid=true;
    for(int i=2;i<8;i++){String value=cells.get(i);if(!value.matches("[0-2][0-9]:[0-5][0-9]")){valid=false;break;}LocalTime t=LocalTime.parse(value);int minute=t.getHour()*60+t.getMinute();if(minute<=previous){valid=false;break;}previous=minute;times.put(value);epochs.put(d.atTime(t).atZone(BERLIN).toInstant().toEpochMilli());}
    if(valid)days.put(d.toString(),new JSONObject().put("date",d.toString()).put("times",times).put("epochs",epochs));
   }catch(Exception ignored){}
  }if(days.length()==0)throw new IllegalArgumentException("Gebetszeitentabelle nicht lesbar");return new JSONObject().put("days",days);
 }
 public static JSONObject state(JSONObject snapshot,String error,long now)throws Exception{
  LocalDate date=Instant.ofEpochMilli(now).atZone(BERLIN).toLocalDate();JSONObject days=snapshot.optJSONObject("days"),today=days==null?null:days.optJSONObject(date.toString());JSONObject result=new JSONObject().put("date",date.toString()).put("error",error).put("fetchedAt",snapshot.optLong("fetchedAt"));
  if(today!=null)result.put("today",today);JSONObject next=null;int nextIndex=-1;
  for(int day=0;day<2&&next==null;day++){JSONObject item=days==null?null:days.optJSONObject(date.plusDays(day).toString());if(item==null)continue;JSONArray epochs=item.getJSONArray("epochs");for(int i:new int[]{0,2,3,4,5})if(epochs.getLong(i)>now){next=item;nextIndex=i;break;}}
  if(next!=null)result.put("nextAt",next.getJSONArray("epochs").getLong(nextIndex)).put("nextIndex",nextIndex).put("nextDate",next.getString("date"));return result;
 }
}
