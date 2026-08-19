package org.telegram.ui.floppa;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.telegram.messenger.floppa.FloppaMeetApi;

/** FloppaMeet's native entry point using Telegram's Android UI foundation. */
public class FloppaMeetActivity extends Activity {
    private final int background = Color.rgb(25, 22, 38);
    private final int panel = Color.rgb(37, 32, 55);
    private final int primary = Color.rgb(150, 100, 245);
    private FloppaMeetApi api;
    private LinearLayout root;
    private TextView error;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = new FloppaMeetApi(this);
        showAuth(false);
    }

    private void showAuth(boolean register) {
        root = column(background);
        root.setGravity(Gravity.CENTER);
        LinearLayout card = column(panel);
        card.setPadding(dp(28), dp(28), dp(28), dp(28));
        TextView logo = text("🐾", 42, Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        card.addView(logo, match());
        TextView title = text(register ? "Join FloppaMeet" : "Welcome back", 27, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title, match());
        TextView subtitle = text(register ? "Create your FloppaMeet account" : "Sign in to your FloppaMeet workspace", 14, Color.LTGRAY);
        card.addView(subtitle, match());
        EditText username = input("Username"); card.addView(username, match());
        EditText displayName = input("Display name"); if (register) card.addView(displayName, match());
        EditText password = input("Password"); password.setInputType(0x81); card.addView(password, match());
        Button action = button(register ? "Create account" : "Sign in"); card.addView(action, match());
        Button toggle = button(register ? "Already have an account? Sign in" : "Create an account"); card.addView(toggle, match());
        error = text("", 13, Color.rgb(255, 130, 145)); card.addView(error, match());
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        toggle.setOnClickListener(v -> showAuth(!register));
        action.setOnClickListener(v -> {
            error.setText(""); action.setEnabled(false);
            FloppaMeetApi.Callback<JsonObject> callback = new FloppaMeetApi.Callback<JsonObject>() {
                @Override public void onResult(JsonObject result) { runOnUiThread(() -> { action.setEnabled(true); if (result.has("error")) error.setText(result.get("error").getAsString()); else loadWorkspace(); }); }
                @Override public void onError(Exception exception) { runOnUiThread(() -> { action.setEnabled(true); error.setText("Network error. Check your connection."); }); }
            };
            if (register) api.register(username.getText().toString(), displayName.getText().toString(), password.getText().toString(), callback);
            else api.login(username.getText().toString(), password.getText().toString(), callback);
        });
    }

    private void loadWorkspace() {
        api.workspace(new FloppaMeetApi.Callback<JsonObject>() {
            @Override public void onResult(JsonObject data) { runOnUiThread(() -> showWorkspace(data)); }
            @Override public void onError(Exception exception) { runOnUiThread(() -> showAuth(false)); }
        });
    }

    private void showWorkspace(JsonObject data) {
        root = column(background);
        LinearLayout header = row(primary); header.setPadding(dp(18), dp(16), dp(18), dp(16));
        TextView title = text("FloppaMeet", 22, Color.WHITE); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button logout = button("Sign out"); header.addView(logout, new LinearLayout.LayoutParams(-2, -2)); logout.setOnClickListener(v -> api.logout(result -> runOnUiThread(() -> showAuth(false))));
        root.addView(header, match());
        ScrollView scroll = new ScrollView(this); LinearLayout content = column(background); content.setPadding(dp(12), dp(12), dp(12), dp(24));
        JsonArray servers = array(data, "servers"), channels = array(data, "channels"), messages = array(data, "messages");
        if (servers.size() > 0) { TextView server = text(servers.get(0).getAsJsonObject().get("name").getAsString(), 18, Color.WHITE); server.setTypeface(Typeface.DEFAULT, Typeface.BOLD); content.addView(server, match()); }
        TextView channelsTitle = text("CHANNELS", 12, Color.LTGRAY); channelsTitle.setPadding(0, dp(20), 0, dp(6)); content.addView(channelsTitle, match());
        String firstTextChannel = null;
        for (JsonElement element : channels) { JsonObject channel = element.getAsJsonObject(); if ("text".equals(channel.get("type").getAsString())) { if (firstTextChannel == null) firstTextChannel = channel.get("id").getAsString(); Button channelButton = button("#  " + channel.get("name").getAsString()); content.addView(channelButton, match()); } }
        TextView messagesTitle = text("RECENT MESSAGES", 12, Color.LTGRAY); messagesTitle.setPadding(0, dp(20), 0, dp(6)); content.addView(messagesTitle, match());
        for (JsonElement element : messages) { JsonObject message = element.getAsJsonObject(); TextView line = text(message.get("content").getAsString(), 15, Color.WHITE); line.setPadding(dp(12), dp(10), dp(12), dp(10)); content.addView(line, match()); }
        if (firstTextChannel != null) addComposer(content, firstTextChannel);
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
    }

    private void addComposer(LinearLayout parent, String channelId) {
        LinearLayout composer = row(panel); composer.setPadding(dp(8), dp(8), dp(8), dp(8)); EditText message = input("Message #channel"); composer.addView(message, new LinearLayout.LayoutParams(0, -2, 1)); Button send = button("Send"); composer.addView(send, new LinearLayout.LayoutParams(-2, -2)); parent.addView(composer, match()); send.setOnClickListener(v -> { String content = message.getText().toString().trim(); if (content.isEmpty()) return; send.setEnabled(false); api.sendMessage(channelId, content, result -> runOnUiThread(() -> { send.setEnabled(true); if (!result.has("error")) message.setText(""); })); });
    }

    private LinearLayout column(int color) { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); view.setBackgroundColor(color); return view; }
    private LinearLayout row(int color) { LinearLayout view = column(color); view.setOrientation(LinearLayout.HORIZONTAL); return view; }
    private TextView text(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view; }
    private EditText input(String hint) { EditText view = new EditText(this); view.setHint(hint); view.setHintTextColor(Color.GRAY); view.setTextColor(Color.WHITE); view.setSingleLine(true); view.setPadding(dp(12), dp(8), dp(12), dp(8)); return view; }
    private Button button(String label) { Button button = new Button(this); button.setText(label); button.setTextColor(Color.WHITE); button.setAllCaps(false); button.setBackgroundColor(primary); return button; }
    private LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private JsonArray array(JsonObject object, String key) { return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray(); }
}
