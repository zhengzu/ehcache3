/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ehcache.impl.internal.statistics;

import org.ehcache.Cache;
import org.ehcache.Status;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.core.InternalCache;
import org.ehcache.core.events.CacheManagerListener;
import org.ehcache.core.spi.service.CacheManagerProviderService;
import org.ehcache.core.spi.service.StatisticsService;
import org.ehcache.core.spi.store.InternalCacheManager;
import org.ehcache.spi.service.Service;
import org.ehcache.spi.service.ServiceDependencies;
import org.ehcache.spi.service.ServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default implementation using the statistics calculated by the observers set on the caches.
 *
 * @author Henri Tremblay
 */
@ServiceDependencies(CacheManagerProviderService.class)
public class DefaultStatisticsService implements StatisticsService, CacheManagerListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultStatisticsService.class);

  private final ConcurrentMap<String, CacheStatistics> cacheStatistics = new ConcurrentHashMap<String, CacheStatistics>();
  private volatile InternalCacheManager cacheManager;
  private volatile boolean started = false;

  private CacheStatistics getStatistics(String cacheName) {
    CacheStatistics stats = cacheStatistics.get(cacheName);
    if (stats == null) {
      throw new IllegalArgumentException("Unknown cache: " + cacheName);
    }
    return stats;
  }

  public boolean isStarted() {
    return started;
  }

  @Override
  public void start(ServiceProvider<Service> serviceProvider) {
    LOGGER.debug("Starting service");
    CacheManagerProviderService cacheManagerProviderService = serviceProvider.getService(CacheManagerProviderService.class);
    cacheManager = cacheManagerProviderService.getCacheManager();
    cacheManager.registerListener(this);
    started = true;
  }

  @Override
  public void stop() {
    LOGGER.debug("Stopping service");
    cacheManager.deregisterListener(this);
    cacheStatistics.clear();
    started = false;
  }

  @Override
  public void stateTransition(Status from, Status to) {
    LOGGER.debug("Moving from " + from + " to " + to);
    switch(to) {
      case AVAILABLE:
        registerAllCaches();
        break;
      case UNINITIALIZED:
        cacheManager.deregisterListener(this);
        cacheStatistics.clear();
        break;
      case MAINTENANCE:
        throw new IllegalStateException("Should not be started in maintenance mode");
      default:
        throw new AssertionError("Unsupported state: " + to);
    }
  }

  private void registerAllCaches() {
    for (Map.Entry<String, CacheConfiguration<?, ?>> entry : cacheManager.getRuntimeConfiguration().getCacheConfigurations().entrySet()) {
      String alias = entry.getKey();
      CacheConfiguration<?, ?> configuration = entry.getValue();
      Cache<?, ?> cache = cacheManager.getCache(alias, configuration.getKeyType(), configuration.getValueType());
      cacheAdded(alias, cache);
    }
  }

  @Override
  public void cacheAdded(String alias, Cache<?, ?> cache) {
    LOGGER.debug("Cache added " + alias);
    cacheStatistics.put(alias, new CacheStatistics((InternalCache<?, ?>) cache));
  }

  @Override
  public void cacheRemoved(String alias, Cache<?, ?> cache) {
    LOGGER.debug("Cache removed " + alias);
    cacheStatistics.remove(alias);
  }

  @Override
  public void clear(String cacheName) {
    getStatistics(cacheName).clear();
  }

  @Override
  public long getCacheHits(String cacheName) {
    return getStatistics(cacheName).getCacheHits();
  }

  @Override
  public float getCacheHitPercentage(String cacheName) {
    return getStatistics(cacheName).getCacheHitPercentage();
  }

  @Override
  public long getCacheMisses(String cacheName) {
    return getStatistics(cacheName).getCacheMisses();
  }

  @Override
  public float getCacheMissPercentage(String cacheName) {
    return getStatistics(cacheName).getCacheMissPercentage();
  }

  @Override
  public long getCacheGets(String cacheName) {
    return getStatistics(cacheName).getCacheGets();
  }

  @Override
  public long getCachePuts(String cacheName) {
    return getStatistics(cacheName).getCachePuts();
  }

  @Override
  public long getCacheRemovals(String cacheName) {
    return getStatistics(cacheName).getCacheRemovals();
  }

  @Override
  public long getCacheEvictions(String cacheName) {
    return getStatistics(cacheName).getCacheEvictions();
  }

  @Override
  public float getAverageGetTime(String cacheName) {
    return getStatistics(cacheName).getAverageGetTime ();
  }

  @Override
  public float getAveragePutTime(String cacheName) {
    return getStatistics(cacheName).getAveragePutTime();
  }

  @Override
  public float getAverageRemoveTime(String cacheName) {
    return getStatistics(cacheName).getAverageRemoveTime();
  }
}
