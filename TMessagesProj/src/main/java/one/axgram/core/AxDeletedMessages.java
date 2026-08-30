package one.axgram.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

/**
 * AxGram: anti-delete storage.
 *
 * Saves the full serialized content of messages just before they are removed
 * from Telegram's local storage (i.e. when someone deletes/recalls them), so
 * they survive and can be viewed later — mirroring AyuGram Desktop.
 *
 * Uses a standalone SQLite database (independent of Telegram's own DB) to keep
 * the feature self-contained and low-risk.
 */
public class AxDeletedMessages extends SQLiteOpenHelper {

    private static final String DB_NAME = "axgram_deleted.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "deleted_messages";

    private static volatile AxDeletedMessages instance;

    public static AxDeletedMessages getInstance() {
        if (instance == null) {
            synchronized (AxDeletedMessages.class) {
                if (instance == null) {
                    instance = new AxDeletedMessages(ApplicationLoader.applicationContext);
                }
            }
        }
        return instance;
    }

    private AxDeletedMessages(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "account INTEGER NOT NULL,"
                + "dialog_id INTEGER NOT NULL,"
                + "message_id INTEGER NOT NULL,"
                + "sender_id INTEGER NOT NULL,"
                + "date INTEGER NOT NULL,"
                + "deleted_at INTEGER NOT NULL,"
                + "text TEXT,"
                + "data BLOB NOT NULL,"
                + "UNIQUE(account, dialog_id, message_id) ON CONFLICT IGNORE"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dialog ON " + TABLE + " (account, dialog_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 only for now
    }

    /**
     * Save a message that is about to be deleted. Reads the already-deserialized
     * TLRPC.Message, re-serializes it to a blob and stores it.
     * Called from MessagesStorage on the storage thread.
     */
    public void saveMessage(int account, long dialogId, TLRPC.Message message) {
        if (message == null || message instanceof TLRPC.TL_messageEmpty) {
            return;
        }
        // Skip service messages with no real content and locally-created placeholders.
        try {
            NativeByteBuffer nbb = new NativeByteBuffer(message.getObjectSize());
            message.serializeToStream(nbb);
            byte[] blob = new byte[nbb.limit()];
            nbb.rewind();
            nbb.readBytes(blob, false);
            nbb.reuse();

            long senderId = MessageObject.getFromChatId(message);

            ContentValues cv = new ContentValues();
            cv.put("account", account);
            cv.put("dialog_id", dialogId);
            cv.put("message_id", message.id);
            cv.put("sender_id", senderId);
            cv.put("date", message.date);
            cv.put("deleted_at", System.currentTimeMillis() / 1000L);
            cv.put("text", message.message != null ? message.message : "");
            cv.put("data", blob);

            getWritableDatabase().insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        } catch (Throwable e) {
            FileLog.e("AxDeletedMessages.saveMessage", e);
        }
    }

    public int getCount(int account, long dialogId) {
        try {
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE + " WHERE account=? AND dialog_id=?",
                    new String[]{String.valueOf(account), String.valueOf(dialogId)});
            int n = 0;
            if (c.moveToFirst()) n = c.getInt(0);
            c.close();
            return n;
        } catch (Throwable e) {
            FileLog.e("AxDeletedMessages.getCount", e);
            return 0;
        }
    }

