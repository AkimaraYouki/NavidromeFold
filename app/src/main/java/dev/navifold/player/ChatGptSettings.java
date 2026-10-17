package dev.navifold.player;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import java.util.*;
import org.json.*;
final class ChatGptSettings {
    private static final java.util.concurrent.atomic.AtomicBoolean signingIn=new java.util.concurrent.atomic.AtomicBoolean();
    interface Task {void run() throws Exception;}
    static void execute(MainActivity a,String status,Task work){android.widget.Toast.makeText(a,status,android.widget.Toast.LENGTH_SHORT).show();a.network.execute(()->{try{work.run();}catch(Exception e){a.main.post(()->{if(!a.destroyed)a.info("ChatGPT",e instanceof java.io.IOException?e.getMessage():"처리하지 못했습니다. 연결·계정 권한을 확인하고 다시 시도하세요.");});}});}
    static void show(MainActivity a){ChatGptPlan plan=new ChatGptPlan(a);new AlertDialog.Builder(a).setTitle("ChatGPT · "+plan.label()).setItems(new String[]{"Continue with ChatGPT","사용 가능한 모델","추천 10곡","사용량 · 앱 권한","연결 해제"},(d,w)->{if(w==0&&signingIn.compareAndSet(false,true))execute(a,"브라우저에서 로그인해 주세요",()->{try{plan.signIn(a);}finally{signingIn.set(false);}a.main.post(()->{if(!a.destroyed){a.renderRows(false);models(a,plan);}});});if(w==1)models(a,plan);if(w==2)recommend(a,plan);if(w==3)a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://chatgpt.com/#settings")));if(w==4)execute(a,"연결 해제 중",()->{plan.disconnect();a.main.post(()->{if(!a.destroyed)a.renderRows(false);});});}).setNegativeButton("닫기",null).show();}
    static void models(MainActivity a,ChatGptPlan plan){execute(a,"계정 모델 확인 중",()->{JSONArray data=plan.models();List<String> names=new ArrayList<>(),slugs=new ArrayList<>();for(int i=0;i<data.length();i++){JSONObject m=data.getJSONObject(i);if("list".equals(m.optString("visibility"))&&!ChatGptPlan.excludedModel(m.optString("slug"))&&!ChatGptPlan.excludedModel(m.optString("display_name"))){names.add(m.optString("display_name",m.optString("slug")));slugs.add(m.getString("slug"));}}a.main.post(()->{if(a.destroyed)return;if(names.isEmpty()){a.info("ChatGPT","GPT-6을 제외한 사용 가능한 모델이 없습니다. 계정 권한을 확인하세요.");return;}new AlertDialog.Builder(a).setTitle("모델 선택").setItems(names.toArray(new String[0]),(d,w)->plan.model(slugs.get(w))).setNegativeButton("취소",null).show();});});}
    static void recommend(MainActivity a,ChatGptPlan plan){
        if(a.api==null){a.showConnection();return;}
        new AlertDialog.Builder(a).setTitle("추천 10곡").setMessage("후보 최대 40곡의 음악 태그와 최근 감상 요약을 OpenAI에 보냅니다. 음원 파일은 보내지 않습니다. ChatGPT 플랜 사용량에 반영됩니다.").setNegativeButton("취소",null).setPositiveButton("추천",(d,w)->{
            Api api=a.api;int session=a.session;String scope=SmartSession.scope;long revision=SmartSession.revision;
            JSONObject seed=SmartPlayback.metadata(a.controller==null?null:a.controller.getCurrentMediaItem());
            execute(a,"추천 준비 중",()->{
                SmartLibrary db=SmartLibrary.get(a);Set<String> excluded=db.recent(scope);excluded.add(seed.optString("id"));
                List<JSONObject> candidates=db.candidates(scope,seed,excluded,40);
                if(candidates.isEmpty()){for(JSONObject song:Library.array(api.call("getRandomSongs","size","100").optJSONObject("randomSongs"),"song"))db.put(scope,song,System.currentTimeMillis());candidates=db.candidates(scope,seed,excluded,40);}
                if(candidates.isEmpty())throw new java.io.IOException("추천 후보가 없습니다. 라이브러리 데이터를 갱신하세요");
                if(SmartPlayback.settings(a).getBoolean("external",true))candidates=MusicMetadata.enrich(db,scope,candidates,()->revision==SmartSession.revision);
                List<JSONObject> selected=RecommendationBatch.select(plan.recommend(RecommendationBatch.flowPrompt(candidates,seed,db.context(scope))),candidates);
                a.main.post(()->{
                    if(a.destroyed||session!=a.session||revision!=SmartSession.revision)return;
                    StringBuilder text=new StringBuilder();for(JSONObject song:selected)text.append(song.optString("title")).append(" · ").append(Library.artist(song)).append('\n');
                    new AlertDialog.Builder(a).setTitle("추천 · "+selected.size()).setMessage(text.toString()).setNegativeButton("닫기",null).setNeutralButton("바로 재생",(x,y)->a.playSongs(selected,0,false)).setPositiveButton("대기열 추가",(x,y)->a.playSongs(selected,0,true)).show();
                });
            });
        }).show();
    }
}
