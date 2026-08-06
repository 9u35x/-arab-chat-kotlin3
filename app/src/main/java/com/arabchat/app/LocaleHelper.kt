package com.arabchat.app

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {
    private const val PREF = "ytalk_locale"
    private const val KEY = "lang"

    fun savedLang(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "ar") ?: "ar"

    fun persist(ctx: Context, lang: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, lang).apply()
    }

    fun apply(ctx: Context, lang: String): Context {
        persist(ctx, lang)
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val res = ctx.resources
        val config = Configuration(res.configuration)
        config.setLocale(locale)
        return if (Build.VERSION.SDK_INT >= 17) {
            ctx.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
            ctx
        }
    }

    fun wrap(ctx: Context): Context = apply(ctx, savedLang(ctx))
}
