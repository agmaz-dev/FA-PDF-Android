package de.taxil150.app;
import java.util.*; import java.util.regex.*;
public final class Parser {
 public static final class Result { public int total,rank,lCount,mCount; public String points="–",firstPoints="–",callId="",target=""; }
 static String plain(String s){return s.replaceAll("(?s)<[^>]*>","").replace("&nbsp;"," ").replace("&#160;"," ").replace("&amp;","&").trim();}
 public static Result parse(String html){return parse(html,"L150");}
 public static Result parse(String html,String number){
 number=Config.normalize(number);
 Result r=new Result(); boolean waiting=false,calls=false; Matcher tables=Pattern.compile("(?is)<table\\b[^>]*>(.*?)</table>").matcher(html);
 while(tables.find()){String table=tables.group(1); boolean w=table.contains("Wartend (")||table.contains("Waiting ("); boolean c=table.contains("Aufgerufen (")||table.contains("Forthcalled ("); if(!w&&!c)continue;
 Matcher count=Pattern.compile("(?:Wartend|Waiting|Aufgerufen|Forthcalled) \\((\\d+)\\)").matcher(table);if(!count.find())throw new IllegalArgumentException("Anzahl fehlt");int declared=Integer.parseInt(count.group(1));
 Matcher rows=Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>").matcher(table);int n=0;
 while(rows.find()){Matcher cells=Pattern.compile("(?is)<td\\b[^>]*>(.*?)</td>").matcher(rows.group(1));List<String> values=new ArrayList<>();while(cells.find())values.add(plain(cells.group(1)));if(values.isEmpty())continue;n++;if(values.size()!=(w?3:5))throw new IllegalArgumentException("Unbekanntes Tabellenformat");if(w){if(n==1)r.firstPoints=values.get(2);String prefix=values.get(0).toUpperCase(Locale.ROOT);if(prefix.matches("L[0-9]+"))r.lCount++;else if(prefix.matches("M[0-9]+"))r.mCount++;}if(values.get(0).equals(number)){if(w){r.rank=n;r.points=values.get(2);}else{r.callId=values.get(1);r.target=values.get(4);}}}
 if(n!=declared)throw new IllegalArgumentException("Unvollständige Liste");if(w){waiting=true;r.total=n;}else calls=true;
 }if(!waiting||!calls)throw new IllegalArgumentException("Tabellen fehlen");return r;
 }
}
