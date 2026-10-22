package org.star5025.backend.vo;

import lombok.Data;

import java.util.List;

/**
 * 用户任务看板统计，仅包含当前登录用户的数据。
 */
@Data
public class TaskDashboardVO {
    private Long total;
    private Long pending;
    private Long completed;
    private Long favourite;
    private Long overduePending;
    private Long dueToday;
    private Long dueNext7;
    private Double completionRate;
    private List<DashboardDayCount> dueByDay;
}
