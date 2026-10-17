package dev.navifold.player;

import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

final class Covers {
    private final ExecutorService workers=Executors.newFixedThreadPool(3);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(16*1024*1024){
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
    };
    private final Set<String> pending=new HashSet<>();
    private final Map<String,List<java.lang.ref.WeakReference<ImageView>>> waiting=new HashMap<>();
    private int generation;
    void bind(ImageView view,Api api,String id) {bind(view,api,id,320);}
    void bind(ImageView view,Api api,String id,int size) {
        String key=api==null?"":api.base+"\n"+api.user+"\n"+id+"\n"+size;
        if(!key.isEmpty()&&key.equals(view.getTag())&&(cache.get(key)!=null||pending.contains(key)))return;
        if(api==null||id.isEmpty()){view.setTag(key);view.setImageDrawable(new Placeholder(view.getContext()));return;}
        try{load(view,key,api.url("getCoverArt","id",id,"size",String.valueOf(size)),size);}catch(Exception ignored){view.setImageDrawable(new Placeholder(view.getContext()));}
    }
    void bindMedia(ImageView view,androidx.media3.common.MediaMetadata metadata,int size){if(metadata.artworkUri==null){view.setImageDrawable(new Placeholder(view.getContext()));return;}String source=metadata.artworkUri.toString();load(view,source+"\n"+size,source,size);}
    void load(ImageView view,String key,String source,int size){
        if(key.equals(view.getTag())&&(cache.get(key)!=null||pending.contains(key)))return;
        view.setTag(key);Bitmap known=cache.get(key);if(known!=null){view.setImageBitmap(known);return;}view.setImageDrawable(new Placeholder(view.getContext()));
        waiting.computeIfAbsent(key,k->new ArrayList<>()).add(new java.lang.ref.WeakReference<>(view));
        if(!pending.add(key))return;
        int request=generation;
        workers.execute(()->{
            Bitmap bitmap=null;
            try {
                HttpURLConnection c=(HttpURLConnection)new URL(source).openConnection();
                c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);
                try {
                    if(c.getResponseCode()==200)try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                        byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>3*1024*1024)throw new IOException();out.write(b,0,n);}
                        byte[] bytes=out.toByteArray();BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                        if(bounds.outWidth>0&&bounds.outHeight>0){BitmapFactory.Options opts=new BitmapFactory.Options();while(Math.max(bounds.outWidth,bounds.outHeight)/Math.max(1,opts.inSampleSize)>Math.max(640,size))opts.inSampleSize=opts.inSampleSize==0?2:opts.inSampleSize*2;bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);}
                    }
                }finally{c.disconnect();}
            }catch(Exception ignored){}
            Bitmap result=bitmap;
            main.post(()->{
                if(request!=generation)return;
                pending.remove(key);List<java.lang.ref.WeakReference<ImageView>> views=waiting.remove(key);
                if(result!=null){cache.put(key,result);if(views!=null)for(java.lang.ref.WeakReference<ImageView> ref:views){ImageView v=ref.get();if(v!=null&&key.equals(v.getTag()))v.setImageBitmap(result);}}
            });
        });
    }
    static final class Placeholder extends android.graphics.drawable.Drawable {
        final android.content.Context context;final Paint paint=new Paint(3);final android.graphics.drawable.Drawable note;Placeholder(android.content.Context context){this.context=context;note=androidx.appcompat.content.res.AppCompatResources.getDrawable(context,R.drawable.ic_music).mutate();note.setTint(0xffa2a2ae);}
        public void draw(Canvas canvas){android.graphics.Rect r=getBounds();paint.setColor(0xff2c2c34);float density=context.getResources().getDisplayMetrics().density;canvas.drawRoundRect(r.left,r.top,r.right,r.bottom,8*density,8*density,paint);int half=(int)Math.min(Math.min(r.width(),r.height())*.3f,16*density);note.setBounds(r.centerX()-half,r.centerY()-half,r.centerX()+half,r.centerY()+half);note.draw(canvas);}
        public void setAlpha(int alpha){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return android.graphics.PixelFormat.OPAQUE;}
    }
    void clear(){generation++;cache.evictAll();waiting.clear();pending.clear();}
    void close(){clear();workers.shutdownNow();main.removeCallbacksAndMessages(null);}
}
