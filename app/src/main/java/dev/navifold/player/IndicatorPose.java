package dev.navifold.player;

import android.content.Context;
import android.graphics.Point;
import android.view.*;

/** Display-only eligibility. Physical tilt and motion sensors never hide an indicator. */
final class IndicatorPose {
 final Point size=new Point();final Context context;final WindowManager windows;
 IndicatorPose(Context context){this.context=context;windows=context.getSystemService(WindowManager.class);}
 boolean eligible(){Display display=windows.getDefaultDisplay();display.getRealSize(size);
  return IndicatorPolicy.eligible(Math.min(size.x,size.y)/context.getResources().getDisplayMetrics().density,display.getRotation());
 }
}
