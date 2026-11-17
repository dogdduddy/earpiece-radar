package com.dogdduddy.earpieceradar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** DI 라이브러리 없이 ViewModel 을 만드는 간단한 팩토리 */
inline fun <reified VM : ViewModel> factory(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
