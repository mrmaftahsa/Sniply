package com.sniply.app;

import android.content.Context;
import android.media.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.chaquo.python.*;
import java.io.*;
import java.nio.file.Files;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DownloadTest {
    @Test public void embeddedEngineDownloadsAndMuxesRealMedia() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context tests = InstrumentationRegistry.getInstrumentation().getContext();
        File folder = new File(app.getCacheDir(), "download-regression");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        for (String name : new String[]{"engine_checks.py", "video.mp4", "audio.m4a", "combined.mp4"}) {
            File file = new File(folder, name);
            try (InputStream input = tests.getAssets().open(name)) { Files.copy(input, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        }
        DownloadService.initPython(app);
        Python py = Python.getInstance();
        py.getModule("sys").get("path").callAttr("insert", 0, folder.getAbsolutePath());
        List<PyObject> parts = py.getModule("engine_checks").callAttr("run", folder.getAbsolutePath()).asList();
        File merged = DownloadService.mux(new File(parts.get(0).toString()), new File(parts.get(1).toString()), new File(folder, "merged.mp4"));
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(merged.getAbsolutePath());
            boolean video = false, audio = false;
            StringBuilder formats = new StringBuilder();
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME); formats.append(format).append("; ");
                video |= "video/avc".equals(mime); audio |= "audio/mp4a-latm".equals(mime);
                extractor.selectTrack(i);
                assertTrue("No playable samples in " + mime, extractor.getSampleSize() > 0);
                extractor.unselectTrack(i);
            }
            assertTrue("Merged file needs video and audio: " + formats, video && audio);
        } finally { extractor.release(); }
    }
}
