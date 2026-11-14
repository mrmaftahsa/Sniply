package com.sniply.app;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.Bitmap;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;

@RunWith(AndroidJUnit4.class)
public class InterfaceTest {
    @Test public void sharedLinkAndHomeRender() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
            .setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://youtu.be/example")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            EditText input = activity.getWindow().getDecorView().findViewWithTag("linkInput");
            TextView save = activity.getWindow().getDecorView().findViewWithTag("saveVideo");
            assertEquals("https://youtu.be/example", input.getText().toString());
            assertTrue(save.isEnabled());
        });
        instrumentation.runOnMainSync(activity::finish);
    }
    @Test public void emptyLinkStaysOnScreenAndScreenshot() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            EditText input = activity.getWindow().getDecorView().findViewWithTag("linkInput"); input.setText("");
            TextView save = activity.getWindow().getDecorView().findViewWithTag("saveVideo"); save.performClick();
            assertNotNull(input.getError());
            input.setError(null); input.clearFocus();
            ((android.view.inputmethod.InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
        });
        instrumentation.waitForIdleSync();
        Thread.sleep(1000);
        Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (FileOutputStream output = new FileOutputStream(new File(context.getExternalCacheDir(), "sniply-home.png"))) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, output); }
        try (android.os.ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand("screencap -p /data/local/tmp/sniply-home.png");
             FileInputStream stream = new FileInputStream(descriptor.getFileDescriptor())) {
            byte[] chunk = new byte[1024]; while (stream.read(chunk) != -1) { }
        }
        instrumentation.runOnMainSync(activity::finish);
    }
}
