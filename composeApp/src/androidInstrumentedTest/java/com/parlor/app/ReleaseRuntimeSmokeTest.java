package com.parlor.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.test.InstrumentationTestCase;

/** Release runtime checks kept on platform APIs so the test APK needs no Maven dependencies. */
@SuppressWarnings("deprecation")
public final class ReleaseRuntimeSmokeTest extends InstrumentationTestCase {
    private static final String STORE_APPLICATION_ID = "com.parlor.app";
    private static final String CHANGE_WIFI_MULTICAST_STATE =
            "android.permission.CHANGE_WIFI_MULTICAST_STATE";

    public void testReleaseBuildLaunchesCanonicalActivityWithoutDebuggableFlag() {
        Context context = getInstrumentation().getTargetContext();
        assertEquals(STORE_APPLICATION_ID, context.getPackageName());

        ApplicationInfo applicationInfo = context.getApplicationInfo();
        assertEquals(0, applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE);

        Intent intent = new Intent(Intent.ACTION_MAIN)
                .setClassName(STORE_APPLICATION_ID, STORE_APPLICATION_ID + ".MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = getInstrumentation().startActivitySync(intent);
        try {
            getInstrumentation().waitForIdleSync();
            assertFalse(activity.isFinishing());
            assertFalse(activity.isDestroyed());
        } finally {
            activity.finish();
            getInstrumentation().waitForIdleSync();
        }
    }

    public void testReleaseBuildCanAcquireDeclaredMulticastLock() {
        Context context = getInstrumentation().getTargetContext();
        assertEquals(
                PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(CHANGE_WIFI_MULTICAST_STATE)
        );

        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        assertNotNull(wifiManager);
        WifiManager.MulticastLock multicastLock =
                wifiManager.createMulticastLock("parlor-release-runtime-smoke");
        multicastLock.setReferenceCounted(false);
        try {
            multicastLock.acquire();
            assertTrue(multicastLock.isHeld());
        } finally {
            if (multicastLock.isHeld()) {
                multicastLock.release();
            }
        }
        assertFalse(multicastLock.isHeld());
    }
}
