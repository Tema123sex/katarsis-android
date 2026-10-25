package one.katarsis.core.helpers;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

import one.katarsis.core.helpers.remote.AxUpdateConfig;
import one.katarsis.core.utils.UpdateUtil;

/**
 * Ненавязчивое напоминание о канале проекта и боте поддержки.
 *
 * Показывается плашкой снизу — её видно, но она не перекрывает работу и
 * убирается смахиванием. Намеренно не диалог: всплывающее окно посреди
 * работы раздражает даже раз в неделю.
 *
 * Правила показа:
 * <ul>
 *   <li>не раньше чем через {@link #FIRST_DELAY_DAYS} дней после установки —
 *       встречать нового пользователя рекламой не стоит;</li>
 *   <li>не чаще одного раза в {@link #INTERVAL_DAYS} дня;</li>
 *   <li>напоминания чередуются, чтобы не повторять одно и то же;</li>
 *   <li>кнопка «Больше не показывать» отключает их навсегда.</li>
 * </ul>
 */
public class AxPromoHelper {

    private static final String PREFS = "axpromo";
    private static final String KEY_DISABLED = "disabled";
    private static final String KEY_LAST_SHOWN = "last_shown";
    private static final String KEY_FIRST_RUN = "first_run";
    private static final String KEY_NEXT_KIND = "next_kind";

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long INTERVAL_DAYS = 3;
    private static final long FIRST_DELAY_DAYS = 3;

    private static final int KIND_CHANNEL = 0;
    private static final int KIND_SUPPORT = 1;
    private static final int KIND_COUNT = 2;

    private static boolean shownThisSession = false;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }

    /** Отключить напоминания навсегда. */
    public static void disable() {
        prefs().edit().putBoolean(KEY_DISABLED, true).apply();
    }

    public static boolean isDisabled() {
        return prefs().getBoolean(KEY_DISABLED, false)
                || !one.katarsis.core.AxCoreConfig.showAxPromo.Bool();
    }

    /**
     * Показывает напоминание, если пришло время. Вызывать при открытии списка
     * чатов: за сессию покажется не более одного раза.
     */
    public static void maybeShow(BaseFragment fragment) {
        if (fragment == null || shownThisSession || isDisabled()) {
            return;
        }
        final SharedPreferences prefs = prefs();
        final long now = System.currentTimeMillis();

        long firstRun = prefs.getLong(KEY_FIRST_RUN, 0);
        if (firstRun == 0) {
            prefs.edit().putLong(KEY_FIRST_RUN, now).apply();
            return;
        }
        if (now - firstRun < FIRST_DELAY_DAYS * DAY) {
            return;
        }
        if (now - prefs.getLong(KEY_LAST_SHOWN, 0) < INTERVAL_DAYS * DAY) {
            return;
        }

        int kind = prefs.getInt(KEY_NEXT_KIND, KIND_CHANNEL);
        // если бот не задан — показывать про него нечего
        if (kind == KIND_SUPPORT && !AxUpdateConfig.supportEnabled()) {
            kind = KIND_CHANNEL;
        }
        if (!show(fragment, kind)) {
            return;
        }
        shownThisSession = true;
        prefs.edit()
                .putLong(KEY_LAST_SHOWN, now)
                .putInt(KEY_NEXT_KIND, (kind + 1) % KIND_COUNT)
                .apply();
    }

    private static boolean show(BaseFragment fragment, int kind) {
        final CharSequence title, subtitle, button;
        final Runnable action;

        if (kind == KIND_SUPPORT && AxUpdateConfig.supportEnabled()) {
            title = LocaleController.getString(R.string.AxPromoSupportTitle);
            subtitle = LocaleController.getString(R.string.AxPromoSupportText);
            button = LocaleController.getString(R.string.AxPromoOpen);
            action = () -> MessagesController.getInstance(fragment.getCurrentAccount())
                    .openByUserName(AxUpdateConfig.SUPPORT_BOT, fragment, 1);
        } else {
            title = LocaleController.getString(R.string.AxPromoChannelTitle);
            subtitle = LocaleController.getString(R.string.AxPromoChannelText);
            button = LocaleController.getString(R.string.AxPromoOpen);
            action = () -> MessagesController.getInstance(fragment.getCurrentAccount())
                    .openByUserName(UpdateUtil.channelUsername, fragment, 1);
        }

        try {
            BulletinFactory.of(fragment)
                    .createSimpleBulletin(R.raw.chats_infotip, title, subtitle, button, action)
                    .show();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
