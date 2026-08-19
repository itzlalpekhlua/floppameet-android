package org.telegram.messenger.floppa;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small FloppaMeet transport used by the Android client. It deliberately keeps
 * the Telegram UI/data layer isolated from FloppaMeet's JSON API while the
 * native screens are being migrated.
 */
public final class FloppaMeetApi {
    public static final String BASE_URL = "https://floppameet.mizodevelopment.com";
    private static final String PREFS = "floppameet_session";
    private static final String SESSION = "session_cookie";

    private final SharedPreferences preferences;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public FloppaMeetApi(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isSignedIn() {
        return preferences.getString(SESSION, null) != null;
    }

    public void login(String username, String password, Callback<JsonObject> callback) {
        request("/api/auth/login", "POST", body("username", username, "password", password), callback);
    }

    public void register(String username, String displayName, String password, Callback<JsonObject> callback) {
        request("/api/auth/register", "POST", body("username", username, "displayName", displayName, "password", password), callback);
    }

    public void workspace(Callback<JsonObject> callback) {
        request("/api/floppameet", "GET", null, callback);
    }

    public void messages(String channelId, Callback<JsonObject> callback) {
        request("/api/floppameet/messages?channelId=" + encode(channelId), "GET", null, callback);
    }

    public void sendMessage(String channelId, String content, Callback<JsonObject> callback) {
        request("/api/floppameet/messages", "POST", body("channelId", channelId, "content", content), callback);
    }

    public void typing(String channelId, Callback<JsonObject> callback) {
        request("/api/floppameet/typing", "POST", body("channelId", channelId), callback);
    }

    public void typingUsers(String channelId, Callback<JsonObject> callback) {
        request("/api/floppameet/typing?channelId=" + encode(channelId), "GET", null, callback);
    }

    public void logout(Callback<JsonObject> callback) {
        request("/api/auth/logout", "POST", null, result -> {
            preferences.edit().remove(SESSION).apply();
            callback.onResult(result);
        });
    }

    private void request(String path, String method, JsonObject payload, Callback<JsonObject> callback) {
        executor.execute(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("Accept", "application/json");
                String cookie = preferences.getString(SESSION, null);
                if (cookie != null) connection.setRequestProperty("Cookie", cookie);
                if (payload != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json");
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(gson.toJson(payload).getBytes(StandardCharsets.UTF_8));
                    }
                }
                int status = connection.getResponseCode();
                String setCookie = connection.getHeaderField("Set-Cookie");
                if (setCookie != null && setCookie.contains("floppa_session=")) {
                    preferences.edit().putString(SESSION, setCookie.split(";", 2)[0]).apply();
                }
                InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                StringBuilder response = new StringBuilder();
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
                    }
                }
                JsonObject json = gson.fromJson(response.toString().isEmpty() ? "{}" : response.toString(), JsonObject.class);
                callback.onResult(json);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    private static JsonObject body(String... values) {
        JsonObject object = new JsonObject();
        for (int i = 0; i + 1 < values.length; i += 2) object.addProperty(values[i], values[i + 1]);
        return object;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public interface Callback<T> {
        void onResult(T result);
        default void onError(Exception error) {}
    }
}
