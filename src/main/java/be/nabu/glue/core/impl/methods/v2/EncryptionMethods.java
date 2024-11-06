/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.glue.core.impl.methods.v2;

import java.io.InputStream;

import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.GlueUtils.ObjectHandler;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.security.PBEAlgorithm;
import be.nabu.utils.security.SecurityUtils;

@MethodProviderClass(namespace = "encryption")
public class EncryptionMethods {
	
	public static final String CONFIGURATION_CRYPT_KEY = "be.nabu.utils.security.crypt.key";
	
	public static Object encrypt(String password, @GlueParam(name = "series") Object object) {
		final String finalPassword = getPassword(password);
		return GlueUtils.wrap(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				try {
					try(InputStream stream = ScriptMethods.toStream(single)) {
						return SecurityUtils.pbeEncrypt(IOUtils.toBytes(IOUtils.wrap(stream)), finalPassword, PBEAlgorithm.AES256);
					}
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}, false, object);
	}
	
	public static Object decrypt(String password, @GlueParam(name = "series") Object object) {
		final String finalPassword = getPassword(password);
		return GlueUtils.wrap(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				try {
					try(InputStream stream = ScriptMethods.toStream(single)) {
						return SecurityUtils.pbeDecrypt(single.toString(), finalPassword, PBEAlgorithm.AES256);
					}
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}, false, object);
	}
	
	private static String getPassword(String password) {
		if (password == null) {
			password = System.getProperty(CONFIGURATION_CRYPT_KEY, "changeit");
		}
		return password;
	}
	
}
