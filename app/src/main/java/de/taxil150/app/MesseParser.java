package de.taxil150.app;
import java.util.*;import java.util.regex.*;

/**
 * Reads the public Messe Berlin event calendar (https://www.messe-berlin.de/de/veranstaltungen/veranstaltungskalender/).
 * Each entry is a list item with a &lt;time&gt; (e.g. "14. - 15.11.2026"), a venue, a title heading and an optional subtitle.
 * Only events in Berlin are kept. Pure Java, testable without Android.
 */
public final class MesseParser {
 public static final class Event {
  public final String name,start,end,desc,venue;public final boolean big;
  public Event(String name,String start,String end,String desc,String venue){this.name=name;this.start=start;this.end=end==null||end.isEmpty()?start:end;this.desc=desc==null?"":desc;this.venue=venue==null?"":venue;this.big=isBig(name);}
 }
 static final Pattern BIG=Pattern.compile("(?i)gr(ü|ue)ne woche|fruit logistica|\\bitb berlin\\b|^itb\\b|\\bifa\\b|innotrans|\\bila\\b|outventure|dmea|bar convent|smart country");
 static final Pattern BERLIN=Pattern.compile("(?i)berlin|citycube|funkturm|messegel(ä|ae)nde|halle|^h\\d|hub\\s?\\d|expo ?center|sommergarten|marshall");
 static final Pattern NOT_BERLIN=Pattern.compile("(?i)singapore|singapur|jakarta|indonesia|augsburg|m(ü|ue)hlengeez|shanghai|dubai|istanbul|bangkok");
 static final Set<String> SKIP=new HashSet<>(Arrays.asList("Mehr erfahren","Termin speichern","Tickets kaufen","Kategorien","Kongress","Datum auswählen"));
 static boolean isBig(String name){return BIG.matcher(name).find();}
 static String text(String s){return s.replaceAll("(?s)<[^>]*>","").replace("&amp;","&").replace("&quot;","\"").replace("&#x27;","'").replace("&#39;","'").replace("&nbsp;"," ").replace("&#160;"," ").replaceAll("\\s+"," ").trim();}
 /** "14.11.2026", "14. - 15.11.2026", "31.05. - 04.06.2027", "30.12.2026 - 02.01.2027" → {start,end} as ISO dates, or null. */
 public static String[] dates(String raw){String s=raw.replaceAll("\\s+","").replace('–','-');
  Matcher m=Pattern.compile("^(\\d{1,2})\\.(?:(\\d{1,2})\\.(\\d{4})?)?-(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})$").matcher(s);
  if(m.matches()){int ed=Integer.parseInt(m.group(4)),em=Integer.parseInt(m.group(5)),ey=Integer.parseInt(m.group(6));int sd=Integer.parseInt(m.group(1));int sm=m.group(2)==null?em:Integer.parseInt(m.group(2));int sy=m.group(3)==null?(sm>em?ey-1:ey):Integer.parseInt(m.group(3));String a=iso(sy,sm,sd),b=iso(ey,em,ed);return a==null||b==null||a.compareTo(b)>0?null:new String[]{a,b};}
  m=Pattern.compile("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})$").matcher(s);
  if(m.matches()){String a=iso(Integer.parseInt(m.group(3)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(1)));return a==null?null:new String[]{a,a};}
  return null;}
 static String iso(int y,int m,int d){if(m<1||m>12||d<1||d>31||y<2000||y>2100)return null;return String.format(Locale.ROOT,"%04d-%02d-%02d",y,m,d);}
 public static String cleanName(String n){return n.replaceAll("\\s+(19|20)\\d{2}$","").trim();}
 public static List<Event> parse(String html){
  List<Event> out=new ArrayList<>();String[] parts=html.split("data-component=\"event-list-item\"");
  for(int i=1;i<parts.length;i++){String item=parts[i].replaceAll("(?s)<svg.*?</svg>","");
   Matcher t=Pattern.compile("(?s)<time[^>]*>(.*?)</time>").matcher(item);if(!t.find())continue;String[] d=dates(text(t.group(1)));if(d==null)continue;
   Matcher h=Pattern.compile("(?s)<h[2-5][^>]*>(.*?)</h[2-5]>").matcher(item);if(!h.find())continue;String title=text(h.group(1));if(title.isEmpty())continue;
   List<String> texts=new ArrayList<>();Matcher x=Pattern.compile(">([^<>]+)<").matcher(item.substring(t.end()));while(x.find()){String v=text(x.group(1));if(!v.isEmpty())texts.add(v);}
   String venue=texts.isEmpty()?"":texts.get(0);int ti=texts.indexOf(title);String desc="";
   if(ti>=0&&ti+1<texts.size()){String nx=texts.get(ti+1);if(!SKIP.contains(nx)&&!nx.matches("(?i)(januar|februar|märz|april|mai|juni|juli|august|september|oktober|november|dezember) \\d{4}"))desc=nx;}
   if(NOT_BERLIN.matcher(venue).find()||!BERLIN.matcher(venue).find())continue;
   if(title.matches("(?i).*\\bauf der\\b.*"))continue; // sub-areas of a larger fair, the main fair is listed separately
   if(desc.length()>90)desc=desc.substring(0,87).replaceAll("\\s+\\S*$","")+" …";
   out.add(new Event(cleanName(title),d[0],d[1],desc,venue.matches("(?i)berlin, deutschland")?"Berlin":venue));}
  return out;}
 public static List<Event> dedupe(List<Event> list){Map<String,Event> m=new LinkedHashMap<>();for(Event e:list)m.putIfAbsent(e.name.toLowerCase(Locale.ROOT)+"|"+e.start,e);List<Event> out=new ArrayList<>(m.values());out.sort((a,b)->a.start.compareTo(b.start));return out;}
}
