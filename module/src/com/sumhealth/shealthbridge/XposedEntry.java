package com.sumhealth.shealthbridge;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * LSPosed entry point.
 *
 * Runs inside every process of Samsung Health and installs the hooks that let the
 * China-only partner app com.samsung.android.swsportplugin ("健康服务") use the
 * legacy Health SDK data path again on the international 7.x build.
 */
public class XposedEntry implements IXposedHookLoadPackage {

    static final String HOST = "com.sec.android.app.shealth";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || !HOST.equals(lpparam.packageName)) {
            return;
        }
        try {
            PolicyHooks.log("loaded in process " + lpparam.processName
                    + " (firstApplication=" + lpparam.isFirstApplication + ")");
            PolicyHooks.install(lpparam.classLoader);
        } catch (Throwable t) {
            PolicyHooks.logError("install failed", t);
        }
    }
}
