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

package com.automq.rocketmq.store;

import com.automq.rocketmq.store.model.message.AckResult;
import com.automq.rocketmq.store.model.message.ChangeInvisibleTimeResult;
import com.automq.rocketmq.store.model.message.PopResult;
import java.util.UUID;

public interface MessageStore {
    PopResult pop(UUID topicId, int queueId, long offset, int maxCount, UUID groupId, boolean isOrder);

    AckResult ack(String receiptHandle);

    ChangeInvisibleTimeResult changeInvisibleTime(String receiptHandle, int nextVisibleTime);

    int getInflightStatsByQueue(UUID topicId, int queueId);
}