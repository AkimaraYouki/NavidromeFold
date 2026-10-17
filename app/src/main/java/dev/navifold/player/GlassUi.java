package dev.navifold.player;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;

/** Translucent surfaces only: text, artwork and touch targets retain full opacity. */
final class GlassUi {
 static int alpha(int color,int opacity){return (Math.max(0,Math.min(255,opacity))<<24)|(color&0x00ffffff);}
 static GradientDrawable panel(Context c,boolean dark,int radius,int opacity){
  float scale=opacity/100f;
  GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,dark?
   new int[]{alpha(0xff38383c,Math.round(100*scale)),alpha(0xff202023,Math.round(205*scale))}:
   new int[]{alpha(0xffffffff,Math.round(235*scale)),alpha(0xffeeeeef,Math.round(170*scale))});
  float density=c.getResources().getDisplayMetrics().density;d.setCornerRadius(radius*density);
  d.setStroke(Math.max(1,Math.round(density)),dark?alpha(0xffffffff,Math.round(38*scale)):alpha(0xffffffff,Math.round(180*scale)));return d;
 }
 static GradientDrawable canvas(boolean dark){return new GradientDrawable(GradientDrawable.Orientation.TL_BR,dark?
  new int[]{0xff121214,0xff121214}:new int[]{0xfff2f2f5,0xfff2f2f5});}
}
