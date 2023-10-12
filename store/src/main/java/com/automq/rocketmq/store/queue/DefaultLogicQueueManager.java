/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.automq.rocketmq.store.queue;

import apache.rocketmq.controller.v1.Code;
import com.automq.rocketmq.common.config.StoreConfig;
import com.automq.rocketmq.controller.exception.ControllerException;
import com.automq.rocketmq.metadata.api.StoreMetadataService;
import com.automq.rocketmq.store.api.LogicQueue;
import com.automq.rocketmq.store.api.MessageStateMachine;
import com.automq.rocketmq.store.api.StreamStore;
import com.automq.rocketmq.store.api.TopicQueueManager;
import com.automq.rocketmq.store.exception.StoreErrorCode;
import com.automq.rocketmq.store.exception.StoreException;
import com.automq.rocketmq.store.model.message.TopicQueueId;
import com.automq.rocketmq.store.service.InflightService;
import com.automq.rocketmq.store.service.api.KVService;
import com.automq.rocketmq.store.service.api.OperationLogService;
import com.automq.stream.utils.FutureUtil;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DefaultLogicQueueManager implements TopicQueueManager {
    protected static final Logger LOGGER = LoggerFactory.getLogger(DefaultLogicQueueManager.class);

    private final StoreConfig storeConfig;
    private final StreamStore streamStore;
    private final KVService kvService;
    private final StoreMetadataService metadataService;
    private final OperationLogService operationLogService;
    private final InflightService inflightService;
    private final Map<TopicQueueId, CompletableFuture<LogicQueue>> topicQueueMap;
    private final String identity = "[DefaultLogicQueueManager]";

    public DefaultLogicQueueManager(StoreConfig storeConfig, StreamStore streamStore,
        KVService kvService, StoreMetadataService metadataService, OperationLogService operationLogService,
        InflightService inflightService) {
        this.storeConfig = storeConfig;
        this.streamStore = streamStore;
        this.kvService = kvService;
        this.metadataService = metadataService;
        this.operationLogService = operationLogService;
        this.inflightService = inflightService;
        this.topicQueueMap = new ConcurrentHashMap<>();
    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void shutdown() throws Exception {

    }

    public int size() {
        return topicQueueMap.size();
    }

    @Override
    public CompletableFuture<LogicQueue> getOrCreate(long topicId, int queueId) {
        TopicQueueId key = new TopicQueueId(topicId, queueId);
        CompletableFuture<LogicQueue> future = topicQueueMap.get(key);

        if (future != null) {
            return future;
        }

        // Prevent concurrent create.
        synchronized (this) {
            future = topicQueueMap.get(key);
            if (future != null) {
                return future;
            }

            // Create and open the topic queue.
            future = createAndOpen(topicId, queueId);
            topicQueueMap.put(key, future);
            future.exceptionally(ex -> {
                Throwable cause = FutureUtil.cause(ex);
                LOGGER.error("{}: Create logic queue failed: topic: {} queue: {}", identity, topicId, queueId, cause);
                topicQueueMap.remove(key);
                return null;
            });
            return future;
        }
    }

    @Override
    public CompletableFuture<Optional<LogicQueue>> get(long topicId, int queueId) {
        TopicQueueId key = new TopicQueueId(topicId, queueId);
        CompletableFuture<LogicQueue> future = topicQueueMap.get(key);
        if (future != null) {
            return future.thenApply(Optional::of);
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Void> close(long topicId, int queueId) {
        LOGGER.info("{}: Close logic queue: {} queue: {}", identity, topicId, queueId);
        TopicQueueId key = new TopicQueueId(topicId, queueId);
        CompletableFuture<LogicQueue> future = topicQueueMap.remove(key);
        if (future != null) {
            return future.thenCompose(LogicQueue::close);
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<LogicQueue> createAndOpen(long topicId, int queueId) {
        TopicQueueId id = new TopicQueueId(topicId, queueId);
        if (topicQueueMap.containsKey(id)) {
            return CompletableFuture.completedFuture(null);
        }

        MessageStateMachine stateMachine = new DefaultLogicQueueStateMachine(topicId, queueId, kvService);
        LogicQueue logicQueue = new StreamLogicQueue(storeConfig, topicId, queueId,
            metadataService, stateMachine, streamStore, operationLogService, inflightService);

        LOGGER.info("{}: Create and open logic queue success: topic: {} queue: {}", identity, topicId, queueId);
        return logicQueue.open()
            .thenApply(nil -> logicQueue)
            .exceptionally(ex -> {
                Throwable cause = FutureUtil.cause(ex);
                LOGGER.error("{}: Open logic queue failed: topic: {} queue: {}", identity, topicId, queueId, cause);
                if (cause instanceof ControllerException) {
                    ControllerException controllerException = (ControllerException) cause;
                    switch (controllerException.getErrorCode()) {
                        case Code.FENCED_VALUE -> throw new CompletionException(new StoreException(StoreErrorCode.QUEUE_FENCED, cause.getMessage()));
                        case Code.NOT_FOUND_VALUE -> throw new CompletionException(new StoreException(StoreErrorCode.QUEUE_NOT_FOUND, cause.getMessage()));
                        default -> throw new CompletionException(new StoreException(StoreErrorCode.INNER_ERROR, cause.getMessage()));
                    }
                } else if (cause instanceof StoreException) {
                    throw new CompletionException(cause);
                } else {
                    throw new CompletionException(new StoreException(StoreErrorCode.INNER_ERROR, cause.getMessage()));
                }
            });
    }

}