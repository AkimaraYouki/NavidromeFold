package dev.navifold.player;
import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;
public class MoodIntentTest {
 JSONObject song(String genre,int bpm)throws Exception{return new JSONObject().put("genre",genre).put("bpm",bpm);}
 @Test public void desiredMoodOverridesDepressedFeeling(){assertEquals("uplifting",MoodIntent.local("기분이 우울해 신나는 노래 틀어줘").mood);}
 @Test public void studyCalmRequest(){assertEquals("calm",MoodIntent.local("공부하고 싶어 차분한 노래 틀어줘").mood);}
 @Test public void explicitSadMusicIsNotAutomaticallyCheeredUp(){assertEquals("melancholic",MoodIntent.local("우울한 노래 틀어줘").mood);}
 @Test public void latestTargetPhraseWins(){assertEquals("calm",MoodIntent.local("신나는 곡보다 차분한 노래").mood);assertEquals("uplifting",MoodIntent.local("차분한 곡 말고 신나는 노래").mood);}
 @Test public void metadataRankingFollowsTarget()throws Exception{assertTrue(MoodIntent.local("공부 차분").score(song("Ambient",80))>MoodIntent.local("공부 차분").score(song("Dance",140)));assertTrue(MoodIntent.local("신나는").score(song("Dance",130))>MoodIntent.local("신나는").score(song("Classical",70)));}
 @Test public void missingTagsDoNotInventMatches(){assertEquals(0,MoodIntent.local("차분한 음악").score(new JSONObject()),0);}
 @Test public void explicitLanguageConflictIsExcluded()throws Exception{MoodIntent q=MoodIntent.parse("{\"mood\":\"calm\",\"language\":\"en\"}");assertTrue(q.score(song("Ambient",80).put("language","ja"))<0);assertTrue(q.score(song("Ambient",80))>0);}
 @Test public void structuredIntentHasBoundedListsAndBpm()throws Exception{JSONArray list=new JSONArray();for(int i=0;i<30;i++)list.put("genre"+i);MoodIntent q=MoodIntent.parse(new JSONObject().put("genres",list).put("terms",list).put("minBpm",400).put("maxBpm",900).toString());assertEquals(6,q.genres.size());assertEquals(8,q.terms.size());assertEquals(300,q.minBpm);assertEquals(300,q.maxBpm);}
 @Test public void minimumBpmDoesNotBecomeAnExactTempo()throws Exception{MoodIntent q=MoodIntent.parse("{\"minBpm\":110}");assertTrue(q.score(song("",130))>0);}
 @Test public void promptDoesNotLeakServerCredentials()throws Exception{JSONObject s=song("Pop",120).put("id","server-private-id").put("password","secret-password").put("path","private/file");String p=RecommendationBatch.moodPrompt("신나게",MoodIntent.local("신나게"),Collections.singletonList(s),"");assertFalse(p.contains("server-private-id"));assertFalse(p.contains("secret-password"));assertFalse(p.contains("private/file"));}
}
