package org.star5025.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.star5025.backend.utils.Md5Util;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 看板统计必须只聚合当前登录用户的数据，并正确计算各到期桶与完成率。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    private long userA;
    private long userB;
    private String tokenA;
    private String tokenB;
    private static final String PASSWORD = "safe-test-password";

    @BeforeEach
    void fixtures() throws Exception {
        String expectedDatabase = System.getenv("STDL_SECURITY_TEST_DATABASE");
        assertNotNull(expectedDatabase, "STDL_SECURITY_TEST_DATABASE must explicitly name the isolated test database");
        assertFalse(expectedDatabase.isBlank(), "STDL_SECURITY_TEST_DATABASE must not be blank");
        assertEquals(expectedDatabase, db.queryForObject("SELECT DATABASE()", String.class),
                "Connected database must match STDL_SECURITY_TEST_DATABASE before inserting fixtures");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        db.update("INSERT INTO user(user_name,user_password,task_count) VALUES(?,?,0)", "a_" + suffix, Md5Util.getMD5String(PASSWORD));
        db.update("INSERT INTO user(user_name,user_password,task_count) VALUES(?,?,0)", "b_" + suffix, Md5Util.getMD5String(PASSWORD));
        userA = db.queryForObject("SELECT user_id FROM user WHERE user_name=?", Long.class, "a_" + suffix);
        userB = db.queryForObject("SELECT user_id FROM user WHERE user_name=?", Long.class, "b_" + suffix);
        tokenA = login("a_" + suffix);
        tokenB = login("b_" + suffix);

        // 用户A：2完成(1收藏) + 4未完成(1逾期、1今天、1明天、1六天后)，其中1未完成收藏
        insert(userA, "done-1", 1, 1, "DATE_SUB(NOW(), INTERVAL 2 DAY)");
        insert(userA, "done-2", 1, 0, "DATE_SUB(NOW(), INTERVAL 3 DAY)");
        insert(userA, "overdue", 0, 0, "DATE_SUB(NOW(), INTERVAL 1 DAY)");
        // 30分钟后到期：绝大多数时刻仍在今天且未逾期；23:30后运行会落到明天，
        // 下面用 sameDay 动态计算期望，保证任何时刻测试自洽。
        insert(userA, "today", 0, 1, "DATE_ADD(NOW(), INTERVAL 30 MINUTE)");
        insert(userA, "tomorrow", 0, 0, "DATE_ADD(CURDATE(), INTERVAL 37 HOUR)");
        insert(userA, "six-days", 0, 0, "DATE_ADD(CURDATE(), INTERVAL 156 HOUR)");

        // 用户B：3完成 + 2逾期未完成，不得影响A的统计
        insert(userB, "b-done-1", 1, 0, "DATE_SUB(NOW(), INTERVAL 1 DAY)");
        insert(userB, "b-done-2", 1, 0, "DATE_SUB(NOW(), INTERVAL 2 DAY)");
        insert(userB, "b-done-3", 1, 0, "DATE_SUB(NOW(), INTERVAL 3 DAY)");
        insert(userB, "b-overdue-1", 0, 1, "DATE_SUB(NOW(), INTERVAL 5 HOUR)");
        insert(userB, "b-overdue-2", 0, 1, "DATE_SUB(NOW(), INTERVAL 6 HOUR)");
    }

    private void insert(long owner, String name, int status, int favourite, String dueSql) {
        db.update("INSERT INTO task(task_name,status,description,created_time,due_time,user_id,favourite) " +
                "VALUES(?,?, 'fixture', NOW(), " + dueSql + ", ?, ?)", name, status, owner, favourite);
    }

    private String login(String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/user/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("userName", name, "userPassword", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        JsonNode response = json.readTree(result.getResponse().getContentAsString());
        assertEquals(1, response.path("code").asInt());
        return response.path("data").path("token").asText();
    }

    private JsonNode dashboard(String token) throws Exception {
        MvcResult result = mvc.perform(get("/api/task/dashboard").header("token", token)).andExpect(status().isOk()).andReturn();
        JsonNode response = json.readTree(result.getResponse().getContentAsString());
        assertEquals(1, response.path("code").asInt());
        return response.path("data");
    }

    @Test
    void dashboardAggregatesOnlyCurrentUsersTasks() throws Exception {
        boolean sameDay = db.queryForObject(
                "SELECT DATE(DATE_ADD(NOW(), INTERVAL 30 MINUTE)) = DATE(NOW())", Boolean.class);
        long expectedDueToday = sameDay ? 1 : 0;
        long expectedOverdue = sameDay ? 1 : 2;
        int expectedGroups = sameDay ? 3 : 2;

        JsonNode a = dashboard(tokenA);
        assertEquals(6, a.path("total").asLong());
        assertEquals(2, a.path("completed").asLong());
        assertEquals(4, a.path("pending").asLong());
        assertEquals(2, a.path("favourite").asLong());
        assertEquals(33.3, a.path("completionRate").asDouble(), 0.01);
        assertEquals(expectedOverdue, a.path("overduePending").asLong());
        assertEquals(expectedDueToday, a.path("dueToday").asLong());
        assertEquals(3, a.path("dueNext7").asLong());
        assertEquals(expectedGroups, a.path("dueByDay").size());
        long distributed = 0;
        for (JsonNode day : a.path("dueByDay")) distributed += day.path("count").asLong();
        assertEquals(3, distributed);

        JsonNode b = dashboard(tokenB);
        assertEquals(5, b.path("total").asLong());
        assertEquals(3, b.path("completed").asLong());
        assertEquals(2, b.path("pending").asLong());
        assertEquals(2, b.path("favourite").asLong());
        assertEquals(2, b.path("overduePending").asLong());
        assertEquals(60.0, b.path("completionRate").asDouble(), 0.01);
    }

    @Test
    void dashboardRequiresAuthenticationAndEmptyUserIsZeroed() throws Exception {
        mvc.perform(get("/api/task/dashboard")).andExpect(status().isUnauthorized());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        db.update("INSERT INTO user(user_name,user_password,task_count) VALUES(?,?,0)", "c_" + suffix, Md5Util.getMD5String(PASSWORD));
        long empty = db.queryForObject("SELECT user_id FROM user WHERE user_name=?", Long.class, "c_" + suffix);
        String token = login("c_" + suffix);
        db.update("DELETE FROM task WHERE user_id=?", empty);
        JsonNode stats = dashboard(token);
        assertEquals(0, stats.path("total").asLong());
        assertEquals(0.0, stats.path("completionRate").asDouble(), 0.0);
        assertEquals(0, stats.path("dueByDay").size());
    }
}
