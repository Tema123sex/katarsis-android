package one.axgram.core.parts

import kotlinx.coroutines.*
import org.telegram.messenger.MessageObject
import org.telegram.messenger.TranslateController
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ChatActivity
import one.axgram.core.AxCoreConfig
import one.axgram.core.transtale.TranslateDb
import one.axgram.core.transtale.Translator
import one.axgram.core.transtale.code2Locale
import one.axgram.core.utils.AlertUtil
import one.axgram.core.utils.UIUtil
import one.axgram.core.utils.uDismiss
import one.axgram.core.utils.uUpdate
import one.axgram.extra.AxConfig
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

fun MessageObject.translateFinished(locale: Locale): Int {

    val db = TranslateDb.forLocale(locale)

    translating = false

    if (isPoll) {
        val pool = (messageOwner.media as TLRPC.TL_messageMediaPoll).poll
        val question = db.query(pool.question.text) ?: return 0
        val translatedPoll = TranslateController.PollText.fromMessage(this)

        translatedPoll.question.text =
            "${if (!AxConfig.hideOriginAfterTranslation.Bool()) translatedPoll.question.text + " | " else ""}$question"
        translatedPoll.answers.forEach {
            val answer = db.query(it.text.text) ?: return 0
            it.text.text += " | $answer"
        }
        if (translatedPoll.solution != null) {
            val solution = db.query(translatedPoll.solution.text) ?: return 0
            translatedPoll.solution.text =
                "${if (!AxConfig.hideOriginAfterTranslation.Bool()) translatedPoll.solution.text + " | " else ""}$solution"
        }
        messageOwner.translatedPoll = translatedPoll
    } else {

        val text = db.query(messageOwner.message.takeIf { !it.isNullOrBlank() } ?: return 1)
                ?: return 0

        messageOwner.translatedMessage =
            "${if (!AxConfig.hideOriginAfterTranslation.Bool()) messageOwner.message + "\n\n--------\n\n" else ""}$text"

    }

    return 2

}

@JvmName("translateMessages")
fun ChatActivity.translateMessages1() = translateMessages()

@JvmName("translateMessages")
fun ChatActivity.translateMessages2(target: Locale) = translateMessages(target)

@JvmName("translateMessages")
fun ChatActivity.translateMessages3(messages: List<MessageObject>) = translateMessages(messages = messages)

@JvmName("translateMessages")
fun ChatActivity.translateMessages4(messages: List<MessageObject>, autoTranslate: Boolean) = translateMessages(messages = messages, autoTranslate = autoTranslate)

fun ChatActivity.translateMessages(
    target: Locale = AxCoreConfig.translateToLang.String().code2Locale,
    messages: List<MessageObject> = messageForTranslate?.let { listOf(it) }
        ?: selectedObjectGroup?.messages
        ?: emptyList(),
    autoTranslate: Boolean = false
) {
    if (messages.any { it.translating }) return

    if (messages.all { it.messageOwner.translated }) {
        messages.forEach {
            it.messageOwner.translated = false
            messageHelper.resetMessageContent(dialogId, it)
            it.translating = false
        }
        return
    } else {
        messages.forEach { it.translating = true }
    }

    var status: AlertDialog? = null
    val cancel = AtomicBoolean()
    if (!autoTranslate) {
        status = AlertUtil.showProgress(parentActivity).apply {
            setOnCancelListener { cancel.set(true) }
            show()
        }
    }

    val deferreds = LinkedList<Deferred<Unit>>()
    val taskCount = AtomicInteger(messages.size)
    val transPool = newFixedThreadPoolContext(5, "Message Trans Pool")

    fun next() {
        val index = taskCount.decrementAndGet()
        if (index == 0) {
            status?.uDismiss()
        } else if (messages.size > 1) {
            status?.uUpdate("${messages.size - index} / ${messages.size}")
        }
    }

    GlobalScope.launch(Dispatchers.IO) {
        messages.forEach { selectedObject ->
            when (selectedObject.translateFinished(target)) {
                1 -> next()
                2 -> {
                    next()
                    withContext(Dispatchers.Main) {
                        selectedObject.messageOwner.translated = true
                        messageHelper.resetMessageContent(dialogId, selectedObject)
                    }
                }
                else -> deferreds.add(async(transPool) {
                    translateMessage(selectedObject, target, cancel, status)
                    if (!cancel.get()) {
                        selectedObject.messageOwner.translated = true
                        next()
                        withContext(Dispatchers.Main) {
                            messageHelper.resetMessageContent(dialogId, selectedObject)
                        }
                    }
                })
            }
        }

        deferreds.awaitAll()
        transPool.cancel()
        UIUtil.runOnUIThread { if (!cancel.get()) status?.uDismiss() }
    }
    messages.forEach { it.translating = false }
}

