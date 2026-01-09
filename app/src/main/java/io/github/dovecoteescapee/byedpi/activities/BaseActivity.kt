package io.github.dovecoteescapee.byedpi.activities

import android.app.Activity
import android.os.Bundle

abstract class BaseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Тут больше ничего не нужно. 
        // Приложение будет использовать системный язык и тему.
    }
}
