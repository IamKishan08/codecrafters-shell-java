package com.enterprise.hr.resume.contract;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Custom SQL contract for resume deduplication operations
 * Extends QueryContract to handle finding duplicate resumes and managing tracker data
 */
public class ResumeDeduplicationContract implements QueryContract {
    
    private static final Logger logger = Logger.getLogger(ResumeDeduplicationContract.class.getName());
    private Connection connection;
    
    @Override
    public void setConnection(Connection connection) {
        this.connection = connection;
    }
    
    @Override
    public Connection getConnection() {
        return connection;
    }
    
    @Override
    public List<Map<String, Object>> executeQuery(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            setParameters(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    int columnCount = rs.getMetaData().getColumnCount();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = rs.getMetaData().getColumnName(i);
                        row.put(columnName, rs.getObject(i));
                    }
                    results.add(row);
                }
            }
        }
        return results;
    }
    
    @Override
    public int executeUpdate(String sql, Object... params) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            setParameters(stmt, params);
            return stmt.executeUpdate();
        }
    }
    
    @Override
    public int[] executeBatch(String sql, List<Object[]> batchParams) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (Object[] params : batchParams) {
                setParameters(stmt, params);
                stmt.addBatch();
            }
            return stmt.executeBatch();
        }
    }
    
    /**
     * Find duplicate resumes by telephone and email combination (case insensitive)
     * @param batchSize Maximum number of duplicate groups to return
     * @return List of duplicate resume groups
     * @throws SQLException if database error occurs
     */
    public List<Map<String, Object>> findDuplicateResumesByTelephoneEmail(int batchSize) throws SQLException {
        String sql = "SELECT " +
            "LOWER(TRIM(r.telephone)) as telephone, " +
            "LOWER(TRIM(r.email)) as email, " +
            "GROUP_CONCAT(r.resumeId ORDER BY r.resumeId) as resumeIds, " +
            "COUNT(*) as duplicateCount " +
            "FROM Resume r " +
            "WHERE r.telephone IS NOT NULL " +
            "AND r.email IS NOT NULL " +
            "AND TRIM(r.telephone) != '' " +
            "AND TRIM(r.email) != '' " +
            "GROUP BY LOWER(TRIM(r.telephone)), LOWER(TRIM(r.email)) " +
            "HAVING COUNT(*) > 1 " +
            "LIMIT ?";
        
        logger.info("Finding duplicate resumes by telephone and email combination, batch size: " + batchSize);
        return executeQuery(sql, batchSize);
    }
    
    /**
     * Get detailed resume data for a group of duplicate resumes
     * @param resumeIds Comma-separated list of resume IDs
     * @return List of resume details with tracker information
     * @throws SQLException if database error occurs
     */
    public List<Map<String, Object>> getResumeGroupDetails(String resumeIds) throws SQLException {
        String sql = "SELECT " +
            "r.resumeId, " +
            "r.telephone, " +
            "r.email, " +
            "r.expEnteredDate, " +
            "dt.priority as dailyTrackerPriority, " +
            "dt.lastUpdated as dailyTrackerUpdated, " +
            "pdt.priority as preDailyTrackerPriority, " +
            "pdt.lastUpdated as preDailyTrackerUpdated " +
            "FROM Resume r " +
            "LEFT JOIN DailyTracker dt ON r.resumeId = dt.resumeId " +
            "LEFT JOIN PreDailyTracker pdt ON r.resumeId = pdt.resumeId " +
            "WHERE r.resumeId IN (" + resumeIds + ") " +
            "ORDER BY " +
            "CASE " +
            "WHEN dt.priority IS NOT NULL THEN 1 " +
            "WHEN pdt.priority IS NOT NULL THEN 2 " +
            "ELSE 3 " +
            "END, " +
            "dt.priority DESC, " +
            "pdt.priority DESC, " +
            "r.expEnteredDate DESC";
        
        logger.info("Getting resume group details for IDs: " + resumeIds);
        return executeQuery(sql);
    }
    
    /**
     * Update tracker tables to point to the kept resume ID
     * @param oldResumeId Resume ID to be replaced
     * @param newResumeId Resume ID to keep
     * @throws SQLException if database error occurs
     */
    public void updateTrackerTables(Long oldResumeId, Long newResumeId) throws SQLException {
        // Update DailyTracker
        String updateDailyTracker = "UPDATE DailyTracker " +
            "SET resumeId = ? " +
            "WHERE resumeId = ? " +
            "AND NOT EXISTS (SELECT 1 FROM DailyTracker WHERE resumeId = ?)";
        
        // Update PreDailyTracker
        String updatePreDailyTracker = "UPDATE PreDailyTracker " +
            "SET resumeId = ? " +
            "WHERE resumeId = ? " +
            "AND NOT EXISTS (SELECT 1 FROM PreDailyTracker WHERE resumeId = ?)";
        
        logger.info("Updating tracker tables: oldResumeId=" + oldResumeId + ", newResumeId=" + newResumeId);
        
        int dailyTrackerUpdated = executeUpdate(updateDailyTracker, newResumeId, oldResumeId, newResumeId);
        int preDailyTrackerUpdated = executeUpdate(updatePreDailyTracker, newResumeId, oldResumeId, newResumeId);
        
        logger.info("Updated " + dailyTrackerUpdated + " DailyTracker records and " + 
                   preDailyTrackerUpdated + " PreDailyTracker records");
    }
    
    /**
     * Delete duplicate resume records
     * @param resumeIdsToDelete List of resume IDs to delete
     * @throws SQLException if database error occurs
     */
    public void deleteDuplicateResumes(List<Long> resumeIdsToDelete) throws SQLException {
        if (resumeIdsToDelete.isEmpty()) {
            return;
        }
        
        String placeholders = String.join(",", resumeIdsToDelete.stream().map(id -> "?").toArray(String[]::new));
        String sql = "DELETE FROM Resume WHERE resumeId IN (" + placeholders + ")";
        
        logger.info("Deleting " + resumeIdsToDelete.size() + " duplicate resume records");
        
        Object[] params = resumeIdsToDelete.toArray();
        int deletedCount = executeUpdate(sql, params);
        
        logger.info("Successfully deleted " + deletedCount + " resume records");
    }
    
    /**
     * Get statistics about the deduplication process
     * @return Map containing deduplication statistics
     * @throws SQLException if database error occurs
     */
    public Map<String, Object> getDeduplicationStats() throws SQLException {
        String sql = "SELECT " +
            "COUNT(*) as totalResumes, " +
            "COUNT(CASE WHEN telephone IS NOT NULL AND email IS NOT NULL THEN 1 END) as resumesWithTelephoneEmail, " +
            "COUNT(DISTINCT CONCAT(LOWER(TRIM(telephone)), '|', LOWER(TRIM(email)))) as uniqueCombinations " +
            "FROM Resume " +
            "WHERE telephone IS NOT NULL AND email IS NOT NULL";
        
        List<Map<String, Object>> results = executeQuery(sql);
        return results.isEmpty() ? new HashMap<>() : results.get(0);
    }
    
    /**
     * Helper method to set parameters in PreparedStatement
     */
    private void setParameters(PreparedStatement stmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }
}