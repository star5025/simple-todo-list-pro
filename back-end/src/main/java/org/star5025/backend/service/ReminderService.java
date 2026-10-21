package org.star5025.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.star5025.backend.context.BaseContext;
import org.star5025.backend.entity.Task;
import org.star5025.backend.entity.TaskSchedule;
import org.star5025.backend.mapper.TaskMapper;
import org.star5025.backend.mapper.TaskScheduleMapper;
import org.star5025.backend.vo.ReminderVO;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReminderService {
    private final TaskScheduleMapper schedules;
    private final TaskMapper tasks;
    private final UserService users;
    private static final Set<String> RULES = Set.of("NONE", "DAILY", "WEEKLY", "MONTHLY");

    private Long currentUser() {
        Long id = BaseContext.getCurrentId();
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        return id;
    }

    private Task owned(Long taskId) {
        Task task = schedules.lockOwnedTask(taskId, currentUser());
        if (task == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        return task;
    }

    @Transactional
    public TaskSchedule getSchedule(Long taskId) {
        owned(taskId);
        schedules.ensure(taskId);
        return schedules.get(taskId);
    }

    @Transactional
    public TaskSchedule setSchedule(Long taskId, String rule) {
        Task task = owned(taskId);
        if (rule == null || !RULES.contains(rule)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的重复规则");
        if (!"NONE".equals(rule) && (Boolean.TRUE.equals(task.getStatus()) || anchor(task) == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请为未完成任务设置截止、提醒或开始时间");
        }
        schedules.ensure(taskId);
        TaskSchedule existing = schedules.get(taskId);
        if (!Objects.equals(rule, existing.getRepeatRule())) {
            schedules.setRepeatDay(taskId, "MONTHLY".equals(rule) ? anchor(task).getDayOfMonth() : null);
        }
        schedules.setRule(taskId, rule);
        return schedules.get(taskId);
    }

    public List<ReminderVO> due() {
        return schedules.due(currentUser());
    }

    @Transactional
    public void acknowledge(Long taskId, Long revision) {
        owned(taskId);
        if (revision == null || revision < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的提醒版本");
        schedules.ensure(taskId);
        if (schedules.acknowledge(taskId, currentUser(), revision) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "提醒已变更或失效");
        }
    }

    // Called in the task update transaction while the original task row is locked.
    public void onTaskChanged(Task before, Task after) {
        schedules.ensure(after.getTaskId());
        if (!Objects.equals(before.getRemindTime(), after.getRemindTime()) || !Objects.equals(before.getStatus(), after.getStatus())) {
            schedules.invalidate(after.getTaskId());
        }
        if (Boolean.TRUE.equals(before.getStatus()) || !Boolean.TRUE.equals(after.getStatus())) return;
        TaskSchedule schedule = schedules.get(after.getTaskId());
        if ("NONE".equals(schedule.getRepeatRule()) || schedule.getSuccessorTaskId() != null) return;
        LocalDateTime base = anchor(after);
        if (base == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "重复任务需要时间锚点");
        LocalDateTime next = nextOccurrence(base, schedule.getRepeatRule(), LocalDateTime.now(java.time.ZoneOffset.UTC), schedule.getRepeatDay());
        Duration shift = Duration.between(base, next);
        Task successor = after.toBuilder().taskId(null).status(false).createdTime(LocalDateTime.now(java.time.ZoneOffset.UTC))
                .startTime(shift(after.getStartTime(), shift)).remindTime(shift(after.getRemindTime(), shift))
                .dueTime(shift(after.getDueTime(), shift)).build();
        tasks.createTask(successor);
        schedules.ensure(successor.getTaskId());
        schedules.setRule(successor.getTaskId(), schedule.getRepeatRule());
        schedules.setRepeatDay(successor.getTaskId(), schedule.getRepeatDay());
        if (schedules.setSuccessor(after.getTaskId(), successor.getTaskId()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已生成下一期任务");
        }
        users.incrementTaskCount(after.getUserId(), 1);
    }

    static LocalDateTime anchor(Task task) {
        return task.getDueTime() != null ? task.getDueTime() : task.getRemindTime() != null ? task.getRemindTime() : task.getStartTime();
    }

    private static LocalDateTime shift(LocalDateTime time, Duration shift) {
        return time == null ? null : time.plus(shift);
    }

    static LocalDateTime nextOccurrence(LocalDateTime anchor, String rule, LocalDateTime now) {
        return nextOccurrence(anchor, rule, now, null);
    }

    static LocalDateTime nextOccurrence(LocalDateTime anchor, String rule, LocalDateTime now, Integer repeatDay) {
        ChronoUnit unit = "DAILY".equals(rule) ? ChronoUnit.DAYS : "WEEKLY".equals(rule) ? ChronoUnit.WEEKS : ChronoUnit.MONTHS;
        long steps = Math.max(1, unit.between(anchor, now));
        LocalDateTime next = occurrence(anchor, unit, steps, repeatDay);
        while (!next.isAfter(now)) next = occurrence(anchor, unit, ++steps, repeatDay);
        return next;
    }

    private static LocalDateTime occurrence(LocalDateTime anchor, ChronoUnit unit, long steps, Integer repeatDay) {
        LocalDateTime time = anchor.plus(steps, unit);
        if (unit == ChronoUnit.MONTHS && repeatDay != null) {
            time = time.withDayOfMonth(Math.min(repeatDay, time.toLocalDate().lengthOfMonth()));
        }
        return time;
    }
}
