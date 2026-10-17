package dev.navifold.player;

import androidx.appcompat.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.EditText;
import org.json.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class SmartSettings {
    private static final AtomicBoolean indexing=new AtomicBoolean();
    static void show(MainActivity a){
        SharedPreferences p=SmartPlayback.settings(a);String scope=SmartSession.scope;
        String[] rows={"자동 큐 보충 · "+on(p,"autoplay"),"취향 기반 자동 넘기기 · "+on(p,"skip"),"감상 기록 학습 · "+on(p,"learn"),"연결 실패 시 로컬 추천 · "+(p.getBoolean("fallback",true)?"ON":"OFF"),"곡·장르·레이블·가수·앨범 검색","라이브러리 데이터 갱신","현재 곡 더 듣기","현재 곡 덜 듣기","현재 곡 선호 초기화","데이터 · 상태","이 계정 감상 데이터 삭제","MusicBrainz 정보 보완 · "+on(p,"external"),"장르 유지 · "+FlowPolicy.LABELS[Math.max(0,Math.min(2,p.getInt("flowMode",1)))]};
        new AlertDialog.Builder(a).setTitle("SMART DJ").setItems(rows,(d,w)->{
            if(w==12)new AlertDialog.Builder(a).setTitle("장르 유지").setSingleChoiceItems(FlowPolicy.LABELS,Math.max(0,Math.min(2,p.getInt("flowMode",1))),(x,y)->{p.edit().putInt("flowMode",y).apply();SmartSession.changed();x.dismiss();show(a);}).setNeutralButton("설명",(x,y)->a.info("장르 유지","엄격하게: 같은 장르 태그만. 자연스럽게: Pop/Synth-pop 등 가까운 장르까지. 두 모드 모두 J-pop/K-pop 등의 명시된 음악권과 알려진 언어 충돌을 제한합니다. 다양하게: 장르 제한 없이 추천합니다. 태그가 없는 곡의 언어를 추측하지 않습니다. 변경은 다음 보충부터 적용됩니다.")).setNegativeButton("닫기",null).show();
            if(w==0)toggle(a,p,"autoplay",true);
            if(w==1)toggle(a,p,"skip",false);
            if(w==2)toggle(a,p,"learn",false);
            if(w==3){p.edit().putBoolean("fallback",!p.getBoolean("fallback",true)).apply();SmartSession.changed();show(a);}
            if(w==4)search(a);
            if(w==5)index(a);
            if(w>=6&&w<=8){JSONObject song=SmartPlayback.metadata(a.controller==null?null:a.controller.getCurrentMediaItem());if(scope.isEmpty()||song.optString("id").isEmpty()){a.info("SMART DJ","먼저 곡을 재생하세요");return;}int vote=w==6?1:w==7?-1:0;a.network.execute(()->SmartLibrary.get(a).vote(scope,song,vote));}
            if(w==9)ChatGptSettings.execute(a,"데이터 확인 중",()->{String text=SmartLibrary.get(a).summary(scope)+"\n"+p.getString("status","대기 중")+"\n\n보충: 남은 곡 3개 이하에서 최대 10곡. ChatGPT 자동 요청 최소 2분 간격·시간당 6회. 실패 시 점차 대기 시간을 늘립니다.\n\n학습: 실제 들은 시간만 집계합니다. 자동 넘김·네트워크 오류·일시정지는 싫어요로 학습하지 않습니다. 자동 넘기기는 추천으로 추가한 곡에만 적용하며 연속 최대 2곡입니다. 반복·셔플 중에는 큐를 보충하지 않습니다.\n\n데이터는 이 휴대폰 내부에 서버·계정별로 저장합니다.";a.main.post(()->{if(!a.destroyed)a.info("SMART DJ",text);});});
            if(w==11){if(p.getBoolean("external",true)){p.edit().putBoolean("external",false).apply();SmartSession.changed();}else new AlertDialog.Builder(a).setTitle("MusicBrainz").setMessage("후보 곡의 앨범명·가수명을 MusicBrainz에 보내 레이블·장르를 보완합니다. 엄격하게 일치하는 결과만 사용하고 서버 원본 태그를 우선합니다. 30일 캐시와 초당 1회 이하 조회 제한을 사용합니다. 음원 파일은 보내지 않습니다.").setNegativeButton("취소",null).setPositiveButton("사용",(x,y)->{p.edit().putBoolean("external",true).apply();SmartSession.changed();}).show();}
            if(w==10)new AlertDialog.Builder(a).setTitle("감상 데이터 삭제").setMessage("이 서버·계정의 곡 색인과 학습 기록을 삭제합니다. 음악 파일은 유지됩니다.").setNegativeButton("취소",null).setPositiveButton("삭제",(x,y)->{p.edit().putBoolean("autoplay",false).putBoolean("skip",false).putBoolean("learn",false).apply();SmartSession.changed();a.network.execute(()->SmartLibrary.get(a).clear(scope));}).show();
        }).setNegativeButton("닫기",null).show();
    }
    static boolean setAutoplay(android.content.Context context,androidx.media3.common.Player player,boolean enabled){
        if(enabled&&(player==null||player.getMediaItemCount()==0))return false;
        SharedPreferences p=SmartPlayback.settings(context);if(p.getBoolean("autoplay",false)!=enabled){p.edit().putBoolean("autoplay",enabled).apply();SmartSession.changed();}return true;
    }
    private static String on(SharedPreferences p,String key){return p.getBoolean(key,key.equals("external"))?"ON":"OFF";}
    private static void toggle(MainActivity a,SharedPreferences p,String key,boolean cloud){
        if(cloud&&!p.getBoolean(key,false)&&(a.controller==null||a.controller.getMediaItemCount()==0)){a.info("SMART DJ","큐에 곡을 먼저 추가하세요");return;}
        if(p.getBoolean(key,false)){p.edit().putBoolean(key,false).apply();SmartSession.changed();show(a);return;}
        String description=cloud?"큐가 끝나기 전에 ChatGPT가 다음 곡을 고릅니다. 후보 태그(곡명·가수·앨범·장르·레이블·연도·BPM)와 최근 감상 8건 요약을 OpenAI에 보냅니다. 플랜 사용량에 반영됩니다. API 키 과금으로 자동 전환하지 않습니다. 실패 시 로컬 추천 설정을 따릅니다.":key.equals("learn")?"완주·직접 스킵·실제 감상 시간을 휴대폰에 저장해 취향에 반영합니다. 서버·계정별로 분리합니다.":"직접 덜 듣기로 표시했거나, 완주 없이 3회 이상 일찍 넘긴 곡을 추천 큐에서 건너뜁니다. 직접 고른 곡·앨범은 넘기지 않으며 연속 최대 2곡까지만 넘깁니다.";
        new AlertDialog.Builder(a).setTitle("SMART DJ").setMessage(description).setNegativeButton("취소",null).setPositiveButton("켜기",(d,w)->{if(cloud&&!setAutoplay(a,a.controller,true))return;if(!cloud){p.edit().putBoolean(key,true).apply();SmartSession.changed();}if(cloud&&a.api!=null)index(a);}).show();
    }
    static void index(MainActivity a){
        if(a.api==null){a.showConnection();return;}if(!indexing.compareAndSet(false,true)){a.info("SMART DJ","라이브러리 갱신 중입니다");return;}
        Api api=a.api;String scope=SmartSession.scope;
        ChatGptSettings.execute(a,"라이브러리 데이터 갱신 중",()->{try{SmartLibrary db=SmartLibrary.get(a);db.index(api,scope,()->SmartSession.api==api);if(SmartSession.api==api){String result=db.summary(scope);a.main.post(()->{if(!a.destroyed)a.info("SMART DJ",result);});}}finally{indexing.set(false);}});
    }
    static void search(MainActivity a){
        if(a.api==null){a.showConnection();return;}EditText input=a.field("곡 · 장르 · 레이블 · 가수 · 앨범","",false);
        new AlertDialog.Builder(a).setTitle("라이브러리 검색").setView(input).setNegativeButton("취소",null).setPositiveButton("검색",(d,w)->{
            String query=input.getText().toString().trim();if(query.isEmpty())return;String scope=SmartSession.scope;
            ChatGptSettings.execute(a,"검색 중",()->{List<JSONObject> result=SmartLibrary.get(a).search(scope,query);a.main.post(()->{if(a.destroyed||!scope.equals(SmartSession.scope))return;if(result.isEmpty()){a.info("검색","결과가 없습니다. 먼저 라이브러리 데이터를 갱신하세요. 레이블이 없는 음원은 해당 조건으로 검색되지 않습니다.");return;}a.tab="곡";a.showSongs(query,result,null);});});
        }).show();
    }
}
