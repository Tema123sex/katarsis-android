package one.katarsis.core;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.style.CharacterStyle;
import android.text.style.URLSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanUserMention;
import org.telegram.ui.PhotoViewer;

import java.util.ArrayList;
import java.util.List;

/**
 * Katarsis: архив удалённых и одноразовых сообщений.
 *
 * Экран двухуровневый. Без указания чата открывается список чатов — включая те,
 * которых в Telegram уже нет: название хранится вместе с сообщениями, поэтому
 * переписка остаётся доступной даже после удаления группы или канала.
 * Внутри чата сообщения показываются настоящими пузырями поверх обоев, с
 * разделителями дат, аватарами и именами отправителей — как в обычной переписке.
 */
public class AxDeletedMessagesActivity extends BaseFragment {

    private static final int MENU_CLEAR = 1;
    private static final int MENU_SEARCH = 2;
    private static final int MENU_OPEN_CHAT = 3;

    private RecyclerListView listView;
    private RecyclerView.Adapter adapter;
    private TextView emptyView;
    private ActionBarMenuItem searchItem;

    /** Все чаты с сохранёнными сообщениями. */
    private final ArrayList<AxDeletedMessages.DialogSummary> dialogs = new ArrayList<>();
    /** Они же после поиска — именно этот список показывается. */
    private final ArrayList<AxDeletedMessages.DialogSummary> shownDialogs = new ArrayList<>();
    /** Сообщения открытого чата, старые сверху. */
    private final ArrayList<MessageObject> messages = new ArrayList<>();
    /** Строки списка сообщений: MessageObject — пузырь, String — разделитель даты. */
    private final ArrayList<Object> items = new ArrayList<>();

    private final long filterDialogId; // 0 = список чатов
    private final boolean onceMode;    // true = одноразовые/исчезающие
    private final boolean dialogsMode;

    private String searchQuery;
    private String dialogTitle;
    private int totalCount;
    /** Показывать ли аватар и имя отправителя: в канале автор один, они лишние. */
    private boolean showSenders;
    /** Когда сообщение попало в архив, по его идентификатору. */
    private final android.util.SparseArray<Long> archivedAt = new android.util.SparseArray<>();

