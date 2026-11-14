package com.sniply.app;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises the real service, including its embedded engine, muxer and gallery writer. */
@RunWith(AndroidJUnit4.class)
public class LiveLinkTest {
    @Test public void suppliedLink() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context app = instrumentation.getTargetContext();
        String url = InstrumentationRegistry.getArguments().getString("video_url", "");
        org.junit.Assume.assumeTrue("Run via the Link Lab workflow", !url.isEmpty());
        JSONObject result = new JSONObject();
        long start = SystemClock.elapsedRealtime();
        Activity activity = null;
        Uri saved = null;
        try {
            activity = instrumentation.startActivitySync(new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.waitForIdleSync();
            DownloadService.status = "Ready to save a video";
            app.startForegroundService(new Intent(app, DownloadService.class)
                .putExtra("link", url)
                .putExtra("quality", InstrumentationRegistry.getArguments().getString("quality", "Best available")));
            long deadline = start + 170000;
            while (SystemClock.elapsedRealtime() < deadline) {
                String status = DownloadService.status;
                if (!DownloadService.running && (status.startsWith("Saved to gallery") || status.startsWith("Could not download:"))) break;
                Thread.sleep(250);
            }
            result.put("engine", com.chaquo.python.Python.getInstance().getModule("downloader").callAttr("engine_version").toString());
            if (!DownloadService.status.startsWith("Saved to gallery")) throw new Exception(DownloadService.status);
            String uri = app.getSharedPreferences("sniply", Context.MODE_PRIVATE).getString("latest_uri", null);
            if (uri == null) throw new Exception("Service returned success without a gallery URI");
            saved = Uri.parse(uri);
            MediaExtractor extractor = new MediaExtractor();
            boolean video = false, audio = false;
            try {
                extractor.setDataSource(app, saved, null);
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                    extractor.selectTrack(i);
                    boolean readable = extractor.getSampleSize() > 0;
                    video |= readable && mime != null && mime.startsWith("video/");
                    audio |= readable && mime != null && mime.startsWith("audio/");
                    extractor.unselectTrack(i);
                }
            } finally { extractor.release(); }
            if (!video) throw new Exception("Gallery file has no readable video track");
            result.put("status", "passed").put("gallery_saved", true).put("video", true).put("audio", audio);
            if (!audio) result.put("note", "No audio track. This may be an intentionally silent video.");
        } catch (Exception error) {
            result.put("status", "failed").put("error", String.valueOf(error.getMessage()).substring(0, Math.min(1200, String.valueOf(error.getMessage()).length())));
        } finally {
            result.put("elapsed_ms", SystemClock.elapsedRealtime() - start);
            Bundle message = new Bundle(); message.putString("sniply_result", result.toString());
            instrumentation.sendStatus(0, message);
            if (saved != null) app.getContentResolver().delete(saved, null, null);
            if (activity != null) {
                Activity finalActivity = activity;
                instrumentation.runOnMainSync(finalActivity::finish);
            }
        }
    }
}
