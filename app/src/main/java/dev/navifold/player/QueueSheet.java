package dev.navifold.player;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.text.TextUtils;
import androidx.media3.common.*;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.*;
import com.google.android.material.bottomsheet.*;
import java.util.function.Supplier;

final class QueueSheet extends BottomSheetDialog {
 final Activity owner;final MediaController player;final Supplier<Api> api;final Covers covers;final int ink,muted,bg,surface,accent;
 com.google.android.material.switchmaterial.SwitchMaterial aiToggle;boolean updatingToggle,registered;
 final android.content.SharedPreferences.OnSharedPreferenceChangeListener preferenceListener=(p,key)->{if("autoplay".equals(key)&&aiToggle!=null)aiToggle.post(this::refresh);};
 boolean queueChanged;int renderedCount=-1,renderedIndex=-1;
 RecyclerView list;QueueAdapter adapter;TextView heading;ItemTouchHelper touch;boolean dragging;
 final Player.Listener listener=new Player.Listener(){
  @Override public void onTimelineChanged(Timeline timeline,int reason){if(reason==Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)queueChanged=true;}
  @Override public void onEvents(Player p,Player.Events events){if(dragging)return;int previous=renderedIndex;boolean changed=queueChanged;queueChanged=false;refresh(changed);if(!changed&&previous==renderedIndex&&events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)&&renderedIndex>=0&&renderedIndex<player.getMediaItemCount())adapter.notifyItemChanged(renderedIndex);}
 };
 QueueSheet(Activity a,MediaController p,Supplier<Api> api,Covers covers,int ink,int muted,int bg,int surface,int accent){super(a);owner=a;player=p;this.api=api;this.covers=covers;this.ink=ink;this.muted=muted;this.bg=bg;this.surface=surface;this.accent=accent;}
 int dp(int n){return Math.round(n*owner.getResources().getDisplayMetrics().density);}
 TextView text(String value,int size,int color){TextView v=new TextView(owner);v.setText(value);v.setTextSize(size);v.setTypeface(UiAssets.font(owner));v.setIncludeFontPadding(false);v.setTextColor(color);return v;}
 @Override protected void onCreate(Bundle b){super.onCreate(b);LinearLayout root=new LinearLayout(owner);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(12),dp(16),dp(16));android.graphics.drawable.GradientDrawable base=new android.graphics.drawable.GradientDrawable();base.setColor(bg);base.setCornerRadius(dp(24));root.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{base,GlassUi.panel(owner,android.graphics.Color.luminance(ink)>.5,24,96)}));setContentView(root);
 LinearLayout top=new LinearLayout(owner);top.setGravity(Gravity.CENTER_VERTICAL);heading=text("QUEUE",18,ink);heading.setTypeface(UiAssets.semibold(owner));top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));TransportButton close=new TransportButton(owner,TransportButton.Kind.CLOSE,ink,0,"대기열 닫기",this::dismiss);top.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(top);
 aiToggle=new com.google.android.material.switchmaterial.SwitchMaterial(owner);aiToggle.setText("AI");aiToggle.setTextColor(ink);aiToggle.setTextSize(12);aiToggle.setContentDescription("AI 큐 보충");aiToggle.setMinimumHeight(dp(48));top.addView(aiToggle,1,new LinearLayout.LayoutParams(-2,dp(48)));aiToggle.setOnCheckedChangeListener((button,checked)->{if(updatingToggle)return;SmartSettings.setAutoplay(owner,player,checked);refresh();});
 TransportButton clear=new TransportButton(owner,TransportButton.Kind.DELETE,accent,0,"대기열 비우기",()->{player.clearMediaItems();refresh();});top.addView(clear,2,new LinearLayout.LayoutParams(dp(48),dp(48)));
 list=new RecyclerView(owner);list.setLayoutManager(new LinearLayoutManager(owner));adapter=new QueueAdapter();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
 touch=new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP|ItemTouchHelper.DOWN,0){public boolean onMove(RecyclerView r,RecyclerView.ViewHolder from,RecyclerView.ViewHolder to){int a=from.getBindingAdapterPosition(),b=to.getBindingAdapterPosition();if(a<0||b<0)return false;dragging=true;player.moveMediaItem(a,b);adapter.notifyItemMoved(a,b);return true;}public void onSwiped(RecyclerView.ViewHolder holder,int direction){}public void clearView(RecyclerView r,RecyclerView.ViewHolder h){super.clearView(r,h);dragging=false;queueChanged=false;refresh(true);}public boolean isLongPressDragEnabled(){return false;}});touch.attachToRecyclerView(list);
 player.addListener(listener);SmartPlayback.settings(owner).registerOnSharedPreferenceChangeListener(preferenceListener);registered=true;refresh(true);list.scrollToPosition(Math.max(0,player.getCurrentMediaItemIndex()));
 }
 @Override public void show(){super.show();getWindow().setBackgroundDrawableResource(android.R.color.transparent);getWindow().setDimAmount(.18f);getWindow().setNavigationBarColor(bg);if(android.os.Build.VERSION.SDK_INT>=29)getWindow().setNavigationBarContrastEnforced(false);getWindow().getDecorView().setSystemUiVisibility(android.graphics.Color.luminance(ink)>.5?0:View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);if(owner.getResources().getConfiguration().screenWidthDp>=600){getWindow().setLayout(dp(360),-1);getWindow().setGravity(Gravity.RIGHT|Gravity.BOTTOM);}android.widget.FrameLayout sheet=findViewById(com.google.android.material.R.id.design_bottom_sheet);if(sheet!=null){sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT);sheet.getLayoutParams().height=(int)(owner.getResources().getDisplayMetrics().heightPixels*.85f);BottomSheetBehavior<android.widget.FrameLayout> behavior=BottomSheetBehavior.from(sheet);behavior.setState(BottomSheetBehavior.STATE_EXPANDED);behavior.setSkipCollapsed(true);}}
 void refresh(){refresh(false);}
 void refresh(boolean contentsChanged){if(adapter==null||!registered)return;int count=player.getMediaItemCount(),index=player.getCurrentMediaItemIndex();if(count==0)SmartSettings.setAutoplay(owner,player,false);updatingToggle=true;aiToggle.setEnabled(count>0);aiToggle.setAlpha(count>0?1f:.4f);aiToggle.setChecked(count>0&&SmartPlayback.settings(owner).getBoolean("autoplay",false));updatingToggle=false;String label="QUEUE · "+Math.max(0,index+1)+" / "+count;if(!label.contentEquals(heading.getText()))heading.setText(label);
  if(contentsChanged||count!=renderedCount)adapter.notifyDataSetChanged();else if(index!=renderedIndex){if(renderedIndex>=0&&renderedIndex<count)adapter.notifyItemChanged(renderedIndex);if(index>=0&&index<count)adapter.notifyItemChanged(index);}renderedCount=count;renderedIndex=index;
 }
 void detach(){if(registered){registered=false;player.removeListener(listener);SmartPlayback.settings(owner).unregisterOnSharedPreferenceChangeListener(preferenceListener);}}
 @Override public void dismiss(){detach();super.dismiss();}
 @Override public void cancel(){detach();super.cancel();}
 final class Holder extends RecyclerView.ViewHolder{LinearLayout row;TextView title,artist;ImageView cover;TransportButton drag,remove;Holder(LinearLayout row){super(row);this.row=row;}}
 final class QueueAdapter extends RecyclerView.Adapter<Holder>{
 public int getItemCount(){return player.getMediaItemCount();}
 public Holder onCreateViewHolder(ViewGroup parent,int type){LinearLayout row=new LinearLayout(owner);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(64));Holder h=new Holder(row);h.drag=new TransportButton(owner,TransportButton.Kind.DRAG,muted,0,"곡 순서 변경",()->{});row.addView(h.drag,new LinearLayout.LayoutParams(dp(48),dp(48)));h.cover=new ImageView(owner);h.cover.setScaleType(ImageView.ScaleType.CENTER_CROP);row.addView(h.cover,new LinearLayout.LayoutParams(dp(40),dp(40)));LinearLayout words=new LinearLayout(owner);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(dp(12),dp(8),0,dp(8));h.title=text("",14,ink);h.title.setMaxLines(2);h.title.setEllipsize(TextUtils.TruncateAt.END);words.addView(h.title);h.artist=text("",12,muted);h.artist.setMaxLines(1);words.addView(h.artist);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));h.remove=new TransportButton(owner,TransportButton.Kind.DELETE,muted,0,"대기열에서 삭제",()->{int at=h.getBindingAdapterPosition();if(at>=0)player.removeMediaItem(at);});row.addView(h.remove,new LinearLayout.LayoutParams(dp(48),dp(48)));h.drag.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN){touch.startDrag(h);return true;}if(event.getAction()==MotionEvent.ACTION_UP)v.performClick();return true;});row.setOnLongClickListener(v->{int at=h.getBindingAdapterPosition();if(at<0)return false;RecommendationUi.show(owner,player,at);return true;});row.setOnClickListener(v->{int at=h.getBindingAdapterPosition();if(at>=0){player.seekToDefaultPosition(at);player.play();}});return h;}
 public void onBindViewHolder(Holder h,int at){MediaMetadata m=player.getMediaItemAt(at).mediaMetadata;boolean suggested=m.extras!=null&&m.extras.getBoolean(SmartPlayback.SUGGESTED,false);h.title.setText((suggested?"✦ ":"")+(m.title==null?"":m.title));h.title.setContentDescription((suggested?"추천곡 · ":"")+(m.title==null?"":m.title));h.title.setTextColor(at==player.getCurrentMediaItemIndex()?accent:ink);h.artist.setText(m.artist);String cover=m.extras==null?"":m.extras.getString("coverArt","");if(api.get()!=null)covers.bind(h.cover,api.get(),cover);else covers.bindMedia(h.cover,m,160);}
 }
}
