package de.taxil150.app;
import java.util.*; import java.util.regex.*;
public final class Parser {
 /** One row of the waiting list, in list order. */
 public static final class Waiting { public final String number,qnr,points; Waiting(String n,String q,String p){number=n;qnr=q;points=p;} }
 /** One row of the call list. elapsedSeconds is the "Zeit" column (time since the call), -1 if unreadable. */
 public static final class Call { public final String number,qnr,points,target; public final int elapsedSeconds; Call(String n,String q,int e,String p,String t){number=n;qnr=q;elapsedSeconds=e;points=p;target=t;} }
 public static final class Result { public int total,rank,lCount,mCount; public String points="–",firstPoints="–",callId="",target=""; public final List<Waiting> waiting=new ArrayList<>(); public final List<Call> calls=new ArrayList<>(); }
 static String plain(String s){return s.replaceAll("(?s)<[^>]*>","").replace("&nbsp;"," ").replace("&#160;"," ").replace("&amp;","&").trim();}
 /** "mm:ss" or "h:mm:ss" → seconds; -1 when the text is not a duration. */
 public static int elapsed(String s){if(s==null)return -1;Matcher m=Pattern.compile("^(?:(\\d{1,2}):)?(\\d{1,3}):(\\d{2})$").matcher(s.trim());if(!m.matches())return -1;int h=m.group(1)==null?0:Integer.parseInt(m.group(1));int sec=Integer.parseInt(m.group(3));if(sec>59)return -1;return h*3600+Integer.parseInt(m.group(2))*60+sec;}
 /** Nord/Süd from the target code (e.g. "1N", "1S"); "" when unknown. */
 public static String side(String target){String t=target==null?"":target.trim().toUpperCase(Locale.ROOT);if(t.matches("[0-9]*N"))return "N";if(t.matches("[0-9]*S"))return "S";return "";}
 public static Result parse(String html){return parse(html,"L150");}
 public static Result parse(String html,String number){
 number=Config.normalize(number);
 Result r=new Result(); boolean waiting=false,calls=false; Matcher tables=Pattern.compile("(?is)<table\\b[^>]*>(.*?)</table>").matcher(html);
 while(tables.find()){String table=tables.group(1); boolean w=table.contains("Wartend (")||table.contains("Waiting ("); boolean c=table.contains("Aufgerufen (")||table.contains("Forthcalled ("); if(!w&&!c)continue;
 Matcher count=Pattern.compile("(?:Wartend|Waiting|Aufgerufen|Forthcalled) \\((\\d+)\\)").matcher(table);if(!count.find())throw new IllegalArgumentException("Anzahl fehlt");int declared=Integer.parseInt(count.group(1));
 Matcher rows=Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>").matcher(table);int n=0;
 while(rows.find()){Matcher cells=Pattern.compile("(?is)<td\\b[^>]*>(.*?)</td>").matcher(rows.group(1));List<String> values=new ArrayList<>();while(cells.find())values.add(plain(cells.group(1)));if(values.isEmpty())continue;n++;if(values.size()!=(w?3:5))throw new IllegalArgumentException("Unbekanntes Tabellenformat");
  if(w){if(n==1)r.firstPoints=values.get(2);String prefix=values.get(0).toUpperCase(Locale.ROOT);if(prefix.matches("L[0-9]+"))r.lCount++;else if(prefix.matches("M[0-9]+"))r.mCount++;r.waiting.add(new Waiting(values.get(0),values.get(1),values.get(2)));}
  else r.calls.add(new Call(values.get(0),values.get(1),elapsed(values.get(2)),values.get(3),values.get(4)));
  if(values.get(0).equals(number)){if(w){r.rank=n;r.points=values.get(2);}else{r.callId=values.get(1);r.target=values.get(4);}}}
 if(n!=declared)throw new IllegalArgumentException("Unvollständige Liste");if(w){waiting=true;r.total=n;}else calls=true;
 }if(!waiting||!calls)throw new IllegalArgumentException("Tabellen fehlen");return r;
 }
}
