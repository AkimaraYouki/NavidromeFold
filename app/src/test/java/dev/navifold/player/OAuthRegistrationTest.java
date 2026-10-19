package dev.navifold.player;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;

public class OAuthRegistrationTest {
    @Test public void firstRunUsesDynamicRegistration(){assertEquals(OAuthRegistration.DYNAMIC,OAuthRegistration.client(new JSONObject()));}
    @Test public void failedExchangeRetainsIssuedRegistration()throws Exception{
        JSONObject original=new JSONObject();
        String issued=OAuthRegistration.issued(OAuthRegistration.client(original),"oaiapp_fixture");
        JSONObject saved=OAuthRegistration.pending(original,issued);
        assertEquals("oaiapp_fixture",OAuthRegistration.client(saved));
        assertFalse(saved.has("client_id"));assertFalse(saved.has("sub"));assertFalse(saved.has("access_token"));assertEquals(0,original.length());
        assertFalse(OAuthRegistration.hintsAllowed(saved,issued));
    }
    @Test public void establishedAccountWinsOverPending()throws Exception{
        JSONObject saved=new JSONObject().put("client_id","registered").put("pending_client_id","pending").put("sub","verified");
        assertEquals("registered",OAuthRegistration.client(saved));assertTrue(OAuthRegistration.hintsAllowed(saved,"registered"));assertFalse(OAuthRegistration.hintsAllowed(saved,"pending"));
        assertEquals(saved.toString(),OAuthRegistration.pending(saved,"other").toString());
    }
    @Test public void returningCallbackMayOmitIssuedId()throws Exception{assertEquals("registered",OAuthRegistration.issued("registered",null));}
    @Test(expected=IOException.class) public void returningCallbackCannotSwitchRegistration()throws Exception{OAuthRegistration.issued("registered","other");}
    @Test(expected=IOException.class) public void dynamicIsNotAnIssuedClient()throws Exception{OAuthRegistration.issued(OAuthRegistration.DYNAMIC,OAuthRegistration.DYNAMIC);}
    @Test(expected=IOException.class) public void newCallbackRequiresIssuedId()throws Exception{OAuthRegistration.issued(OAuthRegistration.DYNAMIC,null);}
    @Test public void invalidSavedSentinelDoesNotBecomeReturningClient()throws Exception{
        JSONObject saved=new JSONObject().put("client_id",OAuthRegistration.DYNAMIC).put("pending_client_id",OAuthRegistration.DYNAMIC);
        assertEquals(OAuthRegistration.DYNAMIC,OAuthRegistration.client(saved));
    }
}
