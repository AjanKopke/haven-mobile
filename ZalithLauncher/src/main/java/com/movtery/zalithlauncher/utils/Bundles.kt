/*
 * Zalith Launcher 2
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.utils

import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import java.io.Serializable

@Suppress("DEPRECATION")
fun <T : Parcelable> Bundle.getParcelableSafely(key: String?, clazz: Class<T>): T? {
    // Android 13's typed getParcelable throws a NullPointerException (Class.isInterface on null) for app classes:
    // the typed call only from Android 14 on, and the app's class loader set for the untyped one
    classLoader = clazz.classLoader
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> getParcelable(key, clazz)
        else -> getParcelable(key)
    }
}

@Suppress("DEPRECATION")
fun <T: Serializable> Bundle.getSerializableSafely(key: String?, clazz: Class<T>): T? {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getSerializable(key, clazz)
        else -> getSerializable(key) as? T
    }
}