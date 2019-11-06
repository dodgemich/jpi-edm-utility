/* Copyright 2011-2013 Google Inc.
 * Copyright 2013 mike wakerly <opensource@hoho.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: https://github.com/mik3y/usb-serial-for-android
 */

package com.hoho.android.usbserial.util;

import android.hardware.usb.UsbRequest;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Utility class which services a {@link UsbSerialPort} in its {@link #run()}
 * method.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
public class SerialInputOutputManager implements Runnable {

    private static final String TAG = SerialInputOutputManager.class.getSimpleName();
    private static final boolean DEBUG = true;

    private static final int READ_WAIT_MILLIS = 200;
    private static final int BUFSIZ = 64;

    private final UsbSerialPort mDriver;

    private boolean hasStarted = false;

//    private final ByteBuffer mReadBuffer = ByteBuffer.allocate(BUFSIZ);
    private OutputStream outputStream ;

    private enum State {
        STOPPED,
        RUNNING,
        STOPPING
    }

    // Synchronized by 'this'
    private State mState = State.STOPPED;

    // Synchronized by 'this'
    private Listener mListener;






    public interface Listener {
        /**
         * Called when new incoming data is available.
         */
        public void onStatusMessage(String message);

        public void onCompletion();

        /**
         * Called when {@link SerialInputOutputManager#run()} aborts due to an
         * error.
         */
        public void onRunError(Exception e);
    }





    /**
     * Creates a new instance with the provided listener.
     */
    public SerialInputOutputManager(UsbSerialPort driver, Listener listener, OutputStream outputStream) {
        mDriver = driver;
        mListener = listener;
        this.outputStream=outputStream;

    }

    public synchronized void setListener(Listener listener) {
        mListener = listener;
    }

    public synchronized Listener getListener() {
        return mListener;
    }


    public synchronized void stop() {
        if (getState() == State.RUNNING) {
            Log.i(TAG, "Stop requested");
            mState = State.STOPPING;
        }
    }

    private synchronized State getState() {
        return mState;
    }

    /**
     * Continuously services the read and write buffers until {@link #stop()} is
     * called, or until a driver exception is raised.
     *
     * NOTE(mikey): Uses inefficient read/write-with-timeout.
     * TODO(mikey): Read asynchronously with {@link UsbRequest#queue(ByteBuffer, int)}
     */
    @Override
    public void run() {
        synchronized (this) {
            if (getState() != State.STOPPED) {
                throw new IllegalStateException("Already running.");
            }
            mState = State.RUNNING;
        }

        Log.i(TAG, "Running ..");
        try {
            while (true) {
                if (getState() != State.RUNNING) {
                    Log.i(TAG, "Stopping mState=" + getState());
                    break;
                }
                step();
            }
        } catch (Exception e) {
            Log.w(TAG, "Run ending due to exception: " + e.getMessage(), e);
            final Listener listener = getListener();

            if (listener != null) {
              listener.onRunError(e);
            }
        } finally {
            synchronized (this) {
                mState = State.STOPPED;
                Log.i(TAG, "Stopped.");
            }
        }
    }

    private void step() throws IOException {
        // Handle incoming data.
        byte[] byteArr = new byte[BUFSIZ];
        int len = mDriver.read(byteArr, READ_WAIT_MILLIS);
        final Listener listener = getListener();

//        boolean empty =  Arrays.equals(byteArr, new byte[byteArr.length]);

        if (len > 0 ) {
            if(!hasStarted){
                listener.onStatusMessage("\nTransfer started\n");
            }
            hasStarted=true;
            outputStream.write(byteArr, 0 , len);
//            listener.onStatusMessage(".");
//            String fetch = new String(byteArr);
//            listener.onStatusMessage(fetch);
//            if(fetch.trim().endsWith("E,4*5D")) {
//                mState=State.STOPPING;
//                if (listener != null) {
//                    listener.onStatusMessage("Download complete.\n");
//                    outputStream.flush();
//                    outputStream.close();
//                }
//            }
        } else {
            if(!hasStarted) {
                listener.onStatusMessage("~");
            } else {
                mState=State.STOPPING;
                hasStarted=false;
                listener.onStatusMessage("\nDownload complete.\n");
                outputStream.flush();
                outputStream.close();

                listener.onCompletion();

            }
        }


    }

}
