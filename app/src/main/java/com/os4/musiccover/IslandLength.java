// SPDX-License-Identifier: Apache-2.0
package com.os4.musiccover;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The length of the Xiaomi super island - the pill at the top of the screen that shows what is
 * playing, drawn by the control centre plugin (miui.systemui.plugin) inside SystemUI's process.
 *
 * The island is three boxes in a row: an area to the left of the camera cutout, the cutout
 * itself, and an area to its right. The plugin measures the two areas' contents and then asks
 * DynamicIslandContentViewPhoneHelper / …PadHelper for the island's size, which answers
 *
 *     width = clamp(cutout + 2 * max(leftArea, rightArea), bigIslandMinWidth, maxWidth)
 *     x     = (screenWidth - width) / 2
 *     left  = right = (width - cutout) / 2      (when the width is not the content's own)
 *
 * so the length is the content's, capped at what the status bar's clock and battery leave. This
 * hands the helper's own answer back with the user's length in it instead: same cutout, same
 * centring, same two areas - only longer or shorter than the content asked for. maxWidth is
 * deliberately not consulted: it is the system's guess at how far the island may reach, and the
 * number the user set is the answer to that question. The screen's own width is still the
 * ceiling, and the plugin's floor (108dp on this build) is still the floor.
 *
 * The class this hooks is in the plugin's own loader, which is built inside SystemUI long after
 * the module's hooks go in - the same problem HyperTweaks.watchForPlugin solves for the plugin's
 * theme, and why this is installed from that watch rather than at startup. Nothing here is
 * load-bearing for anything else: every failure is logged and leaves the island exactly as the
 * system drew it.
 */
final class IslandLength {

    private IslandLength() {
    }

    private static final String TAG = "MCMini: ";

    /** The island's content view, which is also the thing that re-measures. */
    private static final String CLS_CONTENT = "miui.systemui.dynamicisland.window.content"
            + ".DynamicIslandContentView";
    private static final String CLS_PHONE_HELPER =
            "miui.systemui.dynamicisland.window.content.helpers"
                    + ".DynamicIslandContentViewPhoneHelper";
    private static final String CLS_PAD_HELPER =
            "miui.systemui.dynamicisland.window.content.helpers"
                    + ".DynamicIslandContentViewPadHelper";
    private static final String CLS_RESULT =
            "miui.systemui.dynamicisland.model.IslandContentViewCalculationResult";

    private static volatile boolean sInstalled;
    private static volatile String sWhy = "the plugin's island classes have not been seen yet";

    /** The result of the calculation, rebuilt rather than mutated: it is a Kotlin data class. */
    private static Constructor<?> sResultCtor;
    /** DynamicIslandContentView.updateBigIslandViewWidth, the one way to make it re-measure. */
    private static Method sRemeasure;

    /**
     * Every island content view seen since the plugin loaded, weakly: a length change re-measures
     * the ones that still exist, and the plugin's own weak handling of its windows decides when
     * one is gone for good.
     */
    private static final List<WeakReference<Object>> sContents = new CopyOnWriteArrayList<>();

    static boolean installed() {
        return sInstalled;
    }

    static String why() {
        return sWhy;
    }

    /**
     * Tries to install on whatever class loader just appeared. Called for every new loader in the
     * process - BaseDexClassLoader's constructor is not a hot path - and returns quietly when the
     * loader is not the plugin's.
     */
    static void install(ClassLoader cl) {
        if (sInstalled || cl == null) return;
        Class<?> content;
        try {
            content = Xp.findClass(CLS_CONTENT, cl);
        } catch (Throwable notThePlugin) {
            return;
        }
        try {
            Class<?> result = Xp.findClass(CLS_RESULT, cl);
            Constructor<?> ctor = result.getDeclaredConstructor(int.class, int.class, int.class,
                    int.class, int.class, int.class, int.class, int.class, int.class);
            ctor.setAccessible(true);
            sResultCtor = ctor;
            Method remeasure = content.getDeclaredMethod("updateBigIslandViewWidth");
            remeasure.setAccessible(true);
            sRemeasure = remeasure;
            Xp.hookAll(content, "updateBigIslandViewWidth", chain -> {
                remember(chain.getThisObject());
                return chain.proceed();
            });
            for (String name : new String[]{CLS_PHONE_HELPER, CLS_PAD_HELPER}) {
                Class<?> helper;
                try {
                    helper = Xp.findClass(name, cl);
                } catch (Throwable absent) {
                    // A build with only one of the two laid out - the other is not the target.
                    continue;
                }
                // After the original, because the system's answer is what is being corrected -
                // the width it computed, the cutout it used and the small-island numbers that
                // belong to the other arrangement all come from it.
                Xp.hookAll(helper, "calculateBigIslandWidth", chain -> apply(
                        chain.getThisObject(), chain.getArgs().isEmpty() ? null
                                : chain.getArgs().get(0), chain.proceed()));
            }
            sInstalled = true;
            Xp.log(TAG + "super island length hooked");
        } catch (Throwable t) {
            sWhy = String.valueOf(t);
            Xp.log(TAG + "super island length hook failed: " + t);
        }
    }

    /**
     * The helper's answer, with the user's length in place of the content's - or the answer
     * itself while the setting is off, the plugin has not been hooked, or its own numbers cannot
     * be read on this build.
     */
    private static Object apply(Object helper, Object params, Object result) {
        int want = Main.islandLengthPx();
        if (params == null || result == null || sResultCtor == null) return result;
        try {
            int screen = ((Number) Xp.callMethod(params, "getScreenWidth")).intValue();
            int cutout = ((Number) Xp.callMethod(params, "getCutoutWidth")).intValue();
            int min = ((Number) Xp.callMethod(helper, "getBigIslandMinWidth")).intValue();
            Main.noteIslandMinPx(min);
            int was = ((Number) Xp.callMethod(result, "getBigIslandViewWidth")).intValue();
            if (want <= 0) {
                // The setting is off: the system's own answer stands, and this is the one place
                // it can be read - it is a function of the content, not a constant.
                Main.noteIslandSystemPx(was);
                return result;
            }
            int width = Math.max(Math.max(min, 1), Math.min(want, screen));
            if (width == was) return result;
            int side = Math.max(0, (width - cutout) / 2);
            // The small-island arrangement is left as the system computed it: there the island
            // shares the row with a second island, and its width is what keeps the two apart.
            return sResultCtor.newInstance(width, side, side, (screen - width) / 2,
                    Xp.callMethod(result, "getBigIslandMarginWidth"),
                    Xp.callMethod(result, "getBigIslandViewWidthHasSmallIsland"),
                    Xp.callMethod(result, "getBigIslandLeftWidthHasSmallIsland"),
                    Xp.callMethod(result, "getBigIslandRightWidthHasSmallIsland"),
                    Xp.callMethod(result, "getBigIslandXHasSmallIsland"));
        } catch (Throwable t) {
            sWhy = String.valueOf(t);
            Xp.log(TAG + "super island length not applied: " + t);
            return result;
        }
    }

    /** The island is on screen while the setting changes: re-measure it rather than wait. */
    static void changed() {
        Method remeasure = sRemeasure;
        if (remeasure == null) return;
        for (WeakReference<Object> ref : sContents) {
            Object view = ref.get();
            if (view == null) continue;
            try {
                remeasure.invoke(view);
            } catch (Throwable t) {
                Xp.log(TAG + "super island re-measure failed: " + t);
            }
        }
    }

    /** One content view, kept once: updateBigIslandViewWidth is called on every scene change. */
    private static void remember(Object view) {
        for (WeakReference<Object> ref : sContents) {
            if (ref.get() == view) return;
        }
        sContents.add(new WeakReference<>(view));
    }
}
