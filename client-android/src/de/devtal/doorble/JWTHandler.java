package de.devtal.doorble;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.MessageDigest;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.util.Arrays;
import java.lang.StringBuilder;

import java.io.ByteArrayOutputStream;
import android.util.Base64OutputStream;
import android.util.Base64;

import java.lang.Exception;

import android.util.Log;

import org.json.*;

public class JWTHandler {
	public class JWTException extends Exception {
		public boolean invalid = false;

		public JWTException(String s) {
			super(s);
		}

		public JWTException(String s, boolean p_invalid) {
			this(s);
			invalid = p_invalid;
		}

	}

	private boolean key_generated = false;

	private KeyPair jwt_kp = null;

	public boolean is_key_generated() {
		return key_generated;
	}

/*	private JWTHandler(){
		super();
	}*/

	public JWTHandler() throws JWTException {
		super();

		final String keyname = "foo3Key";
		KeyStore ks;

		PrivateKey priv_key = null;
		PublicKey pub_key = null;

		if (jwt_kp != null)
			throw new JWTException("already initialized");

		try{
			ks = KeyStore.getInstance("AndroidKeyStore");
			// ks = KeyStore.getInstance(KeyStore.getDefaultType());
			assert(ks != null);
			ks.load(null);

			// try to fetch key from keystore first
			priv_key = (PrivateKey) ks.getKey(keyname, null);
			if (priv_key != null) { // private key in store: get public key too
				pub_key = (PublicKey) ks.getCertificate(keyname).getPublicKey();
				jwt_kp = new KeyPair(pub_key, priv_key);
				return;
			}
		} catch (Exception e) {
			throw new JWTException(String.format(
				"exception fetching key from keystore: %s", e.toString()));
		}

		try{
			// if we did not return yet, we will now generate a KeyPair, being
			// stored automatically in KeyStore.
			KeyPairGenerator kpg = KeyPairGenerator.getInstance(
//				KeyProperties.KEY_ALGORITHM_EC, KeyStore.getDefaultType());
				KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
			kpg.initialize(
				new KeyGenParameterSpec.Builder(keyname,
					KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
				.setDigests(KeyProperties.DIGEST_NONE,
					KeyProperties.DIGEST_SHA256)
				.build());
			jwt_kp = kpg.generateKeyPair();

			// show short toast / item in SnackBar (TODO!) that key generated
			key_generated = true;
			Log.e("DoorBLE.JWT", "new key pair generated");
		} catch(java.security.GeneralSecurityException e){
			throw new JWTException(String.format(
				"exception generating key: %s", e.toString()));
		}
	}

	/**
	 * generate PEM format public key
	 **/
	public String export_pubkey() throws JWTException{
		if(jwt_kp == null)
			throw new JWTException("JWTHandler uninitialized!");

		StringBuilder sb = new StringBuilder();
		PublicKey pub_key = jwt_kp.getPublic();
		assert(pub_key.getFormat().equals("X.509"));

		String b64_2 = Base64.encodeToString(pub_key.getEncoded(),
			Base64.NO_WRAP);
		sb.append("-----BEGIN PUBLIC KEY-----\n");
		for(int i = 0; i <= b64_2.length(); i+=64){
			int x = ((b64_2.length()) < i+64) ? (b64_2.length()) : (i+64);
			sb.append(b64_2.substring(i, x));
			sb.append("\n");
		}
		sb.append("-----END PUBLIC KEY-----");
		return sb.toString();
	}

	/**
	 * generates hex-encoded SHA256 digest
	 * @param token message for digest
	 * @return sha256 hex encoded digest or null
	 **/
	private String sha256_hexdigest(String token) {
		final char[] hex = "0123456789abcdef".toCharArray();
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] d = md.digest(token.getBytes());
			char[] s = new char[d.length * 2];
			for(int i = 0; i < d.length; i++){
				s[i*2] = hex[(d[i] & 0xF0) >>> 4];
				s[i*2+1] = hex[d[i] & 0x0F];
			}
			return new String(s);
		} catch(java.security.NoSuchAlgorithmException e){
			return null; // FIXME
		}
	}

