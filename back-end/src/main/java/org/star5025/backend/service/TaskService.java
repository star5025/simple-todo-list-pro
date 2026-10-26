package org.star5025.backend.service;

import org.star5025.backend.dto.TaskDTO;
import org.star5025.backend.dto.TaskPageQueryDTO;
import org.star5025.backend.dto.TaskPatchDTO;
import org.star5025.backend.entity.Task;
import org.star5025.backend.result.PageResult;
import org.star5025.backend.vo.TaskDashboardVO;
import org.star5025.backend.vo.DayTasksVO;

import java.time.LocalDateTime;
import java.util.List;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskService {

    /**
     * 新增任务
     * @param taskDTO
     */
    void createTask(TaskDTO taskDTO);

    /**
     * 删除当前登录用户的任务；不存在或不属于当前用户均返回404。
     * @param taskId
     */
    void deleteTask(Long taskId);

    /**
     * 原子删除当前用户的任务，重复ID去重；空参数返回400，混入他人或不存在的ID整批返回404。
     * @param taskIds
     */
    void deleteTasks(List<Long> taskIds);

    /**
     * 根据用户Id进行分页查询
     * @param taskPageQueryDTO
     * @return
     */
    PageResult pageQuery(TaskPageQueryDTO taskPageQueryDTO);
    
    /**
     * 根据用户Id进行分页查询
     * @param page 页码
     * @param pageSize 每页大小
     * @param userId 用户ID
     * @param status 完成状态
     * @param dueTime 截止时间
     * @param orderBy 排序字段
     * @param favourite 收藏状态
     * @return
     */
    PageResult pageQuery(int page, int pageSize, Long userId, Boolean status, LocalDateTime dueTime, String orderBy, Boolean favourite);

    /**
     * 更新任务
     * @param taskId
     * @param taskPatchDTO
     */
    void updateTask(Long taskId, TaskPatchDTO taskPatchDTO);

    /**
     *根据任务Id查询任务
     * @param taskId
     * @return
     */
    Task getTaskById(Long taskId);

    /**
     * 获取当前登录用户的看板统计。
     */
    TaskDashboardVO getDashboard();

    /**
     * 获取当前用户开始时间落在 [from, to) 范围内的任务（日历视图）。
     */
    List<Task> getCalendarTasks(LocalDateTime from, LocalDateTime to);

    /**
     * 获取当前用户开始或截止时间落在 [from, to) 范围内的任务（日历当日视图）。
     */
    DayTasksVO getDayTasks(LocalDateTime from, LocalDateTime to);
}