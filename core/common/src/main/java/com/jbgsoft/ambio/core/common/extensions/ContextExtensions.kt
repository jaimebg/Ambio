package com.jbgsoft.ambio.core.common.extensions

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.RawRes

fun Context.getRawUri(@RawRes rawResId: Int): Uri {
    return Uri.parse("android.resource://$packageName/$rawResId")
}

fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    startActivity(intent)
}
