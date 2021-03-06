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

package org.apache.shardingsphere.governance.core.registry.lock;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import org.apache.shardingsphere.governance.core.lock.node.LockAck;
import org.apache.shardingsphere.governance.core.lock.node.LockNode;
import org.apache.shardingsphere.governance.core.registry.RegistryCenterNode;
import org.apache.shardingsphere.governance.core.registry.instance.GovernanceInstance;
import org.apache.shardingsphere.governance.repository.spi.RegistryCenterRepository;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Lock registry center.
 */
public final class LockRegistryCenter {
    
    private static final int CHECK_ACK_MAXIMUM = 5;
    
    private static final int CHECK_ACK_INTERVAL_SECONDS = 1;
    
    private final String instanceId;
    
    private final RegistryCenterRepository repository;
    
    private final RegistryCenterNode node;
    
    private final LockNode lockNode;
    
    public LockRegistryCenter(final RegistryCenterRepository repository) {
        instanceId = GovernanceInstance.getInstance().getId();
        this.repository = repository;
        node = new RegistryCenterNode();
        lockNode = new LockNode();
        initLockNode();
    }
    
    private void initLockNode() {
        repository.persist(lockNode.getLockRootNodePath(), "");
        repository.persist(lockNode.getLockedAckRootNodePah(), "");
    }
    
    /**
     * Load all instances.
     *
     * @return collection of all instances
     */
    public Collection<String> loadAllInstances() {
        return repository.getChildrenKeys(node.getProxyNodesPath());
    }
    
    /**
     * Try to get lock.
     *
     * @param lockName lock name
     * @param timeout the maximum time in milliseconds to acquire lock
     * @return true if get the lock, false if not
     */
    public boolean tryLock(final String lockName, final long timeout) {
        return repository.tryLock(lockNode.getLockNodePath(lockName), timeout, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Release lock.
     * 
     * @param lockName lock name
     */
    public void releaseLock(final String lockName) {
        repository.releaseLock(lockNode.getLockNodePath(lockName));
    }
    
    /**
     * Ack lock.
     * 
     * @param lockName lock name
     */
    public void ackLock(final String lockName) {
        repository.persistEphemeral(lockNode.getLockedAckNodePath(Joiner.on("-").join(instanceId, lockName)), LockAck.LOCKED.getValue());
    }
    
    /**
     * Ack unlock.
     * 
     * @param lockName lock name
     */
    public void ackUnlock(final String lockName) {
        repository.persistEphemeral(lockNode.getLockedAckNodePath(Joiner.on("-").join(instanceId, lockName)), LockAck.UNLOCKED.getValue());
    }
    
    /**
     * Delete lock ack.
     * 
     * @param lockName lock name
     */
    public void deleteLockAck(final String lockName) {
        repository.delete(lockNode.getLockedAckNodePath(Joiner.on("-").join(instanceId, lockName)));
    }
    
    /**
     * Check lock ack.
     * 
     * @param lockName lock name
     * @return true if all instances ack lock, false if not
     */
    public boolean checkLockAck(final String lockName) {
        boolean result = checkAck(loadAllInstances(), lockName, LockAck.LOCKED.getValue());
        if (!result) {
            releaseLock(lockName);
        }
        return result;
    }
    
    /**
     * Check unlock ack.
     * 
     * @param lockName lock name
     * @return true if all instances ack unlock, false if not
     */
    public boolean checkUnlockAck(final String lockName) {
        return checkAck(loadAllInstances(), lockName, LockAck.UNLOCKED.getValue());
    }
    
    private boolean checkAck(final Collection<String> instanceIds, final String lockName, final String ackValue) {
        for (int i = 0; i < CHECK_ACK_MAXIMUM; i++) {
            if (check(instanceIds, lockName, ackValue)) {
                return true;
            }
            try {
                Thread.sleep(CHECK_ACK_INTERVAL_SECONDS * 1000L);
                // CHECKSTYLE:OFF
            } catch (final InterruptedException ex) {
                // CHECKSTYLE:ON
            }
        }
        return false;
    }
    
    private boolean check(final Collection<String> instanceIds, final String lockName, final String ackValue) {
        for (String each : instanceIds) {
            if (!ackValue.equalsIgnoreCase(loadLockAck(each, lockName))) {
                return false;
            }
        }
        return true;
    }
    
    private String loadLockAck(final String instanceId, final String lockName) {
        return Strings.nullToEmpty(repository.get(lockNode.getLockedAckNodePath(Joiner.on("-").join(instanceId, lockName))));
    }
}