    public int getTotalCount(int account) {
        try {
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE + " WHERE account=?",
                    new String[]{String.valueOf(account)});
            int n = 0;
            if (c.moveToFirst()) n = c.getInt(0);
            c.close();
            return n;
        } catch (Throwable e) {
            return 0;
        }
    }

    /**
     * Load saved deleted messages for a dialog, newest-deleted first,
     * reconstructed as TLRPC.Message objects.
     */
    public List<TLRPC.Message> getMessages(int account, long dialogId) {
        ArrayList<TLRPC.Message> result = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT data FROM " + TABLE + " WHERE account=? AND dialog_id=? ORDER BY date ASC, message_id ASC",
                    new String[]{String.valueOf(account), String.valueOf(dialogId)});
            while (c.moveToNext()) {
                byte[] blob = c.getBlob(0);
                if (blob == null) continue;
                try {
                    NativeByteBuffer nbb = new NativeByteBuffer(blob.length);
                    nbb.writeBytes(blob);
                    nbb.position(0);
                    TLRPC.Message m = TLRPC.Message.TLdeserialize(nbb, nbb.readInt32(false), false);
                    nbb.reuse();
                    if (m != null) {
                        result.add(m);
                    }
                } catch (Throwable ignore) {
                }
            }
        } catch (Throwable e) {
            FileLog.e("AxDeletedMessages.getMessages", e);
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    /** Lightweight holder for the viewer. */
    public static class Entry {
        public long dialogId;
        public long senderId;
        public int date;
        public long deletedAt;
        public TLRPC.Message message;
    }

    private static TLRPC.Message deserialize(byte[] blob) {
        try {
            NativeByteBuffer nbb = new NativeByteBuffer(blob.length);
            nbb.writeBytes(blob);
            nbb.position(0);
            TLRPC.Message m = TLRPC.Message.TLdeserialize(nbb, nbb.readInt32(false), false);
            nbb.reuse();
            return m;
        } catch (Throwable e) {
            return null;
        }
    }

    /** All saved deleted messages for an account, most-recently-deleted first. */
    public List<Entry> getAllEntries(int account) {
        ArrayList<Entry> result = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT dialog_id, sender_id, date, deleted_at, data FROM " + TABLE
                            + " WHERE account=? ORDER BY deleted_at DESC, date DESC",
                    new String[]{String.valueOf(account)});
            while (c.moveToNext()) {
                byte[] blob = c.getBlob(4);
                if (blob == null) continue;
                TLRPC.Message m = deserialize(blob);
                if (m == null) continue;
                Entry e = new Entry();
                e.dialogId = c.getLong(0);
                e.senderId = c.getLong(1);
                e.date = c.getInt(2);
                e.deletedAt = c.getLong(3);
                e.message = m;
                result.add(e);
            }
        } catch (Throwable e) {
            FileLog.e("AxDeletedMessages.getAllEntries", e);
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    // === Session (in-memory) cache of live deleted MessageObjects per dialog ===
    // Cleared on app restart — mirrors desktop where deleted items stay in the in-memory
    // history model until the app is closed, then are only in the DB ("View Deleted").
    private static final java.util.HashMap<Long, java.util.LinkedHashMap<Integer, MessageObject>> sessionCache = new java.util.HashMap<>();

    public static void cacheDeleted(long dialogId, MessageObject mo) {
        if (mo == null) return;
        synchronized (sessionCache) {
            java.util.LinkedHashMap<Integer, MessageObject> m = sessionCache.get(dialogId);
            if (m == null) {
                m = new java.util.LinkedHashMap<>();
                sessionCache.put(dialogId, m);
            }
            m.put(mo.getId(), mo);
        }
    }

    public static void uncacheDeleted(long dialogId, int msgId) {
        synchronized (sessionCache) {
            java.util.LinkedHashMap<Integer, MessageObject> m = sessionCache.get(dialogId);
            if (m != null) m.remove(msgId);
        }
    }

    public static ArrayList<MessageObject> getCachedDeleted(long dialogId) {
        synchronized (sessionCache) {
            java.util.LinkedHashMap<Integer, MessageObject> m = sessionCache.get(dialogId);
            if (m == null || m.isEmpty()) return new ArrayList<>();
            return new ArrayList<>(m.values());
        }
    }

    public void clearAll(int account) {
        try {
            getWritableDatabase().delete(TABLE, "account=?", new String[]{String.valueOf(account)});
        } catch (Throwable e) {
            FileLog.e("AxDeletedMessages.clearAll", e);
        }
    }

    public void deleteOne(int account, long dialogId, int messageId) {
        try {
            getWritableDatabase().delete(TABLE, "account=? AND dialog_id=? AND message_id=?",
                    new String[]{String.valueOf(account), String.valueOf(dialogId), String.valueOf(messageId)});
        } catch (Throwable e) {
            FileLog.e("AxDeletedMessages.deleteOne", e);
        }
    }

    public void clearDialog(int account, long dialogId) {
        try {
            getWritableDatabase().delete(TABLE, "account=? AND dialog_id=?",
                    new String[]{String.valueOf(account), String.valueOf(dialogId)});
        } catch (Throwable e) {
            FileLog.e("AxDeletedMessages.clearDialog", e);
        }
    }
}
