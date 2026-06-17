package com.aimei.logkit

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import java.lang.ref.WeakReference

internal object ClickEventLogger {

    fun register(context: Context) {
        (context.applicationContext as? Application)
            ?.registerActivityLifecycleCallbacks(ClickLifecycleCallbacks)
    }

    private object ClickLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = install(activity)
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private fun install(activity: Activity) {
        val original = activity.window.callback ?: return
        if (original is ClickLoggingCallback) return
        activity.window.callback = ClickLoggingCallback(WeakReference(activity), original)
    }
}

private class ClickLoggingCallback(
    private val activityRef: WeakReference<Activity>,
    private val delegate: Window.Callback
) : Window.Callback by delegate {

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val activity = activityRef.get()
            if (activity != null) {
                findClickedView(activity.window.decorView, event.rawX.toInt(), event.rawY.toInt())
                    ?.takeIf { it.hasOnClickListeners() }
                    ?.let { logClick(it, activity) }
            }
        }
        return delegate.dispatchTouchEvent(event)
    }

    private fun logClick(v: View, activity: Activity) {
        val id = try {
            v.resources.getResourceEntryName(v.id)
        } catch (e: Exception) {
            "0x${v.id.toString(16)}"
        }
        val hint = when (v) {
            is TextView -> v.text?.toString()?.take(20)?.trim()
            else -> v.contentDescription?.toString()?.take(20)?.trim()
        }.orEmpty()
        val label = if (hint.isNotEmpty()) "$id[$hint]" else id
        KLog.i("Click", "onClick: $label @ ${activity.javaClass.simpleName}")
    }

    private fun findClickedView(view: View, x: Int, y: Int): View? {
        if (view.visibility != View.VISIBLE) return null
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        if (x < loc[0] || x >= loc[0] + view.width || y < loc[1] || y >= loc[1] + view.height) return null
        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                findClickedView(view.getChildAt(i), x, y)?.let { return it }
            }
        }
        return if (view.isClickable) view else null
    }
}
