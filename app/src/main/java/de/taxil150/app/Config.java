package de.taxil150.app;
import java.util.Locale;
public final class Config {
 public static final Object LOCK=new Object();
 public static String normalize(String value){
  String n=value==null?"":value.trim().toUpperCase(Locale.ROOT);
  if(!n.matches("[A-Z0-9]{1,16}"))throw new IllegalArgumentException("Bitte eine Taxinummer mit 1–16 Buchstaben oder Ziffern eingeben, z. B. L150.");
  return n;
 }
 public static String callKey(String number,String qnr){return "seen_"+normalize(number)+"_"+qnr;}
}
