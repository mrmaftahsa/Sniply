package com.sniply.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.MediaStore;
import android.net.Uri;
import com.chaquo.python.*;
import com.chaquo.python.android.AndroidPlatform;
import java.io.*;
import java.nio.file.Files;

public class DownloadService extends Service {
    public static volatile String status = "Ready to save a video";
    public static volatile boolean running = false;
    public interface Progress { void onProgress(String text); }
    public static synchronized void initPython(Context context) {
        if (!Python.isStarted()) Python.start(new AndroidPlatform(context.getApplicationContext()));
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (running || intent == null) return START_NOT_STICKY;
        running = true;
        status = "Finding video…";
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        startForeground(1, new Notification.Builder(this, "downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Sniply")
            .setContentText("Downloading video").setContentIntent(open).setOngoing(true).build());
        String link = intent.getStringExtra("link");
        String quality = intent.getStringExtra("quality");
        new Thread(() -> {
            File folder = new File(getCacheDir(), "video-" + System.nanoTime());
            Uri destination = null;
            try {
                if (!folder.mkdirs()) throw new IOException("Cannot create download folder");
                initPython(this);
                Progress progress = text -> status = text;
                String path = Python.getInstance().getModule("downloader").callAttr("download", link, folder.getAbsolutePath(), quality, progress).toString();
                File file = new File(path);
                status = "Saving to gallery…";
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, "Sniply-" + System.currentTimeMillis() + (path.endsWith(".mp4") ? ".mp4" : ".webm"));
                values.put(MediaStore.Video.Media.MIME_TYPE, path.endsWith(".mp4") ? "video/mp4" : "video/webm");
                values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Sniply");
                values.put(MediaStore.Video.Media.IS_PENDING, 1);
                destination = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (destination == null) throw new IOException("Gallery storage unavailable");
                try (OutputStream output = getContentResolver().openOutputStream(destination)) {
                    if (output == null) throw new IOException("Cannot write video");
                    Files.copy(file.toPath(), output);
                }
                values.clear();
                values.put(MediaStore.Video.Media.IS_PENDING, 0);
                getContentResolver().update(destination, values, null, null);
                getSharedPreferences("sniply", MODE_PRIVATE).edit().putString("latest_uri", destination.toString()).apply();
                status = "Saved to gallery • Movies/Sniply";
            } catch (Exception error) {
                if (destination != null) getContentResolver().delete(destination, null, null);
                status = "Could not download: " + error.getMessage() + "\nTry another public video or check for updates. Some videos require login or have no combined audio/video format.";
            } finally {
                File[] files = folder.listFiles();
                if (files != null) for (File file : files) file.delete();
                folder.delete();
                running = false;
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }, "Sniply-download").start();
        return START_NOT_STICKY;
    }
}
