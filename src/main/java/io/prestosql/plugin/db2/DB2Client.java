/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.plugin.db2;

import io.prestosql.plugin.jdbc.BaseJdbcClient;
import io.prestosql.plugin.jdbc.BaseJdbcConfig;
import io.prestosql.plugin.jdbc.ConnectionFactory;
import io.prestosql.plugin.jdbc.JdbcIdentity;
import io.prestosql.plugin.jdbc.JdbcSplit;
import io.prestosql.plugin.jdbc.WriteMapping;
import io.prestosql.spi.PrestoException;
import io.prestosql.spi.connector.ConnectorSession;
import io.prestosql.spi.connector.SchemaTableName;
import io.prestosql.spi.type.Type;
import io.prestosql.spi.type.TypeManager;
import io.prestosql.spi.type.VarcharType;

import javax.inject.Inject;

import java.sql.Connection;
import java.sql.SQLException;

import static io.prestosql.plugin.jdbc.JdbcErrorCode.JDBC_ERROR;
import static io.prestosql.plugin.jdbc.StandardColumnMappings.varcharWriteFunction;
import static io.prestosql.spi.type.Varchars.isVarcharType;
import static java.lang.String.format;

public class DB2Client
        extends BaseJdbcClient
{
	private final int varcharMaxLength;

    @Inject
    public DB2Client(
            BaseJdbcConfig config,
            DB2Config db2config,
            ConnectionFactory connectionFactory,
            TypeManager typeManager) throws SQLException
    {
        super(config, "\"", connectionFactory);
        this.varcharMaxLength = db2config.getVarcharMaxLength();

        // http://stackoverflow.com/questions/16910791/getting-error-code-4220-with-null-sql-state
        System.setProperty("db2.jcc.charsetDecoderEncoder", "3");
    }

    @Override
    public Connection getConnection(JdbcIdentity identity, JdbcSplit split)
            throws SQLException
    {
        Connection connection = super.getConnection(identity, split);
        try {
            // TRANSACTION_READ_UNCOMMITTED = Uncommitted read
            // http://www.ibm.com/developerworks/data/library/techarticle/dm-0509schuetz/
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
        }
        catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }
    
    /**
     * To map data types when generating SQL.
     */
    @Override
    public WriteMapping toWriteMapping(ConnectorSession session, Type type)
    {
    	if (isVarcharType(type)) {
            VarcharType varcharType = (VarcharType) type;
            String dataType;
            
            if (varcharType.isUnbounded()) {
            	dataType = "VARCHAR(" + this.varcharMaxLength + ")";
            }
            else if (varcharType.getBoundedLength() > this.varcharMaxLength) {
                dataType = "CLOB(" + varcharType.getBoundedLength() + ")";
            }
            else if (varcharType.getBoundedLength() < this.varcharMaxLength) {
                dataType = "VARCHAR(" + varcharType.getBoundedLength() + ")";
            }
            else {
            	dataType = "VARCHAR(" + this.varcharMaxLength + ")";
            }

            return WriteMapping.sliceMapping(dataType, varcharWriteFunction());
        }
    	
        return super.toWriteMapping(session, type);
    }

    @Override
    protected void renameTable(JdbcIdentity identity, String catalogName, String schemaName, String tableName, SchemaTableName newTable)
    {
    	// TODO figure if it supports changing schema while renaming table

        String sql = format(
                "RENAME TABLE %s TO %s",
                quoted(catalogName, schemaName, tableName),
                quoted(newTable.getTableName()));

        try (Connection connection = connectionFactory.openConnection(identity)) {
            execute(connection, sql);
        }
        catch (SQLException e) {
            throw new PrestoException(JDBC_ERROR, e);
        }
    }
}
