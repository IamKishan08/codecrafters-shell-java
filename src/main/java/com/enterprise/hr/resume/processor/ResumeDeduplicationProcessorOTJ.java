package com.enterprise.hr.resume.processor;

import com.enterprise.hr.resume.contract.ResumeDeduplicationContract;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * One-Time Job processor for resume deduplication
 * Processes duplicates in batches with proper priority handling
 */
public class ResumeDeduplicationProcessorOTJ {
    
    private static final Logger logger = Logger.getLogger(ResumeDeduplicationProcessorOTJ.class.getName());
    
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 3;
    
    private final ResumeDeduplicationContract contract;
    private final Connection connection;
    private final int batchSize;
    
    public ResumeDeduplicationProcessorOTJ(Connection connection) {
        this(connection, DEFAULT_BATCH_SIZE);
    }
    
    public ResumeDeduplicationProcessorOTJ(Connection connection, int batchSize) {
        this.connection = connection;
        this.batchSize = batchSize;
        this.contract = new ResumeDeduplicationContract();
        this.contract.setConnection(connection);
    }
    
    /**
     * Execute the complete deduplication process
     * @return DeduplicationResult containing statistics about the process
     */
    public DeduplicationResult executeDeduplication() {
        DeduplicationResult result = new DeduplicationResult();
        
        try {
            logger.info("Starting resume deduplication process with batch size: " + batchSize);
            
            // Get initial statistics
            Map<String, Object> initialStats = contract.getDeduplicationStats();
            result.setInitialStats(initialStats);
            
            logger.info("Initial stats: " + initialStats);
            
            int processedGroups = 0;
            int totalDuplicatesRemoved = 0;
            
            // Process duplicates in batches
            while (true) {
                List<Map<String, Object>> duplicateGroups = contract.findDuplicateResumesByTelephoneEmail(batchSize);
                
                if (duplicateGroups.isEmpty()) {
                    logger.info("No more duplicate groups found. Deduplication complete.");
                    break;
                }
                
                logger.info("Processing batch of " + duplicateGroups.size() + " duplicate groups");
                
                for (Map<String, Object> group : duplicateGroups) {
                    try {
                        int duplicatesRemoved = processDuplicateGroup(group);
                        totalDuplicatesRemoved += duplicatesRemoved;
                        processedGroups++;
                        
                        if (processedGroups % 10 == 0) {
                            logger.info("Processed " + processedGroups + " groups, removed " + 
                                       totalDuplicatesRemoved + " duplicates so far");
                        }
                        
                    } catch (SQLException e) {
                        logger.log(Level.SEVERE, "Error processing duplicate group: " + group, e);
                        result.addError("Failed to process group: " + group + " - " + e.getMessage());
                        // Continue with next group rather than failing completely
                    }
                }
            }
            
            // Get final statistics
            Map<String, Object> finalStats = contract.getDeduplicationStats();
            result.setFinalStats(finalStats);
            result.setProcessedGroups(processedGroups);
            result.setTotalDuplicatesRemoved(totalDuplicatesRemoved);
            result.setSuccess(true);
            
            logger.info("Deduplication completed successfully. Processed " + processedGroups + 
                       " groups, removed " + totalDuplicatesRemoved + " duplicates");
            logger.info("Final stats: " + finalStats);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fatal error during deduplication process", e);
            result.setSuccess(false);
            result.addError("Fatal error: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Process a single duplicate group using priority-based logic
     * @param group Duplicate group information
     * @return Number of duplicates removed
     * @throws SQLException if database error occurs
     */
    private int processDuplicateGroup(Map<String, Object> group) throws SQLException {
        String resumeIds = (String) group.get("resumeIds");
        int duplicateCount = ((Number) group.get("duplicateCount")).intValue();
        
        logger.fine("Processing duplicate group with IDs: " + resumeIds + ", count: " + duplicateCount);
        
        // Get detailed information about each resume in the group
        List<Map<String, Object>> resumeDetails = contract.getResumeGroupDetails(resumeIds);
        
        if (resumeDetails.isEmpty()) {
            logger.warning("No resume details found for group: " + resumeIds);
            return 0;
        }
        
        // Apply deduplication logic with priority handling
        DeduplicationDecision decision = applyDeduplicationLogic(resumeDetails);
        
        if (decision.getKeepResumeId() == null || decision.getDeleteResumeIds().isEmpty()) {
            logger.warning("No clear deduplication decision for group: " + resumeIds);
            return 0;
        }
        
        // Execute deduplication with transaction management
        return executeDeduplicationWithTransaction(decision);
    }
    
    /**
     * Apply deduplication logic based on priority rules
     * Priority: DailyTracker > PreDailyTracker > expEnteredDate
     */
    private DeduplicationDecision applyDeduplicationLogic(List<Map<String, Object>> resumeDetails) {
        DeduplicationDecision decision = new DeduplicationDecision();
        
        Map<String, Object> bestResume = null;
        int bestPriority = Integer.MIN_VALUE;
        
        for (Map<String, Object> resume : resumeDetails) {
            int priority = calculateResumePriority(resume);
            
            if (bestResume == null || priority > bestPriority) {
                bestResume = resume;
                bestPriority = priority;
            }
        }
        
        if (bestResume != null) {
            Long keepResumeId = ((Number) bestResume.get("resumeId")).longValue();
            decision.setKeepResumeId(keepResumeId);
            
            List<Long> deleteIds = new ArrayList<>();
            for (Map<String, Object> resume : resumeDetails) {
                Long resumeId = ((Number) resume.get("resumeId")).longValue();
                if (!resumeId.equals(keepResumeId)) {
                    deleteIds.add(resumeId);
                }
            }
            decision.setDeleteResumeIds(deleteIds);
            
            logger.fine("Deduplication decision: keep " + keepResumeId + ", delete " + deleteIds);
        }
        
        return decision;
    }
    
    /**
     * Calculate priority score for a resume based on tracker data and entered date
     */
    private int calculateResumePriority(Map<String, Object> resume) {
        // Highest priority: DailyTracker data
        Object dailyTrackerPriority = resume.get("dailyTrackerPriority");
        if (dailyTrackerPriority != null) {
            return 1000000 + ((Number) dailyTrackerPriority).intValue();
        }
        
        // Medium priority: PreDailyTracker data
        Object preDailyTrackerPriority = resume.get("preDailyTrackerPriority");
        if (preDailyTrackerPriority != null) {
            return 100000 + ((Number) preDailyTrackerPriority).intValue();
        }
        
        // Lowest priority: expEnteredDate (more recent = higher priority)
        Object expEnteredDate = resume.get("expEnteredDate");
        if (expEnteredDate != null) {
            // Assuming expEnteredDate is a timestamp - use it as priority
            return ((Number) expEnteredDate).intValue();
        }
        
        return 0; // No priority data available
    }
    
    /**
     * Execute deduplication with proper transaction management
     */
    private int executeDeduplicationWithTransaction(DeduplicationDecision decision) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        
        try {
            connection.setAutoCommit(false);
            
            // Update tracker tables for each duplicate
            for (Long deleteId : decision.getDeleteResumeIds()) {
                contract.updateTrackerTables(deleteId, decision.getKeepResumeId());
            }
            
            // Delete duplicate resume records
            contract.deleteDuplicateResumes(decision.getDeleteResumeIds());
            
            connection.commit();
            
            logger.fine("Successfully deduplicated group: kept " + decision.getKeepResumeId() + 
                       ", deleted " + decision.getDeleteResumeIds().size() + " duplicates");
            
            return decision.getDeleteResumeIds().size();
            
        } catch (SQLException e) {
            connection.rollback();
            logger.log(Level.SEVERE, "Error during deduplication transaction, rolled back", e);
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }
    
    /**
     * Inner class to hold deduplication decision
     */
    private static class DeduplicationDecision {
        private Long keepResumeId;
        private List<Long> deleteResumeIds = new ArrayList<>();
        
        public Long getKeepResumeId() { return keepResumeId; }
        public void setKeepResumeId(Long keepResumeId) { this.keepResumeId = keepResumeId; }
        
        public List<Long> getDeleteResumeIds() { return deleteResumeIds; }
        public void setDeleteResumeIds(List<Long> deleteResumeIds) { this.deleteResumeIds = deleteResumeIds; }
    }
    
    /**
     * Result class for deduplication process
     */
    public static class DeduplicationResult {
        private boolean success;
        private int processedGroups;
        private int totalDuplicatesRemoved;
        private Map<String, Object> initialStats;
        private Map<String, Object> finalStats;
        private List<String> errors = new ArrayList<>();
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public int getProcessedGroups() { return processedGroups; }
        public void setProcessedGroups(int processedGroups) { this.processedGroups = processedGroups; }
        
        public int getTotalDuplicatesRemoved() { return totalDuplicatesRemoved; }
        public void setTotalDuplicatesRemoved(int totalDuplicatesRemoved) { this.totalDuplicatesRemoved = totalDuplicatesRemoved; }
        
        public Map<String, Object> getInitialStats() { return initialStats; }
        public void setInitialStats(Map<String, Object> initialStats) { this.initialStats = initialStats; }
        
        public Map<String, Object> getFinalStats() { return finalStats; }
        public void setFinalStats(Map<String, Object> finalStats) { this.finalStats = finalStats; }
        
        public List<String> getErrors() { return errors; }
        public void addError(String error) { this.errors.add(error); }
        
        @Override
        public String toString() {
            return "DeduplicationResult{" +
                    "success=" + success +
                    ", processedGroups=" + processedGroups +
                    ", totalDuplicatesRemoved=" + totalDuplicatesRemoved +
                    ", errors=" + errors.size() +
                    '}';
        }
    }
}