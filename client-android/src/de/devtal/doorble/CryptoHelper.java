package de.devtal.doorble;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import android.util.Base64;

class CryptoHelper {
	static public PublicKey decode_pubkey(String pem) {
		KeyFactory kf;
		X509EncodedKeySpec ks;
		String pemstart = "-----BEGIN PUBLIC KEY-----\n";
		String pemend = "-----END PUBLIC KEY-----";

		pem = pem.trim();
		if(!pem.startsWith(pemstart) ||
				!pem.endsWith(pemend))
			return null;

		try{
			ks = new X509EncodedKeySpec(
				Base64.decode(pem.substring(pemstart.length()-1,
					pem.length()-pemend.length()), Base64.NO_WRAP));

			kf = KeyFactory.getInstance("EC");
			return kf.generatePublic(ks);
		} catch(IllegalArgumentException e){
			return null;
		} catch(java.security.NoSuchAlgorithmException e){
			return null;
		} catch(java.security.spec.InvalidKeySpecException e){
			return null;
		}
	}

	static public String encode_pubkey(PublicKey pubkey) {
		StringBuilder sb = new StringBuilder();
		assert(pubkey.getFormat().equals("X.509"));

		String b64_2 = Base64.encodeToString(pubkey.getEncoded(),
			Base64.NO_WRAP);
		sb.append("-----BEGIN PUBLIC KEY-----\n");
		for (int i = 0; i <= b64_2.length(); i+=64) {
			int x = ((b64_2.length()) < i+64) ? (b64_2.length()) : (i+64);
			sb.append(b64_2.substring(i, x));
			sb.append("\n");
		}
		sb.append("-----END PUBLIC KEY-----");
		return sb.toString();
	}
}
