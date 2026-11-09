package com.riansares.r4r.vector;

import com.riansares.r4r.rag.EmbeddingConfigurationException;
import com.riansares.r4r.rag.EmbeddingUnavailableException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

/** Positive JDBC evidence is required; non-connectivity SQL and embedding failures veto fallback. */
public final class PostgresFailureClassifier {
    private PostgresFailureClassifier() { }
    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("PostgreSQL operation cancelled");
    }
    public static String reason(Throwable failure) {
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        var pending=new ArrayDeque<Throwable>(); pending.add(failure);
        boolean jdbc=false, connection=false; String reason=null;
        while (!pending.isEmpty()) {
            Throwable cause=pending.removeFirst(); if (!seen.add(cause)) continue;
            if (cause instanceof CancellationException || cause instanceof InterruptedException
                    || cause instanceof java.nio.channels.ClosedByInterruptException
                    || cause instanceof java.io.InterruptedIOException && !(cause instanceof java.net.SocketTimeoutException)) {
                if (!(cause instanceof CancellationException)) Thread.currentThread().interrupt();
                return null;
            }
            if (cause instanceof EmbeddingUnavailableException || cause instanceof EmbeddingConfigurationException
                    || cause instanceof IllegalArgumentException || cause instanceof javax.net.ssl.SSLException
)
                return null;
            if (cause instanceof CannotGetJdbcConnectionException) { jdbc=true; reason="jdbc_connection"; }
            if (cause instanceof SQLException sql) {
                jdbc=true; String state=sql.getSQLState();
                if (state!=null && !state.isBlank()) {
                    if (state.matches("08[A-Z0-9]{3}") || Set.of("57P01","57P02","57P03","53300").contains(state)) reason="sqlstate_"+state;
                    else if (sql instanceof SQLTimeoutException && Set.of("HYT00","HYT01").contains(state)) reason="jdbc_timeout";
                    else return null; // Authentication, syntax, data/schema errors and SQL cancellation veto fallback.
                } else if (sql instanceof SQLTimeoutException) reason="jdbc_timeout";
                else if (sql instanceof SQLTransientConnectionException) reason="jdbc_connection";
                if (sql.getNextException()!=null) pending.add(sql.getNextException());
            }
            connection |= cause instanceof java.net.ConnectException || cause instanceof java.net.SocketTimeoutException
                    || cause instanceof java.net.UnknownHostException;
            if (cause.getCause()!=null) pending.add(cause.getCause());
        }
        checkCancelled();
        return jdbc ? reason!=null ? reason : connection ? "jdbc_transport" : null : null;
    }
}
