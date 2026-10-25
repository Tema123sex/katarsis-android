package one.katarsis.core.helpers.remote;

import android.os.Build;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Проверка обновлений через GitHub Releases.
 *
 * Запрашивает последний релиз, сравнивает код версии с текущим и, если он выше,
 * отдаёт ссылку на APK под ABI устройства. Установка не автоматическая:
 * открывается страница загрузки, файл ставит сам пользователь.
 *
 * Номер версии берётся из имени файла APK — оно формируется сборкой как
 * Katarsis-v12.7.3(1238)-arm64-v8a.apk, где в скобках код версии.
 */
public class GithubUpdateSource {

    private static final int TIMEOUT_MS = 15_000;
    private static final int MAX_BODY_BYTES = 512 * 1024;

    /**
     * Katarsis-v<версия>(<код>)-<abi>[-debug].apk
     *
     * Разделителем может быть скобка, точка или дефис: GitHub запрещает
     * скобки в именах файлов релиза и заменяет их точками, поэтому
     * Katarsis-v12.7.3(1238)-arm64-v8a.apk приезжает как
     * Katarsis-v12.7.3.1238.-arm64-v8a.apk.
     */
    private static final Pattern APK_NAME = Pattern.compile(
            "^Katarsis-v(\\d+\\.\\d+(?:\\.\\d+)?)[.(_-]+(\\d+)[.)_-]+(.+?)(?:-debug)?\\.apk$");
    private static final DispatchQueue queue = new DispatchQueue("AxUpdateCheck");

    public interface Callback {
        /** update == null означает «обновлений нет»; error != null — не удалось проверить. */
        void onResult(TLRPC.TL_help_appUpdate update, String error);
    }

    public static void check(Callback callback) {
        if (!AxUpdateConfig.githubEnabled()) {
            callback.onResult(null, null);
            return;
        }
        queue.postRunnable(() -> {
            try {
                callback.onResult(parse(fetch(AxUpdateConfig.releasesApiUrl())), null);
            } catch (Exception e) {
                FileLog.e("Katarsis update: GitHub недоступен", e);
                callback.onResult(null, e.getMessage() != null ? e.getMessage() : "network error");
            }
        });
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "Katarsis/" + BuildConfig.VERSION_NAME);
            int code = conn.getResponseCode();
            if (code == 404) {
                // релизов ещё нет — это не ошибка
                return null;
            }
            if (code != 200) {
                throw new IllegalStateException("HTTP " + code);
            }
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n, total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_BODY_BYTES) {
                        throw new IllegalStateException("ответ слишком большой");
                    }
                    out.write(buf, 0, n);
                }
                return out.toString("UTF-8");
            }
        } finally {
            conn.disconnect();
        }
    }

    private static TLRPC.TL_help_appUpdate parse(String json) throws Exception {
        if (TextUtils.isEmpty(json)) {
            return null;
        }
        JSONObject release = new JSONObject(json);
        if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
            return null;
        }

        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) {
            return null;
        }

        String bestUrl = null, bestVersion = null;
        int bestCode = -1;
        String[] abis = Build.SUPPORTED_ABIS;

        // среди APK ищем тот, чей ABI устройство поддерживает лучше всего
        int bestAbiRank = Integer.MAX_VALUE;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            Matcher m = APK_NAME.matcher(asset.optString("name", ""));
            if (!m.matches()) continue;

            int rank = abiRank(m.group(3), abis);
            if (rank == Integer.MAX_VALUE) continue;

            int code = Integer.parseInt(m.group(2));
            if (rank < bestAbiRank || (rank == bestAbiRank && code > bestCode)) {
                bestAbiRank = rank;
                bestCode = code;
                bestVersion = m.group(1);
                bestUrl = asset.optString("browser_download_url", null);
            }
        }

        if (bestUrl == null || bestCode <= BuildConfig.VERSION_CODE) {
            return null;
        }

        TLRPC.TL_help_appUpdate update = new TLRPC.TL_help_appUpdate();
        update.version = bestVersion;
        update.can_not_skip = false;
        update.url = bestUrl;
        update.flags |= 4;

        String notes = release.optString("body", "");
        update.text = TextUtils.isEmpty(notes)
                ? ("Katarsis " + bestVersion)
                : notes.trim();
        return update;
    }

    /** Чем меньше число, тем предпочтительнее ABI для этого устройства. */
    private static int abiRank(String abi, String[] supported) {
        for (int i = 0; i < supported.length; i++) {
            if (supported[i].equals(abi)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }
}
