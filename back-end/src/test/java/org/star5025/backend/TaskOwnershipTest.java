package org.star5025.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import org.star5025.backend.context.BaseContext;
import org.star5025.backend.dto.TaskDTO;
import org.star5025.backend.dto.TaskPageQueryDTO;
import org.star5025.backend.dto.TaskPatchDTO;
import org.star5025.backend.entity.Task;
import org.star5025.backend.mapper.TaskMapper;
import org.star5025.backend.service.TaskService;
import org.star5025.backend.service.UserService;
import org.star5025.backend.service.impl.TaskServiceImpl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskOwnershipTest {
    @Mock private TaskMapper taskMapper;
    @Mock private UserService userService;
    @Mock private org.star5025.backend.service.ReminderService reminderService;
    @Mock private org.star5025.backend.mapper.TaskScheduleMapper taskScheduleMapper;
    @InjectMocks private TaskServiceImpl service;

    @AfterEach
    void clearContext() {
        BaseContext.removeCurrentId();
        PageHelper.clearPage();
    }

    private Task ownedTask(long userId) {
        return Task.builder().taskId(userId * 10).userId(userId).taskName("original")
                .status(false).favourite(true).description("keep").build();
    }

    private void assertStatus(HttpStatus status, org.junit.jupiter.api.function.Executable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action).getStatus());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2})
    void readIsScopedForBothUsers(long userId) {
        BaseContext.setCurrentId(userId);
        Task own = ownedTask(userId);
        long foreignId = (3 - userId) * 10;
        when(taskMapper.getTaskById(own.getTaskId(), userId)).thenReturn(own);
        assertSame(own, service.getTaskById(own.getTaskId()));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getTaskById(foreignId));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getTaskById(999L));
        verify(taskMapper).getTaskById(foreignId, userId);
        verifyNoInteractions(userService);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2})
    void updateIsScopedForBothUsersAndPreservesOldSnapshot(long userId) {
        BaseContext.setCurrentId(userId);
        Task own = ownedTask(userId);
        long foreignId = (3 - userId) * 10;
        TaskPatchDTO patch = TaskPatchDTO.builder().taskName("changed").status(true).favourite(false).build();
        assertStatus(HttpStatus.NOT_FOUND, () -> service.updateTask(foreignId, patch));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.updateTask(999L, patch));
        verify(taskMapper, never()).updateTask(any());
        when(taskMapper.getTaskByIdForUpdate(own.getTaskId(), userId)).thenReturn(own);
        when(taskMapper.updateTask(any())).thenReturn(1);
        service.updateTask(own.getTaskId(), patch);
        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateTask(saved.capture());
        assertEquals(userId, saved.getValue().getUserId());
        assertEquals(own.getTaskId(), saved.getValue().getTaskId());
        assertEquals("changed", saved.getValue().getTaskName());
        assertEquals("keep", saved.getValue().getDescription());
        assertTrue(saved.getValue().getStatus());
        assertFalse(saved.getValue().getFavourite());
        assertEquals("original", own.getTaskName());
        assertFalse(own.getStatus());
        verify(taskMapper, never()).getTaskById(anyLong(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2})
    void singleDeleteValidatesOwnershipBeforeWriting(long userId) {
        BaseContext.setCurrentId(userId);
        Task own = ownedTask(userId);
        long foreignId = (3 - userId) * 10;
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deleteTask(foreignId));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deleteTask(999L));
        verify(taskMapper, never()).deleteTask(anyLong(), anyLong());
        when(taskMapper.getTaskById(own.getTaskId(), userId)).thenReturn(own);
        when(taskMapper.deleteTask(own.getTaskId(), userId)).thenReturn(1);
        service.deleteTask(own.getTaskId());
        var order = inOrder(taskMapper);
        order.verify(taskMapper).getTaskById(own.getTaskId(), userId);
        order.verify(taskMapper).deleteTask(own.getTaskId(), userId);
        verifyNoInteractions(userService);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2})
    void batchMixedForeignOrMissingIdsRejectsWholeBatchBeforeWriting(long userId) {
        BaseContext.setCurrentId(userId);
        long ownId = userId * 10;
        long foreignId = (3 - userId) * 10;
        List<Long> mixed = List.of(ownId, foreignId);
        List<Long> missing = List.of(ownId, 999L);
        when(taskMapper.getOwnedTaskIdsForUpdate(mixed, userId)).thenReturn(List.of(ownId));
        when(taskMapper.getOwnedTaskIdsForUpdate(missing, userId)).thenReturn(List.of(ownId));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deleteTasks(mixed));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deleteTasks(missing));
        verify(taskMapper, never()).deleteTasks(anyList(), anyLong());
        verifyNoInteractions(userService);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2})
    void ownedBatchDeduplicatesAndDeletesOnlyAfterValidation(long userId) {
        BaseContext.setCurrentId(userId);
        List<Long> unique = List.of(userId * 10, userId * 10 + 1);
        when(taskMapper.getOwnedTaskIdsForUpdate(unique, userId)).thenReturn(unique);
        when(taskMapper.deleteTasks(unique, userId)).thenReturn(2);
        service.deleteTasks(List.of(unique.get(0), unique.get(1), unique.get(0)));
        var order = inOrder(taskMapper);
        order.verify(taskMapper).getOwnedTaskIdsForUpdate(unique, userId);
        order.verify(taskMapper).deleteTasks(unique, userId);
        verifyNoMoreInteractions(taskMapper);
    }

    static Stream<List<Long>> invalidBatches() {
        return Stream.of(null, List.of(), Arrays.asList(10L, null), List.of(0L), List.of(-1L));
    }

    @ParameterizedTest
    @MethodSource("invalidBatches")
    void invalidBatchIs400WithoutAnyDatabaseCall(List<Long> ids) {
        BaseContext.setCurrentId(1L);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.deleteTasks(ids));
        verifyNoInteractions(taskMapper, userService);
    }

    static Stream<Long> invalidIds() {
        return Stream.of(null, 0L, -1L);
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void invalidSingleIdsAre400WithoutDatabaseCalls(Long id) {
        BaseContext.setCurrentId(1L);
        TaskPatchDTO patch = TaskPatchDTO.builder().taskName("changed").build();
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.getTaskById(id));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.deleteTask(id));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.updateTask(id, patch));
        verifyNoInteractions(taskMapper, userService);
    }

    @Test
    void emptyPatchAndNullCreateAre400WithoutDatabaseCalls() {
        BaseContext.setCurrentId(1L);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.updateTask(10L, null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.updateTask(10L, new TaskPatchDTO()));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createTask(null));
        verifyNoInteractions(taskMapper, userService);
    }

    @Test
    void missingAuthenticationRejectsAllOperationsBeforeDatabaseCalls() {
        BaseContext.removeCurrentId();
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.getTaskById(10L));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.updateTask(10L, TaskPatchDTO.builder().taskName("x").build()));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.deleteTask(10L));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.deleteTasks(List.of(10L)));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.createTask(TaskDTO.builder().build()));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.pageQuery(1, 10, 2L, null, null, null, null));
        verifyNoInteractions(taskMapper, userService);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2})
    void createAndPaginationIgnoreSuppliedUserId(long userId) {
        BaseContext.setCurrentId(userId);
        service.createTask(TaskDTO.builder().userId(3 - userId).taskName("new").build());
        ArgumentCaptor<Task> created = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).createTask(created.capture());
        assertEquals(userId, created.getValue().getUserId());
        verify(userService).incrementTaskCount(userId, 1);
        when(taskMapper.pageQueryWithFavourite(userId, null, null, null, null)).thenReturn(new Page<>());
        service.pageQuery(1, 10, 3 - userId, null, null, null, null);
        service.pageQuery(new TaskPageQueryDTO(1, 10, 3 - userId));
        verify(taskMapper, times(2)).pageQueryWithFavourite(userId, null, null, null, null);
    }

    @Test
    void explicitNullReminderClearsButOmissionPreservesReminder() throws Exception {
        BaseContext.setCurrentId(1L);
        Task old = ownedTask(1L);
        LocalDateTime reminder = LocalDateTime.of(2026, 11, 1, 12, 0);
        old.setRemindTime(reminder);
        when(taskMapper.getTaskByIdForUpdate(10L, 1L)).thenReturn(old);
        when(taskMapper.updateTask(any())).thenReturn(1);
        ObjectMapper json = new ObjectMapper();
        TaskPatchDTO clear = json.readValue("{\"remindTime\":null}", TaskPatchDTO.class);
        assertTrue(clear.isRemindTimeSet());
        service.updateTask(10L, clear);
        service.updateTask(10L, json.readValue("{\"taskName\":\"changed\"}", TaskPatchDTO.class));
        ArgumentCaptor<Task> updated = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper, times(2)).updateTask(updated.capture());
        assertNull(updated.getAllValues().get(0).getRemindTime());
        assertEquals(reminder, updated.getAllValues().get(1).getRemindTime());
        assertEquals(reminder, old.getRemindTime());
        assertFalse(json.writeValueAsString(clear).contains("remindTimeSet"));
        assertFalse(json.readValue("{\"remindTimeSet\":true}", TaskPatchDTO.class).isRemindTimeSet());
    }

    private TaskService transactionalService(PlatformTransactionManager manager) {
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        ProxyFactory factory = new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (TaskService) factory.getProxy();
    }

    @Test
    void batchCountMismatchRollsBackTransaction() {
        BaseContext.setCurrentId(1L);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TaskService transactional = transactionalService(manager);
        List<Long> ids = List.of(10L, 11L);
        when(taskMapper.getOwnedTaskIdsForUpdate(ids, 1L)).thenReturn(ids);
        when(taskMapper.deleteTasks(ids, 1L)).thenReturn(1);
        assertStatus(HttpStatus.NOT_FOUND, () -> transactional.deleteTasks(ids));
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
    }

    @Test
    void mixedBatchRollsBackWithoutDelete() {
        BaseContext.setCurrentId(1L);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TaskService transactional = transactionalService(manager);
        when(taskMapper.getOwnedTaskIdsForUpdate(List.of(10L, 20L), 1L)).thenReturn(List.of(10L));
        assertStatus(HttpStatus.NOT_FOUND, () -> transactional.deleteTasks(List.of(10L, 20L)));
        verify(taskMapper, never()).deleteTasks(anyList(), anyLong());
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
    }

    @Test
    void createCountFailureRollsBackTaskInsert() {
        BaseContext.setCurrentId(1L);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TaskService transactional = transactionalService(manager);
        doThrow(new IllegalStateException("count unavailable")).when(userService).incrementTaskCount(1L, 1);
        assertThrows(IllegalStateException.class, () -> transactional.createTask(TaskDTO.builder().taskName("new").build()));
        verify(taskMapper).createTask(any());
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
    }

    @Test
    void updateAndDeleteReturn404WhenGuardedWriteAffectsNoRow() {
        BaseContext.setCurrentId(1L);
        when(taskMapper.getTaskById(10L, 1L)).thenReturn(ownedTask(1L));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deleteTask(10L));
        when(taskMapper.getTaskByIdForUpdate(10L, 1L)).thenReturn(ownedTask(1L));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.updateTask(10L, TaskPatchDTO.builder().taskName("x").build()));
    }
}
