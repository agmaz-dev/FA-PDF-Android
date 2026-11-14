package de.taxil150.app;
import android.app.*;import android.os.*;import android.content.*;import android.media.*;import java.net.*;import java.io.*;import java.util.concurrent.*;import java.text.*;import java.util.*;import org.json.*;
public class MonitorService extends Service {
 static final String URL="https://tms-info.apcoa.de/external/display/98AD0D50-A726-11E2-9E96-0800200C9A66/zones/139A83F0-B534-4832-ADC0-9C1717820849";
 static final String HINT_CHANNEL="hint10";
 ScheduledExecutorService executor;SharedPreferences p;CallHistory history;QueueLog log;int failures;long retryAfter;volatile boolean active;static volatile boolean running;
 public void onCreate(){super.onCreate();p=getSharedPreferences("status",0);history=new CallHistory(this);log=new QueueLog(this);NotificationManager nm=getSystemService(NotificationManager.class);NotificationChannel quiet=new NotificationChannel("monitor","Überwachung",NotificationManager.IMPORTANCE_LOW);nm.createNotificationChannel(quiet);NotificationChannel hint=new NotificationChannel(HINT_CHANNEL,"Hinweis 10 Min vorher",NotificationManager.IMPORTANCE_HIGH);nm.createNotificationChannel(hint);Alerts.ensure(this);}
 PendingIntent open(){return PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
 public int onStartCommand(Intent i,int f,int id){startForeground(1,new Notification.Builder(this,"monitor").setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Taxi "+p.getString("number","L150")+" – Überwachung aktiv").setContentText("Abruf alle 5 Sekunden").setContentIntent(open()).setOngoing(true).build());if(executor==null){active=true;running=true;p.edit().putString("monitor","Aktiv").putLong("monitorSince",System.currentTimeMillis()).putBoolean("sawOutside",false).apply();executor=Executors.newSingleThreadScheduledExecutor();executor.scheduleWithFixedDelay(this::poll,0,5,TimeUnit.SECONDS);}return START_NOT_STICKY;}
 void poll(){if(!active||System.currentTimeMillis()<retryAfter)return;String number=p.getString("number","L150");HttpURLConnection conn=null;try{conn=(HttpURLConnection)new java.net.URL(URL).openConnection();conn.setConnectTimeout(15000);conn.setReadTimeout(15000);conn.setInstanceFollowRedirects(false);conn.setRequestProperty("User-Agent","TaxiMonitor/1.4.1");conn.setUseCaches(false);int code=conn.getResponseCode();if(code!=200){if(code==429||code==503){String retry=conn.getHeaderField("Retry-After");try{retryAfter=System.currentTimeMillis()+Math.max(300,Long.parseLong(retry))*1000;}catch(Exception ignored){retryAfter=System.currentTimeMillis()+900000;}}if(code==401||code==403){retryAfter=Long.MAX_VALUE;}throw new IOException("HTTP "+code);}String html;try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>2000000)throw new IOException("Antwort zu groß");}html=out.toString("UTF-8");}Parser.Result r=Parser.parse(html,number);synchronized(Config.LOCK){if(!active||!number.equals(p.getString("number","L150")))return;failures=0;p.edit().putString("state",r.callId.isEmpty()?(r.rank>0?"Wartend":number+" nicht in der Warteschlange"):"AUFGERUFEN!").putString("rank",r.rank>0?""+r.rank:"–").putString("points",r.points).putString("firstPoints",r.firstPoints).putString("total",""+r.total).putString("lCount",""+r.lCount).putString("mCount",""+r.mCount).putLong("updatedAt",System.currentTimeMillis()).putString("target",r.callId.isEmpty()?"–":r.target.isEmpty()?"Nicht angegeben":r.target).putString("time",DateFormat.getDateTimeInstance().format(new Date())).putString("connection","Verbunden").apply();
 boolean newOwnCall=false;
 if(!r.callId.isEmpty()){
 try{history.record(number,r.callId,r.target,System.currentTimeMillis());p.edit().remove("historyError").apply();}catch(Exception e){p.edit().putString("historyError","Aufrufverlauf konnte nicht gespeichert werden").apply();}
 String key=Config.callKey(number,r.callId);
 if(!key.equals(p.getString("detectedKey",""))){newOwnCall=true;p.edit().putString("detectedKey",key).putString("callTime",new SimpleDateFormat("HH:mm 'Uhr'",Locale.GERMAN).format(new Date())).putString("lastDetected","Taxi "+number+" · QNr "+r.callId+" · "+DateFormat.getDateTimeInstance().format(new Date())).apply();}
 boolean seen=p.contains(key)||(number.equals("L150")&&p.contains("seen_"+r.callId));
 if(!seen){if(Alerts.post(this,number,r.target,false))p.edit().putBoolean(key,true).commit();}
 else if(!key.equals(p.getString("reportedKey","")))p.edit().putString("lastAlarm","Aufruf QNr "+r.callId+" bereits bearbeitet (keine zweite Meldung)").apply();
 p.edit().putString("reportedKey",key).apply();
 }
 try{insights(number,r,newOwnCall);}catch(Exception e){p.edit().putString("insightsError",""+e.getMessage()).apply();}
 } }catch(Exception e){if(active&&number.equals(p.getString("number","L150"))){failures++;retryAfter=Math.max(retryAfter,System.currentTimeMillis()+Math.min(900000L,60000L*(1L<<Math.min(failures-1,4))));p.edit().putString("connection","Fehler – Werte veraltet: "+e.getMessage()).apply();}}finally{if(conn!=null)conn.disconnect();}}

