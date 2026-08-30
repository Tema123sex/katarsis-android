package one.axgram.core.transtale.source

import one.axgram.extra.AxConfig

/**
 * Official DeepL Pro API translator (https://api.deepl.com).
 *
 * Uses the API key stored in [AxConfig.deepLApiKey]. The key must be a DeepL
 * Pro authentication key (the Free plan uses [DeepLFreeTranslator] instead).
 */
object DeepLTranslator : DeepLOfficialTranslatorBase() {

    override val logTag: String = "DeepL"

    override val baseUrl: String = "https://api.deepl.com"

    override val displayName: String = "DeepL"

    override val apiKey: String?
        get() = AxConfig.deepLApiKey.String()
}
