package org.kontalk.xmppserver.registration.cognalys;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.*;

/**
 * A simple REST client for Cognalys verification API.
 * @author Daniele Ricci
 */
public class CognalysVerifyClient {

    private static final String BASE_URL = "https://www.cognalys.com/api/v1/otp";
    private static final String REQUEST_URL = BASE_URL + "/";
    private static final String CONFIRM_URL = BASE_URL + "/confirm/";

    private final String appId;
    private final String token;

    private CloseableHttpClient client = HttpClients.createDefault();

    public CognalysVerifyClient(String appId, String token) {
        this.appId = appId;
        this.token = token;
    }

    public RequestResult request(String number) throws IOException {
        JsonObject data = _request(number);

        int status = parseStatus(data);
        if (status == AbstractResult.STATUS_SUCCESS) {
            String keymatch, otpStart;
            try {
                keymatch = data.getAsJsonPrimitive("keymatch").getAsString();
            }
            catch (NullPointerException e) {
                keymatch = null;
            }
            try {
                otpStart = data.getAsJsonPrimitive("otp_start").getAsString();
            }
            catch (NullPointerException e) {
                otpStart = null;
            }
            return new RequestResult(keymatch, otpStart);
        }
        else {
            return new RequestResult(parseErrors(data));
        }
    }

    public ConfirmResult confirm(String keymatch, String otp) throws IOException {
        JsonObject data = _confirm(keymatch, otp);

        String mobile;
        try {
            mobile = data.getAsJsonPrimitive("mobile").getAsString();
        }
        catch (NullPointerException e) {
            mobile = null;
        }

        int status = parseStatus(data);
        if (status == AbstractResult.STATUS_SUCCESS) {
            return new ConfirmResult(mobile);
        }
        else {
            return new ConfirmResult(mobile, parseErrors(data));
        }
    }

    private int parseStatus(JsonObject data) {
        JsonPrimitive status = data.getAsJsonPrimitive("status");
        if (status != null && status.getAsString().equals("success"))
            return AbstractResult.STATUS_SUCCESS;
        else
            return AbstractResult.STATUS_FAILED;
    }

    private List<CognalysError> parseErrors(JsonObject data) {
        JsonObject errorsJson = data.getAsJsonObject("errors");
        if (errorsJson != null) {
            Set<Map.Entry<String, JsonElement>> entries = errorsJson.entrySet();
            List<CognalysError> errors = new ArrayList<>(entries.size());
            for (Map.Entry<String, JsonElement> entry : entries) {
                try {
                    int code = Integer.parseInt(entry.getKey());
                    String text = entry.getValue().getAsString();
                    errors.add(new CognalysError(code, text));
                }
                catch (Exception e) {
                    // just skip the entry
                }
            }
            return errors;
        }

        return Collections.emptyList();
    }

    private JsonObject _request(String number) throws IOException {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("mobile", number));
        return _get(REQUEST_URL, params);
    }

    private JsonObject _confirm(String keymatch, String otp) throws IOException {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("keymatch", keymatch));
        params.add(new BasicNameValuePair("keymatch", otp));
        return _get(CONFIRM_URL, params);
    }

    private JsonObject _get(String url, List<NameValuePair> params) throws IOException {
        URI uri;
        try {
            uri = new URIBuilder(url)
                    .addParameters(params)
                    // add authentication parameters
                    .addParameter("app_id", appId)
                    .addParameter("access_token", token)
                    .build();
        }
        catch (URISyntaxException e) {
            throw new IOException("Invalid URL", e);
        }
        HttpGet req = new HttpGet(uri);
        CloseableHttpResponse res = client.execute(req);
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

}
