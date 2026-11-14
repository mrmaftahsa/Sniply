package com.sniply.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.provider.MediaStore;
import android.net.Uri;
import java.nio.ByteBuffer;
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
    private static int firstTrack(MediaExtractor extractor, String prefix) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(prefix)) return i;
        }
        return -1;
    }
    /** Joins a video-only and an audio-only file into one MP4 without ffmpeg. */
    static File mux(File video, File audio, File output) throws IOException {
        MediaExtractor videoSource = new MediaExtractor(), audioSource = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean started = false;
        try {
            videoSource.setDataSource(video.getAbsolutePath());
            audioSource.setDataSource(audio.getAbsolutePath());
            int videoTrack = firstTrack(videoSource, "video/"), audioTrack = firstTrack(audioSource, "audio/");
            if (videoTrack < 0 || audioTrack < 0) throw new IOException("Downloaded streams could not be read");
            videoSource.selectTrack(videoTrack);
            audioSource.selectTrack(audioTrack);
            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaFormat videoFormat = videoSource.getTrackFormat(videoTrack);
            if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) muxer.setOrientationHint(videoFormat.getInteger(MediaFormat.KEY_ROTATION));
            int videoOut = muxer.addTrack(videoFormat);
            int audioOut = muxer.addTrack(audioSource.getTrackFormat(audioTrack));
            muxer.start();
            started = true;
            ByteBuffer buffer = ByteBuffer.allocate(8 * 1024 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                long videoTime = videoSource.getSampleTime(), audioTime = audioSource.getSampleTime();
                if (videoTime < 0 && audioTime < 0) break;
                boolean useVideo = audioTime < 0 || (videoTime >= 0 && videoTime <= audioTime);
                MediaExtractor source = useVideo ? videoSource : audioSource;
                long sampleSize = source.getSampleSize();
                if (sampleSize > 64 * 1024 * 1024) throw new IOException("Video sample is too large to combine on this device");
                if (sampleSize > buffer.capacity()) buffer = ByteBuffer.allocate((int) sampleSize);
                buffer.clear();
                int size = source.readSampleData(buffer, 0);
                if (size < 0) {
                    if (useVideo) videoSource.unselectTrack(videoTrack); else audioSource.unselectTrack(audioTrack);
                    continue;
                }
                int flags = (source.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                info.set(0, size, source.getSampleTime(), flags);
                muxer.writeSampleData(useVideo ? videoOut : audioOut, buffer, info);
                source.advance();
            }
            muxer.stop();
            started = false;
            return output;
        } catch (RuntimeException error) {
            throw new IOException("Could not combine video and audio: " + error.getMessage(), error);
        } finally {
            videoSource.release();
            audioSource.release();
            if (muxer != null) {
                try { if (started) muxer.stop(); } catch (RuntimeException ignored) { }
                muxer.release();
            }
        }
    }
    private static String mimeFor(File file) throws IOException {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".mp4") || name.endsWith(".m4v")) return "video/mp4";
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".mov")) return "video/quicktime";
        if (name.endsWith(".3gp") || name.endsWith(".3gpp")) return "video/3gpp";
        if (name.endsWith(".mkv")) return "video/x-matroska";
        if (name.endsWith(".ts")) return "video/mp2t";
        throw new IOException("Unsupported downloaded video container: " + name);
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
                java.util.List<PyObject> parts = Python.getInstance().getModule("downloader").callAttr("download", link, folder.getAbsolutePath(), quality, progress).asList();
                File file;
                if (parts.isEmpty() || parts.size() > 2) throw new IOException("No complete video was returned");
                if (parts.size() > 1) {
                    status = "Combining video and audio…";
                    file = mux(new File(parts.get(0).toString()), new File(parts.get(1).toString()), new File(folder, "merged.mp4"));
                } else file = new File(parts.get(0).toString());
                String mime = mimeFor(file);
                String extension = file.getName().substring(file.getName().lastIndexOf('.')).toLowerCase(java.util.Locale.ROOT);
                status = "Saving to gallery…";
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, "Sniply-" + System.currentTimeMillis() + extension);
                values.put(MediaStore.Video.Media.MIME_TYPE, mime);
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
                status = "Could not download: " + error.getMessage() + "\nTry another public video or check for updates. Some videos require login or are protected.";
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
