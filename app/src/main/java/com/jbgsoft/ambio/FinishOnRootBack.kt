package com.jbgsoft.ambio

import androidx.activity.ComponentActivity
import androidx.activity.addCallback

/**
 * Makes Back on the last screen finish the activity, as it did before Android 12.
 *
 * Since 12, Back on a task root that the home screen launched moves the task to the
 * back instead, so the activity survives, its ViewModel is never cleared, and
 * AudioService never sees the app's controller leave: the service and its
 * notification stay behind until the task is swiped away (issue #9). A shell
 * `am start` is not a home launch, which is why that path never showed it.
 *
 * Call it before setContent. The dispatcher asks the newest callback first, so
 * NavHost's, registered during composition, still pops Settings and Stats.
 */
fun ComponentActivity.finishOnRootBack() {
    onBackPressedDispatcher.addCallback(this) { finish() }
}
