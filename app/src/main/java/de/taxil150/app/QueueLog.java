package de.taxil150.app;
import android.content.*;import android.database.*;import android.database.sqlite.*;import org.json.*;
import java.time.*;import java.util.*;

/**
 * Local memory of what the monitor has observed:
 *  - every call seen in the APCOA list (each QNr once), with its time and target,
 *  - the own rank while waiting (for the progress chart and the estimate),
 *  - own completed waits (joined → called) for the waiting-time statistics.
 * Old rows are pruned automatically. Separate from CallHistory, which stays unchanged.
 */
public final class QueueLog extends SQLiteOpenHelper {
 static final ZoneId BERLIN=ZoneId.of("Europe/Berlin");
 public QueueLog(Context c){super(c,"queue_log.db",null,1);}
 public void onCreate(SQLiteDatabase db){
  db.execSQL("CREATE TABLE seen_calls (qnr TEXT PRIMARY KEY, number TEXT NOT NULL, target TEXT NOT NULL, called_at INTEGER NOT NULL)");
  db.execSQL("CREATE INDEX seen_calls_at ON seen_calls(called_at)");
  db.execSQL("CREATE TABLE rank_samples (at INTEGER NOT NULL, number TEXT NOT NULL, rank INTEGER NOT NULL)");
  db.execSQL("CREATE INDEX rank_samples_at ON rank_samples(at)");
  db.execSQL("CREATE TABLE waits (number TEXT NOT NULL, joined_at INTEGER NOT NULL, called_at INTEGER NOT NULL, exact INTEGER NOT NULL, target TEXT NOT NULL, PRIMARY KEY(number, called_at))");
 }
 public void onUpgrade(SQLiteDatabase db,int old,int next){}

 /** Stores calls not seen before. The call time is derived from the "Zeit" column when readable. */
 public void recordCalls(List<Parser.Call> calls,long now){
  SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
  try{for(Parser.Call c:calls){if(c.qnr==null||c.qnr.isEmpty())continue;long at=c.elapsedSeconds>=0&&c.elapsedSeconds<6*3600?now-c.elapsedSeconds*1000L:now;
   ContentValues v=new ContentValues();v.put("qnr",c.qnr);v.put("number",c.number);v.put("target",c.target==null?"":c.target);v.put("called_at",at);
   db.insertWithOnConflict("seen_calls",null,v,SQLiteDatabase.CONFLICT_IGNORE);
   if(c.target!=null&&!c.target.isEmpty()){ContentValues u=new ContentValues();u.put("target",c.target);db.update("seen_calls",u,"qnr=? AND target=''",new String[]{c.qnr});}}
   db.delete("seen_calls","called_at<?",new String[]{""+(now-8L*24*3600*1000)});
   db.setTransactionSuccessful();}finally{db.endTransaction();}
 }
 public void recordRank(String number,int rank,long now){
  SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();v.put("at",now);v.put("number",number);v.put("rank",rank);db.insert("rank_samples",null,v);
  db.delete("rank_samples","at<?",new String[]{""+(now-12L*3600*1000)});
 }
 public void addWait(String number,long joinedAt,long calledAt,boolean exact,String target){
  ContentValues v=new ContentValues();v.put("number",number);v.put("joined_at",joinedAt);v.put("called_at",calledAt);v.put("exact",exact?1:0);v.put("target",target==null?"":target);
  SQLiteDatabase db=getWritableDatabase();db.insertWithOnConflict("waits",null,v,SQLiteDatabase.CONFLICT_IGNORE);db.delete("waits","called_at<?",new String[]{""+(calledAt-60L*24*3600*1000)});
 }
 public List<Forecast.Call> calls(long since){
  List<Forecast.Call> out=new ArrayList<>();
  try(Cursor c=getReadableDatabase().rawQuery("SELECT called_at,target FROM seen_calls WHERE called_at>=? ORDER BY called_at",new String[]{""+since})){while(c.moveToNext())out.add(new Forecast.Call(c.getLong(0),Parser.side(c.getString(1))));}
  return out;
 }
 public List<Forecast.Sample> samples(String number,long since){
  List<Forecast.Sample> out=new ArrayList<>();
  try(Cursor c=getReadableDatabase().rawQuery("SELECT at,rank FROM rank_samples WHERE number=? AND at>=? ORDER BY at",new String[]{number,""+since})){while(c.moveToNext())out.add(new Forecast.Sample(c.getLong(0),c.getInt(1)));}
  return out;
 }
 /** Up to 3 calls before and after the given call, by call time. */
 public JSONObject neighbours(String qnr,long at)throws JSONException{
  JSONArray before=new JSONArray(),after=new JSONArray();SQLiteDatabase db=getReadableDatabase();
  try(Cursor c=db.rawQuery("SELECT number,target,called_at FROM seen_calls WHERE qnr<>? AND (called_at<? OR (called_at=? AND qnr<?)) ORDER BY called_at DESC, qnr DESC LIMIT 3",new String[]{qnr,""+at,""+at,qnr})){while(c.moveToNext())before.put(new JSONObject().put("number",c.getString(0)).put("target",c.getString(1)).put("at",c.getLong(2)));}
  try(Cursor c=db.rawQuery("SELECT number,target,called_at FROM seen_calls WHERE qnr<>? AND (called_at>? OR (called_at=? AND qnr>?)) ORDER BY called_at, qnr LIMIT 3",new String[]{qnr,""+at,""+at,qnr})){while(c.moveToNext())after.put(new JSONObject().put("number",c.getString(0)).put("target",c.getString(1)).put("at",c.getLong(2)));}
  return new JSONObject().put("before",before).put("after",after);
 }
 public long callTime(String qnr){try(Cursor c=getReadableDatabase().rawQuery("SELECT called_at FROM seen_calls WHERE qnr=?",new String[]{qnr})){return c.moveToFirst()?c.getLong(0):0;}}

