package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(LauncherIcon.DEFAULT);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.mipmap.ic_launcher_ax, R.mipmap.icon_background_ax, R.string.AppIconDefault),
        GOOGLE("GoogleIcon", R.mipmap.icon_background_google, R.drawable.ic_launcher_ax_google_foreground, R.string.AppIconGoogle),
        COLORFUL("ColorfulIcon", R.mipmap.icon_background_colorful, R.drawable.ic_launcher_ax_colorful_foreground, R.string.AppIconColorful),
        DARKGREEN("DarkGreenIcon", R.mipmap.icon_background_darkgreen, R.drawable.ic_launcher_ax_darkgreen_foreground, R.string.AppIconDarkGreen),
        NEON("NeonIcon", R.mipmap.icon_background_neon, R.drawable.ic_launcher_ax_neon_foreground, R.string.AppIconNeon),
        NIELLO("NielloIcon", R.drawable.ic_launcher_ax_round_niello_background, R.drawable.ic_launcher_ax_round_niello_foreground, R.string.AppIconNiello),
        BLUE("BlueIcon", R.color.ax_block_round_background, R.drawable.ic_launcher_ax_block_round_foreground, R.string.AppIconBlue),
        DARKBLUE("DarkBlueIcon", R.color.ax_round_blue_background, R.drawable.ic_launcher_ax_round_blue_foreground, R.string.AppIconDarkBlue),
        BLURBLUE("BlurBlueIcon", R.drawable.ic_launcher_ax_blue_background, R.drawable.ic_launcher_ax_blue_foreground, R.string.AppIconBlurBlue),
        NOX("PremiumIcon", R.drawable.icon_3_background_sa, R.mipmap.icon_3_foreground_sa, R.string.AppIconPremium);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
        }

        public boolean isAx() {
            return this == DEFAULT;
        }
    }
}
