import de.taxil150.app.Parser;import de.taxil150.app.Config;import java.nio.file.*;
public class ParserTest {
 static String table(String heading,String rows){return "<table><th>"+heading+"</th>"+rows+"</table>";}
 static String row(String...v){String s="<tr>";for(String x:v)s+="<td>"+x+"</td>";return s+"</tr>";}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[]args)throws Exception{
 String empty=table("Aufgerufen (0)","");String waiting=table("Wartend (2)",row("L1500","12","9")+row("&nbsp;<span>L150</span>","13","8"));
 Parser.Result r=Parser.parse(empty+waiting);check(r.rank==2&&r.total==2&&r.points.equals("8")&&r.firstPoints.equals("9")&&r.callId.isEmpty());
 r=Parser.parse(table("Aufgerufen (1)",row("L150","13","00:03","8","1N"))+table("Wartend (0)",""));check(r.callId.equals("13")&&r.target.equals("1N")&&r.rank==0);
 r=Parser.parse(table("Aufgerufen (1)",row("L150","13","04:57","8",""))+table("Wartend (0)",""));check(r.callId.equals("13")&&r.target.isEmpty());
 r=Parser.parse(empty+table("Wartend (0)",""));check(r.rank==0&&r.total==0&&r.firstPoints.equals("–"));
 for(String bad:new String[]{"Login",empty+table("Wartend (2)",row("L150","13","8")),table("Wartend (1)",row("L150","13"))+empty}){boolean rejected=false;try{Parser.parse(bad);}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
 
 r=Parser.parse(empty+waiting," l150 ");check(r.rank==2);
 r=Parser.parse(empty+waiting,"L1500");check(r.rank==1&&r.points.equals("9"));
 r=Parser.parse(table("Aufgerufen (2)",row("L150","13","00:03","8","1N")+row("M200","13","00:04","7","1S"))+table("Wartend (0)",""),"m200");check(r.callId.equals("13")&&r.target.equals("1S"));
 check(!Config.callKey("L150","13").equals(Config.callKey("M200","13")));
 check(Config.callKey("l150","13").equals(Config.callKey("L150","13")));
 for(String bad:new String[]{"","L 150","<script>","ABCDEFGHIJKLMNOPQ"}){boolean rejected=false;try{Config.normalize(bad);}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
 r=Parser.parse(empty+table("Wartend (4)",row("L001","1","3")+row("M009","2","2")+row("M010","3","1")+row("X001","4","0")));check(r.lCount==1&&r.mCount==2&&r.total==4);
 r=Parser.parse(table("Aufgerufen (1)",row("L150","13","00:03","8","1N"))+table("Wartend (1)",row("M005","14","1")));check(r.lCount==0&&r.mCount==1);
 r=Parser.parse(table("Aufgerufen (1)",row("M999","900","00:01","99","1N"))+table("Wartend (2)",row("L001","1","0")+row("L150","2","10")));check(r.firstPoints.equals("0")&&r.points.equals("10"));
 r=Parser.parse(empty+table("Wartend (2)",row("M002","1","38")+row("L150","2","21")));check(r.firstPoints.equals("38")&&r.points.equals("21")&&!r.firstPoints.equals("17"));
 r=Parser.parse(empty+table("Wartend (2)",row("L150","1","38")+row("M002","2","21")));check(r.firstPoints.equals("38")&&r.points.equals("38"));
 System.out.println("PASS: Parser, alternative Taxinummern, Normalisierung, Eingabevalidierung und getrennte Aufrufschlüssel"); if(args.length>0){r=Parser.parse(Files.readString(Path.of(args[0])));check(r.lCount+r.mCount<=r.total);System.out.println("Live-HTML geparst: Gesamt="+r.total+", Brandenburg(L)="+r.lCount+", Berlin(M)="+r.mCount+", Rang L150="+r.rank);}
 }
}
