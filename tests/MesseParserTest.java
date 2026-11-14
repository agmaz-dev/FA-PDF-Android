import de.taxil150.app.MesseParser;import java.nio.file.*;import java.util.*;
public class MesseParserTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] a)throws Exception{
  check(Arrays.equals(MesseParser.dates("14.11.2026"),new String[]{"2026-11-14","2026-11-14"}),"ein Tag");
  check(Arrays.equals(MesseParser.dates("14. - 15.11.2026"),new String[]{"2026-11-14","2026-11-15"}),"Bereich");
  check(Arrays.equals(MesseParser.dates("31.05. - 04.06.2027"),new String[]{"2027-05-31","2027-06-04"}),"über Monatsgrenze");
  check(Arrays.equals(MesseParser.dates("30.12. - 02.01.2027"),new String[]{"2026-12-30","2027-01-02"}),"über Jahresgrenze");
  check(MesseParser.dates("bald")==null&&MesseParser.dates("32.13.2026")==null,"ungültig");
  String html=new String(Files.readAllBytes(Paths.get("tests/fixtures/messe-kalender-auszug.html")),"UTF-8");
  List<MesseParser.Event> l=MesseParser.dedupe(MesseParser.parse(html));Map<String,MesseParser.Event> by=new HashMap<>();for(MesseParser.Event e:l)by.put(e.name,e);
  check(by.containsKey("Grüne Woche")&&by.get("Grüne Woche").big&&by.get("Grüne Woche").start.equals("2027-01-15")&&by.get("Grüne Woche").end.equals("2027-01-24"),"Grüne Woche");
  check(by.containsKey("ITB Berlin")&&by.get("ITB Berlin").big,"ITB");check(by.containsKey("NUMISMATA")&&!by.get("NUMISMATA").big,"kleine Messe");
  check(by.containsKey("ESPID")&&by.get("ESPID").end.equals("2027-06-04"),"Kongress über Monatsgrenze");
  check(!by.containsKey("Regio Agrar Bayern")&&!by.containsKey("CMS Asia"),"nicht in Berlin aussortiert");
  check(by.keySet().stream().noneMatch(n->n.contains("auf der")),"Unterbereiche aussortiert");
  for(int i=1;i<l.size();i++)check(l.get(i-1).start.compareTo(l.get(i).start)<=0,"sortiert");
  System.out.println("PASS: Messe-Kalender – Datumsformate, echte Einträge, groß/klein, nur Berlin, Unterbereiche, Sortierung ("+l.size()+" Termine im Auszug)");
 }
}
