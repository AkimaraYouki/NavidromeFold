package dev.navifold.player;
import android.content.Context;
import android.graphics.*;
import android.view.View;
final class TransportButton extends View {
 enum Kind {PLAY,PAUSE,PREVIOUS,NEXT,BACKWARD,FORWARD,SHUFFLE,REPEAT,QUEUE,INFO,CLOSE,DELETE,DRAG}
 private android.graphics.drawable.Drawable icon;private int iconResource;
 private Kind kind;private final int color,fill;private boolean buffering;private final Paint paint=new Paint(3);
 TransportButton(Context c,Kind kind,int color,int fill,String label,Runnable click){super(c);this.kind=kind;this.color=color;this.fill=fill;setContentDescription(label);setTooltipText(label);setFocusable(true);setClickable(true);setOnClickListener(v->click.run());}
 void setPlaying(boolean playing){Kind k=playing?Kind.PAUSE:Kind.PLAY;if(kind!=k){kind=k;setContentDescription(playing?"일시정지":"재생");invalidate();}}
 void setBuffering(boolean b){if(buffering!=b){buffering=b;invalidate();}}
 @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo i){super.onInitializeAccessibilityNodeInfo(i);i.setClassName(android.widget.Button.class.getName());}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float size=Math.min(getWidth(),getHeight()),cx=getWidth()/2f,cy=getHeight()/2f;paint.setColor(fill);paint.setAlpha(isPressed()?170:255);if(fill!=Color.TRANSPARENT)canvas.drawCircle(cx,cy,size*.48f,paint);
 int id;switch(kind){case PLAY:id=R.drawable.ic_play;break;case PAUSE:id=R.drawable.ic_pause;break;case PREVIOUS:id=R.drawable.ic_previous;break;case NEXT:id=R.drawable.ic_next;break;case BACKWARD:id=R.drawable.ic_backward;break;case FORWARD:id=R.drawable.ic_forward;break;case SHUFFLE:id=R.drawable.ic_shuffle;break;case REPEAT:id=R.drawable.ic_repeat;break;case QUEUE:id=R.drawable.ic_queue;break;case INFO:id=R.drawable.ic_info;break;case CLOSE:id=R.drawable.ic_close;break;case DELETE:id=R.drawable.ic_delete;break;default:id=R.drawable.ic_drag;}
 if(icon==null||iconResource!=id){icon=androidx.appcompat.content.res.AppCompatResources.getDrawable(getContext(),id).mutate();iconResource=id;}android.graphics.drawable.Drawable d=icon;d.setTint(isEnabled()?color:Color.GRAY);float h=Math.min(size*.3f,(kind==Kind.PLAY||kind==Kind.PAUSE?18:12)*getResources().getDisplayMetrics().density);d.setBounds((int)(cx-h),(int)(cy-h),(int)(cx+h),(int)(cy+h));d.draw(canvas);
 if(buffering){paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*getResources().getDisplayMetrics().density);canvas.drawArc(cx-size*.46f,cy-size*.46f,cx+size*.46f,cy+size*.46f,(android.os.SystemClock.uptimeMillis()%1000)*.36f,100,false,paint);paint.setStyle(Paint.Style.FILL);postInvalidateOnAnimation();}
 }
}
