// Copyright 2016 InnerFunction Ltd.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License
package com.innerfunction.semo.commands;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by juliangoacher on 07/05/16.
 */
public class RunQueue extends LinkedBlockingQueue<Runnable> {

    private Thread runThread;

    public RunQueue() {
        runThread = new Thread(new Runnable() {
            public void run() {
                while( true ) {
                    try {
                        Runnable next = take();
                        next.run();
                    }
                    catch(Exception e) {

                    }
                }
            }
        });
        runThread.start();
    }

    public boolean dispatch(Runnable runnable) {
        boolean ok = false;
        try {
            put( runnable );
        }
        catch(Exception e) {
            ok = false;
        }
        return ok;
    }

    /**
     * Test whether the current thread is the queue's execution thread.
     * @return true if the current thread is the same as the queue's execution thread.
     */
    public boolean isRunningOnQueueThread() {
        return Thread.currentThread() == runThread;
    }

}
