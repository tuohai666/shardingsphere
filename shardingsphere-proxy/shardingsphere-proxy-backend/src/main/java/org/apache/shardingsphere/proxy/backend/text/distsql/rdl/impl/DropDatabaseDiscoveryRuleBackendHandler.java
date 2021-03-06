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

package org.apache.shardingsphere.proxy.backend.text.distsql.rdl.impl;

import org.apache.shardingsphere.dbdiscovery.api.config.DatabaseDiscoveryRuleConfiguration;
import org.apache.shardingsphere.dbdiscovery.api.config.rule.DatabaseDiscoveryDataSourceRuleConfiguration;
import org.apache.shardingsphere.dbdiscovery.common.yaml.config.YamlDatabaseDiscoveryRuleConfiguration;
import org.apache.shardingsphere.distsql.parser.statement.rdl.drop.impl.DropDatabaseDiscoveryRuleStatement;
import org.apache.shardingsphere.governance.core.registry.listener.event.rule.RuleConfigurationsAlteredEvent;
import org.apache.shardingsphere.infra.config.RuleConfiguration;
import org.apache.shardingsphere.infra.eventbus.ShardingSphereEventBus;
import org.apache.shardingsphere.infra.yaml.swapper.YamlRuleConfigurationSwapperEngine;
import org.apache.shardingsphere.proxy.backend.communication.jdbc.connection.BackendConnection;
import org.apache.shardingsphere.proxy.backend.context.ProxyContext;
import org.apache.shardingsphere.proxy.backend.exception.DatabaseDiscoveryRuleDataSourcesNotExistedException;
import org.apache.shardingsphere.proxy.backend.exception.DatabaseDiscoveryRuleNotExistedException;
import org.apache.shardingsphere.proxy.backend.response.header.ResponseHeader;
import org.apache.shardingsphere.proxy.backend.response.header.update.UpdateResponseHeader;
import org.apache.shardingsphere.proxy.backend.text.SchemaRequiredBackendHandler;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Drop database discovery rule backend handler.
 */
public final class DropDatabaseDiscoveryRuleBackendHandler extends SchemaRequiredBackendHandler<DropDatabaseDiscoveryRuleStatement> {

    public DropDatabaseDiscoveryRuleBackendHandler(final DropDatabaseDiscoveryRuleStatement sqlStatement, final BackendConnection backendConnection) {
        super(sqlStatement, backendConnection);
    }
    
    @Override
    public ResponseHeader execute(final String schemaName, final DropDatabaseDiscoveryRuleStatement sqlStatement) {
        Optional<DatabaseDiscoveryRuleConfiguration> ruleConfig = ProxyContext.getInstance().getMetaData(schemaName).getRuleMetaData().getConfigurations().stream()
                .filter(each -> each instanceof DatabaseDiscoveryRuleConfiguration).map(each -> (DatabaseDiscoveryRuleConfiguration) each).findFirst();
        if (!ruleConfig.isPresent()) {
            throw new DatabaseDiscoveryRuleNotExistedException(schemaName);
        }
        check(ruleConfig.get(), sqlStatement);
        YamlDatabaseDiscoveryRuleConfiguration yamlConfig = new YamlRuleConfigurationSwapperEngine()
                .swapToYamlRuleConfigurations(Collections.singletonList(ruleConfig.get())).stream()
                .map(each -> (YamlDatabaseDiscoveryRuleConfiguration) each).findFirst().get();
        drop(yamlConfig, sqlStatement);
        post(schemaName, new YamlRuleConfigurationSwapperEngine()
                .swapToRuleConfigurations(yamlConfig.getDataSources().isEmpty() ? Collections.emptyList() : Collections.singletonList(yamlConfig)));
        return new UpdateResponseHeader(sqlStatement);
    }
    
    private void check(final DatabaseDiscoveryRuleConfiguration ruleConfig, final DropDatabaseDiscoveryRuleStatement sqlStatement) {
        Collection<String> databaseDiscoveryDataSourceNames = ruleConfig.getDataSources().stream().map(DatabaseDiscoveryDataSourceRuleConfiguration::getName).collect(Collectors.toList());
        Collection<String> notExistedRuleNames = sqlStatement.getRuleNames().stream().filter(each -> !databaseDiscoveryDataSourceNames.contains(each)).collect(Collectors.toList());
        if (!notExistedRuleNames.isEmpty()) {
            throw new DatabaseDiscoveryRuleDataSourcesNotExistedException(notExistedRuleNames);
        }
    }

    private void drop(final YamlDatabaseDiscoveryRuleConfiguration yamlDatabaseDiscoveryRuleConfiguration, final DropDatabaseDiscoveryRuleStatement sqlStatement) {
        for (String each : sqlStatement.getRuleNames()) {
            yamlDatabaseDiscoveryRuleConfiguration.getDiscoveryTypes()
                    .remove(yamlDatabaseDiscoveryRuleConfiguration.getDataSources().get(each).getDiscoveryTypeName());
            yamlDatabaseDiscoveryRuleConfiguration.getDataSources().remove(each);
        }
    }
    
    private void post(final String schemaName, final Collection<RuleConfiguration> rules) {
        ShardingSphereEventBus.getInstance().post(new RuleConfigurationsAlteredEvent(schemaName, rules));
    }
}
