package com.seyed.ali.timeentryservice.util;

import com.seyed.ali.timeentryservice.client.AuthenticationServiceClient;
import com.seyed.ali.timeentryservice.client.ProjectServiceClient;
import com.seyed.ali.timeentryservice.client.TaskServiceClient;
import com.seyed.ali.timeentryservice.exceptions.OperationNotSupportedException;
import com.seyed.ali.timeentryservice.model.domain.TimeEntry;
import com.seyed.ali.timeentryservice.model.domain.TimeSegment;
import com.seyed.ali.timeentryservice.model.payload.TimeBillingDTO;
import com.seyed.ali.timeentryservice.model.payload.TimeEntryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TimeEntryUtility {

    private final AuthenticationServiceClient authenticationServiceClient;
    private final ProjectServiceClient projectServiceClient;
    private final TaskServiceClient taskServiceClient;
    private final TimeParser timeParser;

    /**
     * Creates a new TimeEntry object based on the provided TimeEntryDTO.
     *
     * @param timeEntryDTO The TimeEntryDTO object containing the time entry details.
     * @return The created TimeEntry object.
     */
    public TimeEntry createTimeEntry(TimeEntryDTO timeEntryDTO) {
        TimeEntry timeEntry = new TimeEntry();
        timeEntry.setTimeEntryId(UUID.randomUUID().toString());
        timeEntry.setBillable(timeEntryDTO.isBillable());
        timeEntry.setUserId(this.authenticationServiceClient.getCurrentLoggedInUsersId());

        if (this.projectServiceClient.isProjectValid(timeEntryDTO.getProjectId()))
            timeEntry.setProjectId(timeEntryDTO.getProjectId());

        if (this.taskServiceClient.isTaskValid(timeEntryDTO.getTaskId()))
            timeEntry.setTaskId(timeEntryDTO.getTaskId());

        if (timeEntryDTO.getHourlyRate() != null) {
            timeEntry.setHourlyRate(new BigDecimal(timeEntryDTO.getHourlyRate()));
        }

        TimeSegment timeSegment = this.createTimeSegment(timeEntryDTO, timeEntry);
        timeEntry.getTimeSegmentList().add(timeSegment);

        return timeEntry;
    }

    /**
     * Creates a new TimeEntry object for time tracking.
     *
     * @param timeBillingDTO The TimeBillingDTO object containing relevant data.
     * @param authenticationServiceClient The AuthenticationServiceClient for retrieving the current logged-in user's ID.
     * @return The created TimeEntry object.
     */
    public TimeEntry createNewTimeEntry(TimeBillingDTO timeBillingDTO, AuthenticationServiceClient authenticationServiceClient) {
        TimeEntry timeEntry = new TimeEntry();
        timeEntry.setTimeEntryId(UUID.randomUUID().toString());
        timeEntry.setBillable(timeBillingDTO.isBillable());
        timeEntry.setHourlyRate(timeBillingDTO.getHourlyRate());
        timeEntry.setProjectId(timeBillingDTO.getProjectId());
        timeEntry.setTaskId(timeBillingDTO.getTaskId());
        timeEntry.setUserId(authenticationServiceClient.getCurrentLoggedInUsersId());

        TimeSegment timeSegment = TimeSegment.builder()
                .timeSegmentId(UUID.randomUUID().toString())
                .startTime(LocalDateTime.now())
                .endTime(null)
                .duration(Duration.ZERO)
                .timeEntry(timeEntry)
                .build();

        timeEntry.getTimeSegmentList().add(timeSegment);
        return timeEntry;
    }

    /**
     * Updates an existing TimeEntry object based on the provided TimeEntryDTO.
     *
     * @param timeEntry    The TimeEntry object to update.
     * @param timeEntryDTO The TimeEntryDTO object containing the updated time entry details.
     * @param timeParser   The TimeParser utility for parsing time-related values.
     */
    public void updateTimeEntry(TimeEntry timeEntry, TimeEntryDTO timeEntryDTO, TimeParser timeParser) {
        TimeSegment lastTimeSegment = timeEntry.getTimeSegmentList().getLast();

        LocalDateTime startTime = timeEntryDTO.getStartTime() != null
                ? timeParser.parseStringToLocalDateTime(timeEntryDTO.getStartTime())
                : lastTimeSegment.getStartTime();

        LocalDateTime endTime = timeEntryDTO.getEndTime() != null
                ? timeParser.parseStringToLocalDateTime(timeEntryDTO.getEndTime())
                : lastTimeSegment.getEndTime();

        Duration duration = timeEntryDTO.getDuration() != null
                ? timeParser.parseStringToDuration(timeEntryDTO.getDuration())
                : lastTimeSegment.getDuration();

        lastTimeSegment.setStartTime(startTime);
        lastTimeSegment.setEndTime(endTime);
        lastTimeSegment.setDuration(duration);
    }

    /**
     * Stops tracking an existing TimeEntry object.
     *
     * @param timeEntry  The TimeEntry object to stop tracking.
     * @param endTime    The end time for the time entry.
     */
    public void stopTimeEntry(TimeEntry timeEntry, LocalDateTime endTime) {
        TimeSegment lastTimeSegment = timeEntry.getTimeSegmentList().getLast();
        LocalDateTime startTime = lastTimeSegment.getStartTime();
        Duration duration = Duration.between(startTime, endTime);
        createTimeInfo(timeEntry, startTime, endTime, duration);
    }

    /**
     * Continues tracking an existing TimeEntry object.
     *
     * @param timeEntry    The TimeEntry object to continue tracking.
     * @param continueTime The time to continue tracking.
     */
    public void continueTimeEntry(TimeEntry timeEntry, LocalDateTime continueTime) {
        TimeSegment timeSegment = TimeSegment.builder()
                .timeSegmentId(UUID.randomUUID().toString())
                .startTime(continueTime)
                .endTime(null)
                .duration(Duration.ZERO)
                .timeEntry(timeEntry)
                .build();

        timeEntry.getTimeSegmentList().add(timeSegment);
    }

    /**
     * Creates a new TimeSegment object based on the provided TimeEntryDTO and TimeEntry.
     *
     * @param timeEntryDTO The TimeEntryDTO object containing the time entry details.
     * @param timeEntry    The TimeEntry object associated with the time segment.
     * @return The created TimeSegment object.
     */
    public TimeSegment createTimeSegment(TimeEntryDTO timeEntryDTO, TimeEntry timeEntry) {
        LocalDateTime startTime = this.timeParser.parseStringToLocalDateTime(timeEntryDTO.getStartTime());
        LocalDateTime endTime = this.timeParser.parseStringToLocalDateTime(timeEntryDTO.getEndTime());
        Duration calculatedDuration = Duration.between(startTime, endTime);

        // if the user entered `duration` field
        Optional<Duration> durationOpt = Optional.ofNullable(timeEntryDTO.getDuration())
                .map(this.timeParser::parseStringToDuration);

        durationOpt.ifPresent(duration -> {
            if (!calculatedDuration.equals(duration)) {
                throw new OperationNotSupportedException("The provided endTime and duration are not consistent with the startTime");
            }
        });

        return TimeSegment.builder()
                .timeSegmentId(UUID.randomUUID().toString())
                .startTime(startTime)
                .endTime(endTime)
                .duration(calculatedDuration)
                .timeEntry(timeEntry)
                .build();
    }

    /**
     * Calculates the total duration of a TimeEntry object by summing the durations of its TimeSegment objects.
     *
     * @param timeEntry The TimeEntry object.
     * @return The total duration of the time entry.
     */
    public Duration getTotalDuration(TimeEntry timeEntry) {
        return timeEntry.getTimeSegmentList().stream()
                .map(TimeSegment::getDuration)
                .reduce(Duration.ZERO, Duration::plus);
    }

    /**
     * Creates time information (start time, end time, duration) for a TimeSegment object
     * associated with a TimeEntry object.
     *
     * @param timeEntry The TimeEntry object.
     * @param startTime The start time for the time segment.
     * @param endTime   The end time for the time segment.
     * @param duration  The duration of the time segment.
     */
    private void createTimeInfo(TimeEntry timeEntry, LocalDateTime startTime, LocalDateTime endTime, Duration duration) {
        TimeSegment lastTimeSegment = timeEntry.getTimeSegmentList().getLast();
        lastTimeSegment.setStartTime(startTime);
        lastTimeSegment.setEndTime(endTime);
        lastTimeSegment.setDuration(duration);
    }

}
