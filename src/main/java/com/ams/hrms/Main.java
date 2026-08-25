package com.ams.hrms;

import com.ams.hrms.config.Bootstrapper;

/**
 * Application entry point. Startup failures terminate the JVM with a
 * non-zero exit code; on success the AWT event thread keeps the process
 * alive and the login window is shown.
 */
public final class Main {

    public static void main(String[] args) {
        new Bootstrapper().launch(args);
    }

    private Main() {
    }
}
