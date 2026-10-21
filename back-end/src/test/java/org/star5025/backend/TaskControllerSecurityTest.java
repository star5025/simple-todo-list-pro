package org.star5025.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.star5025.backend.controller.TaskController;
import org.star5025.backend.dto.TaskPatchDTO;
import org.star5025.backend.service.TaskService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaskControllerSecurityTest {
    @Mock private TaskService taskService;
    @InjectMocks private TaskController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // Standalone MVC checks controller propagation; global error translation is integrated separately.
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void updatePropagatesTypedErrorWithoutStringMatching() {
        TaskPatchDTO patch = TaskPatchDTO.builder().taskName("x").build();
        ResponseStatusException error = new ResponseStatusException(HttpStatus.NOT_FOUND);
        doThrow(error).when(taskService).updateTask(10L, patch);
        assertSame(error, assertThrows(ResponseStatusException.class, () -> controller.updateTask(10L, patch)));
    }

    @Test
    void ownershipErrorsBecome404AtEveryObjectEndpoint() throws Exception {
        when(taskService.getTaskById(20L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(taskService).updateTask(eq(20L), any());
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(taskService).deleteTask(20L);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(taskService).deleteTasks(List.of(10L, 20L));
        mvc.perform(get("/api/task/20")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/task/20").contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/task/20")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/task/batch/delete").contentType(MediaType.APPLICATION_JSON).content("[10,20]"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingBodyIs400BeforeCallingService() throws Exception {
        mvc.perform(delete("/api/task/batch/delete").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/task/10").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        verifyNoInteractions(taskService);
    }

    @Test
    void emptyBatchHas400RatherThanSuccessfulEnvelope() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST)).when(taskService).deleteTasks(List.of());
        mvc.perform(delete("/api/task/batch/delete").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingAuthenticationHas401() throws Exception {
        when(taskService.getTaskById(10L)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED)).when(taskService).updateTask(eq(10L), any());
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED)).when(taskService).deleteTask(10L);
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED)).when(taskService).deleteTasks(List.of(10L));
        mvc.perform(get("/api/task/10")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/task/10").contentType(MediaType.APPLICATION_JSON).content("{\"status\":true}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/task/10")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/task/batch/delete").contentType(MediaType.APPLICATION_JSON).content("[10]"))
                .andExpect(status().isUnauthorized());
    }
}
