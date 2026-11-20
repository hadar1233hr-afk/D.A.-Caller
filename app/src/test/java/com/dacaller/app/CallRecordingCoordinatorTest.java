package com.dacaller.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class CallRecordingCoordinatorTest {
    private static final class FakeBackend implements CallRecordingCoordinator.Backend {
        int starts, stops;
        String lastToken;
        boolean failStart, failStop;
        @Override public void startSegment(String token) throws Exception {
            starts++; lastToken=token;
            if (failStart) throw new Exception("start failed");
        }
        @Override public void stopSegment() throws Exception {
            stops++;
            if (failStop) throw new Exception("stop failed");
        }
    }
    private static CallRecordingCoordinator ready(FakeBackend backend) {
        CallRecordingCoordinator c=new CallRecordingCoordinator(backend);
        c.setUserOptedIn(true);
        c.setShizukuAuthorizedAndRunning(true);
        return c;
    }
    @Test public void defaultIsOffEvenWhenCallIsActive() {
        FakeBackend b=new FakeBackend();CallRecordingCoordinator c=new CallRecordingCoordinator(b);
        assertEquals(CallRecordingCoordinator.Result.BLOCKED_OPT_IN,c.onCallState("a",CallRecordingCoordinator.CallState.ACTIVE));
        assertEquals(0,b.starts);
    }
    @Test public void shizukuIsMandatoryEvenWithOptIn() {
        FakeBackend b=new FakeBackend();CallRecordingCoordinator c=new CallRecordingCoordinator(b);
        c.setUserOptedIn(true);
        assertEquals(CallRecordingCoordinator.Result.BLOCKED_SHIZUKU,c.onCallState("a",CallRecordingCoordinator.CallState.ACTIVE));
        assertEquals(0,b.starts);
    }
    @Test public void ringingAndDialingNeverStartCapture() {
        FakeBackend b=new FakeBackend();CallRecordingCoordinator c=ready(b);
        c.onCallState("a",CallRecordingCoordinator.CallState.RINGING);
        c.onCallState("a",CallRecordingCoordinator.CallState.DIALING);
        c.onCallState("a",CallRecordingCoordinator.CallState.CONNECTING);
        assertEquals(0,b.starts);
    }
    @Test public void activeStartsOnceAndDisconnectStopsOnce() {
        FakeBackend b=new FakeBackend();CallRecordingCoordinator c=ready(b);
        assertEquals(CallRecordingCoordinator.Result.STARTED,c.onCallState("a",CallRecordingCoordinator.CallState.ACTIVE));
        assertEquals(CallRecordingCoordinator.Result.ALREADY_RECORDING,c.onCallState("a",CallRecordingCoordinator.CallState.ACTIVE));
        assertEquals(CallRecordingCoordinator.Result.STOPPED,c.onCallState("a",CallRecordingCoordinator.CallState.DISCONNECTED));
        assertEquals(CallRecordingCoordinator.Result.NO_CHANGE,c.onCallRemoved("a"));
        assertEquals(1,b.starts);assertEquals(1,b.stops);
    }
    @Test public void otherCallCannotTakeOverRecording() {
        FakeBackend b=new FakeBackend();CallRecordingCoordinator c=ready(b);
        c.onCallState("a",CallRecordingCoordinator.CallState.ACTIVE);
        assertEquals(CallRecordingCoordinator.Result.OTHER_CALL_ACTIVE,c.onCallState("b",CallRecordingCoordinator.CallState.ACTIVE));
        assertEquals(CallRecordingCoordinator.Result.NO_CHANGE,c.onCallRemoved("b"));
        assertEquals(1,b.starts);assertEquals(0,b.stops);
    }
    @Test public void holdStopsAndResumeStartsNewSegment() {
        FakeBackend b=new FakeBackend();CallRecordingCoordinator c=ready(b);
        c.onCallState("a",CallRecordingCoordinator.CallState.ACTIVE);
        assertEquals(CallRecordingCoordinator.Result.STOPPED,c.onCallState("a",CallRecordingCoordinator.CallState.HOLDING));
        assertEquals(CallRecordingCoordinator.Result.STARTED,c.onCallState("a",CallRecordingCoordinator.CallState.ACTIVE));
        assertEquals(2,b.starts);assertEquals(1,b.stops);
    }
    @Test public void revocationStopsAndDoesNotAutoRestart() {
        FakeBackend b=new FakeBackend();CallRecordingCoordinator c=ready(b);
        c.onCallState("a",CallRecordingCoordinator.CallState.ACTIVE);
        assertEquals(CallRecordingCoordinator.Result.STOPPED,c.setShizukuAuthorizedAndRunning(false));
        assertEquals(CallRecordingCoordinator.Result.BLOCKED_SHIZUKU,c.onCallState("a",CallRecordingCoordinator.CallState.ACTIVE));
        assertEquals(1,b.stops);assertEquals(1,b.starts);
    }
    @Test public void turningOptInOffStops() {
        FakeBackend b=new FakeBackend();CallRecordingCoordinator c=ready(b);
        c.onCallState("a",CallRecordingCoordinator.CallState.ACTIVE);
        assertEquals(CallRecordingCoordinator.Result.STOPPED,c.setUserOptedIn(false));
        assertEquals(CallRecordingCoordinator.Result.BLOCKED_OPT_IN,c.onCallState("a",CallRecordingCoordinator.CallState.ACTIVE));
        assertEquals(1,b.stops);
    }
    @Test public void partialStartFailureTriggersCleanupAndLatches() {
        FakeBackend b=new FakeBackend();b.failStart=true;
        CallRecordingCoordinator c=ready(b);
        assertEquals(CallRecordingCoordinator.Result.BACKEND_FAILURE,c.onCallState("a",CallRecordingCoordinator.CallState.ACTIVE));
        b.failStart=false;
        assertEquals(CallRecordingCoordinator.Result.BLOCKED_BACKEND_FAILURE,c.onCallState("a",CallRecordingCoordinator.CallState.ACTIVE));
        assertTrue(c.isFaulted());assertEquals(1,b.starts);assertEquals(1,b.stops);
    }
    @Test public void stopFailureLatchesAndBlocksOtherCalls() {
        FakeBackend b=new FakeBackend();CallRecordingCoordinator c=ready(b);
        c.onCallState("a",CallRecordingCoordinator.CallState.ACTIVE);
        b.failStop=true;
        assertEquals(CallRecordingCoordinator.Result.BACKEND_FAILURE,c.onCallRemoved("a"));
        assertEquals(CallRecordingCoordinator.Result.BLOCKED_BACKEND_FAILURE,c.onCallState("b",CallRecordingCoordinator.CallState.ACTIVE));
        assertEquals(1,b.starts);assertTrue(c.isFaulted());
    }
    @Test public void shutdownStopsEvenWithoutDisconnectEvent() {
        FakeBackend b=new FakeBackend();CallRecordingCoordinator c=ready(b);
        c.onCallState("a",CallRecordingCoordinator.CallState.ACTIVE);
        assertEquals(CallRecordingCoordinator.Result.STOPPED,c.shutdown());
        assertFalse(c.isRecording());assertEquals(1,b.stops);
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectEmptyToken() {
        ready(new FakeBackend()).onCallState("",CallRecordingCoordinator.CallState.ACTIVE);
    }
}
