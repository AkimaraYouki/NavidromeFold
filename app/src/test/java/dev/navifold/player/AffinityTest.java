package dev.navifold.player;
import org.json.*;import org.junit.Test;import static org.junit.Assert.*;
public class AffinityTest {
 @Test public void preserveWeightedMetadataScore()throws Exception{JSONObject a=new JSONObject().put("artist","Artist").put("albumArtist","Artist").put("album","Album").put("genre","Pop").put("label","Label").put("year",2000).put("bpm",100);JSONObject b=new JSONObject(a.toString()).put("label","label").put("year",2010).put("bpm",115);assertEquals(11,SmartLibrary.affinity(a,b),0.000001);assertEquals(11,SmartLibrary.affinity(new SmartLibrary.Features(a),new SmartLibrary.Features(b)),0.000001);}
 @Test public void missingMetadataDoesNotAddAffinity(){assertEquals(0,SmartLibrary.affinity(new JSONObject(),new JSONObject()),0);}
}
