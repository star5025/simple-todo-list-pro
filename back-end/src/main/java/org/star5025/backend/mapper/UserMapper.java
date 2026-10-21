package org.star5025.backend.mapper;

import org.apache.ibatis.annotations.*;
import org.star5025.backend.entity.User;

@Mapper
public interface UserMapper {
    @Insert("insert into user (user_name, user_password, create_time, task_count) values (#{userName}, #{userPassword}, #{createTime}, #{taskCount})")
    void register(User user);

    @Select("select user_id, user_name, user_password, create_time, task_count from user where user_name = #{userName}")
    User getByUserName(String userName);

    @Select("select user_id, user_name, user_password, create_time, task_count from user where user_id = #{userId}")
    User getByUserId(Long userId);

    @Select("select exists(select 1 from user where user_id = #{userId})")
    boolean existsById(Long userId);

    @Update("update user set user_name = #{userName} where user_id = #{userId}")
    int updateName(@Param("userId") Long userId, @Param("userName") String userName);

    @Update("update user set user_name = #{user.userName}, user_password = #{user.userPassword} " +
            "where user_id = #{user.userId} and binary user_password = #{expectedPassword}")
    int updateWithPassword(@Param("user") User user, @Param("expectedPassword") String expectedPassword);

    @Update("update user set user_password = #{newPassword} " +
            "where user_id = #{userId} and binary user_password = #{expectedPassword}")
    int updatePasswordIfUnchanged(@Param("userId") Long userId,
                                  @Param("expectedPassword") String expectedPassword,
                                  @Param("newPassword") String newPassword);

    @Delete("DELETE s FROM task_schedule s JOIN task t ON t.task_id=s.task_id WHERE t.user_id=#{userId}")
    void deleteSchedules(Long userId);

    @Delete("DELETE FROM task WHERE user_id=#{userId}")
    void deleteTasks(Long userId);

    @Delete("delete from user where user_id = #{userId}")
    int delete(User user);

    @Update("update user set task_count = task_count + #{count} where user_id = #{userId}")
    void incrementTaskCount(@Param("userId") Long userId, @Param("count") int count);
}
