package org.example.profitpilot.sync;

import org.springframework.stereotype.Component;

@Component
public class ImportPause {

    public void await(long milliseconds) throws InterruptedException {
        Thread.sleep(milliseconds);
    }
}
