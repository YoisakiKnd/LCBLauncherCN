package io.github.lcblauncher.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Ordinary Android Activity, no host SDK, no host code on its classpath. Never installed by CI. */
public final class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            if (!getPackageName().equals("io.github.lcblauncher.fixture")) throw new IllegalStateException("package");
            String asset;
            try (java.io.InputStream input = getAssets().open("guest.txt")) {
                asset = new java.io.BufferedReader(new java.io.InputStreamReader(input, StandardCharsets.UTF_8)).readLine();
            }
            File counter = new File(getFilesDir(), "launch-count.txt");
            int count = counter.exists() ? Integer.parseInt(new String(Files.readAllBytes(counter.toPath()), StandardCharsets.UTF_8)) + 1 : 1;
            Files.write(counter.toPath(), Integer.toString(count).getBytes(StandardCharsets.UTF_8));
            String value = getString(R.string.guest_title) + "\n" + asset + "\nGUEST_COUNT=" + count;
            TextView text = new TextView(this);
            text.setText(value);
            text.setTextSize(22);
            text.setPadding(24, 24, 24, 24);
            text.setTextColor(0xffeeeeee);
            setContentView(text);
        } catch (Exception error) {
            throw new IllegalStateException("Guest execution failed", error);
        }
    }
}
