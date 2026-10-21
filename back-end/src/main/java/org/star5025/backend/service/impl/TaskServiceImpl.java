package org.star5025.backend.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.star5025.backend.context.BaseContext;
import org.star5025.backend.dto.TaskDTO;
import org.star5025.backend.dto.TaskPageQueryDTO;
import org.star5025.backend.dto.TaskPatchDTO;
import org.star5025.backend.entity.Task;
import org.star5025.backend.mapper.TaskMapper;
import org.star5025.backend.result.PageResult;
import org.star5025.backend.service.TaskService;
import org.star5025.backend.service.ReminderService;
import org.star5025.backend.mapper.TaskScheduleMapper;
import org.star5025.backend.service.UserService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TaskServiceImpl implements TaskService {

    @Autowired
    private TaskMapper taskMapper;
    
    @Autowired
    private UserService userService;

    @Autowired
    private ReminderService reminderService;

    @Autowired
    private TaskScheduleMapper taskScheduleMapper;

    /**
     * 新增任务
     * @param taskDTO
     */
    @Override
    @Transactional
    public void createTask(TaskDTO taskDTO) {
        Long userId = requireCurrentUserId();
        if (taskDTO == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task is required");
        }
        Task task = new Task();

        BeanUtils.copyProperties(taskDTO, task);
        task.setUserId(userId);

        task.setCreatedTime(LocalDateTime.now(java.time.ZoneOffset.UTC));

        taskMapper.createTask(task);
        
        // 增加用户任务计数
        userService.incrementTaskCount(userId, 1);
    }

    /**
     * 删除单个任务
     * @param taskId
     */
    @Override
    @Transactional
    public void deleteTask(Long taskId) {
        Long userId = requireCurrentUserId();
        requireTaskId(taskId);
        if (taskMapper.getTaskById(taskId, userId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        if (taskMapper.deleteTask(taskId, userId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        taskScheduleMapper.delete(taskId);
    }

    /**
     * 批量删除任务
     * @param taskIds
     */
    @Override
    @Transactional
    public void deleteTasks(List<Long> taskIds) {
        Long userId = requireCurrentUserId();
        if (taskIds == null || taskIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task IDs must not be empty");
        }
        taskIds.forEach(this::requireTaskId);
        List<Long> uniqueIds = new ArrayList<>(new LinkedHashSet<>(taskIds));
        // Lock all owned rows before validation so a mixed batch never partially deletes.
        List<Long> ownedIds = taskMapper.getOwnedTaskIdsForUpdate(uniqueIds, userId);
        if (ownedIds.size() != uniqueIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more tasks not found");
        }
        if (taskMapper.deleteTasks(uniqueIds, userId) != uniqueIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more tasks not found");
        }
        uniqueIds.forEach(taskScheduleMapper::delete);
    }

    /**
     * 根据用户id分页查询任务
     * @param taskPageQueryDTO
     */
    @Override
    public PageResult pageQuery(TaskPageQueryDTO taskPageQueryDTO) {
        return pageQuery(taskPageQueryDTO.getPage(), taskPageQueryDTO.getPageSize(), taskPageQueryDTO.getUserId(), taskPageQueryDTO.getStatus(), taskPageQueryDTO.getDueTime(), taskPageQueryDTO.getOrderBy(), taskPageQueryDTO.getFavourite());
    }
    
    /**
     * 根据用户id分页查询任务
     * @param page 页码
     * @param pageSize 每页大小
     * @param userId 用户ID
     * @param status 完成状态
     * @param dueTime 截止时间
     * @param orderBy 排序字段
     * @param favourite 收藏状态
     */
    public PageResult pageQuery(int page, int pageSize, Long userId, Boolean status, LocalDateTime dueTime, String orderBy, Boolean favourite) {
        userId = requireCurrentUserId();
        log.info("分页查询任务: page={}, pageSize={}, userId={}, status={}, dueTime={}, orderBy={}, favourite={}", 
                 page, pageSize, userId, status, dueTime, orderBy, favourite);
        
        PageHelper.startPage(page, pageSize);

        Page<Task> taskPage = taskMapper.pageQueryWithFavourite(userId, status, dueTime, orderBy, favourite);
        
        log.info("查询结果: 总数={}, 当前页数量={}", taskPage.getTotal(), taskPage.size());

        return new PageResult(taskPage.getTotal(), taskPage.getResult());
    }

    /**
     * 更新任务
     * @param taskId
     * @param taskPatchDTO
     */
    @Override
    @Transactional
    public void updateTask(Long taskId, TaskPatchDTO taskPatchDTO) {
        Long userId = requireCurrentUserId();
        requireTaskId(taskId);
        BeanWrapper patch = taskPatchDTO == null ? null : new BeanWrapperImpl(taskPatchDTO);
        if (patch == null || java.util.Arrays.stream(patch.getPropertyDescriptors())
                .filter(property -> !"class".equals(property.getName()) && !"remindTimeSet".equals(property.getName()))
                .allMatch(property -> patch.getPropertyValue(property.getName()) == null) && !taskPatchDTO.isRemindTimeSet()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one task field is required");
        }
        Task existingTask = taskMapper.getTaskByIdForUpdate(taskId, userId);
        if (existingTask == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }

        // Preserve the locked old state for subsequent task-change handling.
        Task updatedTask = existingTask.toBuilder().build();
        BeanUtils.copyProperties(taskPatchDTO, updatedTask, getNullPropertyNames(taskPatchDTO));
        if (taskPatchDTO.isRemindTimeSet()) {
            updatedTask.setRemindTime(taskPatchDTO.getRemindTime());
        }
        updatedTask.setUserId(userId);
        if (taskMapper.updateTask(updatedTask) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        reminderService.onTaskChanged(existingTask, updatedTask);
    }

    /**
     *根据任务Id查询任务
     * @param taskId
     * @return
     */
    @Override
    public Task getTaskById(Long taskId) {
        Long userId = requireCurrentUserId();
        requireTaskId(taskId);
        Task task = taskMapper.getTaskById(taskId, userId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return task;
    }

    private Long requireCurrentUserId() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return userId;
    }

    private void requireTaskId(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive task ID is required");
        }
    }

    /**
     * 获取对象中所有为null的属性名 (用于更新任务)
     * @param source 源对象
     * @return 为null的属性名数组
     */
    private String[] getNullPropertyNames(Object source) {
        BeanWrapper src = new BeanWrapperImpl(source);
        java.beans.PropertyDescriptor[] pds = src.getPropertyDescriptors();

        Set<String> emptyNames = new HashSet<>();
        for(java.beans.PropertyDescriptor pd : pds) {
            Object srcValue = src.getPropertyValue(pd.getName());
            if (srcValue == null) emptyNames.add(pd.getName());
        }
        return emptyNames.toArray(new String[0]);
    }
}