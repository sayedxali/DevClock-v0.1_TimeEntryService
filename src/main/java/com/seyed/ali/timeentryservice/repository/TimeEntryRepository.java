package com.seyed.ali.timeentryservice.repository;

import com.seyed.ali.timeentryservice.model.domain.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, String> {

    Optional<TimeEntry> findByUserId(String userId);

    TimeEntry findByUserIdAndTimeEntryId(String userId, String timeEntryId);

    List<TimeEntry> findByProjectId(String projectId);

    List<TimeEntry> findByTaskId(String taskId);

}