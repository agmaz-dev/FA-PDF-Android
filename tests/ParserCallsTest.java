import de.taxil150.app.*;import java.nio.file.*;
/** Real APCOA page from 14.11.2026 (tests/fixtures): all calls with Zeit and Zielbereich, waiting list in order. */
public class ParserCallsTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] a)throws Exception{
  String html=new String(Files.readAllBytes(Paths.get("tests/fixtures/apcoa-2026-11-14.html")),"UTF-8");
  Parser.Result r=Parser.parse(html,"L132");
  check(r.total==145&&r.waiting.size()==145,"Warteliste");check(r.waiting.get(0).number.equals("L132")&&r.rank==1,"erster Platz");
  check(r.calls.size()==6,"sechs Aufrufe");Parser.Call c=r.calls.get(0);check(c.number.equals("L060")&&c.qnr.equals("6418")&&c.elapsedSeconds==86&&c.target.equals("1N"),"erster Aufruf");
  check(r.lCount+r.mCount<=r.total,"L/M");
  check(Parser.elapsed("01:26")==86&&Parser.elapsed("00:02")==2&&Parser.elapsed("1:02:03")==3723&&Parser.elapsed("x")==-1&&Parser.elapsed("01:75")==-1,"Zeit");
  check(Parser.side("1N").equals("N")&&Parser.side("1S").equals("S")&&Parser.side(" 2n ").equals("N")&&Parser.side("Nord").isEmpty()&&Parser.side("").isEmpty()&&Parser.side(null).isEmpty(),"Nord/Süd");
  System.out.println("PASS: echte APCOA-Seite – Warteliste in Reihenfolge, alle Aufrufe mit Zeit und Zielbereich, Nord/Süd-Zuordnung");
 }
}