	/**
	 * converts JWT type signature to DER signature format
	 * @param data JWS signature
	 * @param sz signature parameter size
	 * @return signature in DER format
	 **/
	private byte[] encode_der(byte[] data, int sz) {
		int r_len;
		for(r_len = sz; data[sz - r_len] == 0x00 && r_len > 0; r_len--);

		byte r_first = data[sz - r_len]; // default case
		if ((r_first & 0x80) > 0){ // highest bit set: sign in DER -> add 0
			r_first = 0x00;
		} else r_len--;

		int s_len;
		for(s_len = sz; data[sz + sz - s_len] == 0x00 && s_len > 0; s_len--);

		byte s_first = data[sz + sz - s_len]; // default case
		if ((s_first & 0x80) > 0){ // highest bit set: sign in DER -> add 0
			s_first = 0x00;
		} else s_len--;

		byte[] out = new byte[4 + 2 + r_len+1 + s_len+1];

		out[0] = 0x30; // sequence
		out[1] = (byte) (out.length - 2); // length of sequence
		out[2] = 0x02; // asn.1 number
		out[3] = (byte) (r_len + 1); // length of R

		// R data
		out[4] = r_first; // R[0]
		for(int i = 0; i<r_len; i++) // R[1:]
			out[4 + i + 1] = data[sz - r_len + i];

		int s_off = 4+r_len+1; // [seq, len(seq), num, len(r)] + r[1:] + r[:1]
		out[s_off] = 0x02; // number
		out[s_off + 1] = (byte) (s_len + 1); // length of S

		// S data
		out[s_off + 2] = s_first; // S[0]
		for(int i = 0; i<s_len; i++) // S[1:]
			out[s_off+2 + i + 1] = data[sz + sz - s_len + i];

		return out;
	}

	/**
	 * converts DER signature format to JWT type signature
	 * @param der signature in DER format
	 * @param sz signature parameter size
	 * @return signature in JWT format
	 **/
	private byte[] decode_der(byte[] der, int sz) {
		byte[] dest = new byte[2*sz];

		assert(0x30 == der[0]); // ASN1 sequence
		assert(der[1] == der.length - 2); // ASN1 seq. length: full array
		assert(0x02 == der[2]);
		assert(sz+1 <= der[3]); // length of R matching
		// sb.append(String.format("%x = len(r) (+1: %x, last: %x)\n",
		// 	der[3], der[4], der[3+der[3]]));
		// sb.append(String.format("%x = 0x02\n", der[4+der[3]]));
		assert(0x02 == der[4+der[3]]);
		assert(sz+1 <= der[4+der[3]+1]); // length of S
		// sb.append(String.format("%x = len(s) (+1: %x, last: %x)\n",
		// 	der[4+der[3]+1], der[4+der[3]+1+1] , der[der.length-1]));
		// sb.append(String.format("%x+%x + 4 + 2 = %x = %x\n",
		// 	der[3], der[4+der[3]+1], der[3]+der[4+der[3]+1],
		// 	der.length));

		Arrays.fill(dest, (byte) 0);

		int x0; // start of DER x data
		int start; // start of copy of real x data
		int end; // end of x data
		int i; // DER position
		int j; // JOSE position

		// first copy R
		x0 = 4; // start of R data
		j = sz-1; // end of target R data

		start = ((der[x0] == 0x00) && ((der[x0+1] & 0x80) != 0)) // skip sign
			? x0+1 : x0;
		end = x0 + der[x0-1];

		for(i = end-1; i >= start; i--) // copy form end to front to have 0
			dest[j--] = der[i];

		// sb.append(String.format(
		// 	"R: %x = len(r) : [ %x %x %x .. %x ] [ %x %x %x .. %x ]\n",
		// 	der[3], der[4], der[5], der[6], der[3+der[3]],
		// 	         dest[0], dest[1], dest[2], dest[sz-1]));

		// now copy S
		x0 = 4+der[x0-1]+2; // start of S data
		j = 2*sz - 1; // end of target S data

		start = ((der[x0] == 0x00) && ((der[x0+1] & 0x80) != 0)) // skip sign
			? x0+1 : x0;
		end = x0 + der[x0-1];

		for(i = end-1; i >= start; i--) // copy form end to front to have 0
			dest[j--] = der[i];

		// sb.append(String.format(
		// 	"S: %x = len(r) : [ %x %x %x .. %x ] [ %x %x %x .. %x ]\n",
		// 	der[x0-1],
		// 	der[x0], der[x0+1], der[x0+2], der[x0-1+der[x0-1]],
		// 	dest[sz+0], dest[sz+1], dest[sz+2], dest[sz+sz-1]));

		return dest;
	}

