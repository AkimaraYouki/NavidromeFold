package dev.navifold.player;

import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Small album context card. Inference runs only when AI is tapped; cache is account-scoped. */
final class AlbumNotes {
    private static final java.util.concurrent.ExecutorService metadataWorker=new java.util.concurrent.ThreadPoolExecutor(1,1,30,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(1),new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    static View card(MainActivity a,JSONObject album,List<JSONObject> tracks){
        LinearLayout card=a.col();card.setPadding(a.dp(12),a.dp(10),a.dp(12),a.dp(10));card.setBackground(GlassUi.panel(a,a.settings.dark,16,86));
        LinearLayout title=new LinearLayout(a);title.setGravity(Gravity.CENTER_VERTICAL);TextView heading=a.text("ALBUM NOTES",10,a.MUTED);title.addView(heading,new LinearLayout.LayoutParams(0,-2,1));Button generate=a.button("AI",()->{});generate.setContentDescription("AI 앨범 요약");generate.setTextSize(12);title.addView(generate,new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));card.addView(title);
        TextView metadata=a.text("",11,a.MUTED);metadata.setMaxLines(3);metadata.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(metadata);
        TextView summary=a.text("",12,a.MUTED);summary.setMaxLines(4);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);summary.setVisibility(View.GONE);card.addView(summary);
        JSONObject source=SongMetadata.safe(tracks.get(0));try{for(String key:new String[]{"artist","albumArtist","year","recordLabels","label","genre","genres"})if(album.has(key))source.put(key,album.get(key));source.put("album",Library.title(album));}catch(JSONException ignored){}
        final JSONObject[] current={source};String scope=SmartSession.scope,id=album.optString("id");int auth=a.session,request=a.detailRequest;
        BooleanSupplier valid=()->!a.destroyed&&auth==a.session&&request==a.detailRequest&&scope.equals(SmartSession.scope);
        metadata.setText(info(source));metadata.setOnClickListener(v->a.info("Album notes",info(current[0])));
        metadataWorker.execute(()->{
            try{
                if(!valid.getAsBoolean())return;
                SmartLibrary db=SmartLibrary.get(a);JSONObject enriched=source;
                if(SmartPlayback.settings(a).getBoolean("external",true))enriched=MusicMetadata.enrich(db,scope,Collections.singletonList(source),valid,false).get(0);
                JSONObject result=enriched;String cached=db.albumNote(scope,id,fingerprint(result,tracks));
                a.main.post(()->{if(!valid.getAsBoolean())return;current[0]=result;metadata.setText(info(result));if(!cached.isEmpty()){summary.setText("AI · "+cached);summary.setVisibility(View.VISIBLE);}});
            }catch(Exception ignored){ }
        });
        generate.setOnClickListener(v->{
            if(!valid.getAsBoolean())return;ChatGptPlan plan=new ChatGptPlan(a);
            if(!plan.ready()){a.info("AI","설정 → ChatGPT 추천에서 로그인하고 모델을 선택하세요");return;}
            JSONObject snapshot=current[0];long revision=SmartSession.revision;String model=plan.selection();generate.setEnabled(false);
            a.network.execute(()->{try{
                if(!valid.getAsBoolean())return;
                String fingerprint=fingerprint(snapshot,tracks);SmartLibrary db=SmartLibrary.get(a);String note=db.albumNote(scope,id,fingerprint);
                if(note.isEmpty())note=clean(plan.albumSummary(prompt(snapshot,tracks)));
                if(note.isEmpty())throw new java.io.IOException();
                if(!valid.getAsBoolean()||revision!=SmartSession.revision||!model.equals(plan.selection()))return;
                db.saveAlbumNote(scope,id,fingerprint,note);String result=note;
                a.main.post(()->{if(valid.getAsBoolean()){summary.setText("AI · "+result);summary.setVisibility(View.VISIBLE);generate.setEnabled(true);}});
            }catch(Exception e){a.main.post(()->{if(valid.getAsBoolean()){generate.setEnabled(true);a.info("AI","앨범 요약을 불러오지 못했습니다. ChatGPT 연결·모델·한도를 확인하세요");}});}finally{a.main.post(()->{if(valid.getAsBoolean())generate.setEnabled(true);});}});
        });
        summary.setOnClickListener(v->a.info("AI · Album notes",summary.getText().toString()));
        return card;
    }
    static String info(JSONObject source){
        List<String> lines=new ArrayList<>();String genre=SongMetadata.names(source,"genres","genre"),label=SmartLibrary.label(source);
        if(!genre.isEmpty())lines.add(genre);if(!label.isEmpty())lines.add(label);
        String origin=source.optString("metadataSource");if(!origin.isEmpty())lines.add(origin);
        return lines.isEmpty()?"장르·레이블 정보 없음":String.join(" · ",lines);
    }
    static String prompt(JSONObject album,List<JSONObject> tracks) throws Exception {
        JSONObject data=new JSONObject().put("album",RecommendationBatch.shortText(album.optString("album"))).put("artist",RecommendationBatch.shortText(album.optString("artist"))).put("year",album.optInt("year")).put("genre",RecommendationBatch.shortText(SongMetadata.names(album,"genres","genre"))).put("label",RecommendationBatch.shortText(SmartLibrary.label(album))).put("metadataSource",album.optString("metadataSource"));
        JSONArray titles=new JSONArray();for(int i=0;i<Math.min(30,tracks.size());i++)titles.put(RecommendationBatch.shortText(tracks.get(i).optString("title")));data.put("trackCount",tracks.size()).put("trackTitles",titles);return data.toString();
    }
    static String fingerprint(JSONObject album,List<JSONObject> tracks) throws Exception{return SmartSession.namespace(prompt(album,tracks),"album-note-v1");}
    static String clean(String value){String out=value.replaceAll("\\s+"," ").trim();return out.substring(0,Math.min(240,out.length()));}
}
