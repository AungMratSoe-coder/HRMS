package com.ams.hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps one row of a {@link ResultSet} to a domain object. Implemented as a
 * lambda next to each repository query, keeping row-to-object mapping local
 * to the query that produced it.
 *
 * @param <T> the mapped type
 */
@FunctionalInterface
public interface RowMapper<T> {

    T map(ResultSet resultSet) throws SQLException;
}
