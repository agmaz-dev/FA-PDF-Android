package de.taxil150.app;
public final class FlightRefreshGate {
 public static long allowedAt(long retryAfter,long finishedAt,String finishedDate,String date){return Math.max(retryAfter,date.equals(finishedDate)&&finishedAt>0?finishedAt+15000:0);}
 private FlightRefreshGate(){}
}
