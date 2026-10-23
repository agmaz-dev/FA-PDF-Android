import de.taxil150.app.CountdownState;
public class CountdownStateTest {
 static void check(boolean yes){if(!yes)throw new AssertionError();}
 public static void main(String[]args){long wall=10000000,elapsed=100000;CountdownState t=new CountdownState();check(t.remaining(wall,elapsed,3)==2700000);t.toggle(wall,elapsed,3);check(t.running&&t.remaining(wall+1000,elapsed+1000,3)==2699000);
 check(t.remaining(wall+3600000,elapsed+1000,3)==2699000); // wall-clock correction does not change same-boot timer
 t.toggle(wall+10000,elapsed+10000,3);check(!t.running&&t.remaining(wall+999999,elapsed+999999,3)==2690000);t.toggle(wall+20000,elapsed+20000,3);check(t.remaining(wall+21000,elapsed+21000,3)==2689000);
 CountdownState restored=new CountdownState();restored.running=t.running;restored.paused=t.paused;restored.wallEnd=t.wallEnd;restored.elapsedEnd=t.elapsedEnd;restored.boot=t.boot;check(restored.remaining(wall+1200000,elapsed+1200000,3)==1510000);check(restored.remaining(wall+1200000,5000,4)==1510000); // reboot fallback
 check(restored.settle(wall+3000000,elapsed+3000000,3));check(restored.finished&&!restored.running&&restored.remaining(wall,elapsed,3)==0);restored.toggle(wall+3000000,elapsed+3000000,3);check(restored.running&&restored.remaining(wall+3000000,elapsed+3000000,3)==2700000);restored.reset();check(!restored.running&&!restored.finished&&restored.paused==2700000);
 t.reset();t.toggle(wall,elapsed,3);t.toggle(wall+2700000,elapsed+2700000,3);check(t.finished&&!t.running); // pause exactly at expiry does not restart
 System.out.println("PASS: 45-minute deadline, pause/resume, sleep/process restoration, clock correction, reboot fallback, expiry, restart/reset");}
}
