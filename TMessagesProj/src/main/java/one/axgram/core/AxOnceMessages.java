package one.axgram.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

/**
 * AxGram: anti self-destruct storage.
 *
 * Saves the full serialized content of one-time / self-destruct media messages
 * as soon as they arrive, so they survive after being viewed (and wiped) on
 * this or another device. Mirrors {@link AxDeletedMessages} but for disappearing
 * media, and is kept in its own standalone SQLite database.
 */
public class AxOnceMessages extends SQLiteOpenHelper {

    private static final String DB_NAME = "axgram_once.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "once_messages";

    private static volatile AxOnceMessages instance;

    public static AxOnceMessages getInstance() {
        if (instance == null) {
            synchronized (AxOnceMessages.class) {
                if (instance == null) {
                    instance = new AxOnceMessages(ApplicationLoader.applicationContext);
                }
            }
        }
        return instance;
    }

    private AxOnceMessages(Context context) {
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
                + "saved_at INTEGER NOT NULL,"
                + "text TEXT,"
                + "data BLOB NOT NULL,"
                + "UNIQUE(account, dialog_id, message_id) ON CONFLICT IGNORE"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_once_dialog ON " + TABLE + " (account, dialog_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 only for now
    }

    /**
     * Save a one-time / self-destruct message as soon as it is received.
     * The blob keeps the original (non-emptied) media so it can be re-displayed later.
     */
    public void saveMessage(int account, long dialogId, TLRPC.Message message) {
        if (message == null || message instanceof TLRPC.TL_messageEmpty) {
            return;
        }
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
            cv.put("saved_at", System.currentTimeMillis() / 1000L);
            cv.put("text", message.message != null ? message.message : "");
            cv.put("data", blob);

            getWritableDatabase().insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        } catch (Throwable e) {
            FileLog.e("AxOnceMessages.saveMessage", e);
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

    /** Lightweight holder for the viewer. */
    public static class Entry {
        public long dialogId;
        public long senderId;
        public int date;
        public long savedAt;
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

    /** All saved one-time messages for an account, most-recently-saved first. */
    public List<Entry> getAllEntries(int account) {
        ArrayList<Entry> result = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT dialog_id, sender_id, date, saved_at, data FROM " + TABLE
                            + " WHERE account=? ORDER BY saved_at DESC, date DESC",
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
                e.savedAt = c.getLong(3);
                e.message = m;
                result.add(e);
            }
        } catch (Throwable e) {
            FileLog.e("AxOnceMessages.getAllEntries", e);
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    public void clearAll(int account) {
        try {
            getWritableDatabase().delete(TABLE, "account=?", new String[]{String.valueOf(account)});
        } catch (Throwable e) {
            FileLog.e("AxOnceMessages.clearAll", e);
        }
    }

    public void deleteOne(int account, long dialogId, int messageId) {
        try {
            getWritableDatabase().delete(TABLE, "account=? AND dialog_id=? AND message_id=?",
                    new String[]{String.valueOf(account), String.valueOf(dialogId), String.valueOf(messageId)});
        } catch (Throwable e) {
            FileLog.e("AxOnceMessages.deleteOne", e);
        }
    }

    public void clearDialog(int account, long dialogId) {
        try {
            getWritableDatabase().delete(TABLE, "account=? AND dialog_id=?",
                    new String[]{String.valueOf(account), String.valueOf(dialogId)});
        } catch (Throwable e) {
            FileLog.e("AxOnceMessages.clearDialog", e);
        }
    }
}
