package one.katarsis.core.helpers;

import android.app.Application;

import org.telegram.messenger.FileLog;

/**
 * Katarsis: заглушка вместо отправки отчётов о падениях.
 *
 * В исходном проекте здесь поднимался Sentry и слал события на чужой DSN
 * (o416616.ingest.us.sentry.io) — падения, ANR-дампы трассировок потоков и
 * даже скриншоты экрана в момент сбоя. Ни SDK, ни плагин в сборку больше не
 * входят, поэтому от класса остались только сигнатуры: вызывающий код менять
 * не нужно, а наружу ничего не уходит.
 *
 * Исключения по-прежнему видны — они пишутся в локальный лог приложения.
 */
public class AnalyticsHelper {

    public static boolean loaded = false;

    public static void start(Application application) {
        // ничего не поднимаем
    }

    public static void captureException(Throwable e) {
        // Раньше улетало в Sentry. Теперь остаётся на устройстве: лог читается
        // из самого приложения, наружу не передаётся.
        FileLog.e(e);
    }

    public static boolean getSentryStatus(Application application) {
        return false;
    }
}
