package one.axgram.core;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.PhotoViewer;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageReceiver;

import java.util.ArrayList;
import java.util.List;

/**
 * AxGram: viewer for deleted messages rendered as real chat bubbles (with media —
 * photos, voice, video circles), mirroring AyuGram Desktop's "View Deleted".
 */
public class AxDeletedMessagesActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private TextView emptyView;

    private final ArrayList<MessageObject> messages = new ArrayList<>();
    private final long filterDialogId; // 0 = all chats
    private final boolean onceMode;    // true = one-time / self-destruct ("anti self-destruct") viewer

    // Provider so PhotoViewer animates back to the cell and refreshes the thumbnail (no black frame).
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
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(onceMode ? "Исчезающие сообщения" : "Удалённые сообщения");
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    showClearConfirm();
                }
            }
        });
        actionBar.createMenu().addItem(1, org.telegram.messenger.R.drawable.baseline_delete_24);

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        LinearLayoutManager lm = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        lm.setStackFromEnd(true); // newest at the bottom, like a chat
        listView.setLayoutManager(lm);
        listView.setVerticalScrollBarEnabled(false);
        adapter = new ListAdapter(context);
        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener((view, position) -> {
            if (position < 0 || position >= messages.size() || getParentActivity() == null) return false;
            showMessageActions(messages.get(position));
            return true;
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));

        emptyView = new TextView(context);
        emptyView.setText(onceMode ? "Пока нет сохранённых одноразовых сообщений" : "Пока нет сохранённых удалённых сообщений");
        emptyView.setTextSize(16);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        emptyView.setVisibility(View.GONE);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        loadData();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        new Thread(() -> {
            final ArrayList<MessageObject> built = new ArrayList<>();
            if (onceMode) {
                List<AxOnceMessages.Entry> all = AxOnceMessages.getInstance().getAllEntries(currentAccount);
                for (AxOnceMessages.Entry e : all) {
                    if (filterDialogId != 0 && e.dialogId != filterDialogId) continue;
                    try {
                        MessageObject mo = new MessageObject(currentAccount, e.message, true, true);
                        built.add(mo);
                    } catch (Throwable ignore) {
                    }
                }
            } else {
                List<AxDeletedMessages.Entry> all = AxDeletedMessages.getInstance().getAllEntries(currentAccount);
                for (AxDeletedMessages.Entry e : all) {
                    if (filterDialogId != 0 && e.dialogId != filterDialogId) continue;
                    try {
                        MessageObject mo = new MessageObject(currentAccount, e.message, true, true);
                        mo.axDeleted = true;
                        built.add(mo);
                    } catch (Throwable ignore) {
                    }
                }
            }
            // oldest first (chat order); stackFromEnd shows the bottom (newest) first
            java.util.Collections.sort(built, (a, b) -> {
                int d = Integer.compare(a.messageOwner.date, b.messageOwner.date);
                return d != 0 ? d : Integer.compare(a.getId(), b.getId());
            });
            AndroidUtilities.runOnUIThread(() -> {
                messages.clear();
                messages.addAll(built);
                if (adapter != null) adapter.notifyDataSetChanged();
                if (emptyView != null) {
                    emptyView.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
                }
            });
        }).start();
    }

    private void showMessageActions(MessageObject mo) {
        final ArrayList<CharSequence> items = new ArrayList<>();
        final ArrayList<Integer> actions = new ArrayList<>();
        boolean hasText = mo.messageOwner != null && mo.messageOwner.message != null && !mo.messageOwner.message.isEmpty();
        if (hasText) {
            items.add("Копировать текст");
            actions.add(0);
        }
        items.add("Переслать / Поделиться");
        actions.add(1);
        boolean isMedia = mo.isPhoto() || mo.isVideo() || mo.isRoundVideo() || mo.isVoice() || mo.isMusic() || mo.getDocument() != null;
        if (isMedia) {
            items.add("Сохранить в галерею");
            actions.add(2);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
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
                        // not downloaded yet — fetch it, user can save again once ready
                        FileLoader.getInstance(currentAccount).loadFile(mo.getDocument(), mo, FileLoader.PRIORITY_HIGH, 0);
                        if (getParentActivity() != null) {
                            android.widget.Toast.makeText(getParentActivity(), "Загружаю файл, попробуй сохранить ещё раз через секунду", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Throwable e) {
                }
            }
        });
        showDialog(builder.create());
    }

    private void showClearConfirm() {
        if (getContext() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Очистить?");
        builder.setMessage(filterDialogId != 0
                ? (onceMode ? "Удалить сохранённые одноразовые сообщения этого чата?" : "Удалить сохранённые удалённые сообщения этого чата?")
                : (onceMode ? "Удалить все сохранённые одноразовые сообщения?" : "Удалить все сохранённые удалённые сообщения?"));
        builder.setPositiveButton("Очистить", (d, w) -> {
            if (onceMode) {
                if (filterDialogId != 0) {
                    AxOnceMessages.getInstance().clearDialog(currentAccount, filterDialogId);
                } else {
                    AxOnceMessages.getInstance().clearAll(currentAccount);
                }
            } else {
                if (filterDialogId != 0) {
                    AxDeletedMessages.getInstance().clearDialog(currentAccount, filterDialogId);
                } else {
                    AxDeletedMessages.getInstance().clearAll(currentAccount);
                }
            }
            loadData();
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
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
            ChatMessageCell cell = (ChatMessageCell) holder.itemView;
            MessageObject mo = messages.get(position);
            cell.setFullyDraw(true);
            cell.setMessageObject(mo, null, false, false, false);
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            // ChatMessageCell only applies content when attached — re-apply on attach.
            int position = holder.getAdapterPosition();
            if (position >= 0 && position < messages.size() && holder.itemView instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) holder.itemView;
                cell.setFullyDraw(true);
                cell.setMessageObject(messages.get(position), null, false, false, false);
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }
}
