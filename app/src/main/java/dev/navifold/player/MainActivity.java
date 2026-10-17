package dev.navifold.player;

import android.app.*;
import androidx.appcompat.app.AlertDialog;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.session.*;
import androidx.media3.session.MediaController;
import com.google.common.util.concurrent.ListenableFuture;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    int INK,MUTED,BG,ACCENT,SOFT,SURFACE,SIDEBAR;
    final Map<String,String> viewSorts=new HashMap<>();
    TextView networkNotice;
    AppSettings settings;ImageView miniCover;String lastArtworkId="";
    static final int PAGE=60;
    final ExecutorService network=Executors.newFixedThreadPool(3);
    final ExecutorService queueWork=Executors.newSingleThreadExecutor();
    final Handler main=new Handler(Looper.getMainLooper());
    final Covers covers=new Covers();
    final Set<String> selectedTrackIds=new LinkedHashSet<>();
    BaseAdapter detailAdapter;LinearLayout selectionBar;TextView selectionCount;
    final ArrayList<JSONObject> albums=new ArrayList<>(), detailSongs=new ArrayList<>();
    final Set<String> albumIds=new HashSet<>();
    final Map<String,Parcelable> scrollPositions=new HashMap<>();
    Api api; Library.Index index; Library.Node folder;
    String visibleSongKey="";List<JSONObject> visibleSongCache=Collections.emptyList();
    final ArrayList<JSONObject> allSongs=new ArrayList<>();volatile int songsRequest;boolean songsLoading=false,songsLoaded=false;String songQuery="";
    QualityOverlay.CameraRing cameraRing;
    TextView pageHeading;int albumTotal=-1;volatile int albumCountRequest=0;boolean statusError=false;
    GridView grid; EntryAdapter adapter; LinearLayout detail, mini; TextView status, now, time,miniArtist,qualityStatus;
    Button retry;TransportButton toggle;SeekBar seek;NowPlayingSheet nowPlayingSheet;
    MediaController controller; ListenableFuture<MediaController> controllerFuture;
    String tab="폴더",sort="alphabeticalByArtist",albumQuery="",message="서버를 연결해 음악을 불러오세요",detailTitle="",selectedAlbumId="";
    String playerError=""; JSONObject selectedAlbum;
    int coverDp=128,albumOffset=0,loginRequest=0;
    volatile int session=0,albumRequest=0,detailRequest=0,indexRequest=0;
    volatile boolean destroyed=false;
    boolean albumLoading=false,albumEnd=false,albumFailed=false,indexLoading=false,indexFailed=false,folderAlbums=false;
    boolean detailVisible=false,wide=false,original=true; int lastColumnCount=-1;
    String serverText="",userText=""; AlertDialog connectionDialog;
    final Runnable tick=new Runnable(){public void run(){if(hasWindowFocus())updatePlayer();if(!destroyed)main.postDelayed(this,500);}};
    final Player.Listener playerListener=new Player.Listener(){
        @Override public void onEvents(Player p,Player.Events events){updatePlayer();}
        @Override public void onPlayerError(PlaybackException error){playerError="재생 실패 · 서버 음질/변환 설정과 연결을 확인하세요";updatePlayer();}
    };
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        PlaybackNotifications.createChannel(this);NetworkUsage.get(this);
        api=SmartSession.api;settings=new AppSettings(getPreferences(0));coverDp=settings.coverDp;original=settings.original;folderAlbums=settings.folderAlbums;sort=settings.sort;tab=settings.startTab;
        serverText=getPreferences(0).getString("server","");userText=getPreferences(0).getString("user","");
        build();
        controllerFuture=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class))).buildAsync();
        controllerFuture.addListener(()->{try{if(destroyed)return;controller=controllerFuture.get();controller.addListener(playerListener);applyPlaybackSettings();updatePlayer();}catch(Exception e){reportError("플레이어 연결 실패. 앱을 다시 열어 주세요.");}},command->main.post(command));
        main.post(tick);
        if(api==null)restoreConnection();else {resetAlbums();if(tab.equals("곡"))loadSongLibrary();}
    }
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    LinearLayout col(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setTypeface(UiAssets.font(this));v.setIncludeFontPadding(false);v.setLineSpacing(dp(2),1f);v.setPadding(0,dp(2),0,dp(2));return v;}
    Button button(String label,Runnable action){Button b=label.isEmpty()?new CompactIconButton(this):new Button(this);b.setText(UiAssets.label(this,label));int resource=label.isEmpty()?0:UiAssets.icon(label.substring(0,1));if(resource!=0){b.setCompoundDrawables(UiAssets.drawable(this,resource,INK,24),null,null,null);b.setCompoundDrawablePadding(dp(8));}b.setSingleLine(true);b.setEllipsize(TextUtils.TruncateAt.END);b.setIncludeFontPadding(false);b.setGravity(Gravity.CENTER);b.setTextSize(14);b.setTypeface(UiAssets.font(this));b.setStateListAnimator(null);b.setTextColor(INK);b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setMinHeight(dp(48));b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x3379afff),GlassUi.panel(this,settings.dark,16,90),null));b.setOnClickListener(v->action.run());return b;}
    Button actionIcon(int resource,String label,Runnable action){Button b=button("",action);b.setContentDescription(label);b.setTooltipText(label);b.setCompoundDrawables(null,UiAssets.drawable(this,resource,INK,24),null,null);b.setPadding(dp(12),dp(12),dp(12),dp(12));return b;}
    void primary(Button b){b.setBackground(shape(ACCENT,12));b.setTextColor(settings.dark?0xff101820:0xffffffff);for(android.graphics.drawable.Drawable icon:b.getCompoundDrawables())if(icon!=null)icon.setTint(settings.dark?0xff101820:0xffffffff);}
    void addWeighted(LinearLayout row,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);if(row.getChildCount()>0)lp.setMarginStart(dp(8));row.addView(v,lp);}
    boolean isWide(){return getResources().getConfiguration().screenWidthDp>=600;}
    String positionKey(){return tab+"|"+(folder==null?"":folder.key)+"|"+folderAlbums+"|"+sort+"|"+albumQuery;}
    void remember(){if(grid!=null)scrollPositions.put(positionKey(),grid.onSaveInstanceState());}
    void applyPalette(){
        if(settings.systemTheme)settings.dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        if(settings.dark){INK=getColor(R.color.color_text_primary);MUTED=getColor(R.color.color_text_secondary);BG=getColor(R.color.color_bg_primary);ACCENT=getColor(R.color.color_accent_primary);SOFT=0xff292d43;SURFACE=getColor(R.color.color_surface_elevated);SIDEBAR=0xff111114;}
        else{INK=0xff171719;MUTED=0xff6c6c75;BG=0xfff2f2f7;ACCENT=0xff1468e8;SOFT=0xffdfeaff;SURFACE=0xffffffff;SIDEBAR=0xffebebf0;}
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(SIDEBAR);
        getWindow().getDecorView().setSystemUiVisibility(settings.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    void build(){
        applyPalette();wide=isWide();lastColumnCount=-1;
        LinearLayout root=col();root.setBackground(GlassUi.canvas(settings.dark));
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());return i;});setContentView(root);
        LinearLayout shell=new LinearLayout(this);root.addView(shell,new LinearLayout.LayoutParams(-1,0,1));
        if(wide){
            LinearLayout rail=col();rail.setGravity(Gravity.CENTER);rail.setPadding(dp(4),0,dp(4),0);rail.setBackground(GlassUi.panel(this,settings.dark,0,65));shell.addView(rail,new LinearLayout.LayoutParams(getResources().getDimensionPixelSize(R.dimen.navigation_rail_width),-1));
            for(String name:new String[]{"앨범","곡","폴더","보관함","설정"})rail.addView(navItem(name),new LinearLayout.LayoutParams(-1,dp(72)));
        }
        LinearLayout content=col();content.setPadding(dp(16),0,dp(16),0);shell.addView(content,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        pageHeading=text(pageTitle(),22,INK);pageHeading.setLetterSpacing(.015f);pageHeading.setTypeface(UiAssets.semibold(this));header.addView(pageHeading,new LinearLayout.LayoutParams(0,-2,1));
        if(tab.equals("앨범"))header.addView(iconButton("⌕","앨범 검색",this::searchAlbums));if(tab.equals("곡"))header.addView(iconButton("⌕","곡 필터",this::filterSongLibrary));
        if(!detailVisible&&(tab.equals("앨범")||tab.equals("곡")||tab.equals("보관함"))){Button order=actionIcon(R.drawable.ic_sort,"정렬",()->showSort(false));header.addView(order,new LinearLayout.LayoutParams(dp(48),dp(48)));}
        header.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.toolbar_height));content.addView(header);
        qualityStatus=text("",11,ACCENT);qualityStatus.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);qualityStatus.setMinHeight(dp(32));qualityStatus.setMaxLines(2);qualityStatus.setMaxWidth(dp(Math.min(240,getResources().getConfiguration().screenWidthDp-40)));qualityStatus.setPadding(dp(12),dp(4),dp(12),dp(4));qualityStatus.setBackground(shape(SURFACE,12));LinearLayout.LayoutParams qualityLayout=new LinearLayout.LayoutParams(-2,-2);qualityLayout.gravity=Gravity.END;content.addView(qualityStatus,qualityLayout);

        networkNotice=text("",11,MUTED);networkNotice.setPadding(dp(8),dp(6),dp(8),dp(6));networkNotice.setMaxLines(2);networkNotice.setVisibility(View.GONE);networkNotice.setContentDescription("데이터 사용 안내");networkNotice.setOnClickListener(v->NetworkSettings.show(this));content.addView(networkNotice);
        LinearLayout workspace=new LinearLayout(this);workspace.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);content.addView(workspace,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout library=col();detail=col();if(wide&&tab.equals("폴더")&&folder!=null){detailVisible=true;detailTitle=folder.breadcrumb();detailSongs.clear();detailSongs.addAll(folderSongs(folder));selectedAlbum=null;}boolean showingDetail=detailVisible&&!detailTitle.isEmpty()&&!tab.equals("설정");
        if(wide&&showingDetail&&tab.equals("앨범")){library.setVisibility(View.GONE);workspace.addView(detail,new LinearLayout.LayoutParams(-1,-1));}
        else if(wide){workspace.addView(library,new LinearLayout.LayoutParams(0,-1,showingDetail?2:1));if(showingDetail){workspace.addView(detail,new LinearLayout.LayoutParams(0,-1,2));detail.setPadding(dp(20),0,0,0);}}
        else{workspace.addView(library,new LinearLayout.LayoutParams(-1,-1));workspace.addView(detail,new LinearLayout.LayoutParams(-1,-1));library.setVisibility(showingDetail?View.GONE:View.VISIBLE);detail.setVisibility(showingDetail?View.VISIBLE:View.GONE);}
        if(tab.equals("폴더")){
            HorizontalScrollView breadcrumbs=new HorizontalScrollView(this);breadcrumbs.setHorizontalScrollBarEnabled(false);LinearLayout pathrow=new LinearLayout(this);breadcrumbs.addView(pathrow);library.addView(breadcrumbs);
            if(folder==null)pathrow.addView(text("ROOT",12,MUTED));else{List<Library.Node> parents=new ArrayList<>();for(Library.Node node=folder;node!=null;node=node.parent)parents.add(0,node);for(Library.Node node:parents){Button path=button(node.parent==null?"루트":node.name,()->{remember();folder=node;detailVisible=false;build();});path.setTextSize(12);path.setBackgroundColor(android.graphics.Color.TRANSPARENT);pathrow.addView(path);if(node!=folder)pathrow.addView(text("/",12,MUTED));}}
            if(folder!=null&&(settings.includeSubfolders?folder.count:folder.songs.size())>0){LinearLayout actions=new LinearLayout(this);addWeighted(actions,actionIcon(R.drawable.ic_play,"폴더 재생",()->playSongs(folderSongs(folder),0,false)));addWeighted(actions,actionIcon(R.drawable.ic_shuffle,"셔플",()->{List<JSONObject> songs=folderSongs(folder);Collections.shuffle(songs);playSongs(songs,0,false);}));library.addView(actions);}
        }
        status=text(statusError?message:"",11,MUTED);status.setMaxLines(2);status.setVisibility(statusError?View.VISIBLE:View.GONE);library.addView(status);
        retry=button("다시 시도",()->{if(indexFailed)syncFolders();else if(albumFailed)loadAlbums();});retry.setVisibility((albumFailed&&tab.equals("앨범"))||(indexFailed&&tab.equals("폴더"))?View.VISIBLE:View.GONE);library.addView(retry);
        grid=new GridView(this);grid.setVerticalSpacing(dp(16));grid.setHorizontalSpacing(dp(12));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setClipToPadding(false);grid.setPadding(0,dp(12),0,dp(12));adapter=new EntryAdapter();grid.setAdapter(adapter);library.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        grid.setOnItemClickListener((p,v,pos,id)->{if(pos<adapter.rows.size())adapter.rows.get(pos).action.run();});
        grid.setOnItemLongClickListener((parent,view,pos,id)->{if(pos>=adapter.rows.size())return false;JSONObject album=adapter.rows.get(pos).album;if(album==null)return false;queueAlbum(album);return true;});
        grid.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView v,int state){}public void onScroll(AbsListView v,int first,int visible,int total){if(tab.equals("앨범")&&total>0&&first+visible>=total-12&&!albumFailed)loadAlbums();}});
        grid.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol)resizeGrid();});
        buildMiniPlayer(root);
        if(!wide){LinearLayout nav=new LinearLayout(this);nav.setBackground(GlassUi.panel(this,settings.dark,0,75));for(String name:new String[]{"앨범","곡","폴더","보관함","설정"})nav.addView(navItem(name),new LinearLayout.LayoutParams(0,-2,1));root.addView(nav);}
        installCameraRing();renderRows(false);renderDetail();resizeGrid();updatePlayer();Parcelable state=scrollPositions.get(positionKey());GridView target=grid;grid.post(()->{if(target!=grid)return;resizeGrid();adapter.notifyDataSetChanged();if(state!=null)grid.onRestoreInstanceState(state);else grid.setSelection(0);});
    }
    void installCameraRing(){
        ViewGroup decor=(ViewGroup)getWindow().getDecorView();
        if(cameraRing==null)cameraRing=new QualityOverlay.CameraRing(this);
        if(cameraRing.getParent()!=decor){
            if(cameraRing.getParent() instanceof ViewGroup)((ViewGroup)cameraRing.getParent()).removeView(cameraRing);
            decor.addView(cameraRing,new android.widget.FrameLayout.LayoutParams(-1,-1,Gravity.TOP));
        }
        cameraRing.invalidate();
    }
    void playerBlurSetting(){
        LinearLayout panel=col();panel.setPadding(dp(20),dp(8),dp(20),dp(12));
        TextView label=text(settings.playerBackgroundBlur+"%",16,INK);panel.addView(label);
        android.widget.FrameLayout preview=new android.widget.FrameLayout(this);preview.setBackground(shape(BG,16));preview.setClipToOutline(true);panel.addView(preview,new LinearLayout.LayoutParams(-1,dp(140)));
        AlbumBackdrop artwork=new AlbumBackdrop(this);preview.addView(artwork,new android.widget.FrameLayout.LayoutParams(-1,-1));
        if(controller!=null&&controller.getCurrentMediaItem()!=null)covers.bindMedia(artwork,controller.getMediaMetadata(),480);else covers.bind(artwork,api,selectedAlbum==null?"":selectedAlbum.optString("coverArt"),480);
        artwork.setBlurStrength(settings.playerBackgroundBlur);
        View shade=new View(this);shade.setBackgroundColor(settings.dark?0x55000000:0x55ffffff);preview.addView(shade,new android.widget.FrameLayout.LayoutParams(-1,-1));
        TextView title=text("BLUR",14,INK);title.setGravity(Gravity.CENTER);preview.addView(title,new android.widget.FrameLayout.LayoutParams(-1,-1));
        SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(settings.playerBackgroundBlur);slider.setContentDescription("앨범 배경 블러");panel.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));
        panel.addView(text(android.os.Build.VERSION.SDK_INT>=31?"0% 선명 · 100% 부드럽게":"Android 12 이상에서 지원",12,MUTED));
        slider.setEnabled(android.os.Build.VERSION.SDK_INT>=31);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean user){label.setText(value+"%");artwork.setBlurStrength(value);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        dialog().setTitle("앨범 배경 블러").setView(panel).setNegativeButton("취소",null).setPositiveButton("적용",(d,w)->{settings.playerBackgroundBlur=slider.getProgress();settings.save();build();}).show();
    }
    void indicatorSetting(){choose("음질 인디케이터",new String[]{"꺼짐","인앱 전용","다른 앱 사용 중에도 표시"},settings.indicatorMode,w->{settings.indicatorMode=w;if(w==2&&!android.provider.Settings.canDrawOverlays(this))startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));});}
    int transportGravity(){return Gravity.CENTER_VERTICAL|(settings.transportAlignment==0?Gravity.START:settings.transportAlignment==2?Gravity.END:Gravity.CENTER_HORIZONTAL);}
    TransportButton addTransport(LinearLayout group,TransportButton.Kind kind,String label,int size,Runnable action){boolean primary=kind==TransportButton.Kind.PLAY;TransportButton b=new TransportButton(this,kind,primary?(settings.dark?0xff101820:0xffffffff):INK,primary?ACCENT:android.graphics.Color.TRANSPARENT,label,action);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(size),dp(size));if(group.getChildCount()>0)params.setMarginStart(dp(settings.playerGapDp));group.addView(b,params);return b;}
    void seekRelative(int seconds){if(controller==null)return;long duration=PlaybackInfo.duration(controller);controller.seekTo(Math.max(0,Math.min(duration>0?duration:Long.MAX_VALUE,controller.getCurrentPosition()+seconds*1000L)));}
    void showNowPlaying(){if(controller==null||controller.getCurrentMediaItem()==null){reportError("먼저 음악을 선택해 주세요");return;}if(nowPlayingSheet!=null&&nowPlayingSheet.isShowing())return;nowPlayingSheet=new NowPlayingSheet(this,controller,()->api,settings,covers,INK,MUTED,BG,SURFACE,ACCENT);NowPlayingSheet opening=nowPlayingSheet;opening.setOnDismissListener(d->{if(nowPlayingSheet==opening)nowPlayingSheet=null;updateMiniVisibility();});opening.show();mini.setVisibility(View.INVISIBLE);}
    View navItem(String name){LinearLayout item=col();item.setGravity(Gravity.CENTER);item.setMinimumHeight(dp(56));item.setPadding(0,dp(4),0,dp(4));item.setContentDescription(name);item.setFocusable(true);item.setOnClickListener(v->navigate(name));
        ImageView image=new ImageView(this);int resource=name.equals("앨범")?R.drawable.ic_album:name.equals("곡")?R.drawable.ic_music:name.equals("폴더")?R.drawable.ic_folder:name.equals("보관함")?R.drawable.ic_favorite:R.drawable.ic_settings;image.setImageDrawable(UiAssets.drawable(this,resource,tab.equals(name)?ACCENT:MUTED,24));item.addView(image,new LinearLayout.LayoutParams(dp(24),dp(24)));item.setTooltipText(name);if(tab.equals(name))item.setBackground(new android.graphics.drawable.InsetDrawable(GlassUi.panel(this,settings.dark,16,100),dp(6),dp(8),dp(6),dp(8)));return item;}
    Button iconButton(String symbol,String label,Runnable action){Button b=button("",action);int resource=UiAssets.icon(symbol);if(resource!=0)b.setCompoundDrawables(null,UiAssets.drawable(this,resource,INK,24),null,null);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setContentDescription(label);b.setBackgroundColor(android.graphics.Color.TRANSPARENT);b.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));return b;}
    AlertDialog.Builder dialog(){return new com.google.android.material.dialog.MaterialAlertDialogBuilder(new android.view.ContextThemeWrapper(this,settings.dark?R.style.NaviDialogDark:R.style.NaviDialogLight));}
    void buildMiniPlayer(LinearLayout root){
        mini=col();updateMiniVisibility();mini.setTag("mini-player");mini.setBackground(GlassUi.panel(this,settings.dark,22,95));mini.setContentDescription("현재 곡 정보 열기");mini.setOnClickListener(v->showNowPlaying());root.addView(mini);
        seek=new SeekBar(this);seek.setPadding(0,0,0,0);seek.setThumb(null);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));seek.setEnabled(false);seek.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);mini.addView(seek,new LinearLayout.LayoutParams(-1,dp(2)));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),0,dp(4),0);mini.addView(row,new LinearLayout.LayoutParams(-1,getResources().getDimensionPixelSize(R.dimen.mini_player_height)-dp(2)));
        miniCover=new RoundedArtwork(this,6);miniCover.setScaleType(ImageView.ScaleType.CENTER_CROP);miniCover.setBackground(shape(SURFACE,6));miniCover.setClipToOutline(true);miniCover.setImageResource(R.drawable.bg_album_placeholder);row.addView(miniCover,new LinearLayout.LayoutParams(dp(44),dp(44)));lastArtworkId="\u0000";
        LinearLayout identity=col();identity.setGravity(Gravity.CENTER_VERTICAL);identity.setPadding(dp(12),0,dp(4),0);identity.setContentDescription("현재 곡 정보");identity.setOnClickListener(v->showNowPlaying());row.addView(identity,new LinearLayout.LayoutParams(0,-1,1));
        now=text("—",14,INK);now.setSingleLine(true);now.setEllipsize(settings.titleMode==1?TextUtils.TruncateAt.MARQUEE:TextUtils.TruncateAt.END);now.setSelected(settings.titleMode==1);now.setMarqueeRepeatLimit(-1);now.setContentDescription("현재 곡 제목");identity.addView(now);miniArtist=text("",12,MUTED);miniArtist.setSingleLine(true);miniArtist.setEllipsize(TextUtils.TruncateAt.END);identity.addView(miniArtist);
        if(wide)miniControl(row,TransportButton.Kind.PREVIOUS,"이전 곡",()->{if(controller!=null)controller.seekToPreviousMediaItem();});
        toggle=miniControl(row,TransportButton.Kind.PLAY,"재생",()->{if(controller==null)return;if(controller.getPlayWhenReady())controller.pause();else{controller.prepare();controller.play();}});
        miniControl(row,TransportButton.Kind.NEXT,"다음 곡",()->{if(controller!=null)controller.seekToNextMediaItem();});
        if(wide)miniControl(row,TransportButton.Kind.QUEUE,"재생 대기열",this::showQueue);
        time=text("",12,MUTED);float[] down={0,0};mini.setOnTouchListener((v,e)->{if(e.getAction()==0){down[0]=e.getX();down[1]=e.getY();}if(e.getAction()==1&&down[1]-e.getY()>dp(40)){showNowPlaying();return true;}return false;});
    }
    TransportButton miniControl(LinearLayout row,TransportButton.Kind kind,String label,Runnable action){TransportButton v=new TransportButton(this,kind,INK,android.graphics.Color.TRANSPARENT,label,action);row.addView(v,new LinearLayout.LayoutParams(dp(48),dp(48)));return v;}
    void showQueue(){if(controller!=null)new QueueSheet(this,controller,()->api,covers,INK,MUTED,BG,SURFACE,ACCENT).show();}
    interface SettingChoice{void set(int which);}
    void choose(String title,String[] labels,int selected,SettingChoice change){dialog().setTitle(title).setSingleChoiceItems(labels,selected,(d,w)->{d.dismiss();remember();change.set(w);settings.save();coverDp=settings.coverDp;folderAlbums=settings.folderAlbums;original=settings.original;sort=settings.sort;applyPlaybackSettings();build();}).setNegativeButton("취소",null).show();}
    void chooseAlbumScope(){choose("앨범 표시 범위",new String[]{"전체 앨범","즐겨찾기만"},settings.favoritesOnly?1:0,w->{settings.favoritesOnly=w==1;resetAlbums();});}
    void applyPlaybackSettings(){if(controller!=null){controller.setRepeatMode(settings.repeat);controller.setShuffleModeEnabled(settings.shuffle);}}
    void resizeGrid(){if(grid==null)return;boolean tiles=tab.equals("앨범")||(tab.equals("폴더")&&folderAlbums);int choice=settings.coverIndex();int columns=tiles?(wide?new int[]{6,5,4}[choice]:new int[]{4,3,2}[choice]):1;if(wide&&detailVisible&&tiles)columns=Math.max(2,columns-2);if(columns!=lastColumnCount){lastColumnCount=columns;grid.setHorizontalSpacing(dp(new int[]{8,12,16}[choice]));grid.setVerticalSpacing(dp(tab.equals("설정")?8:tiles?16:0));grid.setNumColumns(columns);grid.invalidateViews();}}
    void navigate(String name){selectedTrackIds.clear();remember();tab=name;detailVisible=false;statusError=false;message="";build();if(api!=null&&tab.equals("앨범")&&albums.isEmpty()&&!albumEnd)loadAlbums();if(tab.equals("곡")&&!songsLoaded)loadSongLibrary();}
    String pageTitle(){if(tab.equals("앨범")){String name=!albumQuery.isEmpty()?"SEARCH":settings.favoritesOnly?"FAVORITES":"ALBUM";return name+(albumTotal>=0?" · "+albumTotal:"");}if(tab.equals("곡"))return "SONG"+(songsLoaded?" · "+visibleSongs().size():"");return tab.equals("폴더")?"FOLDERS":tab.equals("보관함")?"LIBRARY":"SETTINGS";}
    void report(String msg){message=msg;statusError=false;if(status!=null){status.setText("");status.setVisibility(View.GONE);}}
    void reportError(String msg){message=msg;statusError=true;if(status!=null){status.setText(msg);status.setVisibility(View.VISIBLE);}}
    void restoreConnection(){int request=++loginRequest;network.execute(()->{try{Api saved=ServerCredentials.load(this);if(saved==null)return;saved.call("ping");main.post(()->{if(destroyed||request!=loginRequest||api!=null)return;api=saved;SmartSession.connect(saved);session++;serverText=saved.base;userText=saved.user;resetAlbums();if(tab.equals("곡"))loadSongLibrary();});}catch(Exception e){main.post(()->{if(!destroyed&&request==loginRequest)reportError("자동 로그인 실패 · 서버 연결을 확인하세요");});}});}
    void testConnection(){if(api==null){showConnection();return;}Api client=api;int auth=session;network.execute(()->{String result;try{client.call("ping");result="연결됨";}catch(Exception e){result=safeError(e);}String message=result;main.post(()->{if(!destroyed&&auth==session){report(message);Toast.makeText(this,message,Toast.LENGTH_LONG).show();}});});}
    void showConnection(){
        loginRequest++;
        LinearLayout fields=col();fields.setPadding(dp(20),dp(10),dp(20),0);
        EditText address=field("https://music.example.com",serverText,false),user=field("사용자 이름",userText,false),password=field("비밀번호 · 기기에 암호화 저장","",true);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        fields.addView(text("Navidrome 서버 기본 주소",13,MUTED));fields.addView(address);fields.addView(user);fields.addView(password);
        fields.addView(text("HTTP는 debug 빌드에서만 허용됩니다. 외부 접속은 HTTPS를 사용하세요.",12,MUTED));
        connectionDialog=dialog().setTitle("서버 연결").setView(fields).setNegativeButton("취소",null).setPositiveButton("연결",null).create();
        connectionDialog.setOnDismissListener(d->{loginRequest++;});
        connectionDialog.setOnShowListener(d->{Button ok=connectionDialog.getButton(AlertDialog.BUTTON_POSITIVE);ok.setOnClickListener(v->{
            final Api candidate;try{if(user.getText().toString().trim().isEmpty()||password.getText().length()==0)throw new Exception("사용자 이름과 비밀번호를 입력하세요.");candidate=new Api(address.getText().toString(),user.getText().toString().trim(),password.getText().toString());}catch(Exception e){password.setError(e.getMessage());return;}
            ok.setEnabled(false);int request=++loginRequest;report("서버 연결 확인 중…");
            network.execute(()->{try{candidate.call("ping");main.post(()->{if(destroyed||request!=loginRequest)return;
                try{ServerCredentials.save(this,candidate);}catch(Exception storage){ok.setEnabled(true);password.setError("로그인 정보 저장 실패 · 다시 시도하세요");return;}
                session++;selectedTrackIds.clear();songsRequest++;songsLoaded=false;songsLoading=false;allSongs.clear();songQuery="";api=candidate;SmartSession.connect(candidate);serverText=candidate.base;userText=candidate.user;getPreferences(0).edit().putString("server",serverText).putString("user",userText).apply();
                covers.clear();indexRequest++;detailRequest++;albumRequest++;indexLoading=false;indexFailed=false;index=null;folder=null;selectedAlbum=null;detailSongs.clear();libraryRows.clear();detailTitle="";scrollPositions.clear();detailVisible=false;
                if(controller!=null){controller.stop();controller.clearMediaItems();}
                connectionDialog.dismiss();report("연결 성공 · 앨범을 불러오는 중");resetAlbums();if(tab.equals("곡"))loadSongLibrary();
            });}catch(Exception e){main.post(()->{if(destroyed||request!=loginRequest)return;ok.setEnabled(true);reportError(safeError(e));password.setError("연결 실패 · 서버 주소/계정을 확인하세요");});}});
        });});connectionDialog.show();
    }
    EditText field(String hint,String value,boolean password){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);e.setSaveEnabled(false);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|(password?android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD:android.text.InputType.TYPE_TEXT_VARIATION_NORMAL));return e;}
    void resetAlbums(){albumRequest++;albumLoading=false;albumFailed=false;albumEnd=false;albumOffset=0;albums.clear();albumIds.clear();if(tab.equals("앨범"))scrollPositions.remove(positionKey());albumTotal=-1;build();loadAlbums();}
    void searchAlbums(){EditText q=field("앨범/아티스트 검색",albumQuery,false);dialog().setTitle("앨범 검색").setView(q).setNegativeButton("취소",null).setPositiveButton("검색",(d,w)->{albumQuery=q.getText().toString().trim();resetAlbums();}).show();}
    void loadAlbums(){
        if(api==null||albumLoading||albumEnd||destroyed)return;albumLoading=true;albumFailed=false;int request=albumRequest,auth=session;Api client=api;String term=albumQuery;boolean favorites=settings.favoritesOnly;
        network.execute(()->{try{List<JSONObject> found=new ArrayList<>();Set<String> ids=new HashSet<>();int offset=0;
            while(!destroyed&&request==albumRequest&&auth==session){JSONObject response=term.isEmpty()?client.call("getAlbumList2","type",favorites?"starred":"alphabeticalByName","size","500","offset",String.valueOf(offset)):client.call("search3","query",term,"artistCount","0","songCount","0","albumCount","500","albumOffset",String.valueOf(offset));List<JSONObject> page=Library.array(response.optJSONObject(term.isEmpty()?"albumList2":"searchResult3"),"album");if(page.isEmpty())break;int added=0;for(JSONObject album:page)if(ids.add(album.optString("id"))){if(!favorites||term.isEmpty()||album.has("starred"))found.add(album);added++;}if(added==0)throw new Exception("Repeated album page");offset+=page.size();}
            main.post(()->{if(destroyed||request!=albumRequest||auth!=session)return;albums.clear();albums.addAll(found);albumTotal=albums.size();albumLoading=false;albumEnd=true;pageHeading.setText(pageTitle());renderRows(false);});
        }catch(Exception e){main.post(()->{if(destroyed||request!=albumRequest||auth!=session)return;albumLoading=false;albumFailed=true;reportError(safeError(e));retry.setVisibility(View.VISIBLE);});}});
    }
    String sortScope(){return detailVisible&&selectedAlbum!=null?"tracks":tab.equals("곡")?"songs":settings.favoritesOnly||tab.equals("보관함")?"favorites":"albums";}
    String activeSort(String scope){return viewSorts.getOrDefault(scope,scope.equals("tracks")?"track":scope.equals("songs")?getPreferences(0).getString("defaultSongOrder",new String[]{"alphabeticalByName","alphabeticalByArtist","album","byYear"}[settings.songSort]):settings.sort);}
    boolean ignoreArticles(){return getPreferences(0).getBoolean("ignoreArticles",true);}
    void showSort(boolean defaults){String scope=sortScope();if(defaults)scope="songs";final String target=scope;String[] labels=new String[MediaSort.LABELS.length+1];labels[0]="기본값 사용";System.arraycopy(MediaSort.LABELS,0,labels,1,MediaSort.LABELS.length);int selected=defaults?Arrays.asList(MediaSort.VALUES).indexOf(getPreferences(0).getString("defaultSongOrder","alphabeticalByName"))+1:viewSorts.containsKey(target)?Arrays.asList(MediaSort.VALUES).indexOf(activeSort(target))+1:0;
        dialog().setTitle(defaults?"곡 기본 정렬":"정렬").setSingleChoiceItems(labels,selected,(d,w)->{d.dismiss();if(defaults)getPreferences(0).edit().putString("defaultSongOrder",w==0?"alphabeticalByName":MediaSort.VALUES[w-1]).apply();else if(w==0)viewSorts.remove(target);else viewSorts.put(target,MediaSort.VALUES[w-1]);if(target.equals("tracks")){detailSongs.sort(MediaSort.comparator(activeSort(target),ignoreArticles()));if(detailAdapter!=null)detailAdapter.notifyDataSetChanged();}else renderRows(true);}).setNegativeButton("닫기",null).show();
    }
    void syncFolders(){
        if(api==null){showConnection();return;}if(indexLoading)return;
        indexLoading=true;indexFailed=false;int request=++indexRequest,auth=session;Api client=api;report("폴더 인덱스 준비 중…");build();
        network.execute(()->{try{
            Library.Index result=new Library.Index();List<JSONObject> roots=Library.array(client.call("getMusicFolders").optJSONObject("musicFolders"),"musicFolder");
            if(roots.isEmpty())throw new Exception("서버가 음악 루트를 반환하지 않았습니다.");
            int scanned=0;
            for(JSONObject root:roots){String rootId=root.optString("id"),name=root.optString("name","음악");int offset=0;
                while(true){if(destroyed||request!=indexRequest||auth!=session)return;
                    List<JSONObject> page=Library.array(client.call("search3","query","","artistCount","0","albumCount","0","songCount","500","songOffset",String.valueOf(offset),"musicFolderId",rootId).optJSONObject("searchResult3"),"song");
                    int fresh=0;for(JSONObject song:page)if(result.add(rootId,name,song))fresh++;
                    scanned+=page.size();offset+=page.size();final int progress=scanned;main.post(()->{if(!destroyed&&request==indexRequest&&auth==session)report("폴더 인덱스 동기화 · "+progress+"곡 확인 중…");});
                    if(page.isEmpty()||page.size()<500)break;
                    if(fresh==0)throw new Exception("서버가 같은 곡을 반복 반환해 동기화를 중단했습니다.");
                }
            }
            main.post(()->{if(destroyed||request!=indexRequest||auth!=session)return;indexLoading=false;indexFailed=false;index=result;folder=result.root;scrollPositions.keySet().removeIf(k->k.startsWith("폴더|"));
                report("폴더 동기화 완료 · "+result.root.count+"곡"+(result.missingPaths>0?" · 경로 없는 "+result.missingPaths+"곡 제외":""));build();});
        }catch(Exception e){main.post(()->{if(destroyed||request!=indexRequest||auth!=session)return;indexLoading=false;indexFailed=true;reportError(safeError(e));build();});}});
    }
    final ArrayList<Entry> libraryRows=new ArrayList<>();int libraryRequest=0;
    void renderRows(boolean reset){
        if(adapter==null)return;Parcelable state=reset||grid.getChildCount()==0?null:grid.onSaveInstanceState();adapter.rows.clear();
        if(tab.equals("앨범")){List<JSONObject> ordered=new ArrayList<>(albums);ordered.sort(MediaSort.comparator(activeSort(settings.favoritesOnly?"favorites":"albums"),ignoreArticles()));for(JSONObject album:ordered)adapter.rows.add(albumEntry(album));}
        else if(tab.equals("곡")){
            List<JSONObject> visible=visibleSongs();for(JSONObject song:visible){Entry row=new Entry(song.optString("title"),Library.artist(song),null,"",()->playSongs(new ArrayList<>(visible),visible.indexOf(song),false));row.song=song;adapter.rows.add(row);}
            if(api==null)adapter.rows.add(new Entry("서버 연결","",null,"",this::showConnection));
        }else if(tab.equals("폴더")){
            if(index==null){adapter.rows.add(new Entry("폴더 연결","설정",null,"",()->navigate("설정")));}
            else if(folder!=null){
                if(folderAlbums){List<JSONObject> scoped=folderSongs(folder);for(JSONObject album:Library.albumsIn(scoped))adapter.rows.add(albumEntry(album));if(adapter.rows.isEmpty())for(Library.Node node:folder.children.values())adapter.rows.add(new Entry("▣ "+node.name,node.count+" tracks",null,"",()->{remember();folder=node;build();}));}
                else{
                    for(Library.Node node:folder.children.values())adapter.rows.add(new Entry("▣ "+node.name,node.count+" tracks",null,"",()->{remember();folder=node;build();}));
                    List<JSONObject> direct=new ArrayList<>(folder.songs);direct.sort(Library.TRACK_ORDER);
                    for(int i=0;i<(wide?0:direct.size());i++){JSONObject song=direct.get(i);int at=i;adapter.rows.add(new Entry(song.optString("title"),Library.artist(song),null,"",()->{showSongs(folder.name,direct,null);playSongs(direct,at,false);}));}
                }
            }
        }else if(tab.equals("보관함")){
            if(libraryRows.isEmpty()){
                adapter.rows.add(new Entry("즐겨찾기 앨범","",null,"",()->{settings.favoritesOnly=true;settings.save();tab="앨범";detailVisible=false;resetAlbums();}));
                adapter.rows.add(new Entry("플레이리스트","",null,"",this::loadPlaylists));
                adapter.rows.add(new Entry("곡 검색","",null,"",this::searchSongs));
            }else adapter.rows.addAll(libraryRows);
        }else if(tab.equals("설정")){
            adapter.rows.add(new Entry("서버 · 계정",api==null?"연결되지 않음":api.base+" · "+api.user,null,"",this::showConnection));
            adapter.rows.add(new Entry("연결 테스트",api==null?"연결 필요":"",null,"",this::testConnection));
            adapter.rows.add(new Entry("데이터 · 네트워크","사용량 · 경고 · 인코딩 추천",null,"",()->NetworkSettings.show(this)));
            adapter.rows.add(new Entry(indexLoading?"폴더 동기화 중단":"폴더 동기화",index==null?"":index.root.count+" tracks",null,"",()->{if(indexLoading){indexRequest++;indexLoading=false;report("동기화 중단 · 이전 인덱스 유지");build();}else syncFolders();}));
            adapter.rows.add(new Entry("로그아웃","재생 중단 · 저장된 로그인 삭제",null,"",this::logout));
            adapter.rows.add(new Entry("음질 인디케이터",new String[]{"꺼짐","인앱 전용",android.provider.Settings.canDrawOverlays(this)?"오버레이":"권한 필요"}[settings.indicatorMode],null,"",this::indicatorSetting));
            adapter.rows.add(new Entry("인앱 인디케이터 모양",new String[]{"펀치홀 링","아일랜드","링 + 아일랜드"}[settings.indicatorStyle]+" · 접힘 세로 전용",null,"",()->choose("인앱 인디케이터 모양",new String[]{"펀치홀 링","아일랜드","링 + 아일랜드"},settings.indicatorStyle,w->settings.indicatorStyle=w)));
            adapter.rows.add(new Entry("펀치홀 · 아일랜드 조정","링 "+settings.ringCompactDp+"dp · X "+settings.islandCompactX+" / Y "+settings.islandCompactY+"dp · 슬라이더 / 숫자 입력",null,"",()->IndicatorSettingsDialog.show(this)));
            adapter.rows.add(new Entry("앨범 배경 블러",settings.playerBackgroundBlur+"%",null,"",this::playerBlurSetting));
            adapter.rows.add(new Entry("잠금화면 곡 변경 팝업",settings.lockPopup?"켜짐":"꺼짐",null,"",()->choose("잠금화면 곡 변경 팝업",new String[]{"꺼짐","켜짐"},settings.lockPopup?1:0,w->settings.lockPopup=w==1)));
            adapter.rows.add(new Entry("음질 색상 안내","파랑 · 노랑 · 보라 · 흰색 · 빨강",null,"",()->info("재생 스트림 인디케이터","파랑: 일반 스트림 / 손실 압축\n노랑: 88.2kHz 이상 무손실 스트림\n보라: 검증된 MQA 디코딩 (현재 미지원)\n흰색: DSD 디코딩 스트림\n빨강: 재생 오류\n\n원본 태그와 현재 재생 스트림은 다를 수 있습니다. 실제 DAC 출력이나 블루투스 변환 이후의 음질을 측정하는 기능은 아닙니다. 링과 아일랜드는 접힌 세로 화면에서만 표시합니다. 기기 기울기는 사용하지 않습니다. 인앱 표시는 카메라 위치 정보가 필요합니다. 아일랜드 중앙은 카메라 여백이며 좌측 포맷 · 우측 샘플링 레이트입니다. 외부 오버레이는 시스템 상태 표시줄에 가려질 수 있습니다.")));
            adapter.rows.add(new Entry("알림 및 잠금화면",PlaybackNotifications.enabled(this)?"알림 켜짐 · 시스템 설정 열기":"알림 꺼짐 · 탭하여 허용",null,"",()->PlaybackNotifications.request(this,true)));
            adapter.rows.add(new Entry("재생 대기열","",null,"",this::showQueue));
            adapter.rows.add(new Entry("SMART DJ","자동 큐 · 취향 학습",null,"",()->SmartSettings.show(this)));
            adapter.rows.add(new Entry("ChatGPT 추천",new ChatGptPlan(this).label(),null,"",()->ChatGptSettings.show(this)));
            adapter.rows.add(new Entry("긴 제목 표시",new String[]{"2줄까지 표시","한 줄 자동 흐르기","한 줄 말줄임"}[settings.titleMode],null,"",()->choose("긴 제목 표시",new String[]{"2줄까지 표시","한 줄 자동 흐르기","한 줄 말줄임"},settings.titleMode,w->settings.titleMode=w)));
            adapter.rows.add(new Entry("앨범 기본 정렬",AppSettings.SORT_LABELS[settings.sortIndex()],null,"",()->choose("앨범 정렬",AppSettings.SORT_LABELS,settings.sortIndex(),w->{settings.sort=AppSettings.SORT_VALUES[w];sort=settings.sort;albumQuery="";resetAlbums();})));
            adapter.rows.add(new Entry("앨범 표시 범위",settings.favoritesOnly?"즐겨찾기만":"전체 앨범",null,"",this::chooseAlbumScope));
            adapter.rows.add(new Entry("앨범 커버 크기",AppSettings.COVER_LABELS[settings.coverIndex()],null,"",()->choose("앨범 커버 크기",AppSettings.COVER_LABELS,settings.coverIndex(),w->settings.coverDp=AppSettings.COVER_SIZES[w])));
            adapter.rows.add(new Entry("폴더 표시 방식",settings.folderAlbums?"앨범 커버":"폴더와 곡 목록",null,"",()->choose("폴더 표시 방식",new String[]{"폴더와 곡 목록","앨범 커버"},settings.folderAlbums?1:0,w->settings.folderAlbums=w==1)));
            adapter.rows.add(new Entry("폴더 재생 범위",settings.includeSubfolders?"하위 폴더 포함":"현재 폴더만",null,"",()->choose("폴더 재생 범위",new String[]{"현재 폴더만","하위 폴더 포함"},settings.includeSubfolders?1:0,w->settings.includeSubfolders=w==1)));
            adapter.rows.add(new Entry("스트리밍 음질",settings.original?"원본":"MP3 · 최대 "+settings.bitrate+"kbps",null,"",()->choose("스트리밍 음질",new String[]{"원본","높음 · 320kbps","보통 · 192kbps"},settings.original?0:settings.bitrate==320?1:2,w->{settings.original=w==0;settings.bitrate=w==1?320:192;})));
            adapter.rows.add(new Entry("화면 테마",settings.systemTheme?"기기 설정에 따름":settings.dark?"One UI 스타일 · 다크":"라이트",null,"",()->choose("화면 테마",new String[]{"기기 설정에 따름","One UI 스타일 · 다크","라이트"},settings.systemTheme?0:settings.dark?1:2,w->{settings.systemTheme=w==0;if(w>0)settings.dark=w==1;})));
            adapter.rows.add(new Entry("재생 버튼 크기",settings.playButtonDp+" dp",null,"",()->choose("재생 버튼 크기",new String[]{"작게 · 56 dp","보통 · 64 dp","크게 · 72 dp"},settings.playButtonDp==56?0:settings.playButtonDp==72?2:1,w->settings.playButtonDp=new int[]{56,64,72}[w])));
            adapter.rows.add(new Entry("재생 버튼 간격",settings.playerGapDp+" dp",null,"",()->choose("재생 버튼 간격",new String[]{"좁게 · 4 dp","보통 · 8 dp","넓게 · 12 dp"},settings.playerGapDp==4?0:settings.playerGapDp==12?2:1,w->settings.playerGapDp=new int[]{4,8,12}[w])));
            adapter.rows.add(new Entry("재생 버튼 위치",new String[]{"왼쪽","가운데","오른쪽"}[settings.transportAlignment],null,"",()->choose("재생 버튼 위치",new String[]{"왼쪽","가운데","오른쪽"},settings.transportAlignment,w->settings.transportAlignment=w)));
            adapter.rows.add(new Entry("뒤로 탐색",settings.seekBackwardSeconds+"초",null,"",()->choose("뒤로 탐색",new String[]{"5초","10초","15초","30초"},Arrays.asList(5,10,15,30).indexOf(settings.seekBackwardSeconds),w->settings.seekBackwardSeconds=new int[]{5,10,15,30}[w])));
            adapter.rows.add(new Entry("앞으로 탐색",settings.seekForwardSeconds+"초",null,"",()->choose("앞으로 탐색",new String[]{"5초","10초","15초","30초"},Arrays.asList(5,10,15,30).indexOf(settings.seekForwardSeconds),w->settings.seekForwardSeconds=new int[]{5,10,15,30}[w])));
            adapter.rows.add(new Entry("글꼴","Pretendard · 기기 글자 크기 반영",null,"",()->info("글꼴","앱에 포함된 Pretendard를 사용하며 기기의 글자 크기 설정을 반영합니다.\n\nPretendard · SIL Open Font License 1.1\nhttps://github.com/orioncactus/pretendard")));
            adapter.rows.add(new Entry("시작 화면",settings.startTab,null,"",()->choose("시작 화면",new String[]{"폴더","앨범","곡"},java.util.Arrays.asList("폴더","앨범","곡").indexOf(settings.startTab),w->settings.startTab=new String[]{"폴더","앨범","곡"}[w])));
            adapter.rows.add(new Entry("곡 기본 정렬","",null,"",()->showSort(true)));
            adapter.rows.add(new Entry("영어 관사 제외",ignoreArticles()?"The / A / An 제외":"포함",null,"",()->{getPreferences(0).edit().putBoolean("ignoreArticles",!ignoreArticles()).apply();renderRows(false);}));
            adapter.rows.add(new Entry("곡 목록 새로고침",songsLoaded?allSongs.size()+" tracks":"",null,"",()->{songsLoaded=false;loadSongLibrary();}));
            adapter.rows.add(new Entry("앨범 목록 새로고침","",null,"",()->{albumQuery="";resetAlbums();}));
            adapter.rows.add(new Entry("NaviFold 0.8.4","",null,"",()->info("NaviFold","Pretendard와 Material 벡터 아이콘을 사용하는 Android 음악 플레이어입니다.\n\n설정에서 ChatGPT 로그인·계정 모델 선택·추천 목록 재생을 사용할 수 있습니다. SMART DJ에서 자동 큐 보충·취향 기반 자동 넘기기를 사용할 수 있습니다. 오프라인 다운로드와 스마트 플레이리스트 편집은 아직 구현하지 않았습니다.")));

        }
        adapter.notifyDataSetChanged();resizeGrid();if(state!=null)grid.onRestoreInstanceState(state);else if(reset)grid.setSelection(0);
    }
    Entry albumEntry(JSONObject album){return new Entry(Library.title(album),Library.artist(album)+(album.optInt("year")>0?" · "+album.optInt("year"):""),album,album.optString("coverArt"),()->openAlbum(album));}
    void queueAlbum(JSONObject album){
        if(api==null){showConnection();return;}Api client=api;int auth=session;
        queueWork.execute(()->{try{List<JSONObject> tracks=Library.array(client.call("getAlbum","id",album.optString("id")).optJSONObject("album"),"song");tracks.sort(Library.TRACK_ORDER);main.post(()->{if(!destroyed&&auth==session)editQueue(tracks,false);});}catch(Exception e){main.post(()->{if(!destroyed&&auth==session)reportError("앨범을 큐에 추가하지 못했습니다");});}});
    }
    void editQueue(List<JSONObject> songs,boolean next){
        if(api==null||controller==null||songs.isEmpty())return;Api client=api;int auth=session;boolean raw=original;int bitrate=settings.bitrate;
        queueWork.execute(()->{try{List<MediaItem> items=new ArrayList<>();for(JSONObject song:songs)items.add(PlaybackItems.make(client,song,raw,bitrate,false));main.post(()->{if(destroyed||auth!=session||controller==null)return;QueueEdits.insert(controller,items,next);android.widget.Toast.makeText(this,items.size()+" · "+(next?"Play next":"Play later"),android.widget.Toast.LENGTH_SHORT).show();});}catch(Exception e){main.post(()->{if(!destroyed&&auth==session)reportError("큐에 추가하지 못했습니다");});}});
    }
    List<JSONObject> selectedTracks(){List<JSONObject> songs=new ArrayList<>();for(JSONObject song:detailSongs)if(selectedTrackIds.contains(song.optString("id")))songs.add(song);return songs;}
    void toggleTrackSelection(JSONObject song){String id=song.optString("id");if(!selectedTrackIds.add(id))selectedTrackIds.remove(id);updateTrackSelection();}
    void updateTrackSelection(){if(selectionBar!=null)selectionBar.setVisibility(selectedTrackIds.isEmpty()?View.GONE:View.VISIBLE);if(selectionCount!=null)selectionCount.setText(selectedTrackIds.size()+" SELECTED");if(detailAdapter!=null)detailAdapter.notifyDataSetChanged();}
    void selectedQueueAction(int action){
        if(controller==null)return;
        if(action==2)QueueEdits.remove(controller,new HashSet<>(selectedTrackIds));else editQueue(selectedTracks(),action==0);
        selectedTrackIds.clear();updateTrackSelection();
    }
    LinearLayout selectionControls(){
        LinearLayout box=col();LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);selectionCount=text("",12,MUTED);title.addView(selectionCount,new LinearLayout.LayoutParams(0,-2,1));title.addView(actionIcon(R.drawable.ic_delete,"선택 해제",()->{selectedTrackIds.clear();updateTrackSelection();}),new LinearLayout.LayoutParams(dp(48),dp(48)));box.addView(title);
        LinearLayout actions=new LinearLayout(this);addWeighted(actions,button("Play next",()->selectedQueueAction(0)));addWeighted(actions,button("Play later",()->selectedQueueAction(1)));addWeighted(actions,button("Remove",()->selectedQueueAction(2)));box.addView(actions);box.setPadding(0,0,0,dp(8));return box;
    }
    void openAlbum(JSONObject album){
        if(api==null){showConnection();return;}
        selectedTrackIds.clear();remember();selectedAlbum=album;selectedAlbumId=album.optString("id");detailTitle=Library.title(album);detailSongs.clear();detailVisible=true;
        int request=++detailRequest,auth=session;Api client=api;report("앨범 곡을 불러오는 중…");build();
        network.execute(()->{try{
            JSONObject loaded=client.call("getAlbum","id",album.optString("id")).optJSONObject("album");List<JSONObject> tracks=Library.array(loaded,"song");tracks.sort(MediaSort.comparator(activeSort("tracks"),ignoreArticles()));
            main.post(()->{if(destroyed||request!=detailRequest||auth!=session)return;if(loaded!=null)for(String key:new String[]{"recordLabels","label","genre","genres","year","musicBrainzId"})try{if(loaded.has(key))album.put(key,loaded.get(key));}catch(JSONException ignored){}detailSongs.clear();detailSongs.addAll(tracks);report("앨범 "+tracks.size()+"곡 · 디스크/트랙 순서");renderDetail();});
        }catch(Exception e){main.post(()->{if(!destroyed&&request==detailRequest&&auth==session){reportError(safeError(e));renderDetail();}});}});
    }
    List<JSONObject> folderSongs(Library.Node node){if(settings.includeSubfolders)return node.allSongs();List<JSONObject> out=new ArrayList<>(node.songs);out.sort(Library.TRACK_ORDER);return out;}
    void showFolderSongs(Library.Node node){showSongs(node.breadcrumb(),folderSongs(node),null);}
    void showSongs(String title,List<JSONObject> tracks,JSONObject album){selectedTrackIds.clear();detailRequest++;remember();selectedAlbum=album;selectedAlbumId=album==null?"":album.optString("id");detailTitle=title;detailSongs.clear();detailSongs.addAll(tracks);detailVisible=true;build();}
    void renderDetail(){
        detail.removeAllViews();detailAdapter=null;selectionBar=null;selectionCount=null;if(detailTitle.isEmpty())return;
        Button back=actionIcon(R.drawable.ic_back,"라이브러리로 돌아가기",()->{selectedTrackIds.clear();detailVisible=false;build();});back.setBackgroundColor(android.graphics.Color.TRANSPARENT);detail.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout body=new LinearLayout(this);boolean split=wide&&tab.equals("앨범");body.setOrientation(split?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);detail.addView(body,new LinearLayout.LayoutParams(-1,0,1));LinearLayout info=col();if(split){info.setPadding(0,dp(12),dp(24),0);ScrollView infoScroll=new ScrollView(this);infoScroll.setFillViewport(true);infoScroll.addView(info);body.addView(infoScroll,new LinearLayout.LayoutParams(dp(240),-1));}else body.addView(info,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout header=new LinearLayout(this);header.setOrientation(split?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);info.addView(header);
        if(selectedAlbum!=null){ImageView art=new RoundedArtwork(this,10);art.setScaleType(ImageView.ScaleType.CENTER_CROP);art.setBackground(shape(SURFACE,10));art.setClipToOutline(true);int size=split?200:96;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(size),dp(size));lp.setMarginEnd(dp(16));header.addView(art,lp);covers.bind(art,api,selectedAlbum.optString("coverArt"),720);}
        LinearLayout words=col();header.addView(words,new LinearLayout.LayoutParams(split?-1:0,-2,split?0:1));TextView title=text(detailTitle,18,INK);title.setTypeface(UiAssets.semibold(this));title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);words.addView(title);if(selectedAlbum!=null)words.addView(text(Library.artist(selectedAlbum),13,MUTED));long seconds=0;for(JSONObject song:detailSongs)seconds+=song.optLong("duration");words.addView(text((selectedAlbum!=null&&selectedAlbum.optInt("year")>0?selectedAlbum.optInt("year")+" · ":"")+detailSongs.size()+" tracks · "+SongMetadata.duration(seconds),12,MUTED));
        LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(12),0,dp(8));Button play=actionIcon(R.drawable.ic_play,"전체 재생",()->playSongs(new ArrayList<>(detailSongs),0,false));primary(play);addWeighted(actions,play);addWeighted(actions,actionIcon(R.drawable.ic_shuffle,"셔플",()->{ArrayList<JSONObject> songs=new ArrayList<>(detailSongs);Collections.shuffle(songs);playSongs(songs,0,false);}));info.addView(actions);
        LinearLayout secondary=new LinearLayout(this);addWeighted(secondary,actionIcon(R.drawable.ic_queue,"대기열 추가",()->playSongs(new ArrayList<>(detailSongs),0,true)));if(selectedAlbum!=null){Button favorite=actionIcon(R.drawable.ic_favorite,selectedAlbum.has("starred")?"즐겨찾기 해제":"즐겨찾기",this::toggleFavorite);favorite.setCompoundDrawables(null,UiAssets.drawable(this,R.drawable.ic_favorite,selectedAlbum.has("starred")?ACCENT:MUTED,24),null,null);addWeighted(secondary,favorite);}info.addView(secondary);
        if(selectedAlbum!=null&&!detailSongs.isEmpty())info.addView(AlbumNotes.card(this,selectedAlbum,new ArrayList<>(detailSongs)));
        LinearLayout trackPane=col();Button trackSort=actionIcon(R.drawable.ic_sort,"곡 정렬",()->showSort(false));trackPane.addView(trackSort,new LinearLayout.LayoutParams(dp(48),dp(48)));selectionBar=selectionControls();trackPane.addView(selectionBar);
        ListView tracks=new ListView(this);tracks.setDivider(new android.graphics.drawable.ColorDrawable(SURFACE));tracks.setDividerHeight(dp(1));detailAdapter=new BaseAdapter(){public int getCount(){return detailSongs.size();}public Object getItem(int p){return detailSongs.get(p);}public long getItemId(int p){return p;}public View getView(int pos,View convert,ViewGroup parent){JSONObject song=detailSongs.get(pos);LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(64));row.setPadding(0,dp(4),0,dp(4));boolean current=controller!=null&&controller.getCurrentMediaItem()!=null&&song.optString("id").equals(controller.getCurrentMediaItem().mediaId);boolean selected=selectedTrackIds.contains(song.optString("id"));row.setBackground(selected?shape(settings.dark?0xff263040:0xffe5edf7,12):null);row.setContentDescription(song.optString("title")+(selected?" · 선택됨":""));TextView number=text(selected?"✓":current?"▶":String.format(Locale.ROOT,"%02d",song.optInt("track",pos+1)),12,current?ACCENT:MUTED);number.setGravity(Gravity.CENTER);row.addView(number,new LinearLayout.LayoutParams(dp(32),-2));LinearLayout label=col();TextView name=text(song.optString("title"),15,current?ACCENT:INK);name.setMaxLines(settings.titleMode==0?2:1);name.setEllipsize(settings.titleMode==1?TextUtils.TruncateAt.MARQUEE:TextUtils.TruncateAt.END);name.setSelected(settings.titleMode==1);label.addView(name);TextView artist=text(Library.artist(song),12,MUTED);artist.setMaxLines(1);label.addView(artist);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));row.addView(text(SongMetadata.duration(song.optLong("duration")),12,MUTED));Button more=iconButton("","곡 옵션",()->trackOptions(song));more.setCompoundDrawables(null,UiAssets.drawable(MainActivity.this,R.drawable.ic_more,MUTED,24),null,null);row.addView(more);row.setOnClickListener(v->{if(!selectedTrackIds.isEmpty())toggleTrackSelection(song);else playSongs(new ArrayList<>(detailSongs),pos,false);});row.setOnLongClickListener(v->{toggleTrackSelection(song);return true;});return row;}};tracks.setAdapter(detailAdapter);trackPane.addView(tracks,new LinearLayout.LayoutParams(-1,0,1));body.addView(trackPane,split?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1));updateTrackSelection();
    }
    void trackOptions(JSONObject song){dialog().setTitle(song.optString("title")).setItems(new String[]{"다음에 재생","대기열 끝에 추가","파일 · 태그 정보"},(d,w)->{if(w==1)editQueue(Collections.singletonList(song),false);else if(w==0){if(api==null||controller==null)return;playNext(song);}else{StringBuilder text=new StringBuilder();for(Map.Entry<String,String> row:SongMetadata.rows(song).entrySet())text.append(row.getKey()).append(" · ").append(row.getValue()).append("\n\n");info("파일 · 태그 정보",text.toString());}}).show();}
    void playNext(JSONObject song){editQueue(Collections.singletonList(song),true);}
    void playSongs(List<JSONObject> songs,int at,boolean append){
        if(api==null||songs.isEmpty()){reportError("재생할 곡이 없습니다.");return;}if(controller==null){reportError("플레이어 연결 중입니다. 잠시 후 다시 선택하세요.");return;}
        Api client=api;boolean raw=original;int auth=session;report("재생 대기열 준비 중…");
        network.execute(()->{try{
            List<MediaItem> items=new ArrayList<>();
            for(JSONObject song:songs){
                items.add(PlaybackItems.make(client,song,raw,settings.bitrate,false));
            }
            main.post(()->{if(destroyed||auth!=session||controller==null)return;playerError="";
                if(append&&controller.getMediaItemCount()>0){controller.addMediaItems(items);report(items.size()+"곡을 대기열에 추가했습니다");}
                else{PlaybackNotifications.request(this,false);controller.setMediaItems(items,at,0);applyPlaybackSettings();controller.prepare();controller.play();report(items.size()+"곡 · 순서대로 재생");}
                updatePlayer();
            });
        }catch(Exception e){main.post(()->{if(!destroyed&&auth==session)reportError("재생 준비 실패");});}});
    }
    void toggleFavorite(){
        if(api==null||selectedAlbum==null)return;JSONObject album=selectedAlbum;boolean starred=album.has("starred");Api client=api;int auth=session;
        network.execute(()->{try{client.call(starred?"unstar":"star","albumId",album.optString("id"));main.post(()->{if(destroyed||auth!=session)return;try{if(starred)album.remove("starred");else album.put("starred","local");}catch(JSONException ignored){}renderDetail();report(starred?"즐겨찾기 해제":"즐겨찾기에 추가했습니다");});}catch(Exception e){main.post(()->{if(!destroyed&&auth==session)reportError(safeError(e));});}});
    }
    void loadPlaylists(){
        if(api==null){showConnection();return;}int request=++libraryRequest,auth=session;Api client=api;report("플레이리스트 조회 중…");
        network.execute(()->{try{List<JSONObject> playlists=Library.array(client.call("getPlaylists").optJSONObject("playlists"),"playlist");main.post(()->{if(destroyed||auth!=session||request!=libraryRequest||!tab.equals("보관함"))return;libraryRows.clear();libraryRows.add(new Entry("‹ 보관함","",null,"",()->{libraryRows.clear();renderRows(true);}));for(JSONObject p:playlists)libraryRows.add(new Entry(p.optString("name"),p.optInt("songCount")+"곡",null,"",()->loadPlaylist(p)));report("플레이리스트 "+playlists.size()+"개");renderRows(true);});}catch(Exception e){main.post(()->{if(!destroyed&&auth==session)reportError(safeError(e));});}});
    }
    void loadPlaylist(JSONObject playlist){
        int request=++detailRequest,auth=session;Api client=api;report("플레이리스트 곡 조회 중…");
        network.execute(()->{try{List<JSONObject> songs=Library.array(client.call("getPlaylist","id",playlist.optString("id")).optJSONObject("playlist"),"entry");main.post(()->{if(!destroyed&&auth==session&&request==detailRequest)showSongs(playlist.optString("name"),songs,null);});}catch(Exception e){main.post(()->{if(!destroyed&&auth==session)reportError(safeError(e));});}});
    }
    List<JSONObject> visibleSongs(){String key=session+"|"+songsRequest+"|"+allSongs.size()+"|"+songQuery+"|"+activeSort("songs")+"|"+ignoreArticles();if(key.equals(visibleSongKey))return visibleSongCache;List<JSONObject> out=new ArrayList<>();String query=songQuery.toLowerCase(Locale.ROOT);for(JSONObject song:allSongs)if(query.isEmpty()||(song.optString("title")+" "+Library.artist(song)+" "+song.optString("album")).toLowerCase(Locale.ROOT).contains(query))out.add(song);
        out.sort(MediaSort.comparator(activeSort("songs"),ignoreArticles()));visibleSongKey=key;visibleSongCache=out;return out;}

    void filterSongLibrary(){EditText q=field("제목 · 아티스트 · 앨범",songQuery,false);dialog().setTitle("SONG").setView(q).setNegativeButton("취소",null).setPositiveButton("검색",(d,w)->{songQuery=q.getText().toString().trim();scrollPositions.remove(positionKey());build();}).show();}
    void loadSongLibrary(){if(api==null||songsLoading||destroyed)return;songsLoading=true;int request=++songsRequest,auth=session;Api client=api;
        network.execute(()->{try{List<JSONObject> found=new ArrayList<>();Set<String> ids=new HashSet<>();int offset=0;while(!destroyed&&request==songsRequest&&auth==session){List<JSONObject> page=Library.array(client.call("search3","query","","artistCount","0","albumCount","0","songCount","500","songOffset",String.valueOf(offset)).optJSONObject("searchResult3"),"song");if(page.isEmpty())break;int added=0;for(JSONObject song:page)if(!song.optString("id").isEmpty()&&ids.add(song.optString("id"))){found.add(song);added++;}if(added==0)throw new Exception("곡 목록 응답 오류");offset+=page.size();}
            main.post(()->{if(destroyed||request!=songsRequest||auth!=session)return;songsLoading=false;songsLoaded=true;allSongs.clear();allSongs.addAll(found);if(tab.equals("곡")){report("");}if(tab.equals("곡")){pageHeading.setText(pageTitle());renderRows(false);}});
        }catch(Exception e){main.post(()->{if(!destroyed&&request==songsRequest&&auth==session){songsLoading=false;reportError(safeError(e));}});}});
    }
    final class SongCell {
        final LinearLayout row=new LinearLayout(MainActivity.this);final ImageView cover=new RoundedArtwork(MainActivity.this,12);final TextView title=text("",15,INK),subtitle=text("",12,MUTED),duration=text("",12,MUTED);final Button more;Entry entry;
        SongCell(){row.setTag(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(72));row.setPadding(dp(4),dp(8),0,dp(8));cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setBackground(shape(SURFACE,12));cover.setClipToOutline(true);cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(cover,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout words=col();words.setPadding(dp(12),0,dp(8),0);title.setTypeface(UiAssets.semibold(MainActivity.this));title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);subtitle.setSingleLine(true);subtitle.setEllipsize(TextUtils.TruncateAt.END);words.addView(title);words.addView(subtitle);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));duration.setFontFeatureSettings("tnum");row.addView(duration);more=actionIcon(R.drawable.ic_more,"곡 옵션",()->{if(entry!=null)trackOptions(entry.song);});row.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));row.setOnClickListener(v->{if(entry!=null)entry.action.run();});}
        void bind(Entry value){entry=value;JSONObject song=value.song;title.setText(song.optString("title"));subtitle.setText(Library.artist(song)+" · "+song.optString("album"));duration.setText(SongMetadata.duration(song.optLong("duration")));more.setContentDescription("곡 옵션 · "+value.title);covers.bind(cover,api,song.optString("coverArt"),160);}
    }
    void searchSongs(){
        if(api==null){showConnection();return;}EditText q=field("곡 제목 또는 아티스트","",false);
        dialog().setTitle("곡 검색").setView(q).setNegativeButton("취소",null).setPositiveButton("검색",(d,w)->{
            String term=q.getText().toString().trim();if(term.isEmpty())return;Api client=api;int auth=session,request=++detailRequest;
            network.execute(()->{try{List<JSONObject> songs=Library.array(client.call("search3","query",term,"songCount","500","artistCount","0","albumCount","0").optJSONObject("searchResult3"),"song");main.post(()->{if(!destroyed&&auth==session&&request==detailRequest){showSongs("검색: "+term,songs,null);report("검색 결과 "+songs.size()+"곡 · 최대 500건");}});}catch(Exception e){main.post(()->{if(!destroyed&&auth==session)reportError(safeError(e));});}});
        }).show();
    }
    void logout(){
        ServerCredentials.clear(this);
        loginRequest++;session++;selectedTrackIds.clear();songsRequest++;songsLoaded=false;songsLoading=false;allSongs.clear();songQuery="";albumRequest++;albumCountRequest++;albumTotal=-1;detailRequest++;indexRequest++;libraryRequest++;SmartSession.connect(null);api=null;index=null;folder=null;indexLoading=false;albumLoading=false;albumFailed=false;indexFailed=false;
        albumEnd=false;albumOffset=0;albums.clear();albumIds.clear();detailSongs.clear();libraryRows.clear();detailTitle="";selectedAlbum=null;detailVisible=false;covers.clear();scrollPositions.clear();
        if(controller!=null){controller.stop();controller.clearMediaItems();}report("로그아웃했습니다");build();
    }
    void updateMiniVisibility(){if(mini!=null)mini.setVisibility(controller==null||controller.getMediaItemCount()==0?View.GONE:nowPlayingSheet!=null&&nowPlayingSheet.isShowing()?View.INVISIBLE:View.VISIBLE);}
    void updatePlayer(){
        updateMiniVisibility();
        NetworkUsage usage=NetworkUsage.get(this);usage.sample(false);if(networkNotice!=null){boolean playing=controller!=null&&controller.getMediaItemCount()>0;boolean raw=playing&&(controller.getCurrentMediaItem()==null||controller.getCurrentMediaItem().mediaMetadata.extras==null||!controller.getCurrentMediaItem().mediaMetadata.extras.getBoolean("transcoded",false));String hint=!usage.prefs.getBoolean("warnings",true)?"":usage.overLimit()?"DATA · 일일 경고 기준 도달 · 사용량 보기":usage.metered&&playing?raw?"DATA · 원본 재생 중 · MP3 192 kbps 권장":"DATA · "+usage.network+" · 사용량 보기":"";if(!hint.contentEquals(networkNotice.getText()))networkNotice.setText(hint);networkNotice.setVisibility(hint.isEmpty()?View.GONE:View.VISIBLE);}
        if(controller==null||now==null)return;MediaMetadata meta=controller.getMediaMetadata();if(qualityStatus!=null){QualityInfo q=AudioQuality.get(controller);qualityStatus.setVisibility(settings.indicatorMode>0&&controller.getMediaItemCount()>0?View.VISIBLE:View.GONE);String qualityKey=settings.indicatorMode+"|"+q.color+"|"+q.label;if(!qualityKey.equals(qualityStatus.getTag())){qualityStatus.setTag(qualityKey);android.text.SpannableString label=new android.text.SpannableString("● "+QualityBadge.compact(q.label));label.setSpan(new android.text.style.ForegroundColorSpan(q.color),0,1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);if(!qualityStatus.getText().toString().equals(label.toString()))qualityStatus.setText(label);qualityStatus.setContentDescription(q.label);qualityStatus.setTooltipText(q.label);qualityStatus.setOnClickListener(v->showNowPlaying());qualityStatus.setTextColor(INK);qualityStatus.setBackground(GlassUi.panel(this,settings.dark,20,85));qualityStatus.setElevation(settings.indicatorMode==1?dp(2):0);}}
        setTextIfChanged(now,playerError.isEmpty()?(meta.title==null?"—":meta.title.toString()):playerError);setTextIfChanged(miniArtist,meta.artist==null?"":meta.artist.toString());
        toggle.setPlaying(controller.getPlayWhenReady());toggle.setBuffering(controller.getPlaybackState()==Player.STATE_BUFFERING);toggle.setEnabled(controller.getMediaItemCount()>0);
        if(miniCover!=null)miniCover.setVisibility(controller.getMediaItemCount()>0?View.VISIBLE:View.GONE);
        String cover=meta.extras==null?"":meta.extras.getString("coverArt","");if(miniCover!=null&&!cover.equals(lastArtworkId)){lastArtworkId=cover;covers.bind(miniCover,api,cover);}long duration=PlaybackInfo.duration(controller),position=controller.getCurrentPosition();
        seek.setEnabled(duration>0);int max=(int)Math.min(Integer.MAX_VALUE,Math.max(1,duration)),progress=(int)Math.min(Integer.MAX_VALUE,position);if(seek.getMax()!=max)seek.setMax(max);if(seek.getProgress()!=progress)seek.setProgress(progress);
        setTextIfChanged(time,formatTime(position)+" / "+(duration>0?formatTime(duration):"--:--")+(controller.getMediaItemCount()>0?" · "+(controller.getCurrentMediaItemIndex()+1)+"/"+controller.getMediaItemCount():""));
    }
    void setTextIfChanged(TextView view,String value){if(!view.getText().toString().equals(value))view.setText(value);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] result){super.onRequestPermissionsResult(request,permissions,result);if(request==31&&tab.equals("설정"))build();}
    @Override protected void onResume(){super.onResume();if(settings!=null&&adapter!=null&&tab.equals("설정"))renderRows(false);}
    @Override protected void onStart(){super.onStart();AppVisibility.activities++;main.removeCallbacks(tick);main.post(tick);}
    @Override protected void onStop(){NetworkUsage.get(this).sample(true);AppVisibility.activities=Math.max(0,AppVisibility.activities-1);main.removeCallbacks(tick);super.onStop();}
    String formatTime(long ms){return String.format(Locale.ROOT,"%d:%02d",ms/60000,ms/1000%60);}
    String safeError(Exception e){
        if(e instanceof javax.net.ssl.SSLException)return "HTTPS 인증서를 확인하세요.";
        if(e instanceof java.net.UnknownHostException)return "서버 주소를 찾을 수 없습니다.";
        if(e instanceof java.net.SocketTimeoutException)return "서버 응답 시간 초과 · 다시 시도하세요.";
        if(e instanceof java.io.IOException)return "네트워크 연결 실패 · 서버 주소와 포트를 확인하세요.";
        if(e instanceof JSONException)return "서버 응답 형식을 확인하세요.";
        return e.getMessage()==null?"요청 실패":e.getMessage();
    }
    void info(String title,String msg){dialog().setTitle(title).setMessage(msg).setPositiveButton("확인",null).show();}
    @Override public void onConfigurationChanged(Configuration c){boolean reopen=nowPlayingSheet!=null&&nowPlayingSheet.isShowing();if(reopen)nowPlayingSheet.dismiss();remember();super.onConfigurationChanged(c);build();if(reopen)main.post(this::showNowPlaying);}
    @Override public void onBackPressed(){
        if(!selectedTrackIds.isEmpty()){selectedTrackIds.clear();updateTrackSelection();}
        else if(detailVisible){detailVisible=false;build();}
        else if(tab.equals("폴더")&&folder!=null&&folder.parent!=null){remember();folder=folder.parent;build();}
        else if(!tab.equals("폴더"))navigate("폴더");else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(nowPlayingSheet!=null)nowPlayingSheet.dismiss();destroyed=true;session++;songsRequest++;indexRequest++;albumRequest++;albumCountRequest++;detailRequest++;main.removeCallbacksAndMessages(null);network.shutdownNow();queueWork.shutdownNow();covers.close();
        if(controller!=null)controller.removeListener(playerListener);if(controllerFuture!=null)MediaController.releaseFuture(controllerFuture);
        if(connectionDialog!=null)connectionDialog.dismiss();api=null;super.onDestroy();
    }
    static final class Entry {
        final String title,subtitle,cover; JSONObject song;final JSONObject album;final Runnable action;
        Entry(String title,String subtitle,JSONObject album,String cover,Runnable action){this.title=title;this.subtitle=subtitle;this.album=album;this.cover=cover;this.action=action;}
    }
    final class LibraryCell {
        final LinearLayout root=col();final TextView title=text("",15,INK),subtitle=text("",12,MUTED);SquareImage cover;
        LibraryCell(boolean album){root.setTag(this);if(album){root.setPadding(dp(2),dp(4),dp(2),dp(8));cover=new SquareImage(MainActivity.this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setBackground(shape(SURFACE,18));cover.setClipToOutline(true);cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams art=new LinearLayout.LayoutParams(-1,-2);art.bottomMargin=dp(8);root.addView(cover,art);title.setTextSize(settings.coverIndex()==0?12:14);title.setSingleLine(true);subtitle.setSingleLine(true);title.setTypeface(UiAssets.semibold(MainActivity.this));root.addView(title);root.addView(subtitle);}else{root.setOrientation(LinearLayout.HORIZONTAL);root.setGravity(Gravity.CENTER_VERTICAL);root.setPadding(dp(16),dp(14),dp(12),dp(14));root.setMinimumHeight(dp(72));LinearLayout words=col();title.setMaxLines(2);title.setTypeface(UiAssets.semibold(MainActivity.this));subtitle.setMaxLines(2);words.addView(title);words.addView(subtitle);root.addView(words,new LinearLayout.LayoutParams(0,-2,1));ImageView chevron=new ImageView(MainActivity.this);chevron.setImageDrawable(UiAssets.drawable(MainActivity.this,R.drawable.ic_next,MUTED,16));chevron.setAlpha(.45f);chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);root.addView(chevron,new LinearLayout.LayoutParams(dp(24),dp(24)));if(tab.equals("설정"))root.setBackground(GlassUi.panel(MainActivity.this,settings.dark,20,65));}title.setEllipsize(TextUtils.TruncateAt.END);subtitle.setEllipsize(TextUtils.TruncateAt.END);}
        void bind(Entry entry){title.setText(entry.title);subtitle.setText(entry.subtitle);subtitle.setVisibility(entry.subtitle.isEmpty()?View.GONE:View.VISIBLE);root.setContentDescription(entry.title+(entry.subtitle.isEmpty()?"":" · "+entry.subtitle));if(cover!=null)covers.bind(cover,api,entry.cover);}
    }
    final class EntryAdapter extends BaseAdapter {
        final ArrayList<Entry> rows=new ArrayList<>();
        public int getCount(){return rows.size();}public Object getItem(int pos){return rows.get(pos);}public long getItemId(int pos){return pos;}
        public int getViewTypeCount(){return 3;}public int getItemViewType(int pos){Entry entry=rows.get(pos);return entry.song!=null?2:entry.album!=null?0:1;}
        public View getView(int pos,View convert,ViewGroup parent){Entry entry=rows.get(pos);if(entry.song!=null){SongCell cell=convert!=null&&convert.getTag() instanceof SongCell?(SongCell)convert.getTag():new SongCell();cell.bind(entry);return cell.row;}LibraryCell cell=convert!=null&&convert.getTag() instanceof LibraryCell?(LibraryCell)convert.getTag():new LibraryCell(entry.album!=null);cell.bind(entry);return cell.root;}
    }
    int entryPadding(){return tab.equals("설정")?10:4;}
    static final class SquareImage extends RoundedArtwork {
        SquareImage(Context context){super(context,18);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){int width=View.MeasureSpec.getSize(widthSpec);setMeasuredDimension(width,width);}
    }
}
