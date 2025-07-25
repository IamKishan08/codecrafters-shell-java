package com.enterprise.hr.example;

import com.enterprise.hr.manager.PartyManager;
import com.enterprise.hr.resume.processor.ResumeDeduplicationProcessorOTJ;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Example class demonstrating the usage of Resume Deduplication functionality
 * This would typically be called from a main application or scheduled job
 */
public class ResumeDeduplicationExample {
    
    private static final Logger logger = Logger.getLogger(ResumeDeduplicationExample.class.getName());
    
    public static void main(String[] args) {
        // Example usage of the resume deduplication system
        
        // Database connection parameters (these would come from configuration)
        String databaseUrl = "jdbc:mysql://localhost:3306/hr_database";
        String username = "hr_user";
        String password = "hr_password";
        
        // Create PartyManager instance
        PartyManager partyManager = new PartyManager(databaseUrl, username, password);
        
        try {
            // Validate database connection first
            logger.info("Validating database connection...");
            if (!partyManager.validateDatabase()) {
                logger.severe("Database validation failed. Exiting.");
                return;
            }
            
            // Get current statistics before deduplication
            logger.info("Getting initial deduplication statistics...");
            Map<String, Object> initialStats = partyManager.getResumeDeduplicationStats();
            logger.info("Initial stats: " + initialStats);
            
            // Preview some duplicate groups (optional)
            logger.info("Previewing duplicate resume groups...");
            List<Map<String, Object>> duplicatePreview = partyManager.previewDuplicateResumes(5);
            logger.info("Found " + duplicatePreview.size() + " duplicate groups to preview");
            
            for (Map<String, Object> group : duplicatePreview) {
                logger.info("Duplicate group: " + group);
            }
            
            // Execute the deduplication process
            logger.info("Starting resume deduplication process...");
            ResumeDeduplicationProcessorOTJ.DeduplicationResult result = 
                partyManager.executeResumeDeduplication(50); // Process 50 groups per batch
            
            // Generate and log the report
            String report = partyManager.formatDeduplicationReport(result);
            logger.info("Deduplication completed. Report:\n" + report);
            
            // Get final statistics
            Map<String, Object> finalStats = partyManager.getResumeDeduplicationStats();
            logger.info("Final stats: " + finalStats);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during resume deduplication example", e);
        }
    }
    
    /**
     * Example of running deduplication as a scheduled job
     */
    public static void runAsScheduledJob(String databaseUrl, String username, String password) {
        PartyManager partyManager = new PartyManager(databaseUrl, username, password);
        
        try {
            // Validate database first
            if (!partyManager.validateDatabase()) {
                logger.severe("Database validation failed for scheduled job");
                return;
            }
            
            // Execute deduplication with default settings
            ResumeDeduplicationProcessorOTJ.DeduplicationResult result = 
                partyManager.executeResumeDeduplication();
            
            if (result.isSuccess()) {
                logger.info("Scheduled deduplication completed successfully: " + 
                           result.getProcessedGroups() + " groups processed, " + 
                           result.getTotalDuplicatesRemoved() + " duplicates removed");
            } else {
                logger.severe("Scheduled deduplication failed with " + result.getErrors().size() + " errors");
                for (String error : result.getErrors()) {
                    logger.severe("Error: " + error);
                }
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during scheduled deduplication job", e);
        }
    }
    
    /**
     * Example of getting statistics only (for monitoring/reporting)
     */
    public static Map<String, Object> getStatisticsForReporting(String databaseUrl, String username, String password) {
        PartyManager partyManager = new PartyManager(databaseUrl, username, password);
        
        try {
            return partyManager.getResumeDeduplicationStats();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting deduplication statistics", e);
            return null;
        }
    }
}