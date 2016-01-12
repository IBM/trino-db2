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
import com.facebook.presto.spi.predicate.Range;
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

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Iterables.getOnlyElement;
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

    private String toPredicate(String columnName, Domain domain, Type type)
    {
        checkArgument(domain.getType().isOrderable(), "Domain type must be orderable");

        if (domain.getValues().isNone()) {
            return domain.isNullAllowed() ? quote(columnName) + " IS NULL" : "FALSE";
        }

        if (domain.getValues().isAll()) {
            return domain.isNullAllowed() ? "TRUE" : quote(columnName) + " IS NOT NULL";
        }

        List<String> disjuncts = new ArrayList<>();
        List<Object> singleValues = new ArrayList<>();
        for (Range range : domain.getValues().getRanges().getOrderedRanges()) {
            checkState(!range.isAll()); // Already checked
            if (range.isSingleValue()) {
                singleValues.add(range.getLow().getValue());
            }
            else {
                List<String> rangeConjuncts = new ArrayList<>();
                if (!range.getLow().isLowerUnbounded()) {
                    switch (range.getLow().getBound()) {
                        case ABOVE:
                            rangeConjuncts.add(toPredicate(columnName, ">", range.getLow().getValue(), type));
                            break;
                        case EXACTLY:
                            rangeConjuncts.add(toPredicate(columnName, ">=", range.getLow().getValue(), type));
                            break;
                        case BELOW:
                            throw new IllegalArgumentException("Low Marker should never use BELOW bound: " + range);
                        default:
                            throw new AssertionError("Unhandled bound: " + range.getLow().getBound());
                    }
                }
                if (!range.getHigh().isUpperUnbounded()) {
                    switch (range.getHigh().getBound()) {
                        case ABOVE:
                            throw new IllegalArgumentException("High Marker should never use ABOVE bound: " + range);
                        case EXACTLY:
                            rangeConjuncts.add(toPredicate(columnName, "<=", range.getHigh().getValue(), type));
                            break;
                        case BELOW:
                            rangeConjuncts.add(toPredicate(columnName, "<", range.getHigh().getValue(), type));
                            break;
                        default:
                            throw new AssertionError("Unhandled bound: " + range.getHigh().getBound());
                    }
                }
                // If rangeConjuncts is null, then the range was ALL, which
                // should already have been checked for
                checkState(!rangeConjuncts.isEmpty());
                disjuncts.add("(" + Joiner.on(" AND ").join(rangeConjuncts) + ")");
            }
        }

        // Add back all of the possible single values either as an equality or
        // an IN predicate
        if (singleValues.size() == 1) {
            disjuncts.add(toPredicate(columnName, "=", getOnlyElement(singleValues), type));
        }
        else if (singleValues.size() > 1) {
            disjuncts.add(quote(columnName) + " IN ("
                    + Joiner.on(",").join(transform(singleValues, QueryBuilder::encode)) + ")");
        }

        // Add nullability disjuncts
        checkState(!disjuncts.isEmpty());
        if (domain.isNullAllowed()) {
            disjuncts.add(quote(columnName) + " IS NULL");
        }

        return "(" + Joiner.on(" OR ").join(disjuncts) + ")";
    }
}
