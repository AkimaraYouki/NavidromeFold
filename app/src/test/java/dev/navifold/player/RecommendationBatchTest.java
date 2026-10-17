package dev.navifold.player;
import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;
public class RecommendationBatchTest {
 List<JSONObject> songs() throws Exception{return Arrays.asList(new JSONObject().put("id","private-server-id").put("title","A"),new JSONObject().put("title","B"));}
 @Test public void preservesOrder() throws Exception{List<JSONObject> s=songs();assertSame(s.get(1),RecommendationBatch.select("[1,0]",s).get(0));}
 @Test public void noServerCredentialsInPrompt() throws Exception{assertFalse(RecommendationBatch.prompt(songs(),"seed").contains("private-server-id"));}
 @Test(expected=Exception.class) public void rejectsInventedId() throws Exception{RecommendationBatch.select("[9]",songs());}
 @Test(expected=Exception.class) public void rejectsDuplicate() throws Exception{RecommendationBatch.select("[0,0]",songs());}
 @Test(expected=Exception.class) public void rejectsFraction() throws Exception{RecommendationBatch.select("[0.5]",songs());}
 @Test(expected=Exception.class) public void rejectsString() throws Exception{RecommendationBatch.select("[\"0\"]",songs());}
}
