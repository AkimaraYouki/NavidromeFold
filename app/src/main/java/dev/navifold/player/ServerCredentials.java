package dev.navifold.player;
import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;
/** Device-bound authenticated encryption; never exported in backups. */
final class ServerCredentials {
 private static final Object LOCK=new Object();
 private static SecretKey key(Context c)throws Exception{KeyStore s=KeyStore.getInstance("AndroidKeyStore");s.load(null);String alias=c.getPackageName()+".navidrome";if(!s.containsAlias(alias)){KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}return (SecretKey)s.getKey(alias,null);}
 static void save(Context c,Api api)throws Exception{synchronized(LOCK){JSONObject data=new JSONObject().put("server",api.base).put("user",api.user).put("password",api.password);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(c));String value=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);if(!c.getSharedPreferences("server-vault",0).edit().putString("vault",value).commit())throw new Exception("Credential storage failed");}}
 static Api load(Context c)throws Exception{synchronized(LOCK){String value=c.getSharedPreferences("server-vault",0).getString("vault","");if(value.isEmpty())return null;String[] parts=value.split(":");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(c),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));JSONObject d=new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8));return new Api(d.getString("server"),d.getString("user"),d.getString("password"));}}
 static void clear(Context c){synchronized(LOCK){c.getSharedPreferences("server-vault",0).edit().clear().commit();}}
}
