package be.nabu.glue.core.impl.methods.v2;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.GlueUtils.ObjectHandler;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "hash")
public class HashMethods {
	
	@GlueMethod(description = "Generates a hash", version = 2)
	public static Object hash(final String algorithm, Object...original) throws IOException {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				try {
					MessageDigest digest = MessageDigest.getInstance(algorithm);
					digest.update((byte[]) single);
					byte [] hash = digest.digest();
					StringBuffer string = new StringBuffer();
					for (int i = 0; i < hash.length; ++i)
						string.append(Integer.toHexString((hash[i] & 0xFF) | 0x100).substring(1,3));
					return string.toString();
				}
				catch (NoSuchAlgorithmException e) {
					// should not occur due to enum
					throw new RuntimeException(e);
				}
			}
		}, byte[].class), false, original);
	}
	
	@GlueMethod(description = "Generates a md5 hash", version = 2)
	public static Object md5(Object...original) throws IOException {
		return hash("MD5", original);
	}
	
	@GlueMethod(description = "Generates a sha1 hash", version = 2)
	public static Object sha1(Object...original) throws IOException {
		return hash("SHA-1", original);
	}
	
	@GlueMethod(description = "Generates a sha256 hash", version = 2)
	public static Object sha256(Object...original) throws IOException {
		return hash("SHA-256", original);
	}
	
	@GlueMethod(description = "Generates a sha512 hash", version = 2)
	public static Object sha512(Object...original) throws IOException {
		return hash("SHA-512", original);
	}
}