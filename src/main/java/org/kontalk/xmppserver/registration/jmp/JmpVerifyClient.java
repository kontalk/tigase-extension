/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.xmppserver.registration.jmp;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.Locale;


/**
 * Client for talking to the JMP REST interface
 * @author Daniele Ricci
 */
public class JmpVerifyClient {
    private static final Log log = LogFactory.getLog(JmpVerifyClient.class);

    /**
     * Service url used unless overridden on the constructor
     */
    public static final String DEFAULT_BASE_URL = "https://jvr.api.jmp.chat";

    /**
     * Default connection timeout of 5000ms used by this client unless specifically overridden onb the constructor
     */
    public static final int DEFAULT_CONNECTION_TIMEOUT = 5000;

    /**
     * Default read timeout of 30000ms used by this client unless specifically overridden onb the constructor
     */
    public static final int DEFAULT_SO_TIMEOUT = 30000;

    private final String baseUrl;
    private final String apiKey;
    private final String apiSecret;

    // TODO these two are not used yet
    private final int connectionTimeout;
    private final int soTimeout;

    private JmpVerifyService service;

    public JmpVerifyClient(final String apiKey,
                           final String apiSecret) {
        this(DEFAULT_BASE_URL,
                apiKey,
                apiSecret,
                DEFAULT_CONNECTION_TIMEOUT,
                DEFAULT_SO_TIMEOUT);
    }

    /**
     * Instanciate a new JmpVerifyClient instance that will communicate using the supplied credentials, and will use the supplied connection and read timeout values.<br>
     * Additionally, you can specify an alternative service base url.
     *
     * @param apiKey Your JMP account api key
     * @param apiSecret Your JMP account api secret
     * @param connectionTimeout over-ride the default connection timeout with this value (in milliseconds)
     * @param soTimeout over-ride the default read-timeout with this value (in milliseconds)
     */
    public JmpVerifyClient(final String baseUrl,
                           final String apiKey,
                           final String apiSecret,
                           final int connectionTimeout,
                           final int soTimeout) {

        // Derive a http and a https version of the supplied base url
        if (baseUrl == null)
            throw new IllegalArgumentException("base url is null");
        String url = baseUrl.trim();
        String lc = url.toLowerCase();
        if (!lc.startsWith("http://") && !lc.startsWith("https://"))
            throw new IllegalArgumentException("base url does not start with http:// or https://");

        this.baseUrl = url;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.connectionTimeout = connectionTimeout;
        this.soTimeout = soTimeout;
        this.service = buildService();
    }

    public VerifyResult verify(String number, String brand) throws IOException {
        return verify(number, brand, null, -1, null);
    }

    public VerifyResult verify(String number, String brand, String from) throws IOException {
        return verify(number, brand, from, -1, null);
    }

    public VerifyResult verify(String number, String brand, String from, int length, Locale locale)
            throws IOException {
        if (number == null || brand == null)
            throw new IllegalArgumentException("number and brand parameters are mandatory.");
        if (length > 0 && length != 4 && length != 6)
            throw new IllegalArgumentException("code length must be 4 or 6.");

        Response<VerifyResult> response = service.verify(apiKey, apiSecret, number, brand).execute();
        if (response.isSuccessful()) {
            return response.body();
        }
        else {
            return new VerifyResult(BaseResult.STATUS_COMMS_FAILURE, null, "Communication error");
        }
    }

    public CheckResult check(String requestId, String code) throws IOException {
        if (requestId == null || code == null)
            throw new IllegalArgumentException("request ID and code parameters are mandatory.");

        Response<CheckResult> response = service.check(apiKey, apiSecret, requestId, code).execute();
        if (response.isSuccessful()) {
            return response.body();
        }
        else {
            return new CheckResult(BaseResult.STATUS_COMMS_FAILURE, null, "Communication error");
        }
    }

    private JmpVerifyService buildService() {
        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();

        return new Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(JmpVerifyService.class);
    }

}
