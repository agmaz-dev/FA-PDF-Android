import de.taxil150.app.Forecast;import java.util.*;
public class ForecastTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static final long M=Forecast.MIN,NOW=1_800_000_000_000L;
 public static void main(String[] a){
  // Zu wenig Beobachtung: keine Prognose, nichts erfunden.
  List<Forecast.Call> few=new ArrayList<>();few.add(new Forecast.Call(NOW-2*M,"N"));few.add(new Forecast.Call(NOW-M,"S"));
  Forecast.Result r=Forecast.compute(NOW,few,NOW-4*M,12,26,new ArrayList<>());check(!r.available&&r.etaMin==0,"zu wenig Daten");check(r.north==1&&r.south==1&&r.lastHourCalls==2,"Zählung trotzdem");
  // 30 Minuten lang alle 2 Minuten ein Aufruf, 2/3 Nord.
  List<Forecast.Call> calls=new ArrayList<>();for(int i=0;i<15;i++)calls.add(new Forecast.Call(NOW-(i*2+1)*M,i%3==2?"S":"N"));
  r=Forecast.compute(NOW,calls,NOW-40*M,12,26,new ArrayList<>());
  check(r.available&&"calls".equals(r.basis),"Aufrufrate");check(Math.abs(r.everyMinutes-2)<0.01,"alle 2 Min");check(r.etaMin==24,"Platz 12 → 24 Min, war "+r.etaMin);
  check(r.earlyMin<=18&&r.lateMin>=33&&r.earlyMin<=r.etaMin&&r.lateMin>=r.etaMin,"Spanne");check(r.north==10&&r.south==5&&r.likelyNorthTenths==7,"Nord/Süd");
  check(r.joinNowMin==54,"Einreihen 27 Taxis → 54 Min, war "+r.joinNowMin);
  int sum=0;for(int[] b:r.buckets)sum+=b[0]+b[1];check(sum==15,"Viertelstunden");
  // Eigener Verlauf hat Vorrang: von Platz 20 auf 10 in 20 Minuten = 0,5/Min → 20 Min.
  List<Forecast.Sample> s=new ArrayList<>();for(int i=0;i<=20;i++)s.add(new Forecast.Sample(NOW-(20-i)*M,20-i/2));
  r=Forecast.compute(NOW,calls,NOW-40*M,10,26,s);check("rank".equals(r.basis)&&r.etaMin==20,"eigener Verlauf, war "+r.etaMin);
  // Platz kann steigen (Taxis mit mehr Punkten kommen davor): kein Absturz, sinnvolle Werte.
  s.add(new Forecast.Sample(NOW,13));r=Forecast.compute(NOW,calls,NOW-40*M,13,26,s);check(r.available&&r.etaMin>0,"Platz gestiegen");
  // Alte Aufrufe zählen nicht in die letzte Stunde.
  List<Forecast.Call> old=new ArrayList<>(calls);old.add(new Forecast.Call(NOW-90*M,"N"));r=Forecast.compute(NOW,old,NOW-120*M,12,26,new ArrayList<>());check(r.lastHourCalls==15,"nur letzte Stunde");
  // Überwachung erst seit 5 Minuten: keine Hochrechnung aus wenigen Minuten.
  r=Forecast.compute(NOW,calls,NOW-5*M,12,26,new ArrayList<>());check(!r.available,"zu kurze Beobachtung");
  System.out.println("PASS: Prognose – Mindestdaten, Aufrufrate, eigener Verlauf mit Vorrang, Spanne, Nord/Süd, Viertelstunden, Einreihen, steigender Platz");
 }
}
