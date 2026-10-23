package de.taxil150.app;
import android.content.*;import android.database.*;import android.database.sqlite.*;import org.json.*;
/** Real call detections only. Unique per taxi number + APCOA queue identifier; no automatic pruning. */
public final class CallHistory extends SQLiteOpenHelper {
 public CallHistory(Context c){super(c,"call_history.db",null,1);}
 public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE calls (_id INTEGER PRIMARY KEY AUTOINCREMENT, number TEXT NOT NULL, call_id TEXT NOT NULL, target TEXT NOT NULL, detected_at INTEGER NOT NULL, UNIQUE(number,call_id))");}
 public void onUpgrade(SQLiteDatabase db,int old,int next){}
 public void record(String number,String id,String target,long now){if(id==null||id.isEmpty())return;ContentValues v=new ContentValues();v.put("number",number);v.put("call_id",id);v.put("target",target==null?"":target);v.put("detected_at",now);SQLiteDatabase db=getWritableDatabase();db.insertWithOnConflict("calls",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(target!=null&&!target.isEmpty()){ContentValues update=new ContentValues();update.put("target",target);db.update("calls",update,"number=? AND call_id=? AND target<>?",new String[]{number,id,target});}}
 public JSONObject page(int offset)throws JSONException{offset=Math.max(0,offset);JSONArray items=new JSONArray();SQLiteDatabase db=getReadableDatabase();long count=android.database.DatabaseUtils.queryNumEntries(db,"calls");try(Cursor c=db.rawQuery("SELECT number,call_id,target,detected_at FROM calls ORDER BY detected_at DESC,_id DESC LIMIT 50 OFFSET ?",new String[]{""+offset})){while(c.moveToNext())items.put(new JSONObject().put("number",c.getString(0)).put("callId",c.getString(1)).put("target",c.getString(2)).put("at",c.getLong(3)));}return new JSONObject().put("items",items).put("count",count).put("offset",offset);}
}