    // Провайдер, чтобы фото закрывалось анимацией обратно в пузырь, а не чёрным кадром.
    private final PhotoViewer.PhotoViewerProvider photoProvider = new PhotoViewer.EmptyPhotoViewerProvider() {
        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            if (listView == null || messageObject == null) return null;
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = listView.getChildAt(a);
                if (view instanceof ChatMessageCell) {
                    ChatMessageCell cell = (ChatMessageCell) view;
                    MessageObject message = cell.getMessageObject();
                    if (message != null && message.getId() == messageObject.getId()) {
                        ImageReceiver imageReceiver = cell.getPhotoImage();
                        int[] coords = new int[2];
                        view.getLocationInWindow(coords);
                        PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                        object.viewX = coords[0];
                        object.viewY = coords[1];
                        object.parentView = listView;
                        object.imageReceiver = imageReceiver;
                        object.thumb = imageReceiver.getBitmapSafe();
                        object.radius = imageReceiver.getRoundRadius(true);
                        return object;
                    }
                }
            }
            return null;
        }
    };

    public AxDeletedMessagesActivity() {
        this(0, false);
    }

    public AxDeletedMessagesActivity(long dialogId) {
        this(dialogId, false);
    }

    public AxDeletedMessagesActivity(long dialogId, boolean onceMode) {
        this.filterDialogId = dialogId;
        this.onceMode = onceMode;
        this.dialogsMode = dialogId == 0;
    }

    private String screenTitle() {
        return onceMode ? "Исчезающие сообщения" : "Удалённые сообщения";
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(screenTitle());
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_CLEAR) {
                    showClearConfirm();
                } else if (id == MENU_OPEN_CHAT) {
                    openRealChat();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(MENU_SEARCH, org.telegram.messenger.R.drawable.msg_search)
                .setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchCollapse() {
                        searchQuery = null;
                        rebuild();
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        searchQuery = editText.getText().toString().trim().toLowerCase();
                        rebuild();
                    }
                });
        searchItem.setSearchFieldHint(dialogsMode ? "Поиск по чатам" : "Поиск по сообщениям");
        if (!dialogsMode && dialogExists()) {
            menu.addItem(MENU_OPEN_CHAT, org.telegram.messenger.R.drawable.msg_openin);
        }
        menu.addItem(MENU_CLEAR, org.telegram.messenger.R.drawable.baseline_delete_24);

        // Обои по умолчанию обрезаются сверху на высоту экшн-бара и статус-бара —
        // в обычном чате эта полоса ничем не видна, а здесь оставалась чёрной.
        // Экран логов канала решает это так же.
        SizeNotifierFrameLayout frameLayout = new SizeNotifierFrameLayout(context) {
            @Override
            protected boolean isActionBarVisible() {
                return false;
            }

            @Override
            protected boolean isStatusBarVisible() {
                return false;
            }
        };
        fragmentView = frameLayout;
        if (dialogsMode) {
            frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        } else {
            // Внутри чата — те же обои, что и в обычной переписке. Если они ещё не
            // подгружены, остаётся обычный фон, а не пустота.
            frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            android.graphics.drawable.Drawable wallpaper = Theme.getCachedWallpaper();
            if (wallpaper != null) {
                frameLayout.setBackgroundImage(wallpaper, Theme.isWallpaperMotion());
            }
        }

        listView = new RecyclerListView(context);
        LinearLayoutManager lm = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        if (!dialogsMode) {
            lm.setStackFromEnd(true); // свежие снизу, как в переписке
        }
        listView.setLayoutManager(lm);
        listView.setVerticalScrollBarEnabled(false);
        adapter = dialogsMode ? new DialogsAdapter(context) : new MessagesAdapter(context);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            if (!dialogsMode || position < 0 || position >= shownDialogs.size()) return;
            AxDeletedMessages.DialogSummary d = shownDialogs.get(position);
            presentFragment(new AxDeletedMessagesActivity(d.dialogId, onceMode));
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (getParentActivity() == null) return false;
            if (dialogsMode) {
                if (position < 0 || position >= shownDialogs.size()) return false;
                showDialogActions(shownDialogs.get(position));
                return true;
            }
            if (position < 0 || position >= items.size()) return false;
            Object item = items.get(position);
            if (item instanceof MessageObject) {
                showMessageActions((MessageObject) item);
                return true;
            }
            return false;
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));

        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(AndroidUtilities.dp(28), 0, AndroidUtilities.dp(28), 0);
        emptyView.setVisibility(View.GONE);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        loadData();
        return fragmentView;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (actionBar != null && actionBar.isSearchFieldVisible()) {
            actionBar.closeSearchField();
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    // ------------------------------------------------------------------ данные

    private void loadData() {
        new Thread(() -> {
            final ArrayList<AxDeletedMessages.DialogSummary> loadedDialogs = new ArrayList<>();
            final ArrayList<MessageObject> loadedMessages = new ArrayList<>();
            final android.util.SparseArray<Long> loadedTimes = new android.util.SparseArray<>();
            String title = null;
            int total = 0;

            if (dialogsMode) {
                List<AxDeletedMessages.DialogSummary> summaries = onceMode
                        ? AxOnceMessages.getInstance().getDialogSummaries(currentAccount)
                        : AxDeletedMessages.getInstance().getDialogSummaries(currentAccount);
                for (AxDeletedMessages.DialogSummary d : summaries) {
                    if (d.title == null) {
                        d.title = liveTitle(d.dialogId);
                        if (d.title != null) {
                            // Записи из старых версий названия не хранили. Запоминаем
                            // сейчас, пока чат ещё жив.
                            if (onceMode) {
                                AxOnceMessages.getInstance().setDialogTitle(currentAccount, d.dialogId, d.title);
                            } else {
                                AxDeletedMessages.getInstance().setDialogTitle(currentAccount, d.dialogId, d.title);
                            }
                        }
                    }
                    total += d.count;
                    loadedDialogs.add(d);
                }
            } else if (onceMode) {
                for (AxOnceMessages.Entry e : AxOnceMessages.getInstance().getDialogEntries(currentAccount, filterDialogId)) {
                    MessageObject mo = build(e.message, false);
                    if (mo != null) {
                        loadedMessages.add(mo);
                        loadedTimes.put(mo.getId(), e.savedAt);
                    }
                }
                title = storedTitle(true);
            } else {
                for (AxDeletedMessages.Entry e : AxDeletedMessages.getInstance().getDialogEntries(currentAccount, filterDialogId)) {
                    MessageObject mo = build(e.message, true);
                    if (mo != null) {
                        loadedMessages.add(mo);
                        loadedTimes.put(mo.getId(), e.deletedAt);
                    }
                }
                title = storedTitle(false);
            }

            final boolean senders = !dialogsMode && shouldShowSenders(loadedMessages);
            final String finalTitle = title;
            final int finalTotal = total;
            AndroidUtilities.runOnUIThread(() -> {
                dialogs.clear();
                dialogs.addAll(loadedDialogs);
                messages.clear();
                messages.addAll(loadedMessages);
                archivedAt.clear();
                for (int i = 0; i < loadedTimes.size(); i++) {
                    archivedAt.put(loadedTimes.keyAt(i), loadedTimes.valueAt(i));
                }
                dialogTitle = finalTitle;
                showSenders = senders;
                totalCount = dialogsMode ? finalTotal : loadedMessages.size();
                updateHeader();
                rebuild();
            });
        }).start();
    }

    /**
     * В группе подписи и аватары нужны, в канале — нет: автор там один.
     * Если чат уже удалён и спросить не у кого, смотрим на самих отправителей.
     */
    private boolean shouldShowSenders(ArrayList<MessageObject> loaded) {
        if (filterDialogId >= 0) {
            return false;
        }
        try {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-filterDialogId);
            if (chat != null) {
                return !org.telegram.messenger.ChatObject.isChannelAndNotMegaGroup(chat);
            }
        } catch (Throwable ignore) {
        }
        java.util.HashSet<Long> senders = new java.util.HashSet<>();
        for (MessageObject mo : loaded) {
            senders.add(mo.getSenderId());
            if (senders.size() > 1) {
                return true;
            }
        }
        return false;
    }

    private MessageObject build(TLRPC.Message message, boolean markDeleted) {
        try {
            MessageObject mo = new MessageObject(currentAccount, message, true, true);
            if (markDeleted) {
                mo.axDeleted = true;
            }
            return mo;
        } catch (Throwable ignore) {
            return null;
        }
    }

    /** Название чата из Telegram — если он ещё существует. */
    private String liveTitle(long dialogId) {
        try {
            MessagesController controller = MessagesController.getInstance(currentAccount);
            if (dialogId > 0) {
                TLRPC.User user = controller.getUser(dialogId);
                if (user != null) return UserObject.getUserName(user);
            } else {
                TLRPC.Chat chat = controller.getChat(-dialogId);
                if (chat != null && chat.title != null) return chat.title;
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    /** Название открытого чата: живое, иначе сохранённое вместе с сообщениями. */
    private String storedTitle(boolean once) {
        String live = liveTitle(filterDialogId);
        if (live != null) return live;
        List<AxDeletedMessages.DialogSummary> summaries = once
                ? AxOnceMessages.getInstance().getDialogSummaries(currentAccount)
                : AxDeletedMessages.getInstance().getDialogSummaries(currentAccount);
        for (AxDeletedMessages.DialogSummary d : summaries) {
            if (d.dialogId == filterDialogId && d.title != null) return d.title;
        }
        return "Удалённый чат";
    }

    private boolean dialogExists() {
        return liveTitle(filterDialogId) != null;
    }

    private void updateHeader() {
        if (actionBar == null) return;
        if (dialogsMode) {
            actionBar.setTitle(screenTitle());
            // Размер архива — сразу видно, когда пора чистить.
            long size = onceMode
                    ? AxOnceMessages.getInstance().getDatabaseSize()
                    : AxDeletedMessages.getInstance().getDatabaseSize();
            actionBar.setSubtitle(dialogs.isEmpty()
                    ? null
                    : dialogs.size() + " чатов · " + totalCount + " сообщений · "
                            + AndroidUtilities.formatFileSize(size));
        } else {
            actionBar.setTitle(dialogTitle != null ? dialogTitle : screenTitle());
            actionBar.setSubtitle(messages.isEmpty()
                    ? screenTitle().toLowerCase()
                    : messages.size() + " сохранено");
        }
    }

    /** Пересобирает видимый список с учётом поиска и разделителей дат. */
    private void rebuild() {
        items.clear();
        boolean empty;
        if (dialogsMode) {
            shownDialogs.clear();
            for (AxDeletedMessages.DialogSummary d : dialogs) {
                if (searchQuery != null && !searchQuery.isEmpty()) {
                    String title = d.title != null ? d.title.toLowerCase() : "";
                    String preview = d.lastText != null ? d.lastText.toLowerCase() : "";
                    if (!title.contains(searchQuery) && !preview.contains(searchQuery)) {
                        continue;
                    }
                }
                shownDialogs.add(d);
            }
            empty = shownDialogs.isEmpty();
        } else {
            String lastDate = null;
            for (MessageObject mo : messages) {
                if (searchQuery != null && !searchQuery.isEmpty()) {
                    String text = mo.messageOwner != null && mo.messageOwner.message != null
                            ? mo.messageOwner.message.toLowerCase() : "";
                    if (!text.contains(searchQuery)) continue;
                }
                String date = LocaleController.formatDateChat(mo.messageOwner.date);
                if (!TextUtils.equals(date, lastDate)) {
                    items.add(date);
                    lastDate = date;
                }
                items.add(mo);
            }
            empty = items.isEmpty();
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        if (emptyView != null) {
            emptyView.setText(emptyText());
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
    }

    private String emptyText() {
        if (searchQuery != null && !searchQuery.isEmpty()) {
            return "Ничего не найдено";
        }
        if (onceMode) {
            return "Здесь появятся одноразовые фото, видео и голосовые — они сохранятся, даже если отправитель их сожжёт.";
        }
        return "Здесь появятся удалённые сообщения. Переписка останется, даже если чат, группу или канал удалят целиком.";
    }

    // --------------------------------------------------------------- действия

    /** Открывает профиль пользователя или чат по идентификатору. */
    private void openPeer(long peerId) {
        if (peerId == 0) {
            return;
        }
        Bundle args = new Bundle();
        if (peerId > 0) {
            args.putLong("user_id", peerId);
            presentFragment(new org.telegram.ui.ProfileActivity(args));
        } else {
            args.putLong("chat_id", -peerId);
            if (MessagesController.getInstance(currentAccount).checkCanOpenChat(args, this)) {
                presentFragment(new ChatActivity(args));
            }
        }
    }

    private void openRealChat() {
        if (!dialogExists()) return;
        Bundle args = new Bundle();
        if (filterDialogId > 0) {
            args.putLong("user_id", filterDialogId);
        } else {
            args.putLong("chat_id", -filterDialogId);
        }
        if (MessagesController.getInstance(currentAccount).checkCanOpenChat(args, this)) {
            presentFragment(new ChatActivity(args));
        }
    }

    private void showDialogActions(AxDeletedMessages.DialogSummary d) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(d.title != null ? d.title : "Чат");
        builder.setItems(new CharSequence[]{"Очистить этот чат"}, (dialog, which) -> {
            AlertDialog.Builder confirm = new AlertDialog.Builder(getParentActivity());
            confirm.setTitle("Очистить?");
            confirm.setMessage("Сохранённые сообщения этого чата будут удалены безвозвратно.");
            confirm.setPositiveButton("Очистить", (d2, w2) -> {
                if (onceMode) {
                    AxOnceMessages.getInstance().clearDialog(currentAccount, d.dialogId);
                } else {
                    AxDeletedMessages.getInstance().clearDialog(currentAccount, d.dialogId);
                }
                loadData();
            });
            confirm.setNegativeButton("Отмена", null);
            showDialog(confirm.create());
        });
        showDialog(builder.create());
    }

    private void showMessageActions(MessageObject mo) {
        // Долгое нажатие может прийти и от самой ячейки, и от списка — второй раз
        // открывать то же меню не нужно.
        if (visibleDialog != null) {
            return;
        }
        final ArrayList<CharSequence> actionItems = new ArrayList<>();
        final ArrayList<Integer> actions = new ArrayList<>();
        boolean hasText = mo.messageOwner != null && mo.messageOwner.message != null && !mo.messageOwner.message.isEmpty();
        if (hasText) {
            actionItems.add("Копировать текст");
            actions.add(0);
        }
        actionItems.add("Переслать / Поделиться");
        actions.add(1);
        boolean isMedia = mo.isPhoto() || mo.isVideo() || mo.isRoundVideo() || mo.isVoice() || mo.isMusic() || mo.getDocument() != null;
        if (isMedia) {
            actionItems.add("Сохранить в галерею");
            actions.add(2);
        }
        actionItems.add("Удалить из архива");
        actions.add(3);

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        Long when = archivedAt.get(mo.getId());
        if (when != null && when > 0) {
            builder.setTitle((onceMode ? "Сохранено " : "Удалено ")
                    + LocaleController.formatDateTime(when, true));
        }
        builder.setItems(actionItems.toArray(new CharSequence[0]), (dialog, which) -> {
            int act = actions.get(which);
            if (act == 0) {
                AndroidUtilities.addToClipboard(mo.messageOwner.message);
                if (getParentActivity() != null) {
                    org.telegram.ui.Components.BulletinFactory.of(AxDeletedMessagesActivity.this)
                            .createCopyBulletin(LocaleController.getString(org.telegram.messenger.R.string.TextCopied)).show();
                }
            } else if (act == 1) {
                ArrayList<MessageObject> arr = new ArrayList<>();
                arr.add(mo);
                ShareAlert shareAlert = new ShareAlert(getParentActivity(), arr, null, false, null, false);
                showDialog(shareAlert);
            } else if (act == 2) {
                try {
                    java.io.File file = FileLoader.getInstance(currentAccount).getPathToMessage(mo.messageOwner);
                    String path = (file != null) ? file.toString() : null;
                    if (path != null && new java.io.File(path).exists()) {
                        MediaController.saveFile(path, getParentActivity(), mo.isVideo() || mo.isRoundVideo() ? 1 : 0, null, null);
                    } else {
                        // ещё не скачано — качаем, сохранить можно будет сразу после
                        FileLoader.getInstance(currentAccount).loadFile(mo.getDocument(), mo, FileLoader.PRIORITY_HIGH, 0);
                        if (getParentActivity() != null) {
                            android.widget.Toast.makeText(getParentActivity(), "Загружаю файл, попробуй сохранить ещё раз через секунду", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Throwable ignore) {
                }
            } else if (act == 3) {
                if (onceMode) {
                    AxOnceMessages.getInstance().deleteOne(currentAccount, mo.getDialogId(), mo.getId());
                } else {
                    AxDeletedMessages.getInstance().deleteOne(currentAccount, mo.getDialogId(), mo.getId());
                }
                loadData();
            }
        });
        showDialog(builder.create());
    }

    private void showClearConfirm() {
        if (getContext() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Очистить?");
        builder.setMessage(dialogsMode
                ? "Все сохранённые сообщения будут удалены безвозвратно."
                : "Сохранённые сообщения этого чата будут удалены безвозвратно.");
        builder.setPositiveButton("Очистить", (d, w) -> {
            if (onceMode) {
                if (dialogsMode) {
                    AxOnceMessages.getInstance().clearAll(currentAccount);
                } else {
                    AxOnceMessages.getInstance().clearDialog(currentAccount, filterDialogId);
                }
            } else {
                if (dialogsMode) {
                    AxDeletedMessages.getInstance().clearAll(currentAccount);
                } else {
                    AxDeletedMessages.getInstance().clearDialog(currentAccount, filterDialogId);
                }
            }
            if (dialogsMode) {
                loadData();
            } else {
                finishFragment();
            }
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    // ------------------------------------------------------------ список чатов

    /** Строка чата: аватар, название, превью последнего сообщения, дата и счётчик. */
    private class DialogRow extends FrameLayout {
        private final BackupImageView avatar;
        private final AvatarDrawable avatarDrawable = new AvatarDrawable();
        private final TextView nameView;
        private final TextView previewView;
        private final TextView dateView;
        private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF badgeRect = new RectF();
        private String badgeText;
        private boolean deletedChat;
        private boolean needDivider;

        DialogRow(Context context) {
            super(context);
            setWillNotDraw(false);

            avatar = new BackupImageView(context);
            avatar.setRoundRadius(AndroidUtilities.dp(26));
            addView(avatar, LayoutHelper.createFrame(52, 52, Gravity.LEFT | Gravity.TOP, 12, 9, 0, 0));

            nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameView.setTypeface(AndroidUtilities.bold());
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 76, 12, 76, 0));

            previewView = new TextView(context);
            previewView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            previewView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            previewView.setSingleLine(true);
            previewView.setEllipsize(TextUtils.TruncateAt.END);
            addView(previewView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 76, 36, 76, 0));

            dateView = new TextView(context);
            dateView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            dateView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
            dateView.setGravity(Gravity.RIGHT);
            addView(dateView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, 14, 14, 0));

            badgeTextPaint.setTextSize(AndroidUtilities.dp(12));
            badgeTextPaint.setTypeface(AndroidUtilities.bold());
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(70), MeasureSpec.EXACTLY));
        }

        void bind(AxDeletedMessages.DialogSummary d, boolean divider) {
            needDivider = divider;
            deletedChat = liveTitle(d.dialogId) == null;
            String title = d.title != null ? d.title : "Удалённый чат";
            nameView.setText(title);
            nameView.setTextColor(Theme.getColor(deletedChat
                    ? Theme.key_windowBackgroundWhiteGrayText
                    : Theme.key_windowBackgroundWhiteBlackText));

            String preview = d.lastText != null ? d.lastText.replace('\n', ' ').trim() : "";
            if (preview.isEmpty()) {
                preview = "Медиа и файлы";
            }
            previewView.setText(preview);

            dateView.setText(d.lastDate > 0 ? LocaleController.formatDateChat(d.lastDate) : "");
            badgeText = String.valueOf(d.count);

            // Аватар: настоящий, если чат ещё жив; иначе — по первой букве названия.
            TLRPC.User user = d.dialogId > 0 ? MessagesController.getInstance(currentAccount).getUser(d.dialogId) : null;
            TLRPC.Chat chat = d.dialogId < 0 ? MessagesController.getInstance(currentAccount).getChat(-d.dialogId) : null;
            if (user != null) {
                avatarDrawable.setInfo(currentAccount, user);
                avatar.setForUserOrChat(user, avatarDrawable);
            } else if (chat != null) {
                avatarDrawable.setInfo(currentAccount, chat);
                avatar.setForUserOrChat(chat, avatarDrawable);
            } else {
                avatarDrawable.setInfo(d.dialogId, title, null);
                avatar.setImageDrawable(avatarDrawable);
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (badgeText != null) {
                badgePaint.setColor(Theme.getColor(deletedChat ? Theme.key_chats_unreadCounterMuted : Theme.key_chats_unreadCounter));
                badgeTextPaint.setColor(Theme.getColor(Theme.key_chats_unreadCounterText));
                float textWidth = badgeTextPaint.measureText(badgeText);
                float w = Math.max(AndroidUtilities.dp(20), textWidth + AndroidUtilities.dp(12));
                float right = getWidth() - AndroidUtilities.dp(14);
                float top = AndroidUtilities.dp(38);
                badgeRect.set(right - w, top, right, top + AndroidUtilities.dp(20));
                canvas.drawRoundRect(badgeRect, AndroidUtilities.dp(10), AndroidUtilities.dp(10), badgePaint);
                canvas.drawText(badgeText, badgeRect.centerX() - textWidth / 2f,
                        badgeRect.centerY() + AndroidUtilities.dp(4.3f), badgeTextPaint);
            }
            if (needDivider) {
                canvas.drawLine(AndroidUtilities.dp(76), getHeight() - 1, getWidth(), getHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    private class DialogsAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        DialogsAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            DialogRow row = new DialogRow(context);
            row.setBackground(Theme.getSelectorDrawable(false));
            return new RecyclerListView.Holder(row);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((DialogRow) holder.itemView).bind(shownDialogs.get(position), position != shownDialogs.size() - 1);
        }

        @Override
        public int getItemCount() {
            return shownDialogs.size();
        }
    }

    // -------------------------------------------------------------- переписка

    /** Разделитель даты — «плашка» поверх обоев, как в обычном чате. */
    private static class DateCell extends TextView {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        DateCell(Context context) {
            super(context);
            setGravity(Gravity.CENTER);
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            setTypeface(AndroidUtilities.bold());
            setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            CharSequence text = getText();
            if (text != null && text.length() > 0) {
                float w = getPaint().measureText(text.toString()) + AndroidUtilities.dp(20);
                float h = getHeight() - AndroidUtilities.dp(12);
                rect.set((getWidth() - w) / 2f, AndroidUtilities.dp(6), (getWidth() + w) / 2f, AndroidUtilities.dp(6) + h);
                paint.setColor(Theme.getColor(Theme.key_chat_serviceBackground));
                canvas.drawRoundRect(rect, h / 2f, h / 2f, paint);
            }
            super.onDraw(canvas);
        }
    }

    private class MessagesAdapter extends RecyclerListView.SelectionAdapter {
        private static final int TYPE_MESSAGE = 0;
        private static final int TYPE_DATE = 1;

        private final Context context;

        MessagesAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.itemView instanceof ChatMessageCell;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof MessageObject ? TYPE_MESSAGE : TYPE_DATE;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == TYPE_DATE) {
                DateCell cell = new DateCell(context);
                cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(cell);
            }
            ChatMessageCell cell = new ChatMessageCell(context, currentAccount);
            cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                @Override
                public boolean canPerformActions() {
                    return true;
                }

                @Override
                public void didLongPress(ChatMessageCell c, float x, float y) {
                    if (c.getMessageObject() != null) {
                        showMessageActions(c.getMessageObject());
                    }
                }

                @Override
                public boolean needPlayMessage(ChatMessageCell c, MessageObject messageObject, boolean muted) {
                    if (messageObject.isVoice() || messageObject.isRoundVideo()) {
                        boolean result = MediaController.getInstance().playMessage(messageObject, muted);
                        MediaController.getInstance().setVoiceMessagesPlaylist(null, false);
                        return result;
                    } else if (messageObject.isMusic()) {
                        return MediaController.getInstance().setPlaylist(messages, messageObject, 0);
                    }
                    return false;
                }

                @Override
                public void didPressUrl(ChatMessageCell c, CharacterStyle url, boolean longPress) {
                    if (url == null || getParentActivity() == null) {
                        return;
                    }
                    if (url instanceof URLSpanMono) {
                        ((URLSpanMono) url).copyToClipboard();
                        org.telegram.ui.Components.BulletinFactory.of(AxDeletedMessagesActivity.this)
                                .createCopyBulletin(LocaleController.getString(org.telegram.messenger.R.string.TextCopied)).show();
                        return;
                    }
                    if (url instanceof URLSpanUserMention) {
                        openPeer(org.telegram.messenger.Utilities.parseLong(((URLSpanUserMention) url).getURL()));
                        return;
                    }
                    if (url instanceof URLSpanNoUnderline) {
                        String str = ((URLSpanNoUnderline) url).getURL();
                        if (str.startsWith("@")) {
                            MessagesController.getInstance(currentAccount)
                                    .openByUserName(str.substring(1), AxDeletedMessagesActivity.this, 0);
                        }
                        return;
                    }
                    if (!(url instanceof URLSpan)) {
                        return;
                    }
                    final String link = ((URLSpan) url).getURL();
                    if (longPress) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(link);
                        builder.setItems(new CharSequence[]{"Открыть", "Копировать ссылку"}, (dialog, which) -> {
                            if (which == 0) {
                                Browser.openUrl(getParentActivity(), link, true);
                            } else {
                                AndroidUtilities.addToClipboard(link);
                                org.telegram.ui.Components.BulletinFactory.of(AxDeletedMessagesActivity.this)
                                        .createCopyBulletin(LocaleController.getString(org.telegram.messenger.R.string.LinkCopied)).show();
                            }
                        });
                        showDialog(builder.create());
                    } else {
                        Browser.openUrl(getParentActivity(), link, true);
                    }
                }

                @Override
                public void didPressUserAvatar(ChatMessageCell c, TLRPC.User user, float touchX, float touchY, boolean asForward) {
                    if (user != null) {
                        openPeer(user.id);
                    }
                }

                @Override
                public void didPressChannelAvatar(ChatMessageCell c, TLRPC.Chat chat, int postId, float touchX, float touchY, boolean asForward) {
                    if (chat != null) {
                        openPeer(-chat.id);
                    }
                }

                @Override
                public void didPressViaBot(ChatMessageCell c, String username) {
                    if (username != null && !username.isEmpty()) {
                        MessagesController.getInstance(currentAccount)
                                .openByUserName(username, AxDeletedMessagesActivity.this, 0);
                    }
                }

                @Override
                public void didPressWebPage(ChatMessageCell c, TLRPC.WebPage webpage, String url, boolean safe) {
                    if (url != null && getParentActivity() != null) {
                        Browser.openUrl(getParentActivity(), url, true);
                    }
                }

                @Override
                public void needOpenWebView(MessageObject message, String url, String title, String description, String originalUrl, int w, int h) {
                    if (getParentActivity() != null) {
                        org.telegram.ui.Components.EmbedBottomSheet.show(AxDeletedMessagesActivity.this, message,
                                photoProvider, title, description, originalUrl, url, w, h, false);
                    }
                }

                @Override
                public void didPressInstantButton(ChatMessageCell c, int type) {
                    MessageObject message = c.getMessageObject();
                    if (message == null || getParentActivity() == null) {
                        return;
                    }
                    if (message.messageOwner.media != null && message.messageOwner.media.webpage != null) {
                        Browser.openUrl(getParentActivity(), message.messageOwner.media.webpage.url, true);
                    }
                }

                @Override
                public void didPressOther(ChatMessageCell c, float otherX, float otherY) {
                    // Кнопка «прочее» у документов и музыки — открываем файл.
                    MessageObject message = c.getMessageObject();
                    if (message == null || getParentActivity() == null) {
                        return;
                    }
                    try {
                        AndroidUtilities.openForView(message, getParentActivity(), null, false);
                    } catch (Exception ignore) {
                    }
                }

                @Override
                public void didPressImage(ChatMessageCell c, float x, float y, boolean fullPreview) {
                    MessageObject message = c.getMessageObject();
                    if (message == null || getParentActivity() == null) return;
                    if (message.isVideo() || message.type == MessageObject.TYPE_PHOTO || message.isGif()) {
                        PhotoViewer.getInstance().setParentActivity(AxDeletedMessagesActivity.this);
                        PhotoViewer.getInstance().openPhoto(message, null, 0, 0, 0, photoProvider);
                    } else {
                        try {
                            AndroidUtilities.openForView(message, getParentActivity(), null, false);
                        } catch (Exception ignore) {
                        }
                    }
                }
            });
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            bind(holder, position);
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            // ChatMessageCell применяет содержимое только будучи прикреплённым.
            bind(holder, holder.getAdapterPosition());
        }

        private void bind(RecyclerView.ViewHolder holder, int position) {
            if (position < 0 || position >= items.size()) return;
            Object item = items.get(position);
            if (item instanceof MessageObject && holder.itemView instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) holder.itemView;
                // В группах показываем аватар и имя отправителя, в каналах — нет.
                cell.isChat = showSenders;
                cell.setFullyDraw(true);
                cell.setMessageObject((MessageObject) item, null, false, false, false);
            } else if (holder.itemView instanceof DateCell) {
                DateCell cell = (DateCell) holder.itemView;
                cell.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
                cell.setText((String) item);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