 /** Own waiting times: today, last 7 days, and the hour of the day with the shortest average (last 30 days). */
 public JSONObject waitStats(String number,long now)throws JSONException{
  LocalDate today=Instant.ofEpochMilli(now).atZone(BERLIN).toLocalDate();
  int todayN=0,weekN=0;long todaySum=0,weekSum=0;int todayExact=0,weekExact=0;long[] hourSum=new long[24];int[] hourN=new int[24];
  try(Cursor c=getReadableDatabase().rawQuery("SELECT joined_at,called_at,exact FROM waits WHERE number=? AND called_at>=?",new String[]{number,""+(now-30L*24*3600*1000)})){
   while(c.moveToNext()){long j=c.getLong(0),k=c.getLong(1);boolean exact=c.getInt(2)==1;long m=Math.max(0,(k-j)/60000);ZonedDateTime z=Instant.ofEpochMilli(k).atZone(BERLIN);
    if(z.toLocalDate().equals(today)){todayN++;if(exact){todayExact++;todaySum+=m;}}
    if(k>=now-7L*24*3600*1000){weekN++;if(exact){weekExact++;weekSum+=m;}}
    if(exact){int h=Instant.ofEpochMilli(j).atZone(BERLIN).getHour();hourSum[h]+=m;hourN[h]++;}}}
  JSONArray hours=new JSONArray();int bestHour=-1;long bestAvg=Long.MAX_VALUE;
  for(int h=0;h<24;h++){if(hourN[h]==0)continue;long avg=Math.round(hourSum[h]/(double)hourN[h]);hours.put(new JSONObject().put("hour",h).put("avg",avg).put("n",hourN[h]));if(hourN[h]>=2&&avg<bestAvg){bestAvg=avg;bestHour=h;}}
  JSONObject o=new JSONObject().put("today",todayN).put("week",weekN).put("hours",hours);
  if(todayExact>0)o.put("todayAvg",Math.round(todaySum/(double)todayExact));if(weekExact>0)o.put("weekAvg",Math.round(weekSum/(double)weekExact));
  if(bestHour>=0)o.put("bestHour",bestHour).put("bestAvg",bestAvg);
  return o;
 }
}
