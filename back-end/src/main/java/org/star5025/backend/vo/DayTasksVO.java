package org.star5025.backend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.star5025.backend.entity.Task;

import java.util.List;

/**
 * 某一天的任务：按开始时间与截止时间分别归类，仅包含当前登录用户的数据。
 */
@Data
@AllArgsConstructor
public class DayTasksVO {
    private List<Task> started;
    private List<Task> due;
}
