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
package com.facebook.presto.plugin.db2;

import com.facebook.presto.plugin.jdbc.JdbcColumnHandle;
import com.facebook.presto.plugin.jdbc.QueryBuilder;
import com.facebook.presto.spi.ColumnHandle;
import com.facebook.presto.spi.predicate.Domain;
import com.facebook.presto.spi.predicate.TupleDomain;
import com.facebook.presto.spi.type.BigintType;
import com.facebook.presto.spi.type.BooleanType;
import com.facebook.presto.spi.type.DateType;
import com.facebook.presto.spi.type.DoubleType;
import com.facebook.presto.spi.type.TimestampType;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.spi.type.VarcharType;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import io.airlift.log.Logger;

import java.util.List;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Iterables.transform;

public class DB2QueryBuilder extends QueryBuilder
{
    private static final Logger log = Logger.get(DB2QueryBuilder.class);

    public DB2QueryBuilder(String quote)
    {
        super(quote);
    }

    @Override
    public String buildSql(String catalog, String schema, String table, List<JdbcColumnHandle> columns,
            TupleDomain<ColumnHandle> tupleDomain)
    {
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT ");
        Joiner.on(", ").appendTo(sql, transform(columns, column -> quote(column.getColumnName())));
        if (columns.isEmpty()) {
            sql.append("null");
        }

        sql.append(" FROM ");
        if (!isNullOrEmpty(catalog)) {
            sql.append(quote(catalog)).append('.');
        }
        if (!isNullOrEmpty(schema)) {
            sql.append(quote(schema)).append('.');
        }
        sql.append(quote(table));

        List<String> clauses = toConjuncts(columns, tupleDomain);
        if (!clauses.isEmpty()) {
            sql.append(" WHERE ").append(Joiner.on(" AND ").join(clauses));
        }
        // for IBM DB2
        sql.append(" WITH UR");
        log.info("SQL from DB2QueryBuilder: " + sql.toString());
        return sql.toString();
    }

    private List<String> toConjuncts(List<JdbcColumnHandle> columns, TupleDomain<ColumnHandle> tupleDomain)
    {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (JdbcColumnHandle column : columns) {
            Type type = column.getColumnType();

            if (type.equals(BigintType.BIGINT) || type.equals(DoubleType.DOUBLE) || type.equals(BooleanType.BOOLEAN)
                    || type.equals(VarcharType.VARCHAR) || type.equals(DateType.DATE)
                    || type.equals(TimestampType.TIMESTAMP)) {
                Domain domain = tupleDomain.getDomains().get().get(column);
                if (domain != null) {
                    String predicate = toPredicate(column.getColumnName(), domain, type);
                    builder.add(predicate);
                }
            }
        }
        return builder.build();
    }
}
