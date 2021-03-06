/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.vm;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.Executor;

abstract class ComputeInExecutor<R> implements Runnable {
    private final Executor executor;
    private R result;
    private Throwable exception;
    private boolean started;
    private boolean done;

    protected ComputeInExecutor(Executor executor) {
        this.executor = executor;
    }

    protected abstract R compute() throws IOException;

    public final R get() throws IOException {
        perform();
        if (executor != null) {
            waitForDone();
        }
        exceptionCheck();
        return result;
    }

    private void waitForDone() throws InterruptedIOException {
        synchronized (this) {
            while (!done) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                    throw new InterruptedIOException(ex.getMessage());
                }
            }
        }
    }

    private void exceptionCheck() throws IOException, RuntimeException {
        if (exception instanceof IOException) {
            throw (IOException) exception;
        }
        if (exception instanceof RuntimeException) {
            throw (RuntimeException) exception;
        }
        if (exception != null) {
            throw new RuntimeException(exception);
        }
    }

    public final void perform() throws IOException {
        if (started) {
            return;
        }
        started = true;
        if (executor == null) {
            run();
        } else {
            executor.execute(this);
        }
        exceptionCheck();
    }

    @Override
    public final void run() {
        try {
            result = compute();
        } catch (Exception ex) {
            exception = ex;
        } finally {
            if (executor != null) {
                synchronized (this) {
                    done = true;
                    notifyAll();
                }
            } else {
                done = true;
            }
        }
    }

    @Override
    public final String toString() {
        return "value=" + result + ",exception=" + exception + ",computed=" + done;
    }
}
