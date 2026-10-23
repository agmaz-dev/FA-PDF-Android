package de.taxil150.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.webkit.WebView;

/** Owns the colors behind transparent system bars, including Android 15+. */
public final class WindowAppearance {
 public static void attach(Activity activity, WebView web) {
  final int background=activity.getColor(R.color.app_background);
  final int navigation=activity.getColor(R.color.navigation_background);
  Window window=activity.getWindow();
  window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(background));
  window.setStatusBarColor(Color.TRANSPARENT);
  window.setNavigationBarColor(Color.TRANSPARENT);
  if(Build.VERSION.SDK_INT>=28)window.setNavigationBarDividerColor(navigation);
  if(Build.VERSION.SDK_INT>=29){window.setStatusBarContrastEnforced(false);window.setNavigationBarContrastEnforced(false);}
  if(Build.VERSION.SDK_INT>=30)window.setDecorFitsSystemWindows(false);
  else window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

  LinearLayout root=new LinearLayout(activity);
  root.setOrientation(LinearLayout.VERTICAL);
  root.setBackgroundColor(background);
  View top=new View(activity),bottom=new View(activity);
  top.setBackgroundColor(background);bottom.setBackgroundColor(navigation);
  root.addView(top,new LinearLayout.LayoutParams(-1,0));
  web.setBackgroundColor(background);
  web.setOverScrollMode(View.OVER_SCROLL_NEVER);
  root.addView(web,new LinearLayout.LayoutParams(-1,0,1f));
  root.addView(bottom,new LinearLayout.LayoutParams(-1,0));
  root.setOnApplyWindowInsetsListener((v,insets)->{
   int left,right,upper,lower;
   if(Build.VERSION.SDK_INT>=30){
    Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
    left=safe.left;right=safe.right;upper=safe.top;lower=safe.bottom;
   }else{
    left=insets.getSystemWindowInsetLeft();right=insets.getSystemWindowInsetRight();
    upper=insets.getSystemWindowInsetTop();lower=insets.getSystemWindowInsetBottom();
   }
   v.setPadding(left,0,right,0);
   if(top.getLayoutParams().height!=upper){top.getLayoutParams().height=upper;top.requestLayout();}
   if(bottom.getLayoutParams().height!=lower){bottom.getLayoutParams().height=lower;bottom.requestLayout();}
   return insets.consumeSystemWindowInsets();
  });
  activity.setContentView(root);
  if(Build.VERSION.SDK_INT>=30){
   WindowInsetsController controller=window.getInsetsController();
   if(controller!=null)controller.setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
  }
  root.requestApplyInsets();
 }
 private WindowAppearance(){}
}
