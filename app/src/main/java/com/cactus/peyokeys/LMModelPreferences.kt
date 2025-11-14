package com.cactus.peyokeys

import android.content.Context
import android.content.SharedPreferences

object LMModelPreferences {
    private const val PREFS_NAME = "lm_model_prefs"
    private const val KEY_ACTIVE_MODEL = "active_lm_model_slug"
    private const val DEFAULT_MODEL = "qwen3-0.6"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setActiveModel(context: Context, modelSlug: String) {
        getPrefs(context).edit().putString(KEY_ACTIVE_MODEL, modelSlug).apply()
    }

    fun getActiveModel(context: Context): String {
        return getPrefs(context).getString(KEY_ACTIVE_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }
}
