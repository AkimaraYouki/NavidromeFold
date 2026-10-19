package dev.navifold.player;

import androidx.appcompat.app.AlertDialog;
import android.text.InputFilter;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

/** Activity-scoped UI; cancellation and account changes never replace the current queue. */
final class MoodSearch {
    private final MainActivity a;
    private volatile int request;
    private Future<?> pending;
    MoodSearch(MainActivity a){this.a=a;}
    void show(){
        if(a.api==null){a.showConnection();return;}
        LinearLayout box=a.col();box.setPadding(a.dp(24),a.dp(8),a.dp(24),0);
        EditText input=a.field("어떤 음악을 들을까요?","",false);input.setSingleLine(false);input.setMinLines(2);input.setMaxLines(4);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});box.addView(input);
        for(String example:new String[]{"기분이 우울해, 신나는 노래 틀어줘","공부하고 싶어, 차분한 노래 틀어줘"})box.addView(a.button(example,()->input.setText(example)));
        CheckBox localOnly=new CheckBox(a);localOnly.setText("저장 데이터로만 추천");localOnly.setTextColor(a.INK);localOnly.setMinHeight(a.dp(48));box.addView(localOnly);
        box.addView(a.text("ChatGPT 추천은 요청 문장과 후보 곡 정보를 보냅니다. 저장 데이터만 선택하면 추가 GPT 사용 없이 간단한 분위기·키워드로 찾습니다.",12,a.MUTED));
        AlertDialog dialog=a.dialog().setTitle("AI MUSIC").setView(box).setNegativeButton("취소",null).setPositiveButton("추천",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String query=input.getText().toString().trim();if(query.isEmpty()){input.setError("원하는 분위기나 상황을 적어주세요");return;}dialog.dismiss();search(query,localOnly.isChecked());}));dialog.show();
    }
    void search(String query){search(query,false);}
    void search(String query,boolean localOnly){
        cancel();int id=++request;Api api=a.api;String scope=SmartSession.scope;long revision=SmartSession.revision;
        if(api==null)return;
        BooleanSupplier valid=()->!a.destroyed&&request==id&&SmartSession.api==api&&revision==SmartSession.revision;
        AlertDialog progress=a.dialog().setTitle("AI MUSIC").setMessage("요청 확인 중…").setNegativeButton("취소",(d,w)->cancel()).create();progress.setOnCancelListener(d->cancel());progress.show();
        java.util.function.Consumer<String> status=text->a.main.post(()->{if(valid.getAsBoolean()&&progress.isShowing())progress.setMessage(text);});
        pending=a.network.submit(()->{
            try{
                MoodRecommender.Result found=MoodRecommender.find(a,api,scope,query,localOnly?new MoodRecommender.Model(){public boolean ready(){return false;}public String interpret(String q){return "";}public String select(String p){return "";}}:MoodRecommender.account(a),localOnly||SmartPlayback.settings(a).getBoolean("fallback",true),valid,status);
                boolean localResult=found.local;List<JSONObject> result=found.songs;
                a.main.post(()->{
                    if(!valid.getAsBoolean())return;progress.dismiss();
                    String[] rows=new String[result.size()];for(int i=0;i<rows.length;i++)rows[i]=result.get(i).optString("title")+" · "+Library.artist(result.get(i));
                    String title=(localResult?"LOCAL · 저장 데이터":"ChatGPT")+" · "+result.size()+"곡";
                    AlertDialog answer=a.dialog().setTitle(title).setItems(rows,(d,w)->{if(valid.getAsBoolean())a.playSongs(result,w,false);})
                      .setPositiveButton("바로 재생",(d,w)->{if(valid.getAsBoolean())a.playSongs(result,0,false);})
                      .setNeutralButton("큐에 추가",(d,w)->{if(valid.getAsBoolean())a.editQueue(result,false);}).setNegativeButton("닫기",null).create();answer.show();
                    SmartStatus.record(a,(localResult?"LOCAL · 저장 데이터":"ChatGPT · 자연어")+" 추천 "+result.size()+"곡");
                });
            }catch(Exception e){a.main.post(()->{if(!valid.getAsBoolean())return;progress.dismiss();a.info("AI MUSIC",NetworkFailure.message(e,"추천을 완료하지 못했습니다 · 연결과 모델을 확인하세요"));});}
            finally{a.main.post(()->{if(!valid.getAsBoolean()&&progress.isShowing())progress.dismiss();});}
        });
    }
    void cancel(){request++;if(pending!=null)pending.cancel(true);pending=null;}
}
