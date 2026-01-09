package io.github.dovecoteescapee.byedpi.activities

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.color.DynamicColors
import io.github.dovecoteescapee.byedpi.utility.getPreferences
import io.github.dovecoteescapee.byedpi.utility.getStringNotNull

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getPreferences()

        val lang = prefs.getStringNotNull("language", "system")
        setLang(lang)

        val theme = prefs.getStringNotNull("app_theme", "system")
        setTheme(theme)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        super.onCreate(savedInstanceState)
    }

    private fun setLang(lang: String) {
        val appLocale = localeByName(lang) ?: return
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != appLocale.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }

    private fun setTheme(name: String) {
        val appTheme = themeByName(name) ?: return
        if (AppCompatDelegate.getDefaultNightMode() != appTheme) {
            AppCompatDelegate.setDefaultNightMode(appTheme)
        }
    }

    private fun localeByName(lang: String): LocaleListCompat? = when (lang) {
        "system" -> LocaleListCompat.getEmptyLocaleList()
        "ru" -> LocaleListCompat.forLanguageTags("ru")
        "en" -> LocaleListCompat.forLanguageTags("en")
        "tr" -> LocaleListCompat.forLanguageTags("tr")
        "kk" -> LocaleListCompat.forLanguageTags("kk")
        else -> null
    }

    private fun themeByName(name: String): Int? = when (name) {
        "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> null
    }
}
