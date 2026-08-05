package com.riansares.r4r.ingestion;

/**
 * Test-only process that remains alive until destroyed.
 * Used for testing child process lifecycle (destroy/forcibly-destroy).
 */
public class TestHelperProcess {
    public static void main(String[] args) throws InterruptedException {
        // Keep running until terminated
        while (!Thread.interrupted()) {
            Thread.sleep(100);
        }
    }
}
