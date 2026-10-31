package io.github.lcblauncher.fixture;

import android.app.Application;
import android.content.Context;
import java.io.File;

/** Exercises application attach/onCreate, including a controlled process exit before any Activity. */
public final class FixtureApplication extends Application {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        File marker = new File(base.getFilesDir(), "exit-during-attach");
        if (marker.exists()) {
            marker.delete();
            System.exit(23);
        }
    }
    @Override public void onCreate() {
        super.onCreate();
        File marker = new File(getFilesDir(), "throw-during-create");
        if (marker.exists()) {
            marker.delete();
            throw new IllegalStateException("fixture-sensitive-message-must-not-be-exported");
        }
    }
}
