# Resume Deduplication Implementation

## Overview

This implementation provides a comprehensive resume deduplication solution with the following components:

1. **ResumeDeduplicationContract** - Custom SQL contract for database operations
2. **ResumeDeduplicationProcessorOTJ** - One-time job processor for batch processing
3. **PartyManager** - Integration class for managing the deduplication process

## Architecture

### Key Components

#### 1. QueryContract Interface
Base interface for database query operations providing:
- `executeQuery()` - For SELECT operations
- `executeUpdate()` - For INSERT/UPDATE/DELETE operations  
- `executeBatch()` - For batch operations
- Connection management

#### 2. ResumeDeduplicationContract
Extends QueryContract and provides specialized methods for resume deduplication:
- `findDuplicateResumesByTelephoneEmail()` - Find duplicates by phone/email combination
- `getResumeGroupDetails()` - Get detailed data for duplicate groups
- `updateTrackerTables()` - Update tracker tables during deduplication
- `deleteDuplicateResumes()` - Remove duplicate resume records
- `getDeduplicationStats()` - Get statistics about the process

#### 3. ResumeDeduplicationProcessorOTJ
One-time job processor that implements the core deduplication logic:
- Batch processing for handling large datasets
- Priority-based duplicate resolution
- Transaction management with rollback capabilities
- Comprehensive error handling and logging
- Statistics tracking

#### 4. PartyManager
High-level manager class that orchestrates the deduplication process:
- Database connection management
- Integration with the deduplication contract and processor
- Validation and reporting functionality
- Statistics and preview methods

## Deduplication Logic

### Priority Rules
The system determines which resume to keep based on priority:

1. **Highest Priority**: DailyTracker data
   - Resumes with DailyTracker entries are preferred
   - Higher priority values within DailyTracker are preferred

2. **Medium Priority**: PreDailyTracker data
   - Resumes with PreDailyTracker entries (but no DailyTracker)
   - Higher priority values within PreDailyTracker are preferred

3. **Lowest Priority**: expEnteredDate
   - Fall back to the most recently entered resume
   - More recent dates get higher priority

### Process Flow

1. **Find Duplicates**: Query for resumes with identical telephone + email (case insensitive)
2. **Group Processing**: For each duplicate group:
   - Retrieve detailed information including tracker data
   - Apply priority rules to determine which resume to keep
   - Update tracker tables to point to the kept resume
   - Delete duplicate resume records
3. **Transaction Management**: Each group is processed in its own transaction
4. **Batch Processing**: Process duplicates in configurable batch sizes
5. **Statistics**: Track and report progress throughout the process

## Database Schema Requirements

The implementation assumes the following database tables:

### Resume Table
```sql
CREATE TABLE Resume (
    resumeId BIGINT PRIMARY KEY,
    telephone VARCHAR(50),
    email VARCHAR(255),
    expEnteredDate TIMESTAMP,
    -- other resume fields
);
```

### DailyTracker Table
```sql
CREATE TABLE DailyTracker (
    resumeId BIGINT,
    priority INT,
    lastUpdated TIMESTAMP,
    -- other tracker fields
);
```

### PreDailyTracker Table
```sql
CREATE TABLE PreDailyTracker (
    resumeId BIGINT,
    priority INT,
    lastUpdated TIMESTAMP,
    -- other tracker fields
);
```

## Usage Examples

### Basic Usage
```java
// Create PartyManager with database connection details
PartyManager partyManager = new PartyManager(
    "jdbc:mysql://localhost:3306/hr_database",
    "username",
    "password"
);

// Execute deduplication with default settings
DeduplicationResult result = partyManager.executeResumeDeduplication();

// Check results
if (result.isSuccess()) {
    System.out.println("Deduplication completed successfully!");
    System.out.println("Groups processed: " + result.getProcessedGroups());
    System.out.println("Duplicates removed: " + result.getTotalDuplicatesRemoved());
} else {
    System.out.println("Deduplication failed with errors:");
    for (String error : result.getErrors()) {
        System.out.println("  - " + error);
    }
}
```

### Batch Processing
```java
// Execute with custom batch size
DeduplicationResult result = partyManager.executeResumeDeduplication(50);
```

### Statistics and Preview
```java
// Get current statistics
Map<String, Object> stats = partyManager.getResumeDeduplicationStats();
System.out.println("Total resumes: " + stats.get("totalResumes"));

// Preview duplicate groups without executing deduplication
List<Map<String, Object>> duplicates = partyManager.previewDuplicateResumes(10);
for (Map<String, Object> group : duplicates) {
    System.out.println("Duplicate group: " + group);
}
```

### Database Validation
```java
// Validate database connection and schema
if (!partyManager.validateDatabase()) {
    System.err.println("Database validation failed!");
    return;
}
```

## Configuration

### Batch Size
The default batch size is 100 duplicate groups per batch. This can be adjusted based on:
- Available memory
- Database performance
- Transaction timeout settings

### Database Connection
The implementation uses standard JDBC connections. Configure connection pooling and timeout settings as needed for your environment.

### Logging
The implementation uses Java's built-in logging framework. Configure logging levels to control verbosity:
- `INFO`: General process information
- `FINE`: Detailed operation information
- `SEVERE`: Errors and warnings

## Error Handling

The implementation includes comprehensive error handling:

1. **Database Errors**: SQL exceptions are caught and logged
2. **Transaction Rollback**: Failed operations trigger automatic rollback
3. **Batch Failures**: Individual batch failures don't stop the entire process
4. **Connection Issues**: Proper cleanup of database connections
5. **Data Validation**: Validation of input parameters and data integrity

## Performance Considerations

1. **Batch Processing**: Processes duplicates in configurable batches to manage memory usage
2. **Database Indexes**: Ensure proper indexes on telephone, email, and resumeId columns
3. **Connection Pooling**: Use connection pooling for better performance in production
4. **Transaction Size**: Each duplicate group is processed in its own transaction to minimize lock time
5. **Logging Level**: Adjust logging levels for production to reduce overhead

## Testing

The implementation includes an example class (`ResumeDeduplicationExample`) that demonstrates:
- Basic usage patterns
- Scheduled job execution
- Statistics retrieval
- Error handling

## Production Deployment

For production deployment:

1. **Configure Database Connection**: Use proper database URLs, credentials, and connection pooling
2. **Set Appropriate Batch Sizes**: Test and tune batch sizes for your data volume
3. **Schedule as Cron Job**: Run the deduplication process on a regular schedule
4. **Monitor Logs**: Set up log monitoring for errors and performance issues
5. **Backup Data**: Always backup data before running deduplication in production
6. **Test on Sample Data**: Validate the process on a subset of data first

## File Structure

```
src/main/java/com/enterprise/hr/
├── resume/
│   ├── contract/
│   │   ├── QueryContract.java
│   │   └── ResumeDeduplicationContract.java
│   └── processor/
│       └── ResumeDeduplicationProcessorOTJ.java
├── manager/
│   └── PartyManager.java
└── example/
    └── ResumeDeduplicationExample.java
```

This implementation follows enterprise Java patterns and integrates seamlessly with existing database-driven applications.