package com.sumhealth.shealthbridge;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * SH Bridge — releases Samsung Health's Health-SDK partner gate so the China-only companion app
 * com.samsung.android.swsportplugin ("健康服务") can read and write health data again on the
 * international Samsung Health 7.x build.
 *
 * Verified on Samsung Health 7.2.0.041 (intl) with LSPosed 7912 (API 102).
 *
 * Why the gate blocks the partner:
 *   SdkPolicyManager.validateCallerSignature(pkg)   throws SecurityException unless app_info holds
 *                                                   a matching signature fingerprint for the package
 *   SdkPolicyManager.isPermitted(pkg, type, op, w)  false unless sdk_policy grants that data type
 *   both tables are provisioned from Samsung's server, and the CN partner is not part of the
 *   international partner programme.
 *   The consent layer (AbstractUserPermissionManager.isPermitted / isPermissionRegistered) is gated
 *   separately and must be released too, otherwise the app reports "permission not granted".
 *
 * Design notes:
 *   - Hooks are installed reflectively: the framework relocates the Xposed API into a randomized
 *     package and its method descriptors do not always match the published API, so the hook entry
 *     point (hookAllMethods / hookMethod / findAndHookMethod) is discovered at runtime.
 *   - Everything fails soft: a missing class or method is logged and skipped, the host process is
 *     never crashed.
 *   - Logging is bounded so a long session cannot flood logcat.
 */
public final class PolicyHooks {

    /** Release build: keep a short audit trail, then stay quiet. */
    private static final int LOG_BUDGET = 24;

    private static final String POLICY_MANAGER =
            "com.samsung.android.service.health.sdkpolicy.SdkPolicyManager";
    private static final String PERMISSION_MANAGER =
            "com.samsung.android.service.health.permission.AbstractUserPermissionManager";

    private static int logCount = 0;

    private static Method hookAllMethodsMethod;
    private static Method hookMethodMethod;
    private static Method findAndHookMethod;

    private PolicyHooks() {
    }

    // ---------------------------------------------------------------- logging

    static synchronized void log(String msg) {
        if (logCount >= LOG_BUDGET) {
            return;
        }
        logCount++;
        logAlways(msg);
    }

    static void logAlways(String msg) {
        try {
            XposedBridge.log("[SHBridge] " + msg);
        } catch (Throwable ignored) {
            // logging must never break the host
        }
    }

    static void logError(String msg, Throwable t) {
        try {
            XposedBridge.log("[SHBridge] " + msg + ": " + t);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------- realm entry

    public static void install(ClassLoader cl) {
        detectApi();
        hookPolicyManager(cl);
        hookPermissionManager(cl);
    }

    /** Finds whichever Xposed hook API this framework actually exposes. */
    private static void detectApi() {
        if (hookAllMethodsMethod != null || hookMethodMethod != null || findAndHookMethod != null) {
            return;
        }
        Class<?> bridge = XposedBridge.class;
        try {
            hookAllMethodsMethod = bridge.getMethod("hookAllMethods", Class.class, String.class,
                    XC_MethodHook.class);
        } catch (Throwable ignored) {
        }
        try {
            hookMethodMethod = bridge.getMethod("hookMethod", Member.class, XC_MethodHook.class);
        } catch (Throwable ignored) {
        }
        try {
            findAndHookMethod = XposedHelpers.class.getMethod("findAndHookMethod", Class.class,
                    String.class, Object[].class);
        } catch (Throwable ignored) {
        }
        if (hookAllMethodsMethod == null && hookMethodMethod == null && findAndHookMethod == null) {
            logAlways("no supported hook API; module inactive");
        }
    }

    // ---------------------------------------------------------------- targets

    private static void hookPolicyManager(ClassLoader cl) {
        Class<?> cls = findClass(POLICY_MANAGER, cl);
        if (cls == null) {
            logAlways("SdkPolicyManager not found (unsupported Samsung Health build)");
            return;
        }

        // Validate the caller's registration/signature: always release it.
        hook(cls, "validateCallerSignature", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                log("allow validateCallerSignature(" + str(param.args, 0) + ")");
                param.setResult(null);
            }
        });

        // Per data-type policy check (read / write / web).
        hook(cls, "isPermitted", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                log("allow isPermitted(" + str(param.args, 0) + ", " + str(param.args, 1)
                        + ", " + str(param.args, 2) + ", write=" + str(param.args, 3) + ")");
                param.setResult(Boolean.TRUE);
            }
        });

        // Backbone of isPermitted(); private, matched by name.
        hook(cls, "hasPermission", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(Boolean.TRUE);
            }
        });

        // Hardcoded Samsung-app allowlist (does not contain the CN partner).
        hook(cls, "isPreGrantedApp", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(Boolean.TRUE);
            }
        });
    }

    private static void hookPermissionManager(ClassLoader cl) {
        Class<?> cls = findClass(PERMISSION_MANAGER, cl);
        if (cls == null) {
            logAlways("AbstractUserPermissionManager not found");
            return;
        }

        // Consent check used by every data request.
        hook(cls, "isPermitted", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                log("allow consent isPermitted(" + str(param.args, 0) + ")");
                param.setResult(Boolean.TRUE);
            }
        });

        // Backing query of HealthPermissionManager#isPermissionAcquired.
        hook(cls, "isPermissionRegistered", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(Boolean.TRUE);
            }
        });
    }

    // ------------------------------------------------------------------ utils

    /** Hooks every overload of {@code name} with whichever API was detected. */
    private static void hook(Class<?> cls, String name, XC_MethodHook callback) {
        int found = 0;
        int hooked = 0;
        for (Method m : cls.getDeclaredMethods()) {
            if (!m.getName().equals(name)) {
                continue;
            }
            found++;
            if (hookOne(cls, m, callback)) {
                hooked++;
            }
        }
        if (found == 0) {
            logAlways("method not found: " + cls.getSimpleName() + "." + name);
        } else {
            logAlways("hooked " + cls.getSimpleName() + "." + name + " " + hooked + "/" + found);
        }
    }

    private static boolean hookOne(Class<?> cls, Method target, XC_MethodHook callback) {
        try {
            if (hookAllMethodsMethod != null) {
                hookAllMethodsMethod.invoke(null, cls, target.getName(), callback);
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (hookMethodMethod != null) {
                target.setAccessible(true);
                hookMethodMethod.invoke(null, target, callback);
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (findAndHookMethod != null) {
                Class<?>[] params = target.getParameterTypes();
                Object[] args = new Object[params.length + 1];
                for (int i = 0; i < params.length; i++) {
                    args[i] = params[i];
                }
                args[params.length] = callback;
                findAndHookMethod.invoke(null, cls, target.getName(), args);
                return true;
            }
        } catch (Throwable t) {
            logError("hook " + target, t);
        }
        return false;
    }

    private static String str(Object[] args, int index) {
        if (args == null || args.length <= index || args[index] == null) {
            return "null";
        }
        return String.valueOf(args[index]);
    }

    private static Class<?> findClass(String name, ClassLoader cl) {
        try {
            return XposedHelpers.findClass(name, cl);
        } catch (Throwable t) {
            logAlways("findClass failed " + name + ": " + t);
            return null;
        }
    }
}
