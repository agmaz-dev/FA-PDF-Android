import de.taxil150.app.FlightRefreshGate;
public class FlightRefreshGateTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[]args){
  check(FlightRefreshGate.allowedAt(0,0,"","2026-10-20")==0);
  check(FlightRefreshGate.allowedAt(0,100000,"2026-10-20","2026-10-20")==115000);
  check(FlightRefreshGate.allowedAt(0,100000,"2026-10-20","2026-10-21")==0);
  check(FlightRefreshGate.allowedAt(400000,100000,"2026-10-20","2026-10-20")==400000);
  check(FlightRefreshGate.allowedAt(400000,100000,"2026-10-20","2026-10-21")==400000);
  System.out.println("PASS: refresh gate, 15s same-date cooldown, changed date, HTTP backoff across dates");
 }
}