private suspend fun ChatActivity.translateMessage(
    message: MessageObject,
    target: Locale,
    cancel: AtomicBoolean,
    status: AlertDialog?
) {
    val db = TranslateDb.forLocale(target)
    if (message.isPoll) {
        translatePoll(message, target, db, cancel, status)
    } else {
        translateText(message, target, db, cancel, status)
    }
}

private suspend fun ChatActivity.translatePoll(
    message: MessageObject,
    target: Locale,
    db: TranslateDb,
    cancel: AtomicBoolean,
    status: AlertDialog?
) {
    val pool = (message.messageOwner.media as TLRPC.TL_messageMediaPoll).poll
    var question = db.query(pool.question.text)
    if (question == null) {
        if (cancel.get()) return
        question = runCatching {
            Translator.translate(target, pool.question.text)
        }.getOrElse {
            handleError(target, it, cancel, status)
            return
        }
    }

    val translatedPoll = TranslateController.PollText.fromMessage(message)
    translatedPoll.question.text = buildString {
        if (!AxConfig.hideOriginAfterTranslation.Bool()) append(pool.question.text + " | ")
        append(question)
    }

    translatedPoll.answers.forEach {
        var answer = db.query(it.text.text)
        if (answer == null) {
            if (cancel.get()) return@forEach
            answer = runCatching {
                Translator.translate(target, it.text.text)
            }.getOrElse { e ->
                handleError(target, e, cancel, status)
                return@forEach
            }
        }
        it.text.text = buildString {
            if (!AxConfig.hideOriginAfterTranslation.Bool()) append(it.text.text + " | ")
            append(answer)
        }
    }

    translatedPoll.solution?.let { solution ->
        var translatedSolution = db.query(solution.text)
        if (translatedSolution == null) {
            if (cancel.get()) return
            translatedSolution = runCatching {
                Translator.translate(target, solution.text)
            }.getOrElse {
                handleError(target, it, cancel, status)
                return
            }
        }
        solution.text = buildString {
            if (!AxConfig.hideOriginAfterTranslation.Bool()) append(solution.text + " | ")
            append(translatedSolution)
        }
    }

    message.messageOwner.translatedPoll = translatedPoll
}

private suspend fun ChatActivity.translateText(
    message: MessageObject,
    target: Locale,
    db: TranslateDb,
    cancel: AtomicBoolean,
    status: AlertDialog?
) {
    var text = db.query(message.messageOwner.message)
    if (text == null) {
        text = runCatching {
            Translator.translate(target, message.messageOwner.message)
        }.getOrElse {
            handleError(target, it, cancel, status)
            return
        }
    }

    message.messageOwner.translatedMessage = buildString {
        if (!AxConfig.hideOriginAfterTranslation.Bool()) append(message.messageOwner.message + "\n\n--------\n\n")
        append(text)
    }
}

private fun ChatActivity.handleError(
    target: Locale,
    error: Throwable,
    cancel: AtomicBoolean,
    status: AlertDialog?
) {
    status?.uDismiss()
    if (parentActivity != null && !cancel.get()) {
        AlertUtil.showTransFailedDialog(
            parentActivity,
            error is UnsupportedOperationException,
            error.message ?: error.javaClass.simpleName
        ) {
            translateMessages(target, messages)
        }
    }
}
