package dev.navifold.player;

import android.content.Context;
import org.json.JSONObject;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Provider boundary permits deterministic fixture tests without account credentials. */
final class MoodRecommender {
    interface Model {boolean ready();String interpret(String query)throws Exception;String select(String prompt)throws Exception;}
    static Model account(Context c){ChatGptPlan plan=new ChatGptPlan(c);return new Model(){public boolean ready(){return plan.ready();}public String interpret(String q)throws Exception{return plan.musicIntent(q);}public String select(String p)throws Exception{return plan.musicSearch(p);}};}
    static final class Result {final List<JSONObject> songs;final boolean local;Result(List<JSONObject> songs,boolean local){this.songs=songs;this.local=local;}}
    static Result find(Context c,Api api,String scope,String query,Model model,boolean fallback,BooleanSupplier valid,Consumer<String> status)throws Exception {
        if(!valid.getAsBoolean())throw new InterruptedException();
        MoodIntent intent;boolean local=false;
        try{if(!model.ready())throw new java.io.IOException("ChatGPT 연결과 모델 선택이 필요합니다");intent=MoodIntent.parse(model.interpret(query));}
        catch(Exception e){if(e instanceof InterruptedException||!valid.getAsBoolean())throw new InterruptedException();if(!fallback)throw e;local=true;intent=MoodIntent.local(query);}
        if(!valid.getAsBoolean())throw new InterruptedException();
        status.accept("라이브러리에서 후보 찾는 중…");SmartLibrary db=SmartLibrary.get(c);
        try{CatalogSync.ensure(c,api,scope,valid,status);}catch(InterruptedException e){throw e;}catch(Exception e){if(db.count(scope)==0)throw new java.io.IOException("라이브러리 조회 실패 · Navidrome 연결과 권한을 확인하세요");status.accept("서버 갱신 실패 · 저장된 목록에서 찾는 중");}
        List<JSONObject> candidates=db.moodCandidates(scope,intent,40,local);
        if(candidates.isEmpty())throw new java.io.IOException(local?"로컬 태그에서 요청에 맞는 곡을 찾지 못했습니다 · ChatGPT 연결 후 다시 추천해 보세요":"라이브러리에 추천할 곡이 없습니다 · 비선호 표시와 서버 연결 확인");
        List<JSONObject> selected;
        try{
            if(local)selected=RecommendationBatch.local(candidates,10);
            else{status.accept("ChatGPT가 곡을 고르는 중…");selected=RecommendationBatch.select(model.select(RecommendationBatch.moodPrompt(query,intent,candidates,db.context(scope))),candidates);}
        }catch(Exception e){
            if(e instanceof InterruptedException||!valid.getAsBoolean())throw new InterruptedException();if(!fallback)throw e;local=true;
            candidates=db.moodCandidates(scope,intent,40,true);selected=RecommendationBatch.local(candidates,10);
            if(selected.isEmpty())throw new java.io.IOException("ChatGPT 응답을 받지 못했고 태그 기반 대체 후보도 없습니다 · 연결·모델·한도 확인");
        }
        if(!valid.getAsBoolean())throw new InterruptedException();return new Result(selected,local);
    }
}
