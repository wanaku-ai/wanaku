package ai.wanaku.core.persistence.infinispan.codeexecution;

import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.infinispan.Cache;
import org.infinispan.commons.api.query.Query;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionStatus;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionTask;

/**
 * Infinispan-based repository for storing and managing {@link CodeExecutionTask} instances.
 * <p>
 * This repository provides persistent storage for code execution tasks, replacing the
 * in-memory storage used in the MVP implementation. It supports CRUD operations
 * and status-based queries.
 */
@Singleton
public class InfinispanCodeTaskRepository {
    private static final String CACHE_NAME = "codeExecutionTasks";

    private final EmbeddedCacheManager cacheManager;
    private final ReentrantLock lock = new ReentrantLock();

    public InfinispanCodeTaskRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        this.cacheManager = cacheManager;

        if (cacheManager.getCacheConfiguration(CACHE_NAME) == null) {
            cacheManager.defineConfiguration(CACHE_NAME, configuration);
        }
    }

    /**
     * Stores a new task in the repository.
     * <p>
     * If the task doesn't have a taskId, one will be generated automatically.
     *
     * @param task the task to store
     * @return the stored task with its assigned ID
     * @throws IllegalArgumentException if task is null
     */
    public CodeExecutionTask store(CodeExecutionTask task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }

        try {
            lock.lock();
            if (task.getTaskId() == null) {
                task.setTaskId(UUID.randomUUID().toString());
            }

            Cache<String, CodeExecutionTask> cache = cacheManager.getCache(CACHE_NAME);
            cache.put(task.getTaskId(), task);
            return task;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves a task by its UUID.
     *
     * @param taskId the task UUID
     * @return an Optional containing the task if found, empty otherwise
     */
    public Optional<CodeExecutionTask> findById(String taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        Cache<String, CodeExecutionTask> cache = cacheManager.getCache(CACHE_NAME);
        return Optional.ofNullable(cache.get(taskId));
    }

    /**
     * Removes a task from the repository.
     *
     * @param taskId the task UUID to remove
     * @return true if the task was removed, false if it didn't exist
     */
    public boolean remove(String taskId) {
        if (taskId == null) {
            return false;
        }

        Cache<String, CodeExecutionTask> cache = cacheManager.getCache(CACHE_NAME);
        return cache.remove(taskId) != null;
    }

    /**
     * Updates a task in the repository.
     *
     * @param task the task to update
     * @return true if the task was updated, false otherwise
     */
    public boolean update(CodeExecutionTask task) {
        if (task == null || task.getTaskId() == null) {
            return false;
        }

        try {
            lock.lock();
            Cache<String, CodeExecutionTask> cache = cacheManager.getCache(CACHE_NAME);
            cache.put(task.getTaskId(), task);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Updates the status of a task.
     *
     * @param taskId the task UUID
     * @param status the new status
     * @return true if the task was found and updated, false otherwise
     */
    public boolean updateStatus(String taskId, CodeExecutionStatus status) {
        if (taskId == null || status == null) {
            return false;
        }

        try {
            lock.lock();
            Cache<String, CodeExecutionTask> cache = cacheManager.getCache(CACHE_NAME);
            CodeExecutionTask task = cache.get(taskId);
            if (task != null) {
                task.setStatus(status);
                cache.put(taskId, task);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if a task exists in the repository.
     *
     * @param taskId the task UUID
     * @return true if the task exists, false otherwise
     */
    public boolean exists(String taskId) {
        return findById(taskId).isPresent();
    }

    /**
     * Returns all tasks in the repository.
     *
     * @return a list of all tasks
     */
    public List<CodeExecutionTask> listAll() {
        Cache<String, CodeExecutionTask> cache = cacheManager.getCache(CACHE_NAME);
        return cache.values().stream().toList();
    }

    /**
     * Returns tasks with the specified status.
     *
     * @param status the status to filter by
     * @return a list of tasks with the given status
     */
    public List<CodeExecutionTask> findByStatus(CodeExecutionStatus status) {
        Cache<String, CodeExecutionTask> cache = cacheManager.getCache(CACHE_NAME);

        Query<CodeExecutionTask> query = cache.query(
                "from ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionTask t where t.status = :status");

        query.setParameter("status", status);
        return query.execute().list();
    }

    /**
     * Returns the number of tasks currently in the repository.
     *
     * @return the task count
     */
    public int size() {
        Cache<String, CodeExecutionTask> cache = cacheManager.getCache(CACHE_NAME);
        return cache.size();
    }

    /**
     * Clears all tasks from the repository.
     * <p>
     * This method is primarily intended for testing purposes.
     */
    public void clear() {
        try {
            lock.lock();
            Cache<String, CodeExecutionTask> cache = cacheManager.getCache(CACHE_NAME);
            cache.clear();
        } finally {
            lock.unlock();
        }
    }
}
