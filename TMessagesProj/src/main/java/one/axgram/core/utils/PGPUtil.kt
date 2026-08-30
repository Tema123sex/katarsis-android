package one.axgram.core.utils

import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpServiceConnection
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import one.axgram.core.AxCoreConfig

object PGPUtil {

    lateinit var serviceConnection: OpenPgpServiceConnection
    lateinit var api: OpenPgpApi

    @JvmStatic
    fun recreateConnection() {

        if (::serviceConnection.isInitialized) {

            runCatching {

                serviceConnection.unbindFromService()

            }

        }

        serviceConnection = OpenPgpServiceConnection(
                ApplicationLoader.applicationContext,
                AxCoreConfig.openPGPApp.String()
        )


    }

    @JvmStatic
    fun post(runnable: Runnable) {

        if (!::serviceConnection.isInitialized) {

            recreateConnection()

        }

        if (!serviceConnection.isBound) {

            serviceConnection.bindToService(object : OpenPgpServiceConnection.OnBound {

                override fun onBound(service: IOpenPgpService2) {

                    api = OpenPgpApi(ApplicationLoader.applicationContext, service)

                    runnable.run()

                }

                override fun onError(e: Exception) {

                    FileLog.e(e)

                    AlertUtil.showToast(e)

                }

            })

        } else {

            runnable.run()

        }

    }

}