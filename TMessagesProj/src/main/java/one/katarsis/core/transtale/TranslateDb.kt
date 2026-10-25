package one.katarsis.core.transtale

import io.objectbox.Box
import org.telegram.messenger.LocaleController
import one.katarsis.core.AxCoreConfig
import one.katarsis.core.database.ChatCCTarget
import one.katarsis.core.database.ChatLanguage
import one.katarsis.core.database.TransItem
import one.katarsis.core.database.mkDatabase
import one.katarsis.core.database.queryTransItemModel
import one.katarsis.core.utils.UIUtil
import java.util.Locale

class TranslateDb(val code: String) {

    var conn: Box<TransItem> = db.boxFor(TransItem::class.java)

    companion object {

        val db = mkDatabase("translate_caches")

        val repo = HashMap<Locale, TranslateDb>()
        val chat: Box<ChatLanguage> = db.boxFor(ChatLanguage::class.java)
        val ccTarget: Box<ChatCCTarget> = db.boxFor(ChatCCTarget::class.java)

        @JvmStatic
        fun getChatLanguage(chatId: Long, default: Locale): Locale {
            return if (chatId == 0L) default
            else chat.get(chatId)?.language?.code2Locale ?: default
        }

        @JvmStatic
        fun saveChatLanguage(chatId: Long, locale: Locale) = UIUtil.runOnIoDispatcher {
            chat.put(ChatLanguage(chatId, locale.locale2code))
        }

        @JvmStatic
        fun getChatCCTarget(chatId: Long, default: String?): String? {
            return if (chatId == 0L) default
            else ccTarget.get(chatId)?.ccTarget ?: default
        }

        @JvmStatic
        fun saveChatCCTarget(chatId: Long, target: String) = UIUtil.runOnIoDispatcher {
            ccTarget.put(ChatCCTarget(chatId, target))
        }

        @JvmStatic
        fun currentTarget() = AxCoreConfig.translateToLang.String()?.transDbByCode
            ?: LocaleController.getInstance().currentLocale.transDb

        @JvmStatic
        fun forLocale(locale: Locale) = locale.transDb

        @JvmStatic
        fun currentInputTarget() = AxCoreConfig.translateInputLang.String().transDbByCode

        @JvmStatic
        fun clearAll() {
            db.removeAllObjects()
            repo.clear()
        }
    }

    fun clear() = synchronized(this) {
        conn.removeAll()
    }

    fun contains(text: String) =
        synchronized(this) { queryTransItemModel(conn, code, text) != null }

    fun save(text: String, trans: String) = synchronized<Unit>(this) {
        var model = queryTransItemModel(conn, code, text)
        if (model != null) {
            model.trans = trans;
        } else {
            model = TransItem(code, text, trans)
        }
        conn.put(model)
    }

    fun query(text: String) = synchronized(this) {
        queryTransItemModel(conn, code, text)?.trans
    }
}
