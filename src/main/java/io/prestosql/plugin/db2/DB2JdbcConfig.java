package io.prestosql.plugin.db2;

import io.airlift.configuration.Config;

public class DB2JdbcConfig {
    private boolean sslConnection;

    public DB2JdbcConfig() {
    }

    public boolean isSslConnection() {
        return this.sslConnection;
    }

    @Config("connection-ssl")
    public DB2JdbcConfig setSslConnection(boolean sslConnection) {
        this.sslConnection = sslConnection;
        return this;
    }

}
