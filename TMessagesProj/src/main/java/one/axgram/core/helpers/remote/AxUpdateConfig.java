package one.axgram.core.helpers.remote;

import android.text.TextUtils;

/**
 * Источники обновлений AxGram.
 *
 * Проверка идёт в два захода: сначала GitHub Releases, затем — если там пусто
 * или GitHub недоступен — телеграм-канал. Канал важен там, где GitHub закрыт
 * без VPN: Telegram в таких условиях обычно работает.
 *
 * Оба источника необязательны. Пустое значение = источник выключен.
 */
public class AxUpdateConfig {

    /** Владелец репозитория на GitHub. */
    public static final String GITHUB_OWNER = "aevornyx";

    /** Имя репозитория. Должно совпадать с тем, куда публикуются релизы. */
    public static final String GITHUB_REPO = "axgram-android";

    /**
     * Канал с метаданными обновлений — БЕЗ символа @.
     *
     * Пока не заведён, оставить пустым: запасной источник просто не используется,
     * приложение никуда не ходит. Формат сообщений в канале описан в
     * {@link ChannelUpdateSource}.
     */
    public static final String UPDATE_CHANNEL = "axgram_updates";

    /**
     * Бот поддержки — юзернейм БЕЗ символа @.
     *
     * Пока пусто, строка «Поддержать автора» в настройках не показывается
     * и приложение никуда не ходит.
     */
    public static final String SUPPORT_BOT = "axgram_support_bot";

    public static boolean supportEnabled() {
        return !TextUtils.isEmpty(SUPPORT_BOT);
    }

    public static boolean githubEnabled() {
        return !TextUtils.isEmpty(GITHUB_OWNER) && !TextUtils.isEmpty(GITHUB_REPO);
    }

    public static boolean channelEnabled() {
        return !TextUtils.isEmpty(UPDATE_CHANNEL);
    }

    public static String releasesApiUrl() {
        return "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    }

    public static String releasesPageUrl() {
        return "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    }
}