 /** Calls seen, own track, estimate, Nord/Süd, sequence of the own call and the 10-minute hint. Never affects the call alarm. */
 void insights(String number,Parser.Result r,boolean newOwnCall)throws Exception{
  long now=System.currentTimeMillis();
  log.recordCalls(r.calls,now);
  SharedPreferences.Editor e=p.edit();
  long joinedAt=number.equals(p.getString("joinedNumber",""))?p.getLong("joinedAt",0):0;
  if(r.rank>0){
   if(joinedAt==0){joinedAt=now;e.putLong("joinedAt",now).putString("joinedNumber",number).putBoolean("joinedExact",p.getBoolean("sawOutside",false)).remove("hintedFor");}
   log.recordRank(number,r.rank,now);
  }else if(r.callId.isEmpty()){
   if(joinedAt!=0)e.remove("joinedAt").remove("joinedNumber");joinedAt=0;e.putBoolean("sawOutside",true);
  }
  if(newOwnCall){
   long at=log.callTime(r.callId);if(at==0)at=now;
   if(joinedAt!=0){log.addWait(number,joinedAt,at,p.getBoolean("joinedExact",false),r.target);e.remove("joinedAt").remove("joinedNumber");joinedAt=0;}
   JSONArray waiting=new JSONArray();int pos=0;for(Parser.Waiting w:r.waiting){if(++pos>3)break;waiting.put(new JSONObject().put("number",w.number).put("position",pos));}
   e.putString("sequence",new JSONObject().put("number",number).put("qnr",r.callId).put("target",r.target).put("at",at).put("waiting",waiting).toString());
   e.putString("waitStats",log.waitStats(number,now).toString());
  }
  // Sequence view: refresh neighbours of the last own call while new calls come in.
  String seq=p.getString("sequence","");
  if(!seq.isEmpty()){JSONObject s=new JSONObject(seq);if(now-s.optLong("at")<6L*3600*1000){JSONObject n=log.neighbours(s.optString("qnr"),s.optLong("at"));s.put("before",n.getJSONArray("before")).put("after",n.getJSONArray("after"));e.putString("sequenceView",s.toString());}}
  long since=p.getLong("monitorSince",now);
  List<Forecast.Sample> samples=joinedAt>0?log.samples(number,joinedAt):new ArrayList<>();
  Forecast.Result f=Forecast.compute(now,log.calls(now-61*Forecast.MIN),since-3*Forecast.MIN,r.rank,r.total,samples);
  JSONObject o=new JSONObject().put("at",now).put("available",f.available).put("rank",r.rank).put("total",r.total).put("north",f.north).put("south",f.south).put("lastHour",f.lastHourCalls).put("likelyNorth",f.likelyNorthTenths).put("basis",f.basis);
  JSONArray buckets=new JSONArray();for(int[] b:f.buckets)buckets.put(new JSONArray().put(b[0]).put(b[1]));o.put("buckets",buckets);
  if(f.available){o.put("every",Math.round(f.everyMinutes*10)/10.0).put("joinNowMin",f.joinNowMin);if(r.rank>0)o.put("etaMin",f.etaMin).put("earlyMin",f.earlyMin).put("lateMin",f.lateMin);}
  if(joinedAt>0){o.put("joinedAt",joinedAt).put("joinedExact",p.getBoolean("joinedExact",false));JSONArray track=new JSONArray();int step=Math.max(1,samples.size()/80);for(int i=0;i<samples.size();i+=step)track.put(new JSONArray().put(samples.get(i).at).put(samples.get(i).rank));if(!samples.isEmpty()){Forecast.Sample last=samples.get(samples.size()-1);track.put(new JSONArray().put(last.at).put(last.rank));}o.put("track",track);}
  e.putString("insights",o.toString());
  if(!p.contains("waitStats"))e.putString("waitStats",log.waitStats(number,now).toString());
  e.apply();
  if(p.getBoolean("hint10",true)&&r.rank>0&&f.available&&f.etaMin<=10&&joinedAt>0&&p.getLong("hintedFor",0)!=joinedAt){
   p.edit().putLong("hintedFor",joinedAt).apply();
   getSystemService(NotificationManager.class).notify(4,new Notification.Builder(this,HINT_CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Gleich bist du dran").setContentText("Taxi "+number+" · Platz "+r.rank+" · voraussichtlich in ca. "+f.etaMin+" Min").setContentIntent(open()).setAutoCancel(true).build());
  }
 }
 public void onTimeout(int startId,int fgsType){p.edit().putString("connection","Android-Zeitlimit erreicht").apply();stopSelf();}
 public void onDestroy(){active=false;running=false;if(executor!=null)executor.shutdownNow();p.edit().putString("monitor","Aus").apply();synchronized(Config.LOCK){if(history!=null)history.close();if(log!=null)log.close();}super.onDestroy();}public IBinder onBind(Intent i){return null;}
}
