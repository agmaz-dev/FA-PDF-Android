package de.taxil150.app;
import android.content.*;import android.os.*;import org.json.*;
import java.io.*;import java.net.*;import java.util.*;import java.util.concurrent.*;

/**
 * Trade fairs in Berlin. Loads the public Messe Berlin event calendar at most once a day
 * (or when the user taps refresh), keeps the last good list offline and starts with a
 * built-in list of confirmed dates so the screen is never empty.
 */
public final class MesseRepository {
 static final String[] SOURCES={"https://www.messe-berlin.de/de/veranstaltungen/veranstaltungskalender/"};
 static final long MAX_AGE=20L*3600*1000;
 /** Confirmed dates (Messe Berlin calendar, as read on 14.11.2026). Replaced by the online list once it loads. */
 static final String[][] SEED={
  {"OutVenture Berlin","2026-11-26","2026-11-29","Wassersport und Outdoor"},
  {"Grüne Woche","2027-01-15","2027-01-24","Ernährung, Landwirtschaft, Gartenbau"},
  {"Fruit Logistica","2027-02-03","2027-02-05","Obst und Gemüse, international"},
  {"E-commerce Berlin Expo","2027-02-17","2027-02-18","Online-Handel"},
  {"ITB Berlin","2027-03-16","2027-03-18","Tourismus, weltweit"},
  {"FIBE Fintech Berlin","2027-04-07","2027-04-08","Finanzen und Technik"},
  {"bio:cap","2027-06-29","2027-06-30","Life Science und KI"},
  {"Smart Country Convention","2027-10-05","2027-10-07","Digitale Verwaltung"},
  {"Smart Health Europe","2027-10-26","2027-10-28","Digitale Gesundheit"},
  {"IFA","2027-09-03","2027-09-07","Consumer Electronics"},
  {"OutVenture Berlin","2027-11-25","2027-11-28","Wassersport und Outdoor"}};
 private final SharedPreferences prefs;private final Handler main=new Handler(Looper.getMainLooper());private final Runnable changed;
 private ExecutorService executor;private volatile boolean loading;private volatile String error="";

 public MesseRepository(Context c,Runnable changed){this.changed=changed;prefs=c.getSharedPreferences("messen",0);}
 public synchronized void start(){if(System.currentTimeMillis()-prefs.getLong("fetchedAt",0)>MAX_AGE&&System.currentTimeMillis()-prefs.getLong("attemptAt",0)>30*60*1000L)fetch();}
 public synchronized void stop(){}
 public synchronized void refresh(){fetch();}
 private synchronized void fetch(){if(loading)return;loading=true;error="";prefs.edit().putLong("attemptAt",System.currentTimeMillis()).apply();main.post(changed);
  if(executor==null)executor=Executors.newSingleThreadExecutor();
  executor.execute(()->{try{List<MesseParser.Event> all=new ArrayList<>();Exception last=null;
    for(String src:SOURCES){try{all.addAll(MesseParser.parse(download(src)));}catch(Exception e){last=e;}}
    if(all.isEmpty())throw last!=null?last:new IOException("Keine Termine gefunden");
    JSONArray items=new JSONArray();for(MesseParser.Event e:MesseParser.dedupe(all))items.put(toJson(e));
    prefs.edit().putString("items",items.toString()).putLong("fetchedAt",System.currentTimeMillis()).apply();error="";
   }catch(Exception e){error=e.getMessage()==null?"Abruf fehlgeschlagen":e.getMessage();}
   finally{loading=false;main.post(changed);}});
 }
 static String download(String src)throws IOException{HttpURLConnection c=(HttpURLConnection)new URL(src).openConnection();try{c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 14) TaxiMonitor/1.7.0");c.setRequestProperty("Accept-Language","de-DE,de;q=0.9,en;q=0.8");int code=c.getResponseCode();if(code!=200)throw new IOException("HTTP "+code);
  try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>3000000)throw new IOException("Antwort zu groß");}return out.toString("UTF-8");}}finally{c.disconnect();}}
 static JSONObject toJson(MesseParser.Event e)throws JSONException{return new JSONObject().put("name",e.name).put("start",e.start).put("end",e.end).put("desc",e.desc).put("venue",e.venue).put("big",e.big);}
 public JSONObject state(){try{JSONObject o=new JSONObject().put("loading",loading).put("fetchedAt",prefs.getLong("fetchedAt",0));if(!error.isEmpty())o.put("error",error);
   JSONArray items;String saved=prefs.getString("items","");if(!saved.isEmpty())items=new JSONArray(saved);else{items=new JSONArray();for(String[] s:SEED)items.put(toJson(new MesseParser.Event(s[0],s[1],s[2],s[3],"Messe Berlin")));o.put("builtIn",true);}
   return o.put("items",items);}catch(JSONException e){return new JSONObject();}}
}
