package dev.navifold.player;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.provider.Settings;
import android.view.*;

/** Camera-centred indicators. System-owned status bar surfaces remain above application overlays. */
final class QualityOverlay {
 final Context context;final WindowManager windows;final IndicatorPose pose;
 WindowManager.LayoutParams params;Capsule capsule;boolean visible;
 QualityOverlay(Context c){context=c;windows=c.getSystemService(WindowManager.class);pose=new IndicatorPose(c);}
 int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
 void update(boolean playing){AppSettings s=AppSettings.cached(context);
  boolean requested=s.indicatorMode==2&&Settings.canDrawOverlays(context)&&playing&&AppVisibility.activities==0;
  if(!requested||!pose.eligible()){hide();return;}
  if(capsule==null){capsule=new Capsule(context);capsule.setOnClickListener(v->context.startActivity(new Intent(context,LockPlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
   params=new WindowManager.LayoutParams(dp(s.islandWidthDp),dp(Math.max(40,s.ringCompactDp+12)),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
   params.gravity=Gravity.TOP|Gravity.LEFT;params.setTitle("NaviFold quality island");
   if(android.os.Build.VERSION.SDK_INT>=28)params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
   capsule.setOnApplyWindowInsetsListener((v,insets)->{position(insets);return insets;});
  }
  if(!visible)try{windows.addView(capsule,params);visible=true;position(capsule.getRootWindowInsets());}catch(RuntimeException ignored){visible=false;}
  if(visible){position(capsule.getRootWindowInsets());capsule.invalidate();}
 }
 void position(WindowInsets insets){if(!visible)return;Point size=new Point();windows.getDefaultDisplay().getRealSize(size);float cx=size.x/2f,cy=dp(24);
  Rect hole=camera(android.os.Build.VERSION.SDK_INT>=30?windows.getMaximumWindowMetrics().getWindowInsets():insets,context.getResources().getDisplayMetrics().density);
  if(hole!=null&&android.os.Build.VERSION.SDK_INT<30){int[] location=new int[2];capsule.getLocationOnScreen(location);hole=new Rect(hole);hole.offset(location[0],location[1]);}
  if(hole!=null){cx=hole.exactCenterX();cy=hole.exactCenterY();}
  AppSettings s=AppSettings.cached(context);int width=Math.min(size.x,dp(s.islandWidthDp)),height=dp(Math.max(40,s.ringCompactDp+12));
  int x=IndicatorPolicy.clamp(Math.round(cx)+dp(s.islandCompactX),0,size.x)-width/2;
  int y=IndicatorPolicy.clamp(Math.round(cy)+dp(s.islandCompactY),0,size.y)-height/2;
  if(params.x==x&&params.y==y&&params.width==width&&params.height==height)return;
  params.x=x;params.y=y;params.width=width;params.height=height;
  try{windows.updateViewLayout(capsule,params);}catch(RuntimeException ignored){}
 }
 void hide(){if(visible){try{windows.removeView(capsule);}catch(RuntimeException ignored){}visible=false;}}
 void close(){hide();}
 static Rect camera(WindowInsets insets,float density){
  if(android.os.Build.VERSION.SDK_INT>=28&&insets!=null&&insets.getDisplayCutout()!=null)
   for(Rect r:insets.getDisplayCutout().getBoundingRects())if(r.top<=80*density&&r.width()<120*density)return r;
  return null;
 }
 static void island(Canvas canvas,Paint p,float cx,float cy,float width,float height,float diameter,float density,float scaledDensity,QualityInfo q){
  p.clearShadowLayer();p.setStyle(Paint.Style.FILL);p.setColor(Color.BLACK);
  canvas.drawRoundRect(cx-width/2,cy-height/2,cx+width/2,cy+height/2,height/2,height/2,p);
  p.setColor(q.color);p.setTextSize(11*scaledDensity);
  String[] parts=IndicatorPolicy.parts(q.label);float gap=diameter/2+10*density;
  float room=width/2-gap-12*density;
  Paint.FontMetrics fm=p.getFontMetrics();float baseline=cy-(fm.ascent+fm.descent)/2;
  for(int side=0;side<2;side++){String label=parts[side];while(label.length()>1&&p.measureText(label)>room)label=label.substring(0,label.length()-1);
   if(room<=0)continue;float textWidth=p.measureText(label);float areaCenter=cx+(side==0?-1:1)*(gap+room/2);
   canvas.drawText(label,areaCenter-textWidth/2,baseline,p);
  }
 }
 static void ring(Canvas c,Paint p,float cx,float cy,float diameter,float density,QualityInfo q){
  p.clearShadowLayer();p.setStyle(Paint.Style.FILL);p.setColor(Color.BLACK);c.drawCircle(cx,cy,diameter/2,p);
  p.setStyle(Paint.Style.STROKE);p.setColor(q.color);p.setStrokeWidth(1.6f*density);p.setShadowLayer(3*density,0,0,q.color);c.drawCircle(cx,cy,diameter/2,p);
  p.clearShadowLayer();p.setStyle(Paint.Style.FILL);
 }
 static final class Capsule extends View {
  final Paint paint=new Paint(3);
  Capsule(Context c){super(c);paint.setTypeface(UiAssets.semibold(c));setContentDescription("음질 아일랜드 · 중앙 카메라 여백");setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
  @Override protected void onDraw(Canvas c){AppSettings s=AppSettings.cached(getContext());float d=getResources().getDisplayMetrics().density,sd=getResources().getDisplayMetrics().scaledDensity;
   island(c,paint,getWidth()/2f,getHeight()/2f,getWidth(),getHeight()-8*d,s.ringCompactDp*d,d,sd,AudioQuality.current);
   ring(c,paint,getWidth()/2f,getHeight()/2f,s.ringCompactDp*d,d,AudioQuality.current);
  }
 }
 static final class CameraRing extends View {
  final Paint paint=new Paint(3);final IndicatorPose pose;String rendered="";final Runnable tick=new Runnable(){public void run(){AppSettings s=AppSettings.cached(getContext());String key=pose.eligible()+"|"+AudioQuality.trackId+"|"+AudioQuality.current.label+"|"+AudioQuality.current.color+"|"+s.indicatorMode+"|"+s.indicatorStyle+"|"+s.ringCompactDp+"|"+s.islandWidthDp+"|"+s.islandCompactX+"|"+s.islandCompactY;if(!key.equals(rendered)){rendered=key;invalidate();}postDelayed(this,250);}};
  CameraRing(Context c){super(c);paint.setTypeface(UiAssets.semibold(c));pose=new IndicatorPose(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();removeCallbacks(tick);if(getWindowVisibility()==VISIBLE)post(tick);}
  @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);removeCallbacks(tick);if(visibility==VISIBLE&&isAttachedToWindow())post(tick);}
  @Override protected void onDetachedFromWindow(){removeCallbacks(tick);super.onDetachedFromWindow();}
  @Override protected void onDraw(Canvas c){AppSettings s=AppSettings.cached(getContext());if(s.indicatorMode==0||AudioQuality.trackId.isEmpty()||!pose.eligible())return;
   float d=getResources().getDisplayMetrics().density;Rect hole=camera(getRootWindowInsets(),d);if(hole==null)return;int[] location=new int[2];getLocationOnScreen(location);
   float cx=hole.exactCenterX()-location[0]+s.islandCompactX*d,cy=hole.exactCenterY()-location[1]+s.islandCompactY*d,diameter=s.ringCompactDp*d;
   if(s.indicatorStyle!=0)island(c,paint,cx,cy,Math.min(getWidth(),s.islandWidthDp*d),Math.max(32*d,diameter+8*d),diameter,d,getResources().getDisplayMetrics().scaledDensity,AudioQuality.current);
   if(s.indicatorStyle!=1)ring(c,paint,cx,cy,diameter,d,AudioQuality.current);
  }
 }
}
