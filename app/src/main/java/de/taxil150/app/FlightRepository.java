package de.taxil150.app;
import android.content.*;
import android.os.*;
import org.json.*;
import java.net.*;import java.io.*;
import java.time.*;
import java.util.*;import java.util.concurrent.*;

/** Independent from taxi polling: refreshes only while the arrival screen is visible. */
public final class FlightRepository {
 public static final String WEBSITE="https://ber.berlin-airport.de/de/fliegen/abfluege-ankuenfte.html";
 private final SharedPreferences prefs;private final Handler main=new Handler(Looper.getMainLooper());private final Runnable changed;
 private ScheduledExecutorService executor;private volatile boolean active,loading;private volatile long generation,lastAttempt,nextAttempt,lastFinished;private volatile HttpURLConnection current;
 private volatile String finishedDate="",requestedDate="",status="Ankünfte noch nicht geladen",error="";private volatile JSONObject snapshot=new JSONObject();
 public FlightRepository(Context c,Runnable changed){this.changed=changed;prefs=c.getSharedPreferences("flights",0);try{snapshot=new JSONObject(prefs.getString("snapshot_v1","{}"));}catch(Exception ignored){}}
 public static String today(){return LocalDate.now(FlightParser.BERLIN).toString();}
 public synchronized void start(){if(active)return;active=true;long g=++generation;executor=Executors.newSingleThreadScheduledExecutor();executor.scheduleWithFixedDelay(()->fetch(g),0,60,TimeUnit.SECONDS);}
 public synchronized void stop(){active=false;generation++;if(current!=null)current.disconnect();if(executor!=null)executor.shutdownNow();executor=null;loading=false;}
 public synchronized void refresh(String date){try{LocalDate chosen=LocalDate.parse(date);if(chosen.isBefore(LocalDate.now(FlightParser.BERLIN).minusDays(1))||chosen.isAfter(LocalDate.now(FlightParser.BERLIN).plusDays(1)))return;}catch(Exception e){return;}
  boolean changedDate=!date.equals(requestedDate);requestedDate=date;
  if(!active)start();else if(changedDate||(!loading&&System.currentTimeMillis()>=FlightRefreshGate.allowedAt(nextAttempt,lastFinished,finishedDate,date))){long g=generation;executor.execute(()->fetch(g));}else notifyUi();
 }
 private void notifyUi(){main.post(changed);}
 private void fetch(long g){
  String date=requestedDate.isEmpty()?today():requestedDate;
  if(!active||g!=generation||loading||System.currentTimeMillis()<FlightRefreshGate.allowedAt(nextAttempt,lastFinished,finishedDate,date))return;loading=true;lastAttempt=System.currentTimeMillis();error="";status="Ankünfte werden geladen …";notifyUi();
  try{
   LocalDate chosen=LocalDate.parse(date);String until=chosen.plusDays(1).toString();
   // Include the previous evening for delayed flights crossing midnight.
   String from=chosen.equals(LocalDate.now(FlightParser.BERLIN))?chosen.minusDays(1)+"T18:00:00":chosen+"T00:00:00";
   Map<String,JSONObject> flights=new LinkedHashMap<>();int page=1,pages=1,total=0,received=0;String sourceAt="";
   do{
    if(!active||g!=generation)return;
    String url="https://ber.berlin-airport.de/api.flights.json?arrivalDeparture=A&dateFrom="+from+"&dateUntil="+until+"&search=&lang=de&page="+page+"&terminal=&itemsPerPage=500";
    FlightParser.Page parsed=FlightParser.parse(request(url));if(parsed.page!=page)throw new IOException("Seitenfolge stimmt nicht");
    pages=parsed.totalPages;total=parsed.totalItems;sourceAt=parsed.sourceAt;received+=parsed.rawCount;if(pages>50)throw new IOException("Zu viele Ankunftsseiten");
    FlightParser.merge(flights,parsed.items);
    page++;
   }while(page<=pages);
   if(received<total)throw new IOException("Ankunftsliste unvollständig");
   if(!active||g!=generation||!date.equals(requestedDate.isEmpty()?today():requestedDate))return;
   JSONObject result=new JSONObject().put("date",date).put("items",FlightParser.sorted(flights.values())).put("sourceAt",sourceAt).put("sourceEpoch",FlightParser.epoch(sourceAt)).put("fetchedAt",System.currentTimeMillis());
   snapshot=result;prefs.edit().putString("snapshot_v1",result.toString()).apply();status="Ankünfte aktualisiert";nextAttempt=0;
  }catch(Exception e){if(active&&g==generation){error=e.getMessage()==null?"Abruf fehlgeschlagen":e.getMessage();status="Ankünfte konnten nicht aktualisiert werden";nextAttempt=System.currentTimeMillis()+(error.contains("403")||error.contains("429")?300000:60000);}}
  finally{if(g==generation){lastFinished=System.currentTimeMillis();finishedDate=date;loading=false;notifyUi();}}
 }
 private String request(String url)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();current=c;
  try{c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Referer",WEBSITE);c.setRequestProperty("User-Agent","TaxiMonitor/1.4.2 Android");
   int code=c.getResponseCode();if(code!=200)throw new IOException("BER antwortet mit HTTP "+code);
   try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(!active||Thread.currentThread().isInterrupted())throw new IOException("Abruf beendet");out.write(b,0,n);if(out.size()>2000000)throw new IOException("Antwort zu groß");}return out.toString("UTF-8");}
  }finally{c.disconnect();if(current==c)current=null;}
 }
 public JSONObject state(){try{return new JSONObject(snapshot.toString()).put("loading",loading).put("message",status).put("error",error).put("requestedDate",requestedDate.isEmpty()?today():requestedDate).put("lastAttempt",lastAttempt).put("nextAllowedAt",FlightRefreshGate.allowedAt(nextAttempt,lastFinished,finishedDate,requestedDate.isEmpty()?today():requestedDate));}catch(Exception e){return new JSONObject();}}
}
