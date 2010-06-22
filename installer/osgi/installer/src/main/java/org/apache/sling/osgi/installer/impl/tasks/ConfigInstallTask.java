/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.osgi.installer.impl.tasks;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Set;

import org.apache.sling.osgi.installer.impl.DigestUtil;
import org.apache.sling.osgi.installer.impl.OsgiInstallerContext;
import org.apache.sling.osgi.installer.impl.RegisteredResource;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

/** Install/remove task for configurations */
public class ConfigInstallTask extends AbstractConfigTask {

    static final String ALIAS_KEY = "_alias_factory_pid";
    static final String CONFIG_PATH_KEY = "_jcr_config_path";
    public static final String [] CONFIG_EXTENSIONS = { ".cfg", ".properties" };

    /** Configuration properties to ignore when comparing configs */
    public static Set<String> ignoredProperties = new HashSet<String>();
    static {
    	ignoredProperties.add("service.pid");
    	ignoredProperties.add(CONFIG_PATH_KEY);
    }

    public ConfigInstallTask(RegisteredResource r) {
        super(r);
    }

    @Override
    public String getSortKey() {
        return TaskOrder.CONFIG_INSTALL_ORDER + pid.getCompositePid();
    }

    @SuppressWarnings("unchecked")
	@Override
    public void execute(OsgiInstallerContext ctx) throws Exception {

        final ConfigurationAdmin ca = ctx.getConfigurationAdmin();
        if(ca == null) {
            ctx.addTaskToNextCycle(this);
            ctx.logDebug("ConfigurationAdmin not available, task will be retried later: " + this);
            return;
        }

        // Convert data to a configuration Dictionary
        Dictionary<String, Object> dict = resource.getDictionary();

        if (dict == null) {
            throw new IllegalArgumentException("Null Dictionary for resource " + resource);
        }

        // Add pseudo-properties
        dict.put(CONFIG_PATH_KEY, resource.getUrl());

        // Factory?
        if(pid.getFactoryPid() != null) {
            dict.put(ALIAS_KEY, pid.getFactoryPid());
        }

        // Get or create configuration, but do not
        // update if the new one has the same values.
        boolean created = false;
        Configuration config = getConfiguration(ca, pid, false, ctx);
        if(config == null) {
            created = true;
            config = getConfiguration(ca, pid, true, ctx);
        } else {
			if(isSameData(config.getProperties(), resource.getDictionary())) {
			    ctx.logDebug("Configuration " + config.getPid()
	                        + " already installed with same data, update request ignored: "
	                        + resource);
				config = null;
			}
        }

        if(config != null) {
            logExecution(ctx);
            if (config.getBundleLocation() != null) {
                config.setBundleLocation(null);
            }
            config.update(dict);
            ctx.logInfo("Configuration " + config.getPid()
                        + " " + (created ? "created" : "updated")
                        + " from " + resource);
        }
    }

    /** True if a and b represent the same config data, ignoring "non-configuration" keys in the dictionaries */
    boolean isSameData(Dictionary<String, Object>a, Dictionary<String, Object>b) throws NoSuchAlgorithmException, IOException {
    	boolean result = false;
    	if(a != null && b != null) {
    		final String da = DigestUtil.computeDigest(a, ignoredProperties);
    		final String db = DigestUtil.computeDigest(b, ignoredProperties);
    		result = da.equals(db);
    	}
    	return result;
    }
}