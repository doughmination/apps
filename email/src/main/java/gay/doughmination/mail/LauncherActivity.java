package gay.doughmination.mail;

import android.content.pm.PackageManager;

import com.google.androidbrowserhelper.trusted.TwaLauncher;

/**
 * Opens the site in Chrome even when another browser is the default. The
 * library would otherwise pick the default browser, and browsers other than
 * Chrome tend to show it as a custom tab with an address bar. Without Chrome
 * installed, it falls back to the library's own choice.
 */
public class LauncherActivity extends com.google.androidbrowserhelper.trusted.LauncherActivity {
    private static final String CHROME = "com.android.chrome";

    @Override
    protected TwaLauncher createTwaLauncher() {
        return new TwaLauncher(this, isInstalled(CHROME) ? CHROME : null);
    }

    private boolean isInstalled(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationInfo(pkg, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
