package com.sniply.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    private static final int INK = Color.rgb(28, 34, 28), MUTED = Color.rgb(105, 112, 101),
        PAPER = Color.rgb(247, 248, 242), LIME = Color.rgb(207, 244, 115), LINE = Color.rgb(224, 228, 216);
    private EditText input;
    private TextView save, source, statusTitle, statusDetail, qualityLabel, recentTitle, recentDetail;
    private LinearLayout statusCard, recentCard;
    private ProgressBar progress;
    private String quality = "Best available", previousStatus = "";
    private boolean wasRunning;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        public void run() { renderStatus(); handler.postDelayed(this, 500); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setBackgroundColor(PAPER);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : insets.consumeSystemWindowInsets();
        });
        LinearLayout page = column(); page.setPadding(dp(24), dp(16), dp(24), dp(28));
        scroll.addView(page);
        LinearLayout header = row();
        LinearLayout identity = row();
        ImageView mark = new ImageView(this); mark.setImageResource(R.drawable.ic_sniply); mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        identity.addView(mark, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView name = text("sniply", 25, INK, true); name.setPadding(dp(10), 0, 0, 0); identity.addView(name);
        header.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
        TextView settings = action("", Color.WHITE, INK, 24); settings.setContentDescription("Settings and updates"); settings.setTag("settings"); settings.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), outline(Color.WHITE, LINE, 16), shape(Color.WHITE, 16)));
        settings.setCompoundDrawablesRelative(null, null, null, null); settings.setPadding(dp(13), dp(13), dp(13), dp(13)); settings.setCompoundDrawablesRelative(icon(R.drawable.ic_settings, INK, 22), null, null, null);
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        settings.setOnClickListener(v -> showSettings()); page.addView(header);
        gap(page, 28);
        TextView badge = text("●  NO ADS. JUST YOUR VIDEOS.", 10, MUTED, true); badge.setLetterSpacing(.12f); page.addView(badge);
        gap(page, 10);
        TextView heading = text("Found it.\nKeep it.", 49, INK, true); heading.setLineSpacing(dp(-5), 1); heading.setLetterSpacing(-.05f);
        page.addView(heading);
        gap(page, 10);
        page.addView(text("Your favorite videos, a little closer.\nDrop a link. We’ll take it from here.", 15, MUTED, false));
        gap(page, 24);

        LinearLayout card = column(); card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(shape(INK, 28)); page.addView(card, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout cardTop = row();
        TextView cardLabel = text("ONE LINK. THAT’S ALL.", 10, LIME, true); cardLabel.setLetterSpacing(.12f);
        cardTop.addView(cardLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text("↙", 28, LIME, false); cardTop.addView(arrow); card.addView(cardTop);
        gap(card, 16);
        LinearLayout field = column(); field.setPadding(dp(14), dp(8), dp(14), dp(8)); field.setBackground(shape(Color.rgb(47, 54, 46), 18));
        input = new EditText(this); input.setTag("linkInput"); input.setTextSize(16); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.rgb(175, 184, 170));
        input.setHint("Paste your video link"); input.setBackgroundColor(Color.TRANSPARENT); input.setPadding(0, dp(8), 0, dp(8));
        input.setSingleLine(true); input.setSelectAllOnFocus(false);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        field.addView(input, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView paste = action("Paste link", Color.rgb(57, 68, 51), LIME, 14);
        paste.setPadding(dp(14), 0, dp(14), 0); paste.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22CFF473), outline(Color.rgb(57, 68, 51), Color.rgb(88, 106, 69), 14), shape(Color.WHITE, 14)));
        paste.setCompoundDrawablePadding(dp(10)); paste.setCompoundDrawablesRelative(icon(R.drawable.ic_clipboard, LIME, 19), null, null, null); paste.setContentDescription("Paste video link from clipboard");
        paste.setOnClickListener(v -> pasteClipboard(true)); field.addView(paste); card.addView(field);
        source = text("YouTube, TikTok & 5 more", 11, Color.rgb(184, 193, 177), false); source.setPadding(dp(2), dp(12), 0, dp(12)); source.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); card.addView(source);
        save = action("Save video", LIME, INK, 16);
        save.setPadding(dp(22), 0, dp(22), 0); save.setCompoundDrawablesRelative(null, null, icon(R.drawable.ic_download, INK, 23), null); save.setElevation(dp(3)); save.setTag("saveVideo");
        card.addView(save, new LinearLayout.LayoutParams(-1, dp(58))); save.setOnClickListener(v -> startDownload());
        gap(card, 12);
        qualityLabel = action("Best available", Color.rgb(40, 47, 39), Color.rgb(215, 225, 207), 12);
        qualityLabel.setPadding(dp(14), 0, dp(14), 0); qualityLabel.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), outline(Color.rgb(40, 47, 39), Color.rgb(73, 85, 64), 14), shape(Color.WHITE, 14)));
        qualityLabel.setCompoundDrawablesRelative(icon(R.drawable.ic_tune, Color.rgb(215, 225, 207), 18), null, icon(R.drawable.ic_chevron, Color.rgb(215, 225, 207), 16), null);
        qualityLabel.setContentDescription("Choose video quality"); qualityLabel.setOnClickListener(v -> chooseQuality()); card.addView(qualityLabel);

        gap(page, 16);
        statusCard = column(); statusCard.setPadding(dp(18), dp(16), dp(18), dp(16)); statusCard.setBackground(outline(Color.WHITE, LINE, 22)); statusCard.setVisibility(View.GONE);
        statusTitle = text("", 16, INK, true); statusTitle.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); statusCard.addView(statusTitle);
        statusDetail = text("", 12, MUTED, false); statusDetail.setPadding(0, dp(6), 0, dp(10)); statusCard.addView(statusDetail);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setProgressTintList(ColorStateList.valueOf(Color.rgb(99, 137, 48))); progress.setProgressBackgroundTintList(ColorStateList.valueOf(LINE)); statusCard.addView(progress, new LinearLayout.LayoutParams(-1, dp(5)));
        page.addView(statusCard);

        LinearLayout platformTitle = row();
        TextView platforms = text("ALL YOUR FAVORITES", 10, MUTED, true); platforms.setLetterSpacing(.1f); platformTitle.addView(platforms, new LinearLayout.LayoutParams(0, -2, 1));
        platformTitle.addView(text("7 platforms", 11, MUTED, false)); page.addView(platformTitle); gap(page, 12);
        platformRow(page, new String[]{"YouTube", "Instagram", "TikTok", "X"}); gap(page, 8);
        platformRow(page, new String[]{"Facebook", "Pinterest", "Reddit"}); gap(page, 26);
        LinearLayout recentHeader = row(); recentHeader.addView(text("Your latest save", 20, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(recentHeader); gap(page, 12);
        recentCard = row(); recentCard.setPadding(dp(16), dp(16), dp(16), dp(16)); recentCard.setBackground(outline(Color.WHITE, LINE, 22));
        TextView recentIcon = text("↧", 25, INK, true); recentIcon.setGravity(Gravity.CENTER); recentIcon.setBackground(shape(PAPER, 14));
        recentCard.addView(recentIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout recentCopy = column(); recentCopy.setPadding(dp(14), 0, 0, 0);
        recentTitle = text("A little empty, for now", 14, INK, true); recentDetail = text("Your first save will feel right at home.", 11, MUTED, false);
        recentCopy.addView(recentTitle); recentCopy.addView(recentDetail); recentCard.addView(recentCopy, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(recentCard); recentCard.setOnClickListener(v -> openLatest()); updateRecent();
        gap(page, 22);
        TextView footer = text("Made for keeping. Always ad-free.", 11, MUTED, false); footer.setGravity(Gravity.CENTER); page.addView(footer);
        setContentView(scroll);
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { updateInput(); }
            public void afterTextChanged(Editable s) {}
        });
        input.setOnEditorActionListener((v, action, event) -> { if (action == android.view.inputmethod.EditorInfo.IME_ACTION_GO) { startDownload(); return true; } return false; });
        if (state != null) { input.setText(state.getString("link", "")); quality = state.getString("quality", "Best available"); updateQuality(); }
        else handleShare(getIntent());
        updateInput(); renderStatus(); checkUpdates(false);
        page.setAlpha(0f); page.setTranslationY(dp(12)); page.animate().alpha(1f).translationY(0).setDuration(320).start();
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private void gap(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private TextView text(String content, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(content); view.setTextSize(size); view.setTextColor(color); view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); return view;
    }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private GradientDrawable outline(int color, int stroke, int radius) { GradientDrawable d = shape(color, radius); d.setStroke(dp(1), stroke); return d; }
    private Drawable icon(int resource, int color, int size) {
        Drawable drawable = getDrawable(resource).mutate(); drawable.setTint(color); drawable.setBounds(0, 0, dp(size), dp(size)); return drawable;
    }
    private TextView action(String title, int background, int color, int size) {
        TextView view = text(title, size, color, true); view.setGravity(Gravity.CENTER); view.setMinHeight(dp(48));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), shape(background, 18), shape(Color.WHITE, 18)));
        android.animation.StateListAnimator press = new android.animation.StateListAnimator();
        press.addState(new int[]{android.R.attr.state_pressed}, android.animation.ObjectAnimator.ofPropertyValuesHolder(view,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", .975f), android.animation.PropertyValuesHolder.ofFloat("scaleY", .975f)).setDuration(90));
        press.addState(new int[]{}, android.animation.ObjectAnimator.ofPropertyValuesHolder(view,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f), android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f)).setDuration(150));
        view.setStateListAnimator(press);
        view.setFocusable(true); view.setClickable(true); view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) { super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName(Button.class.getName()); }
        }); return view;
    }
    private void platformRow(LinearLayout parent, String[] names) {
        LinearLayout row = row();
        for (int i = 0; i < names.length; i++) {
            TextView chip = text(names[i], 11, INK, true); chip.setGravity(Gravity.CENTER); chip.setPadding(dp(3), dp(10), dp(3), dp(10)); chip.setBackground(outline(PAPER, LINE, 14));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1); if (i > 0) params.leftMargin = dp(6); row.addView(chip, params);
        }
        parent.addView(row);
    }
    private String supportedLink() {
        Matcher matcher = Pattern.compile("https://[^\\s<>\"\\u200b]+", Pattern.CASE_INSENSITIVE).matcher(input.getText());
        String[] domains = {"youtube.com", "youtu.be", "instagram.com", "facebook.com", "fb.watch", "fb.com", "x.com", "twitter.com", "pinterest.com", "pin.it", "reddit.com", "redd.it", "tiktok.com"};
        while (matcher.find()) {
            String url = matcher.group().replaceAll("[.,);!?\\]]+$", ""); String host = Uri.parse(url).getHost();
            if (host == null) continue; host = host.toLowerCase(java.util.Locale.ROOT);
            for (String domain : domains) if (host.equals(domain) || host.endsWith("." + domain)) return url;
        }
        return null;
    }
    private void updateInput() {
        String url = supportedLink(); boolean busy = DownloadService.running;
        save.setEnabled(!busy); save.setAlpha(busy ? .55f : 1f); save.setText(busy ? "Saving your video…" : "Save video");
        source.setText(url != null ? "●  Link ready. Let’s keep it." : input.length() == 0 ? "YouTube, TikTok & 5 more" : "Use a video link from a supported platform");
        source.setTextColor(url != null ? LIME : Color.rgb(184, 193, 177));
    }
    private void startDownload() {
        if (DownloadService.running) return;
        String url = supportedLink();
        if (url == null) { input.setError("Add a supported HTTPS video link"); input.requestFocus(); return; }
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0); input.clearFocus();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        Intent intent = new Intent(this, DownloadService.class).putExtra("link", url).putExtra("quality", quality);
        startForegroundService(intent);
        handler.postDelayed(this::renderStatus, 100);
    }
    private void chooseQuality() {
        String[] labels = {"Best available", "Balanced · up to 720p", "Data saver · up to 360p"};
        String[] values = {"Best available", "Balanced (720p)", "Data saver (360p)"};
        int selected = java.util.Arrays.asList(values).indexOf(quality);
        new AlertDialog.Builder(this).setTitle("Make it yours").setSingleChoiceItems(labels, selected, (dialog, index) -> { quality = values[index]; updateQuality(); dialog.dismiss(); }).setNegativeButton("Done", null).show();
    }
    private void updateQuality() { qualityLabel.setText((quality.startsWith("Balanced") ? "Up to 720p" : quality.startsWith("Data") ? "Up to 360p" : "Best available")); }
    private void renderStatus() {
        String value = DownloadService.status; boolean busy = DownloadService.running;
        if (value.equals(previousStatus) && busy == wasRunning) return;
        previousStatus = value; wasRunning = busy; updateInput();
        boolean success = value.startsWith("Saved to gallery"), failed = value.startsWith("Could not download:");
        statusCard.setVisibility(busy || success || failed ? View.VISIBLE : View.GONE);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (success) { statusTitle.setText("Yours. Just like that. ✓"); statusDetail.setText("Saved to your gallery. Tap your latest save to watch."); updateRecent(); }
        else if (failed) {
            statusTitle.setText("This one needs another try"); statusDetail.setText("Try another public link, or tap for details.");
        } else {
            statusTitle.setText(value.startsWith("Downloading") ? "On its way to you…" : value.startsWith("Combining") ? "Bringing picture and sound together…" : value.startsWith("Saving") ? "Making room in your gallery…" : "Finding your video…");
            statusDetail.setText(value.startsWith("Downloading") ? value : "You can leave Sniply open or come back in a moment.");
            Matcher percent = Pattern.compile("(\\d+)%").matcher(value); boolean determinate = percent.find(); progress.setIndeterminate(!determinate); if (determinate) progress.setProgress(Math.min(100, Integer.parseInt(percent.group(1))), true);
        }
        statusCard.setOnClickListener(failed ? v -> new AlertDialog.Builder(this).setTitle("Download details").setMessage(value).setPositiveButton("OK", null).show() : null);
    }
    private void updateRecent() {
        android.content.SharedPreferences prefs = getSharedPreferences("sniply", MODE_PRIVATE);
        if (!prefs.contains("latest_uri")) return;
        recentTitle.setText("Ready whenever you are"); recentDetail.setText("Saved to Movies / Sniply  ·  Tap to watch"); recentCard.setContentDescription("Play your latest saved video");
    }
    private void openLatest() {
        String uri = getSharedPreferences("sniply", MODE_PRIVATE).getString("latest_uri", null);
        if (uri == null) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
        catch (ActivityNotFoundException error) { showMessage("Open your gallery to watch your video."); }
    }
    private void showSettings() {
        new AlertDialog.Builder(this).setTitle("A simpler kind of saving.")
            .setItems(new String[]{"Check for updates", "Download quality", "About Sniply"}, (dialog, item) -> {
                if (item == 0) checkUpdates(true); else if (item == 1) chooseQuality();
                else new AlertDialog.Builder(this).setTitle("Sniply")
                    .setMessage("No ads. No account. Videos saved on your phone.\n\nSave public videos you own or have permission to keep. Some links and formats may be unavailable.\n\nSniply contacts GitHub to check for app updates and the source platforms to download your videos.")
                    .setPositiveButton("Lovely", null).show();
            }).setNegativeButton("Close", null).show();
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("link", input.getText().toString()); state.putString("quality", quality); super.onSaveInstanceState(state); }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (focused && input != null && input.length() == 0) pasteClipboard(false); }
    @Override public void onResume() { super.onResume(); handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override public void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleShare(intent); }
    private void handleShare(Intent intent) { if (Intent.ACTION_SEND.equals(intent.getAction())) { String text = intent.getStringExtra(Intent.EXTRA_TEXT); if (text != null) input.setText(text); } }
    private void pasteClipboard(boolean explicit) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); ClipData clip = clipboard.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) { CharSequence text = clip.getItemAt(0).coerceToText(this); if (text != null && (explicit || text.toString().contains("https://"))) input.setText(text); }
        else if (explicit) showMessage("Copy a video link first.");
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
