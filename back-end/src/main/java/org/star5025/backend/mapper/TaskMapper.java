package org.star5025.backend.mapper;

import com.github.pagehelper.Page;
import org.apache.ibatis.annotations.*;
import org.star5025.backend.entity.Task;
import org.star5025.backend.vo.DashboardDayCount;
import org.star5025.backend.vo.TaskDashboardVO;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaskMapper {

    /**
     * 新增任务
     * @param task
     */
    @Insert("insert into task (task_name, status, description, created_time, start_time, remind_time, due_time, user_id, favourite)" +
            "values (#{taskName},#{status},#{description},UTC_TIMESTAMP(),#{startTime},#{remindTime},#{dueTime},#{userId},#{favourite})")
    @Options(useGeneratedKeys = true, keyProperty = "taskId")
    void createTask(Task task);

    /**
     * 删除task表中的单个任务
     * @param taskId
     */
    @Delete("delete from task where task_id = #{taskId} and user_id = #{userId}")
    int deleteTask(@Param("taskId") Long taskId, @Param("userId") Long userId);

    /**
     * 批量删除任务
     * @param taskIds
     */
    @Delete("<script>" +
            "DELETE FROM task WHERE user_id = #{userId} AND task_id IN " +
            "<foreach item='id' collection='taskIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int deleteTasks(@Param("taskIds") List<Long> taskIds, @Param("userId") Long userId);

    @Select("<script>" +
            "SELECT task_id FROM task WHERE user_id = #{userId} AND task_id IN " +
            "<foreach item='id' collection='taskIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach> FOR UPDATE" +
            "</script>")
    List<Long> getOwnedTaskIdsForUpdate(@Param("taskIds") List<Long> taskIds, @Param("userId") Long userId);

    /**
     * 根据用户Id分页查询任务
     * @param userId
     * @param status 完成状态，如果为null则不筛选
     * @param dueTime 截止时间，如果为null则不筛选
     * @param orderBy 排序字段
     */
    @Select("<script>" +
            "select task_id as taskId, task_name as taskName, description as description, due_time as dueTime, status, start_time as startTime, remind_time as remindTime, created_time as createdTime, favourite as favourite " +
            "from task " +
            "where user_id = #{userId} " +
            "<if test='status != null'> AND status = #{status} </if>" +
            "<if test='dueTime != null'> AND DATE(due_time) = DATE(#{dueTime}) </if>" +
            "order by " +
            "<choose>" +
            "  <when test='orderBy == \"dueTime\"'>due_time asc</when>" +
            "  <when test='orderBy == \"dueTimeDesc\"'>due_time desc</when>" +
            "  <when test='orderBy == \"startTime\"'>start_time asc</when>" +
            "  <when test='orderBy == \"startTimeDesc\"'>start_time desc</when>" +
            "  <when test='orderBy == \"createdTime\"'>created_time asc</when>" +
            "  <when test='orderBy == \"createdTimeDesc\"'>created_time desc</when>" +
            "  <otherwise>created_time desc, task_id desc</otherwise>" +
            "</choose>" +
            "</script>")
    Page<Task> pageQuery(@Param("userId") Long userId, @Param("status") Boolean status, @Param("dueTime") LocalDateTime dueTime, @Param("orderBy") String orderBy);
    
    /**
     * 根据用户Id分页查询任务（支持收藏筛选）
     * @param userId
     * @param status 完成状态，如果为null则不筛选
     * @param dueTime 截止时间，如果为null则不筛选
     * @param orderBy 排序字段
     * @param favourite 收藏状态，如果为null则不筛选
     */
    @Select("<script>" +
            "select task_id as taskId, task_name as taskName, description as description, due_time as dueTime, status, start_time as startTime, remind_time as remindTime, created_time as createdTime, favourite as favourite " +
            "from task " +
            "where user_id = #{userId} " +
            "<if test='status != null'> AND status = #{status} </if>" +
            "<if test='dueTime != null'> AND DATE(due_time) = DATE(#{dueTime}) </if>" +
            "<if test='favourite != null'> AND favourite = <choose><when test='favourite'>1</when><otherwise>0</otherwise></choose> </if>" +
            "order by " +
            "<choose>" +
            "  <when test='orderBy == \"dueTime\"'>due_time asc</when>" +
            "  <when test='orderBy == \"dueTimeDesc\"'>due_time desc</when>" +
            "  <when test='orderBy == \"startTime\"'>start_time asc</when>" +
            "  <when test='orderBy == \"startTimeDesc\"'>start_time desc</when>" +
            "  <when test='orderBy == \"createdTime\"'>created_time asc</when>" +
            "  <when test='orderBy == \"createdTimeDesc\"'>created_time desc</when>" +
            "  <otherwise>created_time desc, task_id desc</otherwise>" +
            "</choose>" +
            "</script>")
    Page<Task> pageQueryWithFavourite(@Param("userId") Long userId, @Param("status") Boolean status, @Param("dueTime") LocalDateTime dueTime, @Param("orderBy") String orderBy, @Param("favourite") Boolean favourite);

    /**
     * 根据任务Id查询单个任务
     * @param taskId
     */
    @Select("select * from task where task_id = #{taskId} and user_id = #{userId}")
    Task getTaskById(@Param("taskId") Long taskId, @Param("userId") Long userId);

    @Select("select * from task where task_id = #{taskId} and user_id = #{userId} for update")
    Task getTaskByIdForUpdate(@Param("taskId") Long taskId, @Param("userId") Long userId);

    /**
     * 更新任务
     * @param newTask
     */
    @Update("update task set task_name = #{taskName}, status = #{status}, description = #{description}, start_time = #{startTime}, remind_time = #{remindTime}, due_time = #{dueTime}, favourite = #{favourite} WHERE task_id = #{taskId} AND user_id = #{userId}")
    int updateTask(Task newTask);

    /**
     * 看板概览：当前用户任务总数、未完成、已完成、收藏数。
     */
    @Select("SELECT COUNT(*) total, " +
            "IFNULL(SUM(status = 0), 0) pending, " +
            "IFNULL(SUM(status = 1), 0) completed, " +
            "IFNULL(SUM(favourite = 1), 0) favourite " +
            "FROM task WHERE user_id = #{userId}")
    TaskDashboardVO selectDashboardCounts(@Param("userId") Long userId);

    /**
     * 看板提醒：逾期未完成、今天到期、未来 7 天到期数量。
     */
    @Select("SELECT " +
            "IFNULL(SUM(status = 0 AND due_time < NOW()), 0) overduePending, " +
            "IFNULL(SUM(status = 0 AND DATE(due_time) = CURDATE()), 0) dueToday, " +
            "IFNULL(SUM(status = 0 AND due_time >= CURDATE() AND due_time < DATE_ADD(CURDATE(), INTERVAL 7 DAY)), 0) dueNext7 " +
            "FROM task WHERE user_id = #{userId}")
    TaskDashboardVO selectDashboardDueStats(@Param("userId") Long userId);

    /**
     * 看板分布：未来 7 天（含今天）每天到期的未完成任务数量。
     */
    @Select("SELECT DATE(due_time) date, COUNT(*) count FROM task " +
            "WHERE user_id = #{userId} AND status = 0 " +
            "AND due_time >= CURDATE() AND due_time < DATE_ADD(CURDATE(), INTERVAL 7 DAY) " +
            "GROUP BY DATE(due_time) ORDER BY 1")
    List<DashboardDayCount> selectDueByDay(@Param("userId") Long userId);

    /**
     * 日历：按开始时间落在 [from, to) 范围内的任务（UTC）。
     */
    @Select("SELECT task_id AS taskId, task_name AS taskName, status, description, " +
            "created_time AS createdTime, start_time AS startTime, remind_time AS remindTime, " +
            "due_time AS dueTime, favourite " +
            "FROM task WHERE user_id = #{userId} AND start_time IS NOT NULL " +
            "AND start_time >= #{from} AND start_time < #{to} " +
            "ORDER BY start_time, task_id")
    List<Task> selectByStartRange(@Param("userId") Long userId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);
}