/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache;

import org.apache.ignite.*;
import org.apache.ignite.cache.*;
import org.apache.ignite.cache.affinity.*;
import org.apache.ignite.cache.cloner.*;
import org.apache.ignite.cache.eviction.*;
import org.apache.ignite.cache.store.*;
import org.apache.ignite.cluster.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.lifecycle.LifecycleAware;
import org.apache.ignite.resources.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.testframework.junits.common.*;
import org.jetbrains.annotations.*;

import javax.cache.*;
import javax.cache.configuration.*;
import javax.cache.integration.*;
import java.util.*;

import static org.apache.ignite.cache.GridCacheDistributionMode.*;
import static org.apache.ignite.cache.CacheMode.*;

/**
 * Test for {@link LifecycleAware} support in {@link CacheConfiguration}.
 */
public class GridCacheLifecycleAwareSelfTest extends GridAbstractLifecycleAwareSelfTest {
    /** */
    private static final String CACHE_NAME = "cache";

    /** */
    private GridCacheDistributionMode distroMode;

    /** */
    private boolean writeBehind;

    /**
     */
    private static class TestStore extends CacheStore implements LifecycleAware {
        /** */
        private final TestLifecycleAware lifecycleAware = new TestLifecycleAware(CACHE_NAME);

        /** {@inheritDoc} */
        @Override public void start() throws IgniteCheckedException {
            lifecycleAware.start();
        }

        /** {@inheritDoc} */
        @Override public void stop() throws IgniteCheckedException {
            lifecycleAware.stop();
        }

        /**
         * @param cacheName Cache name.
         */
        @IgniteCacheNameResource
        public void setCacheName(String cacheName) {
            lifecycleAware.cacheName(cacheName);
        }

        /** {@inheritDoc} */
        @Nullable @Override public Object load(Object key) {
            return null;
        }

        /** {@inheritDoc} */
        @Override public void loadCache(IgniteBiInClosure clo, @Nullable Object... args) {
            // No-op.
        }

        /** {@inheritDoc} */
        @Override public Map loadAll(Iterable keys) throws CacheLoaderException {
            return Collections.emptyMap();
        }

        /** {@inheritDoc} */
        @Override public void write(Cache.Entry entry) {
            // No-op.
        }

        /** {@inheritDoc} */
        @Override public void writeAll(Collection col) {
            // No-op.
        }

        /** {@inheritDoc} */
        @Override public void delete(Object key) {
            // No-op.
        }

        /** {@inheritDoc} */
        @Override public void deleteAll(Collection keys) {
            // No-op.
        }

        /** {@inheritDoc} */
        @Override public void txEnd(boolean commit) {
            // No-op.
        }
    }

    /**
     */
    private static class TestAffinityFunction extends TestLifecycleAware implements GridCacheAffinityFunction {
        /**
         */
        TestAffinityFunction() {
            super(CACHE_NAME);
        }

        /** {@inheritDoc} */
        @Override public void reset() {
            // No-op.
        }

        /** {@inheritDoc} */
        @Override public int partitions() {
            return 1;
        }

        /** {@inheritDoc} */
        @Override public int partition(Object key) {
            return 0;
        }

        /** {@inheritDoc} */
        @Override public List<List<ClusterNode>> assignPartitions(GridCacheAffinityFunctionContext affCtx) {
            List<List<ClusterNode>> res = new ArrayList<>();

            res.add(nodes(0, affCtx.currentTopologySnapshot()));

            return res;
        }

        /** {@inheritDoc} */
        public List<ClusterNode> nodes(int part, Collection<ClusterNode> nodes) {
            return new ArrayList<>(nodes);
        }

        /** {@inheritDoc} */
        @Override public void removeNode(UUID nodeId) {
            // No-op.
        }
    }

    /**
     */
    private static class TestEvictionPolicy extends TestLifecycleAware implements GridCacheEvictionPolicy {
        /**
         */
        TestEvictionPolicy() {
            super(CACHE_NAME);
        }

        /** {@inheritDoc} */
        @Override public void onEntryAccessed(boolean rmv, GridCacheEntry entry) {
            // No-op.
        }
    }

    /**
     */
    private static class TestEvictionFilter extends TestLifecycleAware implements GridCacheEvictionFilter {
        /**
         */
        TestEvictionFilter() {
            super(CACHE_NAME);
        }

        /** {@inheritDoc} */
        @Override public boolean evictAllowed(GridCacheEntry entry) {
            return false;
        }
    }