	public String get_jwt(String token, long expiry, String action)
			throws JWTException{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Base64OutputStream b64os = new Base64OutputStream(baos,
			Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
		if(jwt_kp == null)
			throw new JWTException("JWTHandler uninitialized!");

		java.util.Date now = new java.util.Date();

		JSONObject jh = new JSONObject(); // jwt header
		JSONObject jp = new JSONObject(); // jwt payload

		String tok_sig = sha256_hexdigest(token);
		if (tok_sig == null) {
			throw new JWTException("T NSAE");
		}

		try {
			// jwt write header
			jh.put("typ", "JWT").put("alg", "ES256");
			b64os.write(jh.toString().getBytes());
			b64os.flush();
			b64os.close();
			baos.write(".".getBytes());
			// jwt body
			long t = now.getTime()/1000;
			jp.put("iat", t)
				.put("exp", expiry)
				.put("act", action)
				.put("token", tok_sig);
			b64os.write(jp.toString().getBytes());
			b64os.flush();
			b64os.close();
		} catch(org.json.JSONException e){
			throw new JWTException("JSONE");
		} catch(java.io.IOException e){
			throw new JWTException("J IOE");
		}

		// jwt signature
		try {
			baos.flush();
			baos.close();

			Signature sig = Signature.getInstance("SHA256withECDSA");
			sig.initSign(jwt_kp.getPrivate());
			sig.update(baos.toString().getBytes());
			baos.write(".".getBytes());
			b64os.write(decode_der(sig.sign(), 0x20));
			b64os.flush();
			b64os.close();
			baos.flush();
			baos.close();
		} catch(java.io.IOException e){
			throw new JWTException("IOE");
		} catch(java.security.NoSuchAlgorithmException e){
			throw new JWTException("NSAE");
		} catch(java.security.InvalidKeyException e){
			throw new JWTException("IKE");
		} catch(java.security.SignatureException e){
			throw new JWTException("SE");
		}

		return baos.toString();
	}

	public long decode_token(byte[] jwt, Door d) throws JWTException {
		Signature sig;
		String[] jwt_sections = new String(jwt).split("\\.", 3);
		if(jwt_sections.length != 3)
			throw new JWTException("invalid section count", true);

		// TODO: parse header

		try {
			// extract and convert signature to be verified by java Signature
			byte[] bsig = Base64.decode(jwt_sections[2],
				Base64.NO_WRAP|Base64.NO_PADDING|Base64.URL_SAFE);
			if(bsig.length != 0x20*2) // ES256 signature length
				return 0;
			byte[] dsig = encode_der(bsig, bsig.length/2);

			// verify header.payload by signature
			sig = Signature.getInstance("SHA256withECDSA");
			sig.initVerify(d.pubkey());
			sig.update((jwt_sections[0]+"."+jwt_sections[1]).getBytes());
			if(!sig.verify(dsig))
				return 0;
		} catch(java.security.NoSuchAlgorithmException e){
			throw new JWTException("decode token: algorithm not found");
		} catch(java.security.InvalidKeyException e){
			throw new JWTException("decode token: invalid key");
		} catch(java.security.SignatureException e){
			throw new JWTException("decode token: signature invalid", true);
		}

		try{
			// decode payload
			String bp = new String(Base64.decode(jwt_sections[1],
				Base64.NO_WRAP|Base64.NO_PADDING|Base64.URL_SAFE));
			// Base64 decode
			JSONObject p = (JSONObject) new JSONTokener(bp).nextValue();

			long now = new java.util.Date().getTime()/1000;
			long iat = p.optLong("iat");
			long exp = p.optLong("exp");
			if(exp == 0 || iat == 0)
				throw new JWTException("decode token: missing restrictions",
					true);

			if(d.validate_time()){
/*				if(iat > now)
					return 0; */
				if(exp < now)
					return 0;
				// TODO: NBF (not before)
				// TODO: jti -> token
				// TODO: valdiation that token not expired when received?
			}
			String token = p.optString("token");
			if(token == null || token.length() <= 1)
				throw new JWTException("decode token: missing token", true);
			return exp + (exp-iat);
		} catch(org.json.JSONException e){
			throw new JWTException("decode token: invalid JSON", true);
		}
	}
}
