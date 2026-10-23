package de.taxil150.app;
import android.content.*;import android.os.*;import org.json.*;import java.net.*;import java.io.*;import java.util.concurrent.*;
public final class WeatherRepository {
 private final SharedPreferences prefs;private final Runnable changed;private final Handler main=new Handler(Looper.getMainLooper());private ScheduledExecutorService executor;private volatile boolean active;private volatile long generation;private volatile HttpURLConnection current;private volatile JSONObject snapshot=new JSONObject();private volatile String error="";
 public WeatherRepository(Context c,Runnable changed){this.changed=changed;prefs=c.getSharedPreferences("weather",0);try{snapshot=new JSONObject(prefs.getString("snapshot_v1","{}"));}catch(Exception ignored){}}
 public synchronized void start(){if(active)return;active=true;long g=++generation;executor=Executors.newSingleThreadScheduledExecutor();long delay=Math.max(0,900000-(System.currentTimeMillis()-snapshot.optLong("fetchedAt",0)));executor.scheduleWithFixedDelay(()->fetch(g),delay,900000,TimeUnit.MILLISECONDS);}
 public synchronized void stop(){active=false;generation++;if(current!=null)current.disconnect();if(executor!=null)executor.shutdownNow();executor=null;}
 private void fetch(long g){if(!active||g!=generation)return;HttpURLConnection c=null;try{
  c=(HttpURLConnection)new URL("https://api.brightsky.dev/current_weather?lat=52.388&lon=13.503").openConnection();current=c;c.setConnectTimeout(10000);c.setReadTimeout(10000);c.setUseCaches(false);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","TaxiMonitor/1.4.1 Android");int code=c.getResponseCode();if(code!=200)throw new IOException("Wetter HTTP "+code);
  String body;try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>65536)throw new IOException("Wetterantwort zu groß");}body=out.toString("UTF-8");}
  JSONObject next=WeatherParser.parse(body).put("fetchedAt",System.currentTimeMillis());if(active&&g==generation){snapshot=next;error="";prefs.edit().putString("snapshot_v1",next.toString()).apply();}
 }catch(Exception e){if(active&&g==generation)error=e.getMessage()==null?"Wetter nicht verfügbar":e.getMessage();}finally{if(c!=null)c.disconnect();if(current==c)current=null;if(active&&g==generation)main.post(changed);}}
 public JSONObject state(){try{return new JSONObject(snapshot.toString()).put("error",error);}catch(Exception e){return new JSONObject();}}
}
