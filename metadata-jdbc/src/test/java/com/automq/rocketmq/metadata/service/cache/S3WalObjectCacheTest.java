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

package com.automq.rocketmq.metadata.service.cache;

import com.automq.rocketmq.metadata.DatabaseTestBase;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class S3WalObjectCacheTest extends DatabaseTestBase {

    @Test
    public void testLoad() throws IOException {
        S3WalObjectCache cache = new S3WalObjectCache(getSessionFactory());
        cache.load(1);
    }
}