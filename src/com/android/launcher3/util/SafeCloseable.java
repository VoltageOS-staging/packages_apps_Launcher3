/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.util;

/**
 * Interface for objects that need to be closed to release resources.
 * Extends AutoCloseable but without throwing checked exceptions.
 */
public interface SafeCloseable extends AutoCloseable {
    void close();
}

