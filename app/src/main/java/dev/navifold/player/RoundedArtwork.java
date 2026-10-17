package dev.navifold.player;
import android.content.Context;import android.graphics.*;
/** Explicit clipping also works when decor.draw renders into the stationary window bitmap. */
class RoundedArtwork extends androidx.appcompat.widget.AppCompatImageView {
 private final Path roundPath=new Path();private final float radius;
 RoundedArtwork(Context c,int radiusDp){super(c);radius=radiusDp*c.getResources().getDisplayMetrics().density;}
 @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);roundPath.reset();roundPath.addRoundRect(0,0,w,h,radius,radius,Path.Direction.CW);}
 @Override public void draw(Canvas canvas){int save=canvas.save();canvas.clipPath(roundPath);super.draw(canvas);canvas.restoreToCount(save);}
}
