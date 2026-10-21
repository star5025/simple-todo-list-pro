package org.star5025.backend.entity;

import lombok.Data;

@Data
public class TaskSchedule {
    private Long taskId;
    private String repeatRule;
    private Long revision;
    private Long ackRevision;
    private Long successorTaskId;
    private Integer repeatDay;
}
