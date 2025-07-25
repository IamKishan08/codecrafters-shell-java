package com.enterprise.hr.resume.contract;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Base interface for database query contracts
 */
public interface QueryContract {
    
    /**
     * Execute a SELECT query and return results as a list of maps
     * @param sql SQL query string
     * @param params Query parameters
     * @return List of result rows as maps
     * @throws SQLException if database error occurs
     */
    List<Map<String, Object>> executeQuery(String sql, Object... params) throws SQLException;
    
    /**
     * Execute an UPDATE, INSERT, or DELETE query
     * @param sql SQL query string
     * @param params Query parameters
     * @return Number of affected rows
     * @throws SQLException if database error occurs
     */
    int executeUpdate(String sql, Object... params) throws SQLException;
    
    /**
     * Execute batch UPDATE, INSERT, or DELETE queries
     * @param sql SQL query string
     * @param batchParams List of parameter arrays for batch execution
     * @return Array of affected row counts
     * @throws SQLException if database error occurs
     */
    int[] executeBatch(String sql, List<Object[]> batchParams) throws SQLException;
    
    /**
     * Set the database connection for this contract
     * @param connection Database connection
     */
    void setConnection(Connection connection);
    
    /**
     * Get the current database connection
     * @return Database connection
     */
    Connection getConnection();
}