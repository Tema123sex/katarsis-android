package one.katarsis.core.transtale.source

import one.katarsis.extra.AxConfig

/**
 * Official DeepL Free API translator (https://api-free.deepl.com).
 *
 * Uses the API key stored in [AxConfig.deepLFreeApiKey]. The key must be a
 * DeepL API Free authentication key (Free plan keys end with ":fx" by
 * convention; we do not enforce this client-side though).
 */
object DeepLFreeTranslator : DeepLOfficialTranslatorBase() {

    override val logTag: String = "DeepLFree"

    override val baseUrl: String = "https://api-free.deepl.com"

    override val displayName: String = "DeepL Free"

    override val apiKey: String?
        get() = AxConfig.deepLFreeApiKey.String()
}
