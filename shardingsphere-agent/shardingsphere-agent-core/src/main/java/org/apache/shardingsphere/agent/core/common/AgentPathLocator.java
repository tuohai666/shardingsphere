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
 *
 */

package org.apache.shardingsphere.agent.core.common;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.agent.core.exception.ShardingSphereAgentException;

/**
 * Agent path locator.
 */
@Slf4j
public class AgentPathLocator {
    
    private static File agentPath;
    
    static {
        agentPath = locatorPath();
    }
    
    /**
     * Find path file.
     *
     * @return file
     */
    public static File findPath() {
        return agentPath;
    }
    
    private static File locatorPath() throws ShardingSphereAgentException {
        String classResourcePath = AgentPathLocator.class.getName().replaceAll("\\.", "/") + ".class";
        URL resource = ClassLoader.getSystemClassLoader().getResource(classResourcePath);
        if (resource != null) {
            String url = resource.toString();
            log.debug("The beacon class location is {}.", url);
            int insidePathIndex = url.indexOf('!');
            boolean isInJar = insidePathIndex > -1;
            if (isInJar) {
                url = url.substring(url.indexOf("file:"), insidePathIndex);
                try {
                    File agentJarFile = new File(new URL(url).toURI());
                    return agentJarFile.exists() ? agentJarFile.getParentFile() : null;
                } catch (MalformedURLException | URISyntaxException e) {
                    log.error("Can not locate agent jar file by url:" + url);
                }
            } else {
                int prefixLength = "file:".length();
                String classLocation = url.substring(prefixLength, url.length() - classResourcePath.length());
                return new File(classLocation);
            }
        }
        log.error("Can not locate agent jar file.");
        throw new ShardingSphereAgentException("Can not locate agent jar file.");
    }
}