    /**
     */
    private static class TestCloner extends TestLifecycleAware implements GridCacheCloner {
        /**
         */
        TestCloner() {
            super(CACHE_NAME);
        }

        /** {@inheritDoc} */
        @Nullable @Override public <T> T cloneValue(T val) throws IgniteCheckedException {
            return val;
        }
    }

    /**
     */
    private static class TestAffinityKeyMapper extends TestLifecycleAware implements GridCacheAffinityKeyMapper {
        /**
         */
        TestAffinityKeyMapper() {
            super(CACHE_NAME);
        }

        /** {@inheritDoc} */
        @Override public Object affinityKey(Object key) {
            return key;
        }

        /** {@inheritDoc} */
        @Override public void reset() {
            // No-op.
        }
    }

    /**
     */
    private static class TestInterceptor extends TestLifecycleAware implements GridCacheInterceptor {
        /**
         */
        private TestInterceptor() {
            super(CACHE_NAME);
        }

        /** {@inheritDoc} */
        @Nullable @Override public Object onGet(Object key, @Nullable Object val) {
            return val;
        }

        /** {@inheritDoc} */
        @Nullable @Override public Object onBeforePut(Object key, @Nullable Object oldVal, Object newVal) {
            return newVal;
        }

        /** {@inheritDoc} */
        @Override public void onAfterPut(Object key, Object val) {
            // No-op.
        }

        /** {@inheritDoc} */
        @SuppressWarnings("unchecked") @Nullable @Override public IgniteBiTuple onBeforeRemove(Object key, @Nullable Object val) {
            return new IgniteBiTuple(false, val);
        }

        /** {@inheritDoc} */
        @Override public void onAfterRemove(Object key, Object val) {
            // No-op.
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override protected final IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        cfg.setDiscoverySpi(new TcpDiscoverySpi());

        CacheConfiguration ccfg = defaultCacheConfiguration();

        ccfg.setCacheMode(PARTITIONED);

        ccfg.setDistributionMode(distroMode);

        ccfg.setWriteBehindEnabled(writeBehind);

        ccfg.setCacheMode(CacheMode.PARTITIONED);

        ccfg.setName(CACHE_NAME);

        TestStore store = new TestStore();

        ccfg.setCacheStoreFactory(new FactoryBuilder.SingletonFactory(store));
        ccfg.setReadThrough(true);
        ccfg.setWriteThrough(true);
        ccfg.setLoadPreviousValue(true);

        lifecycleAwares.add(store.lifecycleAware);

        TestAffinityFunction affinity = new TestAffinityFunction();

        ccfg.setAffinity(affinity);

        lifecycleAwares.add(affinity);

        TestEvictionPolicy evictionPlc = new TestEvictionPolicy();

        ccfg.setEvictionPolicy(evictionPlc);

        lifecycleAwares.add(evictionPlc);

        TestEvictionPolicy nearEvictionPlc = new TestEvictionPolicy();

        ccfg.setNearEvictionPolicy(nearEvictionPlc);

        lifecycleAwares.add(nearEvictionPlc);

        TestEvictionFilter evictionFilter = new TestEvictionFilter();

        ccfg.setEvictionFilter(evictionFilter);

        lifecycleAwares.add(evictionFilter);

        TestCloner cloner = new TestCloner();

        ccfg.setCloner(cloner);

        lifecycleAwares.add(cloner);

        TestAffinityKeyMapper mapper = new TestAffinityKeyMapper();

        ccfg.setAffinityMapper(mapper);

        lifecycleAwares.add(mapper);

        TestInterceptor interceptor = new TestInterceptor();

        lifecycleAwares.add(interceptor);

        ccfg.setInterceptor(interceptor);

        cfg.setCacheConfiguration(ccfg);

        return cfg;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("ErrorNotRethrown")
    @Override public void testLifecycleAware() throws Exception {
        for (GridCacheDistributionMode mode : new GridCacheDistributionMode[] {PARTITIONED_ONLY, NEAR_PARTITIONED}) {
            distroMode = mode;

            writeBehind = false;

            try {
                super.testLifecycleAware();
            }
            catch (AssertionError e) {
                throw new AssertionError("Failed for [distroMode=" + distroMode + ", writeBehind=" + writeBehind + ']',
                    e);
            }

            writeBehind = true;

            try {
                super.testLifecycleAware();
            }
            catch (AssertionError e) {
                throw new AssertionError("Failed for [distroMode=" + distroMode + ", writeBehind=" + writeBehind + ']',
                    e);
            }
        }
    }
}
