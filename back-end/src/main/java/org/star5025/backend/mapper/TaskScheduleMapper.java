package org.star5025.backend.mapper;

import org.apache.ibatis.annotations.*;
import org.star5025.backend.entity.Task;
import org.star5025.backend.entity.TaskSchedule;
import org.star5025.backend.vo.ReminderVO;
import java.util.List;

@Mapper
public interface TaskScheduleMapper {
    @Select("SELECT task_id AS taskId, task_name AS taskName, status, description, created_time AS createdTime, start_time AS startTime, remind_time AS remindTime, due_time AS dueTime, user_id AS userId, favourite FROM task WHERE task_id=#{taskId} AND user_id=#{userId} FOR UPDATE")
    Task lockOwnedTask(@Param("taskId") Long taskId, @Param("userId") Long userId);

    @Insert("INSERT IGNORE INTO task_schedule(task_id) VALUES(#{taskId})")
    void ensure(Long taskId);

    @Select("SELECT task_id AS taskId, repeat_rule AS repeatRule, revision, ack_revision AS ackRevision, successor_task_id AS successorTaskId, repeat_day AS repeatDay FROM task_schedule WHERE task_id=#{taskId}")
    TaskSchedule get(Long taskId);

    @Update("UPDATE task_schedule SET repeat_rule=#{rule} WHERE task_id=#{taskId}")
    void setRule(@Param("taskId") Long taskId, @Param("rule") String rule);

    @Update("UPDATE task_schedule SET repeat_day=#{day} WHERE task_id=#{taskId}")
    void setRepeatDay(@Param("taskId") Long taskId, @Param("day") Integer day);

    @Delete("DELETE FROM task_schedule WHERE task_id=#{taskId}")
    void delete(Long taskId);

    @Update("UPDATE task_schedule SET revision=revision+1 WHERE task_id=#{taskId}")
    void invalidate(Long taskId);

    @Update("UPDATE task_schedule SET successor_task_id=#{successorId} WHERE task_id=#{taskId} AND successor_task_id IS NULL")
    int setSuccessor(@Param("taskId") Long taskId, @Param("successorId") Long successorId);

    @Select("SELECT t.task_id AS taskId, t.task_name AS taskName, t.remind_time AS remindTime, COALESCE(s.revision,1) AS revision FROM task t LEFT JOIN task_schedule s ON s.task_id=t.task_id WHERE t.user_id=#{userId} AND t.status=0 AND t.remind_time IS NOT NULL AND t.remind_time<=UTC_TIMESTAMP() AND COALESCE(s.ack_revision,0)<COALESCE(s.revision,1) ORDER BY t.remind_time,t.task_id LIMIT 50")
    List<ReminderVO> due(Long userId);

    @Update("UPDATE task_schedule s JOIN task t ON t.task_id=s.task_id SET s.ack_revision=s.revision WHERE t.task_id=#{taskId} AND t.user_id=#{userId} AND t.status=0 AND t.remind_time IS NOT NULL AND t.remind_time<=UTC_TIMESTAMP() AND s.revision=#{revision}")
    int acknowledge(@Param("taskId") Long taskId, @Param("userId") Long userId, @Param("revision") Long revision);
}
