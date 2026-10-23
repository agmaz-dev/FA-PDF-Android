package de.taxil150.app;
import android.content.*;import android.os.SystemClock;import android.provider.Settings;import org.json.*;
public final class CountdownStore {
 private final SharedPreferences p;private final Context context;private final CountdownState value=new CountdownState();
 public CountdownStore(Context c){context=c;p=c.getSharedPreferences("countdown_45",0);value.paused=p.getLong("paused",CountdownState.DURATION);value.wallEnd=p.getLong("wallEnd",0);value.elapsedEnd=p.getLong("elapsedEnd",0);value.boot=p.getInt("boot",-1);value.running=p.getBoolean("running",false);value.finished=p.getBoolean("finished",false);}
 private int boot(){try{return Settings.Global.getInt(context.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}catch(Exception e){return -1;}}
 private void save(){if(!p.edit().putLong("paused",value.paused).putLong("wallEnd",value.wallEnd).putLong("elapsedEnd",value.elapsedEnd).putInt("boot",value.boot).putBoolean("running",value.running).putBoolean("finished",value.finished).commit())throw new IllegalStateException("Timer konnte nicht gespeichert werden");}
 public void toggle(){value.toggle(System.currentTimeMillis(),SystemClock.elapsedRealtime(),boot());save();}
 public void reset(){value.reset();save();}
 public JSONObject state()throws JSONException{long wall=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();int boot=boot();if(value.settle(wall,elapsed,boot))save();long left=value.remaining(wall,elapsed,boot);return new JSONObject().put("remainingSeconds",(left+999)/1000).put("running",value.running).put("finished",value.finished).put("pristine",!value.running&&!value.finished&&left==CountdownState.DURATION);}
}
