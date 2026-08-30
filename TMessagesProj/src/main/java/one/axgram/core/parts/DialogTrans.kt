package one.axgram.core.parts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import one.axgram.core.transtale.TranslateDb
import one.axgram.core.transtale.Translator
import one.axgram.core.transtale.code2Locale
import one.axgram.core.utils.AlertUtil
import one.axgram.core.utils.UIUtil
import one.axgram.core.utils.uDismiss
import java.util.concurrent.atomic.AtomicBoolean

fun startTrans(ctx: Context, text: String) {

    val dialog = AlertUtil.showProgress(ctx)

    val canceled = AtomicBoolean(false)

    dialog.setOnCancelListener {

        canceled.set(true)

    }

    dialog.show()

    fun update(message: String) {

        UIUtil.runOnUIThread(Runnable { dialog.setMessage(message) })

    }

    GlobalScope.launch(Dispatchers.IO) {

        val target = TranslateDb.currentTarget()

        if (target.contains(text)) {

            dialog.uDismiss()

            AlertUtil.showCopyAlert(ctx, target.query(text) ?: "")

            return@launch

        }

        runCatching {

            val result = Translator.translate(target.code.code2Locale, text)

            if (!canceled.get()) {

                dialog.uDismiss()

                AlertUtil.showCopyAlert(ctx, result)

            }

        }.onFailure {

            dialog.uDismiss()

            if (!canceled.get()) {

                AlertUtil.showTransFailedDialog(ctx, it is UnsupportedOperationException, it.message
                        ?: it.javaClass.simpleName) {

                    startTrans(ctx, text)

                }

            }

        }

    }

}