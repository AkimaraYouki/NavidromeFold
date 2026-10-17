package dev.navifold.player;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.widget.Button;

/** Icon-only buttons measure 48dp, without Button's empty text baseline below a top drawable. */
final class CompactIconButton extends androidx.appcompat.widget.AppCompatButton {
 CompactIconButton(Context c){super(c);setBackgroundTintList(null);setIncludeFontPadding(false);}
 int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 @Override protected void onMeasure(int width,int height){setMeasuredDimension(resolveSize(dp(48),width),resolveSize(dp(48),height));}
 @Override protected void onDraw(Canvas canvas){Drawable[] icons=getCompoundDrawables();Drawable icon=icons[1]!=null?icons[1]:icons[0];if(icon==null)return;int half=dp(12),cx=getWidth()/2,cy=getHeight()/2;icon.setBounds(cx-half,cy-half,cx+half,cy+half);icon.draw(canvas);}
}
