package de.taxil150.app;
import java.util.*;

/**
 * Pure calculation of the departure estimate and the Nord/Süd statistics.
 * No Android classes, so it can be tested on a plain JVM.
 *
 * Inputs are the calls the app has seen (time of call and side) and the own
 * rank samples since joining the queue. Nothing is invented: when there is not
 * enough observed data, {@link Result#available} stays false.
 */
public final class Forecast {
 public static final long MIN=60_000L;
 /** A call seen in the APCOA list. side is "N", "S" or "". */
 public static final class Call { public final long at; public final String side; public Call(long at,String side){this.at=at;this.side=side==null?"":side;} }
 /** Own rank at a moment in time. */
 public static final class Sample { public final long at; public final int rank; public Sample(long at,int rank){this.at=at;this.rank=rank;} }

 public static final class Result {
  public boolean available;          // enough data for an estimate
  public double perMinute;           // taxis leaving the queue per minute
  public int etaMin,earlyMin,lateMin; // minutes from now (only when rank>0 and available)
  public int joinNowMin;             // estimate for a taxi joining now at the end of the queue
  public double everyMinutes;        // average gap between calls
  public int north,south,lastHourCalls;
  public final int[][] buckets=new int[4][2]; // last hour in 15-minute steps, oldest first: [n][0]=Nord,[n][1]=Süd
  public int likelyNorthTenths=-1;   // 0..10, -1 unknown
  public String basis="";            // "rank" or "calls"
 }

 /** Calls per minute in [now-window, now], counted only inside the trusted coverage. */
 static double callRate(List<Call> calls,long now,long window,long coverageStart){
  long from=Math.max(now-window,coverageStart);double minutes=(now-from)/(double)MIN;if(minutes<10)return -1;
  int n=0;for(Call c:calls)if(c.at>from&&c.at<=now)n++;
  if(n<3)return -1;return n/minutes;
 }
 /** Own rank drop per minute over [now-window, now]; -1 when the track is too short. */
 static double rankRate(List<Sample> samples,long now,long window){
  Sample first=null,last=null;for(Sample s:samples){if(s.at<now-window||s.at>now||s.rank<=0)continue;if(first==null)first=s;last=s;}
  if(first==null||last==null)return -1;double minutes=(last.at-first.at)/(double)MIN;int drop=first.rank-last.rank;
  if(minutes<8||drop<2)return -1;return drop/minutes;
 }

 public static Result compute(long now,List<Call> calls,long coverageStart,int rank,int total,List<Sample> samples){
  Result r=new Result();
  for(Call c:calls){if(c.at<=now-60*MIN||c.at>now)continue;r.lastHourCalls++;if("N".equals(c.side))r.north++;else if("S".equals(c.side))r.south++;int b=(int)((c.at-(now-60*MIN))/(15*MIN));if(b>=0&&b<4){if("N".equals(c.side))r.buckets[b][0]++;else if("S".equals(c.side))r.buckets[b][1]++;}}
  if(r.north+r.south>=4)r.likelyNorthTenths=(int)Math.round(10.0*r.north/(r.north+r.south));
  double c30=callRate(calls,now,30*MIN,coverageStart),c15=callRate(calls,now,15*MIN,coverageStart),c60=callRate(calls,now,60*MIN,coverageStart);
  double own=rank>0?rankRate(samples,now,30*MIN):-1;
  double best=own>0?own:c30>0?c30:c60;
  if(best<=0)return r;
  r.available=true;r.perMinute=best;r.basis=own>0?"rank":"calls";
  double callBased=c30>0?c30:c60>0?c60:best;r.everyMinutes=1/callBased;
  // Range from the spread of the observed rates, at least -25 % / +40 % around the estimate:
  // calls come in bursts and taxis with more points can be placed ahead (observed in real APCOA data).
  double hi=best,lo=best;for(double v:new double[]{c15,c30,c60,own})if(v>0){hi=Math.max(hi,v);lo=Math.min(lo,v);}
  if(rank>0){double eta=rank/best;r.etaMin=(int)Math.max(1,Math.round(eta));r.earlyMin=(int)Math.max(1,Math.floor(Math.min(eta*0.75,rank/hi)));r.lateMin=(int)Math.ceil(Math.max(eta*1.4,rank/lo));if(r.earlyMin>r.etaMin)r.earlyMin=r.etaMin;if(r.lateMin<r.etaMin)r.lateMin=r.etaMin;}
  r.joinNowMin=(int)Math.max(1,Math.round((total+1)/callBased));
  return r;
 }
}
