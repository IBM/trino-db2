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
package io.trino.plugin.db2;

import io.trino.plugin.jdbc.BaseJdbcClient;
import io.trino.plugin.jdbc.BaseJdbcConfig;
import io.trino.plugin.jdbc.ColumnMapping;
import io.trino.plugin.jdbc.ConnectionFactory;
import io.trino.plugin.jdbc.JdbcSplit;
import io.trino.plugin.jdbc.JdbcTypeHandle;
import io.trino.plugin.jdbc.LongReadFunction;
import io.trino.plugin.jdbc.ObjectReadFunction;
import io.trino.plugin.jdbc.ObjectWriteFunction;
import io.trino.plugin.jdbc.WriteMapping;
import io.trino.spi.TrinoException;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.type.LongTimestamp;
import io.trino.spi.type.TimestampType;
import io.trino.spi.type.Type;
import io.trino.spi.type.TypeManager;
import io.trino.spi.type.VarcharType;

import javax.inject.Inject;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static io.trino.plugin.jdbc.JdbcErrorCode.JDBC_ERROR;
import static io.trino.plugin.jdbc.StandardColumnMappings.fromLongTimestamp;
import static io.trino.plugin.jdbc.StandardColumnMappings.timestampWriteFunction;
import static io.trino.plugin.jdbc.StandardColumnMappings.timestampWriteFunctionUsingSqlTimestamp;
import static io.trino.plugin.jdbc.StandardColumnMappings.toLongTimestamp;
import static io.trino.plugin.jdbc.StandardColumnMappings.toTrinoTimestamp;
import static io.trino.plugin.jdbc.StandardColumnMappings.varcharWriteFunction;
import static io.trino.spi.type.TimestampType.TIMESTAMP_MILLIS;
import static java.lang.String.format;
import static java.util.Locale.ENGLISH;
import static java.util.stream.Collectors.joining;

