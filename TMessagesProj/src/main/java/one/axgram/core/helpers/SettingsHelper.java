package one.axgram.core.helpers;

import static org.telegram.ui.ProfileActivity.sendLogs;

import android.app.Activity;
import android.net.Uri;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.Map;

import one.axgram.core.ChatHistoryActivity;
import one.axgram.core.settings.BaseAxSettingsActivity;
import one.axgram.core.settings.BaseAxLegacySettingsActivity;
import one.axgram.core.settings.AxChatSettingsActivity;
import one.axgram.core.settings.AxAccountSettingsActivity;
import one.axgram.core.settings.AxEmojiSettingsActivity;
import one.axgram.core.settings.AxExperimentalSettingsActivity;
import one.axgram.core.settings.AxGeneralSettingsActivity;
import one.axgram.core.settings.AxPasscodeSettingsActivity;
import one.axgram.core.settings.AxSettingsActivity;
import one.axgram.extra.network.NetworkLogActivity;
import one.axgram.core.settings.AxDebugSettingsActivity;

public class SettingsHelper {

    public static void processDeepLink(Activity activity, Uri uri, Callback callback, Runnable unknown) {
        if (uri == null) {
            unknown.run();
            return;
        }
        var segments = uri.getPathSegments();
        if (segments.isEmpty() || segments.size() > 2 || !"axsettings".equals(segments.get(0))) {
            unknown.run();
            return;
        }
        BaseFragment fragment;
        BaseAxSettingsActivity neko_fragment = null;
        BaseAxLegacySettingsActivity axFragment = null;
        if (segments.size() == 1) {
            fragment = new AxSettingsActivity();
        } else if (PasscodeHelper.getSettingsKey().equals(segments.get(1))) {
            fragment = neko_fragment = new AxPasscodeSettingsActivity();
        } else {
            switch (segments.get(1)) {
                case "a":
                case "account":
                    fragment = axFragment = new AxAccountSettingsActivity();
                    break;
                case "about":
                    fragment = neko_fragment = new AxSettingsActivity();
                    break;
                case "chat":
                case "chats":
                case "c":
                    fragment = axFragment = new AxChatSettingsActivity();
                    break;
                case "debug":
                case "d":
                    fragment = axFragment = new AxDebugSettingsActivity();
                    break;
                case "experimental":
                case "e":
                    fragment = axFragment = new AxExperimentalSettingsActivity();
                    break;
                case "emoji":
                    fragment = neko_fragment = new AxEmojiSettingsActivity();
                    break;
                case "general":
                case "g":
                    fragment = axFragment = new AxGeneralSettingsActivity();
                    break;
                case "network_logs":
                case "n":
                    fragment = new NetworkLogActivity();
                    break;
                case "recent_dialogs":
                case "r":
                    fragment = new ChatHistoryActivity();
                    break;
                case "send_logs":
                    sendLogs(activity, false);
                    return;
                default:
                    unknown.run();
                    return;
            }
        }
        callback.presentFragment(fragment);
        var row = uri.getQueryParameter("r");
        if (TextUtils.isEmpty(row)) {
            row = uri.getQueryParameter("row");
        }
        var value = uri.getQueryParameter("v");
        if (TextUtils.isEmpty(value)) {
            value = uri.getQueryParameter("value");
        }
        if (!TextUtils.isEmpty(row)) {
            var rowFinal = row;
            if (neko_fragment != null) {
                BaseAxSettingsActivity finalNeko_fragment = neko_fragment;
                AndroidUtilities.runOnUIThread(() -> finalNeko_fragment.scrollToRow(rowFinal, unknown));
            } else if (axFragment != null) {
                BaseAxLegacySettingsActivity finalAxFragment = axFragment;
                if (!TextUtils.isEmpty(value)) {
                    String finalValue = value;
                    AndroidUtilities.runOnUIThread(() -> finalAxFragment.importToRow(rowFinal, finalValue, unknown));
                } else {
                    AndroidUtilities.runOnUIThread(() -> finalAxFragment.scrollToRow(rowFinal, unknown));
                }
            }
        }
    }

    public interface Callback {
        void presentFragment(BaseFragment fragment);
    }

    public static ArrayList<SettingsSearchResult> onCreateSearchArray(Callback callback) {
        ArrayList<SettingsSearchResult> items = new ArrayList<>();
        ArrayList<BaseAxLegacySettingsActivity> fragments = new ArrayList<>();
        fragments.add(new AxGeneralSettingsActivity());
        fragments.add(new AxChatSettingsActivity());
        fragments.add(new AxExperimentalSettingsActivity());
        fragments.add(new AxDebugSettingsActivity());
        String n_title = LocaleController.getString(R.string.N_Config);
        for (BaseAxLegacySettingsActivity fragment: fragments) {
            int uid = fragment.getBaseGuid();
            int drawable = fragment.getDrawable();
            String f_title = fragment.getTitle();
            for (Map.Entry<Integer, String> entry : fragment.getRowMapReverse().entrySet()) {
                Integer i = entry.getKey();
                String key = entry.getValue();
                if (key.equals(String.valueOf(i))) {
                    continue;
                }
                int guid = uid + i;
                String title = LocaleController.getString(key);
                if (title == null || title.isEmpty()) {
                    continue;
                }
                Runnable open = () -> {
                    try {
                        BaseAxLegacySettingsActivity f = fragment.getClass().getDeclaredConstructor().newInstance();
                        callback.presentFragment(f);
                        AndroidUtilities.runOnUIThread(() -> f.scrollToRow(key, null));
                    } catch (Exception ignored) {}
                };
                SettingsSearchResult result = new SettingsSearchResult(
                        guid, title, n_title, f_title, drawable, open
                );
                items.add(result);
            }
        }
        return items;
    }
}
