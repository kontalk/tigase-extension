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

package org.kontalk.xmppserver.registration.checkmobi;

import com.google.gson.*;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;


/**
 * A simple REST client for CheckMobi validation API.
 * @author Daniele Ricci
 */
public class CheckmobiValidationClient {

    private static final String BASE_URL = "https://api.checkmobi.com/v1/validation";
    private static final String REQUEST_URL = BASE_URL + "/request";
    private static final String VERIFY_URL = BASE_URL + "/verify";
    private static final String STATUS_URL = BASE_URL + "/status/%s";

    private static final Gson jsonFormatter = new GsonBuilder().create();

    private final String apiKey;
    private final String verificationType;

    private CloseableHttpClient client = HttpClients.createDefault();

    public static CheckmobiValidationClient reverseCallerID(String apiKey) {
        return new CheckmobiValidationClient(apiKey, "reverse_cli");
    }

    public static CheckmobiValidationClient callerID(String apiKey) {
        return new CheckmobiValidationClient(apiKey, "cli");
    }

    private CheckmobiValidationClient(String apiKey, String verificationType) {
        this.apiKey = apiKey;
        this.verificationType = verificationType;
    }

    public RequestResult request(String number) throws IOException {
        try {
            JsonObject data = _request(number);
            try {
                String id = data.getAsJsonPrimitive("id").getAsString();
                String dialingNumber = data.has("dialing_number") ?
                        data.getAsJsonPrimitive("dialing_number").getAsString() : null;
                return new RequestResult(id, dialingNumber);
            }
            catch (NullPointerException e) {
                // simulate bad request
                throw new HttpResponseException(400, "Bad request");
            }
        }
        catch (HttpResponseException e) {
            return new RequestResult(e);
        }
    }

    public VerifyResult verify(String requestId, String pin) throws IOException {
        try {
            JsonObject data = _verify(requestId, pin);
            try {
                boolean validated = data.getAsJsonPrimitive("validated").getAsBoolean();
                return new VerifyResult(validated);
            }
            catch (NullPointerException e) {
                // simulate bad request
                throw new HttpResponseException(400, "Bad request");
            }
        }
        catch (HttpResponseException e) {
            return new VerifyResult(e);
        }
    }

    public StatusResult status(String requestId) throws IOException {
        try {
            JsonObject data = _status(requestId);
            try {
                boolean validated = data.getAsJsonPrimitive("validated").getAsBoolean();
                return new StatusResult(validated);
            }
            catch (NullPointerException e) {
                // simulate bad request
                throw new HttpResponseException(400, "Bad request");
            }
        }
        catch (HttpResponseException e) {
            return new StatusResult(e);
        }
    }

    private JsonObject _request(String number) throws IOException {
        Map<String, String> data = new HashMap<>();
        data.put("number", number);
        data.put("type", verificationType);
        return _post(REQUEST_URL, data);
    }

    private JsonObject _verify(String requestId, String pin) throws IOException {
        Map<String, String> data = new HashMap<>();
        data.put("id", requestId);
        data.put("pin", pin);
        return _post(VERIFY_URL, data);
    }

    private JsonObject _status(String requestId) throws IOException {
        return _get(String.format(STATUS_URL, URLEncoder.encode(requestId, "UTF-8")));
    }

    private JsonObject _post(String url, Map<String, String> data) throws IOException {
        HttpPost req = new HttpPost(url);

        // authentication
        req.addHeader("Authorization", apiKey);

        // request body
        req.setEntity(new StringEntity(toJson(data),
                ContentType.create("application/json", Charset.forName("UTF-8"))));

        CloseableHttpResponse res = client.execute(req);
        return parseResponse(res);
    }

    private JsonObject _get(String url) throws IOException {
        HttpGet req = new HttpGet(url);

        // authentication
        req.addHeader("Authorization", apiKey);

        CloseableHttpResponse res = client.execute(req);
        return parseResponse(res);
    }

    private JsonObject parseResponse(CloseableHttpResponse res) throws IOException {
        try {
            if (res.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                HttpEntity entity = res.getEntity();
                if (entity != null) {
                    ContentType contentType = ContentType.getOrDefault(entity);
                    Charset charset = contentType.getCharset();
                    if (charset == null)
                        charset = Charset.forName("UTF-8");
                    Reader reader = new InputStreamReader(entity.getContent(), charset);
                    return (JsonObject) new JsonParser().parse(reader);
                }

                // no response body
                return new JsonObject();
            }
            else
                throw new HttpResponseException(
                        res.getStatusLine().getStatusCode(),
                        res.getStatusLine().getReasonPhrase());
        }
        finally {
            res.close();
        }
    }

    private String toJson(Map<String, String> data) {
        return jsonFormatter.toJson(data);
    }

}
