/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.phone.testapps.embmsmw;

import android.os.RemoteException;
import android.telephony.mbms.IStreamingServiceCallback;
import android.telephony.mbms.StreamingService;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

// Tracks the states of the streams for a single (uid, appName, subscriptionId) tuple
public class AppActiveStreams {
    // Wrapper for a pair (StreamingServiceCallback, streaming state)
    private static class StreamCallbackWithState {
        private final IStreamingServiceCallback mCallback;
        private int mState;
        private int mMethod;
        private boolean mMethodSet = false;

        StreamCallbackWithState(IStreamingServiceCallback callback, int state, int method) {
            mCallback = callback;
            mState = state;
            mMethod = method;
        }

        public IStreamingServiceCallback getCallback() {
            return mCallback;
        }

        public int getState() {
            return mState;
        }

        public void setState(int state) {
            mState = state;
        }

        public int getMethod() {
            return mMethod;
        }

        public void setMethod(int method) {
            mMethod = method;
            mMethodSet = true;
        }

        public boolean isMethodSet() {
            return mMethodSet;
        }
    }

    // Stores the state and callback per service ID.
    private final Map<String, StreamCallbackWithState> mStreamStates = new HashMap<>();
    private final FrontendAppIdentifier mAppIdentifier;
    private final Random mRand = new Random();

    public AppActiveStreams(FrontendAppIdentifier appIdentifier) {
        mAppIdentifier = appIdentifier;
    }

    public int getStateForService(String serviceId) {
        StreamCallbackWithState callbackWithState = mStreamStates.get(serviceId);
        return callbackWithState == null ?
                StreamingService.STATE_STOPPED : callbackWithState.getState();
    }

    public void startStreaming(String serviceId, IStreamingServiceCallback callback) {
        if (mStreamStates.get(serviceId) != null) {
            // error - already started
            return;
        }
        for (StreamCallbackWithState c : mStreamStates.values()) {
            if (c.getCallback() == callback) {
                // error - callback already in use
                return;
            }
        }
        mStreamStates.put(serviceId,
                new StreamCallbackWithState(callback, StreamingService.STATE_STARTED,
                        StreamingService.UNICAST_METHOD));
        try {
            callback.streamStateUpdated(StreamingService.STATE_STARTED);
            updateStreamingMethod(serviceId);
        } catch (RemoteException e) {
            dispose(serviceId);
        }
    }

    public void stopStreaming(String serviceId) {
        StreamCallbackWithState entry = mStreamStates.get(serviceId);

        if (entry != null) {
            try {
                if (entry.getState() != StreamingService.STATE_STOPPED) {
                    entry.setState(StreamingService.STATE_STOPPED);
                    entry.getCallback().streamStateUpdated(StreamingService.STATE_STOPPED);
                }
            } catch (RemoteException e) {
                dispose(serviceId);
            }
        }
    }

    public void dispose(String serviceId) {
        mStreamStates.remove(serviceId);
    }

    private void updateStreamingMethod(String serviceId) {
        StreamCallbackWithState callbackWithState = mStreamStates.get(serviceId);
        if (callbackWithState != null) {
            int oldMethod = callbackWithState.getMethod();
            int newMethod = oldMethod;
            if (mRand.nextInt(99) < 50) {
                newMethod = StreamingService.UNICAST_METHOD;
            } else {
                newMethod = StreamingService.BROADCAST_METHOD;
            }
            if (newMethod != oldMethod || callbackWithState.isMethodSet()) {
                callbackWithState.setMethod(newMethod);
                try {
                    callbackWithState.getCallback().streamMethodUpdated(newMethod);
                } catch (RemoteException e) {
                    dispose(serviceId);
                }
            }
        }
    }
}
