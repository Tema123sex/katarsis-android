package one.katarsis.core.database

import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.BoxStoreBuilder
import org.telegram.messenger.ApplicationLoader
import one.katarsis.core.transtale.TranslateDb
import one.katarsis.core.utils.FileUtil
import one.katarsis.extra.network.NetworkLogDb
import java.io.File


@JvmOverloads
fun mkDatabase(name: String, delete: Boolean = false): BoxStore {

    val oldFile = File("${ApplicationLoader.getDataDirFixed()}/databases/$name.db")
    FileUtil.initDir(oldFile.parentFile!!)
    if (oldFile.exists()) {
        oldFile.deleteRecursively()
    }

    val dir = File("${ApplicationLoader.getDataDirFixed()}/databases/$name")
    if (delete) {
        dir.deleteRecursively()
    }

    fun create(): BoxStore {
        val builder: BoxStoreBuilder = MyObjectBox.builder()
        return builder
            .androidContext(ApplicationLoader.applicationContext)
            .directory(dir)
            .noReaderThreadLocals()
            .build()
    }

    runCatching {
        return create()
    }.onFailure {
        dir.deleteRecursively()
    }
    return create()
}

fun queryTransItemModel(box: Box<TransItem>, code: String, text: String): TransItem? {
    val query = box.query(TransItem_.code.equal(code).and(TransItem_.text.equal(text)))
            .build()
    return query.findFirst()
}

fun clearAll() {
    TranslateDb.clearAll()
    NetworkLogDb.clearAll()
}
