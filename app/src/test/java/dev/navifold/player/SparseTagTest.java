package dev.navifold.player;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class SparseTagTest {
 JSONObject s(String genre)throws Exception{return new JSONObject().put("id","track").put("genre",genre).put("artist","A");}
 @Test public void emptyServerFieldsPreserveEnrichedTags()throws Exception{JSONObject merged=SongMetadata.complete(s("Pop").put("year",2024).put("genres",new JSONArray().put("Pop")),s("").put("year",0).put("genres",new JSONArray()));assertEquals("Pop",merged.getString("genre"));assertEquals(2024,merged.getInt("year"));assertEquals(1,merged.getJSONArray("genres").length());}
 @Test public void nonEmptyServerTagsStillWin()throws Exception{assertEquals("Jazz",SongMetadata.complete(s("Pop"),s("Jazz")).getString("genre"));}
 @Test public void naturalFallbackAllowsUnknownCandidate()throws Exception{assertTrue(new FlowPolicy.Matcher(s("Pop"),1,true).allows(s("")));assertFalse(new FlowPolicy.Matcher(s("Pop"),0,true).allows(s("")));}
 @Test public void noKnownGenreBroadening()throws Exception{assertFalse(new FlowPolicy.Matcher(s("Pop"),1,true).allows(s("Jazz")));assertFalse(new FlowPolicy.Matcher(s("Pop"),1,true).allows(s("J-pop")));}
 @Test public void knownLanguageConflictSurvivesMissingGenre()throws Exception{assertFalse(new FlowPolicy.Matcher(s("Pop").put("language","en"),1,true).allows(s("").put("language","ja")));}
 @Test public void completelyUnknownAnchorCanStartInsteadOfDeadlocking()throws Exception{assertTrue(new FlowPolicy.Matcher(s(""),1,true).allows(s("Jazz").put("artist","B")));}
 @Test public void explicitRegionCannotHideInMissingGenre()throws Exception{assertFalse(new FlowPolicy.Matcher(s("Pop"),1,true).allows(s("").put("musicScene","jp")));}
 @Test public void strictAndNormalMatcherRemainStrictAboutUnknowns()throws Exception{assertFalse(FlowPolicy.allows(s("Pop"),s(""),1));assertTrue(FlowPolicy.allows(s("Pop"),s("Synth-pop"),1));}
}
