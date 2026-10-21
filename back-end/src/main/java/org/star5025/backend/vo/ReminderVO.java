package org.star5025.backend.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReminderVO {
    private Long taskId;
    private String taskName;
    private LocalDateTime remindTime;
    private Long revision;
}
