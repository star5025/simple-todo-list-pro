package org.star5025.backend;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.star5025.backend.entity.Task;
import org.star5025.backend.mapper.TaskMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskMapperSecurityTest {
    private Configuration configuration;

    @BeforeEach
    void parseMapperWithoutDatabase() {
        configuration = new Configuration();
        configuration.addMapper(TaskMapper.class);
    }

    private MappedStatement statement(String method) {
        return configuration.getMappedStatement(TaskMapper.class.getName() + "." + method);
    }

    private String sql(BoundSql bound) {
        return bound.getSql().toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private void assertUserBound(BoundSql bound) {
        assertTrue(sql(bound).contains("user_id = ?"), sql(bound));
        assertTrue(bound.getParameterMappings().stream().anyMatch(mapping -> mapping.getProperty().equals("userId")));
    }

    @Test
    void detailAndSingleDeleteUseBothTaskAndUserPredicates() {
        for (String method : List.of("getTaskById", "getTaskByIdForUpdate", "deleteTask")) {
            BoundSql bound = statement(method).getBoundSql(Map.of("taskId", 10L, "userId", 1L));
            assertUserBound(bound);
            assertTrue(sql(bound).contains("task_id = ?"));
        }
        assertTrue(sql(statement("getTaskByIdForUpdate").getBoundSql(Map.of("taskId", 10L, "userId", 1L))).endsWith("for update"));
    }

    @Test
    void updateCannotWriteAnotherOwnersTask() {
        BoundSql bound = statement("updateTask").getBoundSql(Task.builder().taskId(10L).userId(1L).build());
        assertUserBound(bound);
        assertTrue(sql(bound).contains("where task_id = ? and user_id = ?"));
        assertFalse(sql(bound).contains("set user_id"));
    }

    @Test
    void batchValidationAndDeleteBindSameUserAndAllIds() {
        Map<String, Object> params = Map.of("taskIds", List.of(10L, 11L), "userId", 1L);
        for (String method : List.of("getOwnedTaskIdsForUpdate", "deleteTasks")) {
            BoundSql bound = statement(method).getBoundSql(params);
            assertUserBound(bound);
            assertTrue(sql(bound).contains("task_id in"));
            assertEquals(3, bound.getParameterMappings().size());
            assertTrue(bound.getParameterMappings().stream()
                    .filter(mapping -> mapping.getProperty().startsWith("__frch_id_"))
                    .allMatch(mapping -> bound.hasAdditionalParameter(mapping.getProperty())));
        }
        assertTrue(sql(statement("getOwnedTaskIdsForUpdate").getBoundSql(params)).endsWith("for update"));
    }

    @Test
    void paginationBindsUserAndCreateReturnsGeneratedId() {
        for (String method : List.of("pageQuery", "pageQueryWithFavourite")) {
            assertUserBound(statement(method).getBoundSql(Map.of("userId", 1L)));
        }
        assertArrayEquals(new String[]{"taskId"}, statement("createTask").getKeyProperties());
    }
}
