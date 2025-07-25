package com.enterprise.hr.manager;

import com.enterprise.hr.resume.contract.ResumeDeduplicationContract;
import com.enterprise.hr.resume.processor.ResumeDeduplicationProcessorOTJ;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Party Manager class for handling HR-related operations
 * Includes integration with resume deduplication functionality
 */
public class PartyManager {
    
    private static final Logger logger = Logger.getLogger(PartyManager.class.getName());
    
    private final String databaseUrl;
    private final String username;
    private final String password;
    
    public PartyManager(String databaseUrl, String username, String password) {
        this.databaseUrl = databaseUrl;
        this.username = username;
        this.password = password;
    }
    
    /**
     * Execute resume deduplication process
     * This method integrates with the ResumeDeduplicationContract and ProcessorOTJ
     * 
     * @param batchSize Number of duplicate groups to process in each batch
     * @return DeduplicationResult containing statistics and results
     */
    public ResumeDeduplicationProcessorOTJ.DeduplicationResult executeResumeDeduplication(int batchSize) {
        logger.info("Starting resume deduplication process with batch size: " + batchSize);
        
        Connection connection = null;
        try {
            // Establish database connection
            connection = getDatabaseConnection();
            
            // Create and execute the deduplication processor
            ResumeDeduplicationProcessorOTJ processor = new ResumeDeduplicationProcessorOTJ(connection, batchSize);
            ResumeDeduplicationProcessorOTJ.DeduplicationResult result = processor.executeDeduplication();
            
            logger.info("Resume deduplication completed: " + result);
            return result;
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error during resume deduplication", e);
            
            ResumeDeduplicationProcessorOTJ.DeduplicationResult errorResult = 
                new ResumeDeduplicationProcessorOTJ.DeduplicationResult();
            errorResult.setSuccess(false);
            errorResult.addError("Database connection error: " + e.getMessage());
            return errorResult;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error during resume deduplication", e);
            
            ResumeDeduplicationProcessorOTJ.DeduplicationResult errorResult = 
                new ResumeDeduplicationProcessorOTJ.DeduplicationResult();
            errorResult.setSuccess(false);
            errorResult.addError("Unexpected error: " + e.getMessage());
            return errorResult;
            
        } finally {
            // Clean up database connection
            if (connection != null) {
                try {
                    connection.close();
                    logger.fine("Database connection closed");
                } catch (SQLException e) {
                    logger.log(Level.WARNING, "Error closing database connection", e);
                }
            }
        }
    }
    
    /**
     * Execute resume deduplication with default batch size
     * @return DeduplicationResult containing statistics and results
     */
    public ResumeDeduplicationProcessorOTJ.DeduplicationResult executeResumeDeduplication() {
        return executeResumeDeduplication(100); // Default batch size
    }
    
    /**
     * Get deduplication statistics without executing the deduplication process
     * @return Map containing current deduplication statistics
     */
    public Map<String, Object> getResumeDeduplicationStats() {
        logger.info("Retrieving resume deduplication statistics");
        
        Connection connection = null;
        try {
            connection = getDatabaseConnection();
            
            ResumeDeduplicationContract contract = new ResumeDeduplicationContract();
            contract.setConnection(connection);
            
            Map<String, Object> stats = contract.getDeduplicationStats();
            logger.info("Retrieved deduplication stats: " + stats);
            return stats;
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error retrieving deduplication stats", e);
            throw new RuntimeException("Failed to retrieve deduplication statistics", e);
            
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.log(Level.WARNING, "Error closing database connection", e);
                }
            }
        }
    }
    
    /**
     * Preview duplicate resume groups without executing deduplication
     * @param limit Maximum number of duplicate groups to return
     * @return List of duplicate resume group information
     */
    public java.util.List<Map<String, Object>> previewDuplicateResumes(int limit) {
        logger.info("Previewing duplicate resume groups, limit: " + limit);
        
        Connection connection = null;
        try {
            connection = getDatabaseConnection();
            
            ResumeDeduplicationContract contract = new ResumeDeduplicationContract();
            contract.setConnection(connection);
            
            java.util.List<Map<String, Object>> duplicates = contract.findDuplicateResumesByTelephoneEmail(limit);
            logger.info("Found " + duplicates.size() + " duplicate groups");
            return duplicates;
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error previewing duplicate resumes", e);
            throw new RuntimeException("Failed to preview duplicate resumes", e);
            
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.log(Level.WARNING, "Error closing database connection", e);
                }
            }
        }
    }
    
    /**
     * Validate database connection and schema
     * @return true if database is accessible and has required tables
     */
    public boolean validateDatabase() {
        logger.info("Validating database connection and schema");
        
        Connection connection = null;
        try {
            connection = getDatabaseConnection();
            
            // Test basic connectivity
            if (connection == null || connection.isClosed()) {
                logger.severe("Database connection is not available");
                return false;
            }
            
            // Test required tables exist by running a simple query
            ResumeDeduplicationContract contract = new ResumeDeduplicationContract();
            contract.setConnection(connection);
            
            // This will throw SQLException if tables don't exist
            contract.getDeduplicationStats();
            
            logger.info("Database validation successful");
            return true;
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database validation failed", e);
            return false;
            
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.log(Level.WARNING, "Error closing database connection during validation", e);
                }
            }
        }
    }
    
    /**
     * Get database connection
     * @return Database connection
     * @throws SQLException if connection cannot be established
     */
    private Connection getDatabaseConnection() throws SQLException {
        logger.fine("Establishing database connection to: " + databaseUrl);
        
        try {
            Connection connection = DriverManager.getConnection(databaseUrl, username, password);
            
            // Set connection properties for optimal performance
            connection.setAutoCommit(true); // Will be changed to false during transactions
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            
            logger.fine("Database connection established successfully");
            return connection;
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to establish database connection", e);
            throw e;
        }
    }
    
    /**
     * Utility method to format deduplication results for reporting
     * @param result DeduplicationResult to format
     * @return Formatted string report
     */
    public String formatDeduplicationReport(ResumeDeduplicationProcessorOTJ.DeduplicationResult result) {
        if (result == null) {
            return "No deduplication result available";
        }
        
        StringBuilder report = new StringBuilder();
        report.append("Resume Deduplication Report\n");
        report.append("===========================\n");
        report.append("Status: ").append(result.isSuccess() ? "SUCCESS" : "FAILED").append("\n");
        report.append("Groups Processed: ").append(result.getProcessedGroups()).append("\n");
        report.append("Duplicates Removed: ").append(result.getTotalDuplicatesRemoved()).append("\n");
        
        if (result.getInitialStats() != null) {
            report.append("Initial Statistics: ").append(result.getInitialStats()).append("\n");
        }
        
        if (result.getFinalStats() != null) {
            report.append("Final Statistics: ").append(result.getFinalStats()).append("\n");
        }
        
        if (!result.getErrors().isEmpty()) {
            report.append("Errors (").append(result.getErrors().size()).append("):\n");
            for (String error : result.getErrors()) {
                report.append("  - ").append(error).append("\n");
            }
        }
        
        return report.toString();
    }
}