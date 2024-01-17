/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.axis2.transport.rabbitmq;

import com.rabbitmq.client.Channel;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

/**
 * Pool implementation for the rabbitmq channel associating with the connection factory name
 */
public class RabbitMQChannelPool extends GenericKeyedObjectPool<String, Channel> {

    private static final Log log = LogFactory.getLog(RabbitMQChannelPool.class);

    public RabbitMQChannelPool(RabbitMQChannelFactory rabbitMQChannelFactory, int poolSize, RabbitMQConnectionFactory
            factory) {
        super(rabbitMQChannelFactory);
        this.setTestOnBorrow(true);
        this.setMaxTotal(poolSize);
        Map<String, String> evictionParams = factory.getConnectionFactoryConfiguration
                (RabbitMQConstants.EVICTION_STRATEGY_PARAMETERS);
        if (evictionParams != null) {
            // For int values
            trySetIntParam(evictionParams, RabbitMQConstants.MAX_IDLE_PER_KEY, this::setMaxIdlePerKey);
            trySetIntParam(evictionParams, RabbitMQConstants.MAX_IDLE_PER_KEY, this::setMaxTotalPerKey);

            // For long values
            trySetLongParam(evictionParams, RabbitMQConstants.MAX_WAIT_MILLIS, this::setMaxWaitMillis);
            trySetLongParam(evictionParams, RabbitMQConstants.MIN_EVICTABLE_IDLE_TIME,
                    this::setMinEvictableIdleTimeMillis);
            trySetLongParam(evictionParams, RabbitMQConstants.TIME_BETWEEN_EVICTION_RUNS
                    , this::setTimeBetweenEvictionRunsMillis);
            this.setTestWhileIdle(true); // Optionally, test objects for validity while idle
        }
    }

    public RabbitMQChannelPool(RabbitMQConfirmChannelFactory rabbitMQConfirmChannelFactory, int poolSize,
                               RabbitMQConnectionFactory factory) {
        super(rabbitMQConfirmChannelFactory);
        this.setTestOnBorrow(true);
        this.setMaxTotal(poolSize);
        Map<String, String> evictionParams = factory.getConnectionFactoryConfiguration
                (RabbitMQConstants.EVICTION_STRATEGY_PARAMETERS);
        if (evictionParams != null) {
            // For int values
            trySetIntParam(evictionParams, RabbitMQConstants.MAX_IDLE_PER_KEY, this::setMaxIdlePerKey);
            trySetIntParam(evictionParams, RabbitMQConstants.MAX_IDLE_PER_KEY, this::setMaxTotalPerKey);

            // For long values
            trySetLongParam(evictionParams, RabbitMQConstants.MAX_WAIT_MILLIS, this::setMaxWaitMillis);
            trySetLongParam(evictionParams, RabbitMQConstants.MIN_EVICTABLE_IDLE_TIME,
                    this::setMinEvictableIdleTimeMillis);
            trySetLongParam(evictionParams, RabbitMQConstants.TIME_BETWEEN_EVICTION_RUNS
                    , this::setTimeBetweenEvictionRunsMillis);
            this.setTestWhileIdle(true); // Optionally, test objects for validity while idle
        }
    }

    // Helper method for int parameters
    private void trySetIntParam(Map<String, String> params, String key, Consumer<Integer> setter) {
        String value = params.get(key);
        if (value != null) {
            try {
                setter.accept(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                log.warn("Invalid value for " + key + " : " + value);
            }
        }
    }

    // Helper method for long parameters
    private void trySetLongParam(Map<String, String> params, String key, Consumer<Long> setter) {
        String value = params.get(key);
        if (value != null) {
            try {
                setter.accept(Long.parseLong(value));
            } catch (NumberFormatException e) {
                log.warn("Invalid value for " + key + " : " + value);
            }
        }
    }


    /**
     * Obtains an instance from the pool for the specified key
     *
     * @param factoryName pool key
     * @return a {@link Channel} object
     * @throws Exception
     */
    @Override
    public Channel borrowObject(String factoryName) throws Exception {
        try {
            return super.borrowObject(factoryName);
        } catch (NoSuchElementException nse) {
            // The exception was caused by an exhausted pool
            if (null == nse.getCause()) {
                throw new AxisRabbitMQException("Error occurred while getting a channel of " + factoryName +
                        " since the pool is exhausted", nse);
            }
            // Otherwise, the exception was caused by the implemented activateObject() or validateObject()
            throw new AxisRabbitMQException("Error occurred while borrowing a channel of " + factoryName, nse);
        }
    }

    /**
     * Returns a channel to a keyed pool if it opens. Otherwise, destroy the channel
     *
     * @param factoryName pool key
     * @param channel     instance to return to the keyed pool
     */
    @Override
    public void returnObject(String factoryName, Channel channel) {
        try {
            if (channel != null) {
                if (channel.isOpen()) {
                    super.returnObject(factoryName, channel);
                } else {
                    super.invalidateObject(factoryName, channel);
                }
            }
        } catch (Exception e) {
            log.error("Error occurred while returning a channel of " + factoryName + " back to the pool", e);
        }
    }
}
