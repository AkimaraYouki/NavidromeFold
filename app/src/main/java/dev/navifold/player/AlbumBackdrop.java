package dev.navifold.player;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Preblur a small cover once; moving sheets never translate a live RenderEffect layer. */
final class AlbumBackdrop extends androidx.appcompat.widget.AppCompatImageView {
 int blurStrength;float blurRadiusDp;private Bitmap blurred;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 AlbumBackdrop(Context c){super(c);setScaleType(ScaleType.CENTER_CROP);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 void setBlurStrength(int strength){blurStrength=IndicatorPolicy.clamp(strength,0,100);blurRadiusDp=blurStrength*.72f;rebuild();}
 @Override public void setImageDrawable(Drawable drawable){super.setImageDrawable(drawable);rebuild();}
 @Override public void setImageBitmap(Bitmap bitmap){super.setImageBitmap(bitmap);rebuild();}
 private void rebuild(){Drawable source=getDrawable();blurred=null;if(source==null||blurStrength==0){invalidate();return;}int size=96;Bitmap small=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(small);Rect old=new Rect(source.getBounds());source.setBounds(0,0,size,size);source.draw(canvas);source.setBounds(old);int[] pixels=new int[size*size];small.getPixels(pixels,0,size,0,0,size,size);int radius=Math.max(1,blurStrength/7);int[] tmp=new int[pixels.length];for(int pass=0;pass<3;pass++){blur(pixels,tmp,size,radius,true);blur(tmp,pixels,size,radius,false);}small.setPixels(pixels,0,size,0,0,size,size);blurred=small;invalidate();}
 private static void blur(int[] src,int[] dst,int n,int r,boolean horizontal){for(int line=0;line<n;line++){long a=0,red=0,g=0,b=0;for(int x=-r;x<=r;x++){int k=Math.max(0,Math.min(n-1,x));int c=src[horizontal?line*n+k:k*n+line];a+=c>>>24;red+=(c>>16)&255;g+=(c>>8)&255;b+=c&255;}int count=2*r+1;for(int x=0;x<n;x++){dst[horizontal?line*n+x:x*n+line]=((int)(a/count)<<24)|((int)(red/count)<<16)|((int)(g/count)<<8)|(int)(b/count);int leave=Math.max(0,x-r),enter=Math.min(n-1,x+r+1);int c=src[horizontal?line*n+leave:leave*n+line],d=src[horizontal?line*n+enter:enter*n+line];a+=(d>>>24)-(c>>>24);red+=((d>>16)&255)-((c>>16)&255);g+=((d>>8)&255)-((c>>8)&255);b+=(d&255)-(c&255);}}}
 @Override protected void onDraw(Canvas canvas){if(blurred==null){super.onDraw(canvas);return;}float side=Math.max(getWidth(),getHeight()),x=(getWidth()-side)/2,y=(getHeight()-side)/2;canvas.drawBitmap(blurred,null,new RectF(x,y,x+side,y+side),paint);}
}
