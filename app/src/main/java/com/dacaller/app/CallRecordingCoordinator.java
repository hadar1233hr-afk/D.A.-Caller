package com.dacaller.app;

/**
 * A fail-closed lifecycle gate for a future, opt-in call recording backend.
 *
 * This class is intentionally independent of Android and Shizuku. It DOES NOT
 * access audio, invoke shell commands, or start recording by itself. A separate
 * backend must first be independently validated on real hardware.
 *
 * The caller passes a non-PII call token (not a phone number). A single backend
 * owns at most one active segment. Call holds end a segment; resuming can begin
 * a new segment with the same call token. Backend segment filenames must not
 * overwrite earlier segments.
 */
final class CallRecordingCoordinator {
    interface Backend {
        void startSegment(String callToken) throws Exception;
        void stopSegment() throws Exception;
    }

    enum CallState {
        NEW, RINGING, DIALING, CONNECTING, ACTIVE, HOLDING,
        DISCONNECTING, DISCONNECTED, REMOVED
    }

    enum Result {
        NO_CHANGE, STARTED, STOPPED, ALREADY_RECORDING, OTHER_CALL_ACTIVE,
        BLOCKED_OPT_IN, BLOCKED_SHIZUKU, BLOCKED_BACKEND_FAILURE, BACKEND_FAILURE
    }

    private final Backend backend;
    private boolean userOptedIn;
    private boolean shizukuAuthorizedAndRunning;
    private boolean backendFailure;
    private String recordingToken;

    CallRecordingCoordinator(Backend backend) {
        if (backend == null) throw new IllegalArgumentException("backend is required");
        this.backend = backend;
    }

    /** Recording remains disabled by default, including after object creation. */
    synchronized Result setUserOptedIn(boolean enabled) {
        userOptedIn = enabled;
        return enabled ? Result.NO_CHANGE : stopCurrent();
    }

    /** A permission grant alone is insufficient: Shizuku must also be running. */
    synchronized Result setShizukuAuthorizedAndRunning(boolean available) {
        shizukuAuthorizedAndRunning = available;
        return available ? Result.NO_CHANGE : stopCurrent();
    }

    synchronized Result onCallState(String callToken, CallState state) {
        validate(callToken, state);
        if (state == CallState.ACTIVE) {
            if (!userOptedIn) return Result.BLOCKED_OPT_IN;
            if (!shizukuAuthorizedAndRunning) return Result.BLOCKED_SHIZUKU;
            if (backendFailure) return Result.BLOCKED_BACKEND_FAILURE;
            if (recordingToken != null) {
                return recordingToken.equals(callToken)
                    ? Result.ALREADY_RECORDING : Result.OTHER_CALL_ACTIVE;
            }
            try {
                backend.startSegment(callToken);
                recordingToken = callToken;
                return Result.STARTED;
            } catch (Exception failedStart) {
                // Even a failed start can have partially opened a capture source.
                backendFailure = true;
                try { backend.stopSegment(); } catch (Exception ignored) {
                    // Keep failure latched; never auto-restart a possibly live source.
                }
                return Result.BACKEND_FAILURE;
            }
        }
        if (recordingToken != null && recordingToken.equals(callToken) &&
            (state == CallState.HOLDING || state == CallState.DISCONNECTING ||
             state == CallState.DISCONNECTED || state == CallState.REMOVED)) {
            return stopCurrent();
        }
        return Result.NO_CHANGE;
    }

    synchronized Result onCallRemoved(String callToken) {
        return onCallState(callToken, CallState.REMOVED);
    }

    /** Release on lifecycle teardown, even if a call-disconnect event was missed. */
    synchronized Result shutdown() {
        userOptedIn = false;
        shizukuAuthorizedAndRunning = false;
        return stopCurrent();
    }

    synchronized boolean isRecording() { return recordingToken != null; }
    synchronized boolean isFaulted() { return backendFailure; }

    private Result stopCurrent() {
        if (recordingToken == null) return Result.NO_CHANGE;
        // Clear first so a reentrant callback cannot cause duplicate stops.
        recordingToken = null;
        try {
            backend.stopSegment();
            return Result.STOPPED;
        } catch (Exception failedStop) {
            backendFailure = true;
            return Result.BACKEND_FAILURE;
        }
    }

    private static void validate(String callToken, CallState state) {
        if (callToken == null || callToken.isEmpty() || state == null) {
            throw new IllegalArgumentException("nonempty call token and state required");
        }
    }
}
