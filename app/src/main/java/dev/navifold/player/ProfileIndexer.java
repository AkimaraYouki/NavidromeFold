package dev.navifold.player;

import androidx.appcompat.app.AlertDialog;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Explicit album-batched indexing; automatic search only reads this cache. */
final class ProfileIndexer {
    private static final AtomicBoolean running=new AtomicBoolean();
    static void show(MainActivity a){
        if(a.api==null){a.showConnection();return;}
        a.dialog().setTitle("AI 음악 데이터").setMessage("앨범·수록곡 정보를 묶어 분위기·활기·보컬 여부를 추정하고 이 휴대폰에 저장합니다. 원본 태그와 음원 파일은 바꾸지 않습니다.\n\n한 번에 최대 3앨범·60곡을 보내며 새 곡·정보가 바뀐 곡·만료된 결과만 분석합니다. ChatGPT 플랜 사용량을 쓰며 한도 오류가 나면 멈춥니다. 언제든 중지하고 나중에 이어갈 수 있습니다.\n\n음원을 직접 듣는 분석은 아닙니다. 불확실한 결과는 추천에 강하게 반영하지 않습니다.")
            .setNegativeButton("닫기",null).setNeutralButton("저장 현황",(d,w)->ChatGptSettings.execute(a,"분석 데이터 확인 중",()->{String scope=SmartSession.scope;String text=SmartLibrary.get(a).profileSummary(scope);a.main.post(()->{if(!a.destroyed&&scope.equals(SmartSession.scope))a.info("AI 음악 데이터",text);});}))
            .setPositiveButton("분석 시작 · 이어서",(d,w)->start(a)).show();
    }
    private static void start(MainActivity a){
        if(!running.compareAndSet(false,true)){a.info("AI 음악 데이터","이미 분석 중입니다");return;}
        AtomicBoolean cancelled=new AtomicBoolean();Api api=a.api;String scope=SmartSession.scope;long revision=SmartSession.revision;
        java.util.function.BooleanSupplier valid=()->!a.destroyed&&!cancelled.get()&&SmartSession.api==api&&revision==SmartSession.revision&&!Thread.currentThread().isInterrupted();
        AlertDialog progress=a.dialog().setTitle("AI 음악 데이터").setMessage("라이브러리 확인 중…").setNegativeButton("중지",(d,w)->cancelled.set(true)).create();progress.setOnCancelListener(d->cancelled.set(true));progress.show();
        a.network.execute(()->{
            try{
                ChatGptPlan plan=new ChatGptPlan(a);if(!plan.ready())throw new java.io.IOException("먼저 ChatGPT 연결과 모델 선택을 해주세요");
                SmartLibrary db=SmartLibrary.get(a);CatalogSync.ensure(a,api,scope,valid,t->a.main.post(()->{if(valid.getAsBoolean())progress.setMessage(t);}));int count=0;
                while(valid.getAsBoolean()){
                    List<JSONObject> batch=db.pendingProfiles(scope,60,3);if(batch.isEmpty())break;
                    List<JSONObject> profiles=MusicProfile.parse(plan.musicProfiles(MusicProfile.prompt(batch)),batch.size());if(!valid.getAsBoolean())break;
                    db.saveProfiles(scope,batch,profiles);count+=batch.size();int done=count;
                    a.main.post(()->{if(valid.getAsBoolean())progress.setMessage(done+"곡 분석 저장 · 중지해도 다음에 이어집니다");});
                    Thread.sleep(1000);
                }
                if(valid.getAsBoolean()){String summary=db.profileSummary(scope);a.main.post(()->{if(valid.getAsBoolean())a.info("분석 완료",summary);});}
            }catch(Exception e){a.main.post(()->{if(valid.getAsBoolean())a.info("AI 음악 데이터",NetworkFailure.message(e,"분석을 완료하지 못했습니다 · 연결·모델·한도 확인. 저장된 곡은 유지됩니다."));});}
            finally{running.set(false);a.main.post(()->{if(progress.isShowing())progress.dismiss();});}
        });
    }
}
