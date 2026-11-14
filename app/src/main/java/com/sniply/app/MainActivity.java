package com.sniply.app;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;

public class MainActivity extends Activity {
    private EditText input;
    private TextView status;
    private Button download;
    private Spinner quality;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        public void run() {
            status.setText(DownloadService.status);
            download.setEnabled(!DownloadService.running);
            handler.postDelayed(this, 700);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(16, 24, 39));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(48, 64, 48, 48);
        page.setBackgroundColor(Color.rgb(16, 24, 39));
        TextView title = label("Sniply", 38);
        page.addView(title);
        page.addView(label("Keep the videos you love.", 18));
        page.addView(label("YouTube · Instagram · Facebook · X\nPinterest · Reddit · TikTok", 14));
        input = new EditText(this);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setHint("Paste a video link here");
        input.setMinLines(3);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        page.addView(input, new LinearLayout.LayoutParams(-1, -2));
        Button paste = button("Paste from clipboard", page);
        paste.setOnClickListener(view -> pasteClipboard(true));
        quality = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"Balanced (720p)", "Data saver (360p)", "Best available"});
        quality.setAdapter(adapter);
        quality.setBackgroundColor(Color.LTGRAY);
        page.addView(quality);
        download = button("Save video", page);
        download.setOnClickListener(view -> {
            if (input.getText().toString().trim().isEmpty()) { input.setError("Paste a video link first"); return; }
            Intent intent = new Intent(this, DownloadService.class);
            intent.putExtra("link", input.getText().toString());
            intent.putExtra("quality", quality.getSelectedItem().toString());
            startForegroundService(intent);
        });
        status = label("", 15);
        page.addView(status);
        Button updates = button("Check for updates", page);
        updates.setOnClickListener(view -> checkUpdates(true));
        page.addView(label("Save videos you own or have permission to download. Public links only; availability varies. Quality depends on the formats the platform provides.", 12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);
        handleShare(getIntent());
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        checkUpdates(false);
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(size); view.setTextColor(Color.WHITE); view.setPadding(0, 12, 0, 20);
        return view;
    }
    private Button button(String text, LinearLayout parent) {
        Button button = new Button(this); button.setText(text); parent.addView(button); return button;
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && input != null && input.getText().length() == 0) pasteClipboard(false);
    }
    @Override public void onResume() { super.onResume(); handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override public void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleShare(intent); }
    private void handleShare(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) input.setText(text);
        }
    }
    private void pasteClipboard(boolean explicit) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            CharSequence text = clip.getItemAt(0).coerceToText(this);
            if (text != null && (explicit || text.toString().contains("https://"))) input.setText(text);
        } else if (explicit) Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
    }
    private void checkUpdates(boolean explicit) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("https://api.github.com/repos/mrmaftahsa/Sniply/releases/latest").openConnection();
                connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Sniply-Android");
                if (connection.getResponseCode() == 404) {
                    if (explicit) showMessage("No signed release yet. APK builds are available in GitHub Actions.");
                    return;
                }
                if (connection.getResponseCode() != 200) throw new IOException("Update service unavailable");
                String json;
                try (InputStream stream = connection.getInputStream()) { ByteArrayOutputStream buffer = new ByteArrayOutputStream(); byte[] chunk = new byte[4096]; int count; while ((count = stream.read(chunk)) != -1) buffer.write(chunk, 0, count); json = buffer.toString("UTF-8"); }
                JSONObject release = new JSONObject(json);
                String tag = release.getString("tag_name");
                int build = Integer.parseInt(tag.substring(tag.lastIndexOf('.') + 1));
                long installed = getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
                if (build > installed) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        new android.app.AlertDialog.Builder(this).setTitle("Sniply update available")
                            .setMessage("Install " + tag + " from the official GitHub release. Android will ask you to approve installation. Updating contacts GitHub; video links stay on your device except for requests to their platforms.")
                            .setPositiveButton("Open release", (dialog, which) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mrmaftahsa/Sniply/releases/latest"))))
                            .setNegativeButton("Later", null).show();
                    });
                } else if (explicit) showMessage("You're up to date.");
            } catch (Exception error) { if (explicit) showMessage("Could not check for updates. Try again later."); }
            finally { if (connection != null) connection.disconnect(); }
        }, "Sniply-updates").start();
    }
    private void showMessage(String message) { runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show()); }
}
