package com.campusflow.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ServerConfig}.
 *
 * Verifies that server.properties defaults (ADR-005) are loaded correctly.
 */
@DisplayName("ServerConfig")
class ServerConfigTest {

    @Test
    @DisplayName("load() reads server.port = 9090 from server.properties")
    void loadsPort() {
        ServerConfig config = ServerConfig.load();
        assertEquals(9090, config.getPort());
    }

    @Test
    @DisplayName("load() reads server.thread.pool.size = 20")
    void loadsPoolSize() {
        ServerConfig config = ServerConfig.load();
        assertEquals(20, config.getThreadPoolSize());
    }

    @Test
    @DisplayName("load() reads server.session.timeout.minutes = 30")
    void loadsSessionTimeout() {
        ServerConfig config = ServerConfig.load();
        assertEquals(30, config.getSessionTimeoutMinutes());
    }

    @Test
    @DisplayName("load() reads server.db.path = campusflow.db")
    void loadsDbPath() {
        ServerConfig config = ServerConfig.load();
        assertEquals("campusflow.db", config.getDbPath());
    }

    @Test
    @DisplayName("All getters return consistent values")
    void allGettersConsistent() {
        ServerConfig c1 = ServerConfig.load();
        ServerConfig c2 = ServerConfig.load();
        assertEquals(c1.getPort(),                  c2.getPort());
        assertEquals(c1.getThreadPoolSize(),         c2.getThreadPoolSize());
        assertEquals(c1.getSessionTimeoutMinutes(),  c2.getSessionTimeoutMinutes());
        assertEquals(c1.getDbPath(),                 c2.getDbPath());
    }

    @Test
    @DisplayName("toString includes all configuration values")
    void toStringIncludesValues() {
        String s = ServerConfig.load().toString();
        assertTrue(s.contains("9090"));
        assertTrue(s.contains("20"));
        assertTrue(s.contains("30"));
        assertTrue(s.contains("campusflow.db"));
    }
}
