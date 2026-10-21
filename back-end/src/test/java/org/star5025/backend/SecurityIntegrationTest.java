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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    private String tokenA;
    private String tokenB;
    private long userA;
    private long userB;
    private long taskA;
    private long taskB;
    private static final String PASSWORD = "safe-test-password";

    @BeforeEach
    void fixtures() throws Exception {
        String expectedDatabase = System.getenv("STDL_SECURITY_TEST_DATABASE");
        assertNotNull(expectedDatabase, "STDL_SECURITY_TEST_DATABASE must explicitly name the isolated test database");
        assertFalse(expectedDatabase.isBlank(), "STDL_SECURITY_TEST_DATABASE must not be blank");
        assertEquals(expectedDatabase, db.queryForObject("SELECT DATABASE()", String.class),
                "Connected database must match STDL_SECURITY_TEST_DATABASE before inserting fixtures");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String nameA = "a_" + suffix;
        String nameB = "b_" + suffix;
        db.update("INSERT INTO user(user_name,user_password,task_count) VALUES(?,?,0)", nameA, Md5Util.getMD5String(PASSWORD));
        db.update("INSERT INTO user(user_name,user_password,task_count) VALUES(?,?,0)", nameB, Md5Util.getMD5String(PASSWORD));
        userA = db.queryForObject("SELECT user_id FROM user WHERE user_name=?", Long.class, nameA);
        userB = db.queryForObject("SELECT user_id FROM user WHERE user_name=?", Long.class, nameB);
        tokenA = login(nameA, PASSWORD);
        tokenB = login(nameB, PASSWORD);
        taskA = task(userA, "owned-a", LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(2));
        taskB = task(userB, "owned-b", LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(2));
    }

    private String login(String name, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/user/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("userName", name, "userPassword", password))))
                .andExpect(status().isOk()).andReturn();
        JsonNode response = json.readTree(result.getResponse().getContentAsString());
        assertEquals(1, response.path("code").asInt());
        return response.path("data").path("token").asText();
    }

    private long task(long owner, String name, LocalDateTime reminder) {
        db.update("INSERT INTO task(task_name,status,created_time,remind_time,due_time,user_id,favourite) VALUES(?,0,NOW(),?,?,?,0)", name, reminder, reminder.plusHours(1), owner);
        return db.queryForObject("SELECT MAX(task_id) FROM task WHERE user_id=?", Long.class, owner);
    }

    @Test
    void taskEndpointsRejectOtherUserAndBatchIsAtomic() throws Exception {
        mvc.perform(get("/api/task/" + taskB).header("token", tokenA)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/task/" + taskB).header("token", tokenA).contentType("application/json").content("{\"taskName\":\"stolen\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/task/" + taskB).header("token", tokenA)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/task/batch/delete").header("token", tokenA).contentType("application/json")
                .content("[" + taskA + "," + taskB + "]")).andExpect(status().isNotFound());
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM task WHERE task_id IN (?,?)", Integer.class, taskA, taskB));
        mvc.perform(get("/api/task/" + taskB).header("token", tokenB)).andExpect(status().isOk());
        mvc.perform(delete("/api/task/batch/delete").header("token", tokenA).contentType("application/json").content("[]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userResponsesNeverExposePasswordAndForeignMutationsFail() throws Exception {
        String body = mvc.perform(get("/api/user/" + userA).header("token", tokenA)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("userPassword"));
        assertFalse(body.contains("$2"));
        mvc.perform(get("/api/user/" + userB).header("token", tokenA)).andExpect(status().isForbidden());
        mvc.perform(put("/api/user").header("token", tokenA).contentType("application/json")
                .content(json.writeValueAsString(Map.of("userId", userB, "userName", "hijacked")))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/user").header("token", tokenA).param("userId", String.valueOf(userB))).andExpect(status().isForbidden());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM user WHERE user_id=?", Integer.class, userB));
    }

    @Test
    void successfulLegacyLoginUpgradesHashAndWrongOldPasswordCannotChangeIt() throws Exception {
        String upgraded = db.queryForObject("SELECT user_password FROM user WHERE user_id=?", String.class, userA);
        assertTrue(upgraded.startsWith("$2"));
        mvc.perform(put("/api/user").header("token", tokenA).contentType("application/json")
                .content(json.writeValueAsString(Map.of("userId", userA, "oldPassword", "wrong-password", "userPassword", "replacement-password"))))
                .andExpect(status().is4xxClientError());
        assertEquals(upgraded, db.queryForObject("SELECT user_password FROM user WHERE user_id=?", String.class, userA));
        mvc.perform(put("/api/user").header("token", tokenA).contentType("application/json")
                .content(json.writeValueAsString(Map.of("userId", userA, "oldPassword", PASSWORD, "userPassword", "replacement-password"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        String name = db.queryForObject("SELECT user_name FROM user WHERE user_id=?", String.class, userA);
        assertFalse(login(name, "replacement-password").isBlank());
    }

    @Test
    void reminderOwnershipRevisionAndCancellationAreEnforcedInDatabase() throws Exception {
        JsonNode reminders = json.readTree(mvc.perform(get("/api/reminders/due").header("token", tokenA)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
        assertTrue(reminders.findValues("taskId").stream().anyMatch(id -> id.asLong() == taskA));
        assertTrue(reminders.findValues("taskId").stream().noneMatch(id -> id.asLong() == taskB));
        mvc.perform(post("/api/reminders/" + taskB + "/ack").header("token", tokenA).contentType("application/json").content("{\"revision\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/reminders/" + taskA + "/ack").header("token", tokenA).contentType("application/json").content("{\"revision\":1}"))
                .andExpect(status().isOk());
        JsonNode after = json.readTree(mvc.perform(get("/api/reminders/due").header("token", tokenA)).andReturn().getResponse().getContentAsString()).path("data");
        assertTrue(after.findValues("taskId").stream().noneMatch(id -> id.asLong() == taskA));
        mvc.perform(patch("/api/task/" + taskA).header("token", tokenA).contentType("application/json").content("{\"remindTime\":null}"))
                .andExpect(status().isOk());
        assertNull(db.queryForObject("SELECT remind_time FROM task WHERE task_id=?", LocalDateTime.class, taskA));
        mvc.perform(post("/api/reminders/" + taskA + "/ack").header("token", tokenA).contentType("application/json").content("{\"revision\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    void repeatedCompletionGeneratesExactlyOneNextTask() throws Exception {
        mvc.perform(put("/api/task/" + taskA + "/schedule").header("token", tokenA).contentType("application/json").content("{\"repeatRule\":\"DAILY\"}"))
                .andExpect(status().isOk());
        for (int i = 0; i < 2; i++) {
            mvc.perform(patch("/api/task/" + taskA).header("token", tokenA).contentType("application/json").content("{\"status\":true}"))
                    .andExpect(status().isOk());
        }
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM task WHERE user_id=?", Integer.class, userA));
        Long nextId = db.queryForObject("SELECT successor_task_id FROM task_schedule WHERE task_id=?", Long.class, taskA);
        assertNotNull(nextId);
        assertEquals("DAILY", db.queryForObject("SELECT repeat_rule FROM task_schedule WHERE task_id=?", String.class, nextId));
        assertEquals(0, db.queryForObject("SELECT status FROM task WHERE task_id=?", Integer.class, nextId));
        mvc.perform(put("/api/task/" + taskB + "/schedule").header("token", tokenA).contentType("application/json").content("{\"repeatRule\":\"DAILY\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void utcDateProtocolRoundTripsExplicitOffsets() throws Exception {
        mvc.perform(patch("/api/task/" + taskA).header("token", tokenA).contentType("application/json")
                .content("{\"dueTime\":\"2028-01-31T09:00:00+08:00\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/task/" + taskA).header("token", tokenA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dueTime").value("2028-01-31T01:00:00Z"));
    }

    @Test
    void deletingOwnAccountRemovesTasksAndInvalidatesToken() throws Exception {
        mvc.perform(get("/api/task/" + taskA + "/schedule").header("token", tokenA)).andExpect(status().isOk());
        mvc.perform(delete("/api/user").header("token", tokenA).param("userId", String.valueOf(userA))).andExpect(status().isOk());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM task WHERE user_id=?", Integer.class, userA));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM task_schedule WHERE task_id=?", Integer.class, taskA));
        mvc.perform(get("/api/reminders/due").header("token", tokenA)).andExpect(status().isUnauthorized());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM task WHERE user_id=?", Integer.class, userB));
    }

    @Test
    void unauthenticatedAndMalformedRequestsUseRealHttpStatuses() throws Exception {
        mvc.perform(get("/api/task/" + taskA)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reminders/due")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/reminders/not-a-number/ack").header("token", tokenA).contentType("application/json").content("{\"revision\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/reminders/" + taskA + "/ack").header("token", tokenA).contentType("application/json").content("{bad"))
                .andExpect(status().isBadRequest());
    }
}
