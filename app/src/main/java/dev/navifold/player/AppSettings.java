package dev.navifold.player;

import android.content.SharedPreferences;
import androidx.media3.common.Player;
import java.util.Arrays;

/** Single source of truth for user-controlled parameters. Credentials are not stored here. */
final class AppSettings {
    static SharedPreferences preferences(android.content.Context context){String name=MainActivity.class.getName(),prefix=context.getPackageName()+".";return context.getSharedPreferences(name.startsWith(prefix)?name.substring(prefix.length()):name,0);}

    private static SharedPreferences cachedPrefs;private static AppSettings cachedValue;private static boolean cacheDirty=true;
    private static final SharedPreferences.OnSharedPreferenceChangeListener cacheListener=(p,k)->{synchronized(AppSettings.class){cacheDirty=true;}};
    /** Read-only shared snapshot for periodic indicator work; mutable screens use their own instance. */
    static synchronized AppSettings cached(android.content.Context context){if(cachedPrefs==null){cachedPrefs=preferences(context.getApplicationContext());cachedPrefs.registerOnSharedPreferenceChangeListener(cacheListener);}if(cacheDirty||cachedValue==null){cachedValue=new AppSettings(cachedPrefs);cacheDirty=false;}return cachedValue;}
    static final int[] COVER_SIZES={84,128,192};
    static final String[] COVER_LABELS={"작게 · 더 많이 보기","보통","크게 · 표지 감상"};
    static final String[] SORT_VALUES={"alphabeticalByArtist","alphabeticalByName","newest","recent","frequent","byYear","byYearAscending","random"};
    static final String[] SORT_LABELS={"앨범 아티스트","앨범명","최근 추가","최근 재생","자주 재생","발매연도 · 최신순","발매연도 · 오래된순","무작위"};
    private final SharedPreferences prefs;
    int songSort;int playerBackgroundBlur;
    int ringCompactDp,ringWideDp,indicatorStyle,islandWidthDp;
    int titleMode,bitrate,indicatorMode,islandCompactX,islandCompactY,islandWideX,islandWideY;boolean lockPopup;
    int coverDp; String sort,startTab;boolean dark,original,folderAlbums,includeSubfolders,favoritesOnly,shuffle;
    int repeat,playButtonDp,playerGapDp,seekBackwardSeconds,seekForwardSeconds,transportAlignment;boolean systemTheme;
    AppSettings(SharedPreferences prefs){
        this.prefs=prefs;songSort=IndicatorPolicy.clamp(prefs.getInt("songSort",0),0,3);playerBackgroundBlur=IndicatorPolicy.clamp(prefs.getInt("playerBackgroundBlur",60),0,100);indicatorStyle=IndicatorPolicy.clamp(prefs.getInt("indicatorStyle",2),0,2);islandWidthDp=IndicatorPolicy.clamp(prefs.getInt("islandWidthDp",224),140,480);ringCompactDp=IndicatorPolicy.clamp(prefs.getInt("ringCompactDp",24),8,96);ringWideDp=prefs.getInt("ringWideDp",24);indicatorMode=Math.max(0,Math.min(2,prefs.getInt("indicatorMode",1)));lockPopup=prefs.getBoolean("lockPopup",true);islandCompactX=IndicatorPolicy.clamp(prefs.getInt("islandCompactX",0),-3000,3000);islandCompactY=IndicatorPolicy.clamp(prefs.getInt("islandCompactY",0),-3000,3000);islandWideX=prefs.getInt("islandWideX",0);islandWideY=prefs.getInt("islandWideY",0);titleMode=Math.max(0,Math.min(2,prefs.getInt("titleMode",0)));bitrate=prefs.getInt("bitrate",192)==320?320:192;transportAlignment=prefs.getInt("transportAlignment",1);if(transportAlignment<0||transportAlignment>2)transportAlignment=1;
        playButtonDp=prefs.getInt("playButtonDp",64);if(playButtonDp!=56&&playButtonDp!=64&&playButtonDp!=72)playButtonDp=64;
        playerGapDp=prefs.getInt("playerGapDp",8);if(playerGapDp!=4&&playerGapDp!=8&&playerGapDp!=12)playerGapDp=8;
        seekBackwardSeconds=validSeek(prefs.getInt("seekBackwardSeconds",5));seekForwardSeconds=validSeek(prefs.getInt("seekForwardSeconds",5));systemTheme=prefs.getBoolean("systemTheme",false);
        coverDp=prefs.getInt("coverSize",128);if(coverDp!=84&&coverDp!=128&&coverDp!=192)coverDp=128;
        sort=prefs.getString("albumSort","alphabeticalByArtist");if(!Arrays.asList(SORT_VALUES).contains(sort))sort=SORT_VALUES[0];
        startTab=prefs.getString("startTab","폴더");if(!startTab.equals("앨범")&&!startTab.equals("곡"))startTab="폴더";
        dark=prefs.getBoolean("darkTheme",true);original=prefs.getBoolean("original",true);
        folderAlbums=prefs.getBoolean("folderAlbums",false);includeSubfolders=prefs.getBoolean("includeSubfolders",true);
        favoritesOnly=prefs.getBoolean("favoritesOnly",false);shuffle=prefs.getBoolean("shuffle",false);
        repeat=prefs.getInt("repeat",Player.REPEAT_MODE_OFF);if(repeat<0||repeat>2)repeat=Player.REPEAT_MODE_OFF;
    }
    private static int validSeek(int value){return value==5||value==10||value==15||value==30?value:5;}
    int coverIndex(){return coverDp==84?0:coverDp==192?2:1;}
    int sortIndex(){return Arrays.asList(SORT_VALUES).indexOf(sort);}
    void save(){prefs.edit().putInt("songSort",songSort).putInt("playerBackgroundBlur",playerBackgroundBlur).putInt("indicatorStyle",indicatorStyle).putInt("islandWidthDp",islandWidthDp).putInt("ringCompactDp",ringCompactDp).putInt("ringWideDp",ringWideDp).putInt("indicatorMode",indicatorMode).putBoolean("lockPopup",lockPopup).putInt("islandCompactX",islandCompactX).putInt("islandCompactY",islandCompactY).putInt("islandWideX",islandWideX).putInt("islandWideY",islandWideY).putInt("titleMode",titleMode).putInt("bitrate",bitrate).putInt("transportAlignment",transportAlignment).putInt("playButtonDp",playButtonDp).putInt("playerGapDp",playerGapDp).putInt("seekBackwardSeconds",seekBackwardSeconds).putInt("seekForwardSeconds",seekForwardSeconds).putBoolean("systemTheme",systemTheme).putInt("coverSize",coverDp).putString("albumSort",sort).putString("startTab",startTab)
        .putBoolean("darkTheme",dark).putBoolean("original",original).putBoolean("folderAlbums",folderAlbums)
        .putBoolean("includeSubfolders",includeSubfolders).putBoolean("favoritesOnly",favoritesOnly)
        .putBoolean("shuffle",shuffle).putInt("repeat",repeat).apply();}
}
