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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 日历范围查询按开始时间过滤，且只返回当前用户的任务。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CalendarRangeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    private long userA;
    private long userB;
    private String tokenA;
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
    }

    private String login(String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/user/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("userName", name, "userPassword", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        JsonNode response = json.readTree(result.getResponse().getContentAsString());
        return response.path("data").path("token").asText();
    }

    private void task(long owner, String name, LocalDateTime start) {
        db.update("INSERT INTO task(task_name,status,created_time,start_time,user_id,favourite) VALUES(?,0,NOW(),?,?,0)", name, start, owner);
    }

    @Test
    void calendarReturnsOnlyOwnTasksWithinRange() throws Exception {
        LocalDateTime from = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 4, 1, 0, 0);
        task(userA, "in-range", LocalDateTime.of(2026, 3, 15, 9, 0));
        task(userA, "before", LocalDateTime.of(2026, 2, 28, 23, 59));
        task(userA, "after", LocalDateTime.of(2026, 4, 1, 0, 0));
        task(userA, "no-start", null);
        task(userB, "foreign-in-range", LocalDateTime.of(2026, 3, 16, 9, 0));

        JsonNode data = json.readTree(mvc.perform(get("/api/task/calendar")
                .param("from", from + "Z").param("to", to + "Z").header("token", tokenA))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertEquals(1, data.size());
        assertEquals("in-range", data.get(0).path("taskName").asText());
    }

    @Test
    void calendarRejectsInvalidRangeAndMissingAuth() throws Exception {
        mvc.perform(get("/api/task/calendar")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/task/calendar").header("token", tokenA)
                .param("from", "2026-04-01T00:00:00Z").param("to", "2026-03-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }
}
