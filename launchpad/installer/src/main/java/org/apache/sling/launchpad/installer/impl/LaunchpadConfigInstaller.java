/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.sling.launchpad.installer.impl;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.launchpad.api.LaunchpadContentProvider;
import org.apache.sling.installer.api.InstallableResource;
import org.apache.sling.installer.api.OsgiInstaller;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class LaunchpadConfigInstaller {

    /** Resources supplied under this path by 
     *  LaunchpadContentProvider are considered for installation
     */
    private static final String ROOT_CONFIG_PATH = "resources/config";

    @Reference
    private OsgiInstaller installer;

    @Reference
    private LaunchpadContentProvider resourceProvider;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    protected void activate(ComponentContext componentContext) {
        logger.info("Activating launchpad config installer, resources path={}", ROOT_CONFIG_PATH);
        Collection<InstallableResource> installables = new HashSet<InstallableResource>();

        Iterator<String> configPaths = resourceProvider.getChildren(ROOT_CONFIG_PATH);
        while (configPaths.hasNext()) {
            String path = configPaths.next();
            logger.info("Config launchpad file will be installed: {}", path);
            InputStream stream = resourceProvider.getResourceAsStream(path);
            installables.add(new InstallableResource(path, stream, null, null, InstallableResource.TYPE_CONFIG, 0));
        }

        final InstallableResource [] toInstall = installables.toArray(new InstallableResource []{});
        installer.registerResources("launchpad", (toInstall));
        logger.info("{} resources registered with OsgiInstaller", toInstall.length);
    }
}