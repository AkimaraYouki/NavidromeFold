package dev.navifold.player;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class RecommendationReasonTest {
    @Test public void missingTagsDoNotInventSimilarity() {assertEquals("선택한 흐름 조건을 통과한 후보",RecommendationReason.explain(new JSONObject(),new JSONObject()));}
    @Test public void onlyReportsMatchingFeatures() throws Exception {
        JSONObject seed=new JSONObject().put("artist","A").put("genre","Pop").put("year",2020).put("bpm",100);
        JSONObject song=new JSONObject().put("artist","B").put("genre","pop").put("year",2023).put("bpm",108);
        String result=RecommendationReason.explain(seed,song);assertTrue(result.contains("같은 장르"));assertTrue(result.contains("가까운 발매 시기"));assertTrue(result.contains("BPM"));assertFalse(result.contains("아티스트"));
    }
    @Test public void capsExplanationAndDoesNotClaimRemoteModelReasoning() throws Exception {
        JSONObject song=new JSONObject().put("artist","A").put("genre","Pop").put("label","L").put("year",2020).put("bpm",100);
        String result=RecommendationReason.explain(song,song);assertEquals(3,result.split(" · ").length);assertFalse(result.contains("ChatGPT"));
    }
}
