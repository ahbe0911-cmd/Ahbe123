package ir.ahmad.speechtexter.twa;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;

import com.google.androidbrowserhelper.trusted.LauncherActivity;
import com.google.androidbrowserhelper.trusted.TwaLauncher;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the normal TWA launch path, but replaces its fragile browser fallback.
 * Some Android builds expose non-browser apps as URL handlers. The stock fallback
 * can target one of them and throw ActivityNotFoundException on startup.
 */
public final class SafeLauncherActivity extends LauncherActivity {
    private static final String TAG = "SafeTwaLauncher";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
        } catch (RuntimeException error) {
            Log.e(TAG, "TWA startup failed; using the verified browser fallback", error);
            openFallbackOrShowError(Uri.parse(getString(R.string.launch_url)), null, null);
        }
    }

    @Override
    protected TwaLauncher.FallbackStrategy getFallbackStrategy() {
        return (context, builder, providerPackage, completionCallback) ->
                openFallbackOrShowError(builder.getUri(), providerPackage, completionCallback);
    }

    private void openFallbackOrShowError(
            Uri uri,
            @Nullable String preferredPackage,
            @Nullable Runnable completionCallback
    ) {
        if (openInSafeCustomTab(uri, preferredPackage)) {
            if (completionCallback != null) {
                completionCallback.run();
            } else {
                finish();
            }
            return;
        }

        if (isFinishing() || isDestroyed()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_error_title)
                .setMessage(R.string.browser_error_message)
                .setCancelable(false)
                .setPositiveButton(R.string.retry, (dialog, which) ->
                        openFallbackOrShowError(uri, null, completionCallback))
                .setNegativeButton(R.string.close, (dialog, which) -> finish())
                .show();
    }

    private boolean openInSafeCustomTab(Uri uri, @Nullable String preferredPackage) {
        for (String packageName : browserCandidates(uri, preferredPackage)) {
            try {
                CustomTabColorSchemeParams colors = new CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(getColor(R.color.status_bar_color))
                        .setNavigationBarColor(getColor(R.color.navigation_bar_color))
                        .build();

                CustomTabsIntent customTab = new CustomTabsIntent.Builder()
                        .setDefaultColorSchemeParams(colors)
                        .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
                        .setShowTitle(false)
                        .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                        .setUrlBarHidingEnabled(true)
                        .build();
                customTab.intent.setPackage(packageName);
                customTab.launchUrl(this, uri);
                return true;
            } catch (ActivityNotFoundException | SecurityException error) {
                Log.w(TAG, "Browser rejected Custom Tab: " + packageName, error);
            } catch (RuntimeException error) {
                Log.w(TAG, "Browser failed to open: " + packageName, error);
            }
        }
        return false;
    }

    private Set<String> browserCandidates(Uri uri, @Nullable String preferredPackage) {
        LinkedHashSet<String> packages = new LinkedHashSet<>();
        addIfBrowser(packages, uri, preferredPackage);

        try {
            addIfBrowser(packages, uri, CustomTabsClient.getPackageName(this, null));
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to resolve the default Custom Tabs provider", error);
        }

        Intent viewIntent = browserIntent(uri);
        for (ResolveInfo info : queryBrowsers(getPackageManager(), viewIntent)) {
            if (info.activityInfo != null) {
                addIfBrowser(packages, uri, info.activityInfo.packageName);
            }
        }
        return packages;
    }

    private void addIfBrowser(Set<String> packages, Uri uri, @Nullable String packageName) {
        if (packageName == null || packageName.equals(getPackageName())) {
            return;
        }
        Intent explicitIntent = browserIntent(uri).setPackage(packageName);
        ResolveInfo resolved = resolveBrowser(getPackageManager(), explicitIntent);
        if (resolved != null && resolved.activityInfo != null
                && packageName.equals(resolved.activityInfo.packageName)) {
            packages.add(packageName);
        }
    }

    private static Intent browserIntent(Uri uri) {
        return new Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE);
    }

    private static List<ResolveInfo> queryBrowsers(PackageManager manager, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return manager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY)
            );
        }
        return queryBrowsersLegacy(manager, intent);
    }

    @SuppressWarnings("deprecation")
    private static List<ResolveInfo> queryBrowsersLegacy(PackageManager manager, Intent intent) {
        return manager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
    }

    @Nullable
    private static ResolveInfo resolveBrowser(PackageManager manager, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return manager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY)
            );
        }
        return resolveBrowserLegacy(manager, intent);
    }

    @SuppressWarnings("deprecation")
    @Nullable
    private static ResolveInfo resolveBrowserLegacy(PackageManager manager, Intent intent) {
        return manager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
    }
}
