package org.star5025.backend.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.star5025.backend.entity.TaskSchedule;
import org.star5025.backend.result.Result;
import org.star5025.backend.service.ReminderService;
import org.star5025.backend.vo.ReminderVO;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReminderController {
    private final ReminderService reminders;

    @GetMapping("/reminders/due")
    public Result<List<ReminderVO>> due() {
        return Result.success(reminders.due());
    }

    @PostMapping("/reminders/{taskId}/ack")
    public Result<Void> acknowledge(@PathVariable Long taskId, @RequestBody Acknowledgement body) {
        reminders.acknowledge(taskId, body.getRevision());
        return Result.success();
    }

    @GetMapping("/task/{taskId}/schedule")
    public Result<TaskSchedule> schedule(@PathVariable Long taskId) {
        return Result.success(reminders.getSchedule(taskId));
    }

    @PutMapping("/task/{taskId}/schedule")
    public Result<TaskSchedule> schedule(@PathVariable Long taskId, @RequestBody ScheduleRequest body) {
        return Result.success(reminders.setSchedule(taskId, body.getRepeatRule()));
    }

    @Data
    public static class Acknowledgement { private Long revision; }
    @Data
    public static class ScheduleRequest { private String repeatRule; }
}
