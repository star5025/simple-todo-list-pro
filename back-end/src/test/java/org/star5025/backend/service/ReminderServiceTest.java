package org.star5025.backend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.star5025.backend.context.BaseContext;
import org.star5025.backend.entity.Task;
import org.star5025.backend.entity.TaskSchedule;
import org.star5025.backend.mapper.TaskMapper;
import org.star5025.backend.mapper.TaskScheduleMapper;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class ReminderServiceTest {
    private final TaskScheduleMapper schedules = mock(TaskScheduleMapper.class);
    private final TaskMapper tasks = mock(TaskMapper.class);
    private final UserService users = mock(UserService.class);
    private final ReminderService service = new ReminderService(schedules, tasks, users);
    private Task task;

    @BeforeEach
    void setup() {
        BaseContext.setCurrentId(1L);
        task = Task.builder().taskId(5L).userId(1L).taskName("repeat").status(false)
                .dueTime(LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(2)).remindTime(LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(2).minusHours(1)).build();
        when(schedules.lockOwnedTask(5L, 1L)).thenReturn(task);
    }

    @AfterEach
    void cleanup() { BaseContext.removeCurrentId(); }

    private TaskSchedule schedule(String rule, Long successor) {
        TaskSchedule schedule = new TaskSchedule();
        schedule.setTaskId(5L);
        schedule.setRepeatRule(rule);
        schedule.setRevision(1L);
        schedule.setAckRevision(0L);
        schedule.setSuccessorTaskId(successor);
        when(schedules.get(5L)).thenReturn(schedule);
        return schedule;
    }

    @Test
    void rejectsForeignTaskBeforeChangingOrAcknowledging() {
        when(schedules.lockOwnedTask(5L, 1L)).thenReturn(null);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.setSchedule(5L, "DAILY")).getRawStatusCode());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.acknowledge(5L, 1L)).getRawStatusCode());
        verify(schedules, never()).ensure(any());
    }

    @Test
    void staleReminderCannotAcknowledge() {
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.acknowledge(5L, 2L)).getRawStatusCode());
        verify(schedules).acknowledge(5L, 1L, 2L);
    }

    @Test
    void acknowledgeUsesCurrentIdentityAndVersion() {
        when(schedules.acknowledge(5L, 1L, 1L)).thenReturn(1);
        service.acknowledge(5L, 1L);
        verify(schedules).acknowledge(5L, 1L, 1L);
    }

    @Test
    void reminderRescheduleInvalidatesOldDelivery() {
        Task after = task.toBuilder().remindTime(task.getRemindTime().plusHours(1)).build();
        service.onTaskChanged(task, after);
        verify(schedules).invalidate(5L);
        verifyNoInteractions(tasks, users);
    }

    @Test
    void cancellationInvalidatesOldDelivery() {
        service.onTaskChanged(task, task.toBuilder().remindTime(null).build());
        verify(schedules).invalidate(5L);
    }

    @Test
    void completingOnceCreatesOneSuccessorAndPreservesRelativeTimes() {
        schedule("DAILY", null);
        doAnswer(invocation -> { ((Task) invocation.getArgument(0)).setTaskId(6L); return null; }).when(tasks).createTask(any());
        when(schedules.setSuccessor(5L, 6L)).thenReturn(1);
        service.onTaskChanged(task, task.toBuilder().status(true).build());
        ArgumentCaptor<Task> next = ArgumentCaptor.forClass(Task.class);
        verify(tasks).createTask(next.capture());
        assertFalse(next.getValue().getStatus());
        assertEquals(task.getDueTime().plusDays(1), next.getValue().getDueTime());
        assertEquals(task.getRemindTime().plusDays(1), next.getValue().getRemindTime());
        verify(schedules).setRule(6L, "DAILY");
        verify(users).incrementTaskCount(1L, 1);
    }

    @Test
    void repeatedCompletionOrReopenedParentDoesNotDuplicateSuccessor() {
        schedule("DAILY", 6L);
        service.onTaskChanged(task, task.toBuilder().status(true).build());
        service.onTaskChanged(task.toBuilder().status(true).build(), task.toBuilder().status(true).build());
        verifyNoInteractions(tasks, users);
    }

    @Test
    void invalidRulesAndMissingAnchorAreRejected() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.setSchedule(5L, "YEARLY")).getRawStatusCode());
        task.setDueTime(null); task.setRemindTime(null);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.setSchedule(5L, "DAILY")).getRawStatusCode());
    }

    @Test
    void dueIsScopedAndRequiresLogin() {
        when(schedules.due(1L)).thenReturn(List.of());
        assertTrue(service.due().isEmpty());
        BaseContext.removeCurrentId();
        assertEquals(401, assertThrows(ResponseStatusException.class, service::due).getRawStatusCode());
    }

    @Test
    void monthlySuccessorsKeepOriginalDayAcrossShortMonths() {
        LocalDateTime january = LocalDateTime.of(2024, 1, 31, 9, 0);
        LocalDateTime february = ReminderService.nextOccurrence(january, "MONTHLY", january, 31);
        LocalDateTime march = ReminderService.nextOccurrence(february, "MONTHLY", february, 31);
        assertEquals(LocalDateTime.of(2024, 2, 29, 9, 0), february);
        assertEquals(LocalDateTime.of(2024, 3, 31, 9, 0), march);
        assertEquals(LocalDateTime.of(2024, 4, 30, 9, 0), ReminderService.nextOccurrence(march, "MONTHLY", march, 31));
    }

    @Test
    void missedDailyWeeklyAndMonthEndOccurrencesSkipForward() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 31, 9, 0);
        assertEquals(LocalDateTime.of(2024, 2, 29, 9, 0), ReminderService.nextOccurrence(start, "MONTHLY", start));
        assertEquals(LocalDateTime.of(2024, 3, 31, 9, 0), ReminderService.nextOccurrence(start, "MONTHLY", LocalDateTime.of(2024, 3, 1, 9, 0)));
        assertEquals(start.plusDays(11), ReminderService.nextOccurrence(start, "DAILY", start.plusDays(10)));
        assertEquals(start.plusWeeks(3), ReminderService.nextOccurrence(start, "WEEKLY", start.plusWeeks(2)));
    }
}
