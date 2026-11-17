package com.termux.terminal;

/**
 * The interface for communication between the terminal and its client. It is used to
 * send callbacks to the client and for sending other back data to the client like logs.
 *
 * Modified by Netrik: the callbacks that took a {@code TerminalSession} were removed together with
 * that class (it spawns a local process through JNI, which Netrik doesn't use).
 */
public interface TerminalSessionClient {

    void onTerminalCursorStateChange(boolean state);



    Integer getTerminalCursorStyle();



    void logError(String tag, String message);

    void logWarn(String tag, String message);

    void logInfo(String tag, String message);

    void logDebug(String tag, String message);

    void logVerbose(String tag, String message);

    void logStackTraceWithMessage(String tag, String message, Exception e);

    void logStackTrace(String tag, Exception e);

}