public class DB2Client
        extends BaseJdbcClient
{
    private final int varcharMaxLength;
    private static final int DB2_MAX_SUPPORTED_TIMESTAMP_PRECISION = 12;
    // java.util.LocalDateTime supports up to nanosecond precision
    private static final int MAX_LOCAL_DATE_TIME_PRECISION = 9;
    private static final String VARCHAR_FORMAT = "VARCHAR(%d)";

    @Inject
    public DB2Client(
            BaseJdbcConfig config,
            DB2Config db2config,
            ConnectionFactory connectionFactory,
            TypeManager typeManager)
            throws SQLException
    {
        super(config, "\"", connectionFactory);
        this.varcharMaxLength = db2config.getVarcharMaxLength();

        // http://stackoverflow.com/questions/16910791/getting-error-code-4220-with-null-sql-state
        System.setProperty("db2.jcc.charsetDecoderEncoder", "3");
    }

    @Override
    public Connection getConnection(ConnectorSession session, JdbcSplit split)
            throws SQLException
    {
        Connection connection = super.getConnection(session, split);
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

    @Override
    public Optional<ColumnMapping> toColumnMapping(ConnectorSession session, Connection connection, JdbcTypeHandle typeHandle)
    {
        Optional<ColumnMapping> mapping = getForcedMappingToVarchar(typeHandle);
        if (mapping.isPresent()) {
            return mapping;
        }

        switch (typeHandle.getJdbcType()) {
            case Types.TIMESTAMP:
                TimestampType timestampType = typeHandle.getDecimalDigits()
                        .map(TimestampType::createTimestampType)
                        .orElse(TIMESTAMP_MILLIS);
                return Optional.of(timestampColumnMapping(timestampType));
        }

        return super.legacyToPrestoType(session, connection, typeHandle);
    }

    public static ColumnMapping timestampColumnMapping(TimestampType timestampType)
    {
        if (timestampType.getPrecision() <= TimestampType.MAX_SHORT_PRECISION) {
            return ColumnMapping.longMapping(
                    timestampType,
                    timestampReadFunction(timestampType),
                    timestampWriteFunctionUsingSqlTimestamp(timestampType));
        }
        checkArgument(timestampType.getPrecision() <= MAX_LOCAL_DATE_TIME_PRECISION, "Precision is out of range: %s", timestampType.getPrecision());
        return ColumnMapping.objectMapping(
                timestampType,
                longtimestampReadFunction(timestampType),
                longTimestampWriteFunction(timestampType));
    }

    public static LongReadFunction timestampReadFunction(TimestampType timestampType)
    {
        checkArgument(timestampType.getPrecision() <= TimestampType.MAX_SHORT_PRECISION, "Precision is out of range: %s", timestampType.getPrecision());
        return (resultSet, columnIndex) -> toTrinoTimestamp(timestampType, resultSet.getTimestamp(columnIndex).toLocalDateTime());
    }

    /**
     * Customized timestampReadFunction to convert timestamp type to LocalDateTime type.
     * Notice that it's because Db2 JDBC driver doesn't support {@link ResetSet#getObject(index, Class<T>)}.
     *
     * @param timestampType
     * @return
     */
    private static ObjectReadFunction longtimestampReadFunction(TimestampType timestampType)
    {
        checkArgument(timestampType.getPrecision() <= MAX_LOCAL_DATE_TIME_PRECISION,
                "Precision is out of range: %s", timestampType.getPrecision());
        return ObjectReadFunction.of(
                LongTimestamp.class,
                (resultSet, columnIndex) -> toLongTimestamp(timestampType, resultSet.getTimestamp(columnIndex).toLocalDateTime()));
    }

    /**
     * Customized timestampWriteFunction.
     * Notice that it's because Db2 JDBC driver doesn't support {@link PreparedStatement#setObject(index, LocalDateTime)}.
     * @param timestampType
     * @return
     */
    private static ObjectWriteFunction longTimestampWriteFunction(TimestampType timestampType)
    {
        checkArgument(timestampType.getPrecision() > TimestampType.MAX_SHORT_PRECISION, "Precision is out of range: %s", timestampType.getPrecision());
        return ObjectWriteFunction.of(
                LongTimestamp.class,
                (statement, index, value) -> statement.setTimestamp(index, Timestamp.valueOf(fromLongTimestamp(value, timestampType.getPrecision()))));
    }

    /**
     * To map data types when generating SQL.
     */
    @Override
    public WriteMapping toWriteMapping(ConnectorSession session, Type type)
    {
        if (type instanceof VarcharType) {
            VarcharType varcharType = (VarcharType) type;
            String dataType;

            if (varcharType.isUnbounded()) {
                dataType = format(VARCHAR_FORMAT, this.varcharMaxLength);
            }
            else if (varcharType.getBoundedLength() > this.varcharMaxLength) {
                dataType = format("CLOB(%d)", varcharType.getBoundedLength());
            }
            else if (varcharType.getBoundedLength() < this.varcharMaxLength) {
                dataType = format(VARCHAR_FORMAT, varcharType.getBoundedLength());
            }
            else {
                dataType = format(VARCHAR_FORMAT, this.varcharMaxLength);
            }

            return WriteMapping.sliceMapping(dataType, varcharWriteFunction());
        }

        if (type instanceof TimestampType) {
            TimestampType timestampType = (TimestampType) type;
            verify(timestampType.getPrecision() <= DB2_MAX_SUPPORTED_TIMESTAMP_PRECISION);
            return WriteMapping.longMapping(format("TIMESTAMP(%s)", timestampType.getPrecision()), timestampWriteFunction(timestampType));
        }

        return super.legacyToWriteMapping(session, type);
    }

    @Override
    protected void renameTable(ConnectorSession session, String catalogName, String schemaName, String tableName, SchemaTableName newTable)
    {
        try (Connection connection = connectionFactory.openConnection(session)) {
            String newTableName = newTable.getTableName();
            if (connection.getMetaData().storesUpperCaseIdentifiers()) {
                newTableName = newTableName.toUpperCase(ENGLISH);
            }
            // Specifies the new name for the table without a schema name
            String sql = format(
                    "RENAME TABLE %s TO %s",
                    quoted(catalogName, schemaName, tableName),
                    quoted(newTableName));
            execute(connection, sql);
        }
        catch (SQLException e) {
            throw new TrinoException(JDBC_ERROR, e);
        }
    }

    @Override
    protected void copyTableSchema(Connection connection, String catalogName, String schemaName, String tableName, String newTableName, List<String> columnNames)
    {
        String sql = format(
                "CREATE TABLE %s AS (SELECT %s FROM %s) WITH NO DATA",
                quoted(catalogName, schemaName, newTableName),
                columnNames.stream()
                        .map(this::quoted)
                        .collect(joining(", ")),
                quoted(catalogName, schemaName, tableName));
        execute(connection, sql);
    }
}
