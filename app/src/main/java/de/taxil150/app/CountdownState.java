package de.taxil150.app;
/** Deadline based countdown. Elapsed realtime survives app/process restarts and includes sleep. */
public final class CountdownState {
 public static final long DURATION=45*60*1000L;
 public long paused=DURATION,wallEnd,elapsedEnd;public int boot=-1;public boolean running,finished;
 public long remaining(long wall,long elapsed,int currentBoot){if(!running)return Math.max(0,Math.min(DURATION,paused));long left=boot>=0&&currentBoot==boot?elapsedEnd-elapsed:wallEnd-wall;return Math.max(0,Math.min(DURATION,left));}
 public void toggle(long wall,long elapsed,int currentBoot){long left=remaining(wall,elapsed,currentBoot);if(running){running=false;paused=left;finished=left==0;}else{if(left==0)left=DURATION;paused=left;wallEnd=wall+left;elapsedEnd=elapsed+left;boot=currentBoot;running=true;finished=false;}}
 public boolean settle(long wall,long elapsed,int currentBoot){if(running&&remaining(wall,elapsed,currentBoot)==0){running=false;paused=0;finished=true;return true;}return false;}
 public void reset(){paused=DURATION;wallEnd=elapsedEnd=0;boot=-1;running=false;finished=false;}
}
