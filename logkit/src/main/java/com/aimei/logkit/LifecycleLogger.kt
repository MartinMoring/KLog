package com.aimei.logkit

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

internal object LifecycleLogger {

    private const val TAG = "Lifecycle"

    fun register(context: Context) {
        (context.applicationContext as? Application)
            ?.registerActivityLifecycleCallbacks(LifecycleCallbacks)
    }

    private val fragmentCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
            KLog.d(TAG, "Fragment onCreate: ${f.javaClass.simpleName}")
        }
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            KLog.i(TAG, "Fragment onResume: ${f.javaClass.simpleName}")
        }
        override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
            KLog.i(TAG, "Fragment onPause: ${f.javaClass.simpleName}")
        }
        override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
            KLog.d(TAG, "Fragment onDestroy: ${f.javaClass.simpleName}")
        }
    }

    private object LifecycleCallbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            KLog.i(TAG, "Activity onCreate: ${activity.javaClass.simpleName}")
            if (activity is FragmentActivity) {
                activity.supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentCallbacks, true)
            }
        }
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) {
            KLog.i(TAG, "Activity onResume: ${activity.javaClass.simpleName}")
        }
        override fun onActivityPaused(activity: Activity) {
            KLog.i(TAG, "Activity onPause: ${activity.javaClass.simpleName}")
        }
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) {
            KLog.d(TAG, "Activity onDestroy: ${activity.javaClass.simpleName}")
        }
    }
}
