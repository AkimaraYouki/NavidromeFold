package dev.navifold.player;
import org.junit.Test;
import static org.junit.Assert.*;
public class ServerAddressTest {
 @Test public void tailscaleIpWithoutScheme() { assertEquals("http://100.108.1.35:4553",ServerAddress.normalize("100.108.1.35:4553")); }
 @Test public void preservesHttpsAndBasePath() { assertEquals("https://music.example.ts.net/music",ServerAddress.normalize(" HTTPS://music.example.ts.net/music/ ")); }
 @Test public void rejectsCommaWithHelpfulMessage() { try { ServerAddress.normalize("100,108,1,35:4553"); fail(); } catch(IllegalArgumentException e) { assertTrue(e.getMessage().contains("쉼표")); } }
 @Test public void rejectsMalformedAndCredentialAddresses() { for(String value:new String[]{"", "http://", "http://bad host", "ftp://music", "https://user:password@music", "https://music?token=secret", "http://music:70000"}) { try { ServerAddress.normalize(value); fail(value); } catch(IllegalArgumentException expected) {} } }
}
