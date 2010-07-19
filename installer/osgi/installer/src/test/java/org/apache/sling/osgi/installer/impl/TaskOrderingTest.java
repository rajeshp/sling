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
package org.apache.sling.osgi.installer.impl;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Set;
import java.util.TreeSet;

import org.apache.sling.osgi.installer.InstallableResourceFactory;
import org.apache.sling.osgi.installer.impl.tasks.BundleInstallTask;
import org.apache.sling.osgi.installer.impl.tasks.BundleRemoveTask;
import org.apache.sling.osgi.installer.impl.tasks.BundleStartTask;
import org.apache.sling.osgi.installer.impl.tasks.BundleUpdateTask;
import org.apache.sling.osgi.installer.impl.tasks.ConfigInstallTask;
import org.apache.sling.osgi.installer.impl.tasks.ConfigRemoveTask;
import org.apache.sling.osgi.installer.impl.tasks.SynchronousRefreshPackagesTask;

/** Test the ordering and duplicates elimination of
 * 	OsgiControllerTasks
 */
public class TaskOrderingTest {

    private static InstallableResourceFactory factory = new InstallableResourceFactoryImpl();

    private Set<OsgiInstallerTask> taskSet;

	@org.junit.Before public void setUp() {
	    // The data type must be consistent with the "tasks" member
	    // of the {@link OsgiControllerImpl} class.
		taskSet = new TreeSet<OsgiInstallerTask>();
	}

	private static RegisteredResource getRegisteredResource(String url) throws IOException {
		return new RegisteredResourceImpl(new MockOsgiInstallerContext(), factory.create(url, new Hashtable<String, Object>(), null, null, null));
	}

	private void assertOrder(int testId, Collection<OsgiInstallerTask> actual, OsgiInstallerTask [] expected) {
		int index = 0;
		for(OsgiInstallerTask t : actual) {
			if(!t.equals(expected[index])) {
				fail("Test " + testId + ": at index " + index + ", expected " + expected[index] + " but got " + t);
			}
			index++;
		}
	}

	@org.junit.Test
	public void testBasicOrdering() throws Exception {
		int testIndex = 1;
		final OsgiInstallerTask [] tasksInOrder = {
		    new ConfigRemoveTask(getRegisteredResource("test:a")),
            new ConfigInstallTask(getRegisteredResource("test:a")),
		    new BundleRemoveTask(getRegisteredResource("test:url")),
		    new BundleUpdateTask(getRegisteredResource("test:url")),
		    new BundleInstallTask(getRegisteredResource("test:url")),
			new SynchronousRefreshPackagesTask(),
			new BundleStartTask(0),
		};

		taskSet.clear();
        taskSet.add(tasksInOrder[6]);
		taskSet.add(tasksInOrder[5]);
		taskSet.add(tasksInOrder[4]);
		taskSet.add(tasksInOrder[3]);
		taskSet.add(tasksInOrder[2]);
        taskSet.add(tasksInOrder[1]);
        taskSet.add(tasksInOrder[0]);

		assertOrder(testIndex++, taskSet, tasksInOrder);

		taskSet.clear();
        taskSet.add(tasksInOrder[0]);
        taskSet.add(tasksInOrder[1]);
		taskSet.add(tasksInOrder[2]);
		taskSet.add(tasksInOrder[3]);
		taskSet.add(tasksInOrder[4]);
		taskSet.add(tasksInOrder[5]);
		taskSet.add(tasksInOrder[6]);

		assertOrder(testIndex++, taskSet, tasksInOrder);

		taskSet.clear();
		taskSet.add(tasksInOrder[3]);
		taskSet.add(tasksInOrder[2]);
        taskSet.add(tasksInOrder[0]);
		taskSet.add(tasksInOrder[5]);
		taskSet.add(tasksInOrder[4]);
        taskSet.add(tasksInOrder[1]);
		taskSet.add(tasksInOrder[6]);

		assertOrder(testIndex++, taskSet, tasksInOrder);

		taskSet.clear();
		taskSet.add(tasksInOrder[4]);
		taskSet.add(tasksInOrder[5]);
		taskSet.add(tasksInOrder[6]);
        taskSet.add(tasksInOrder[0]);
		taskSet.add(tasksInOrder[2]);
		taskSet.add(tasksInOrder[3]);
        taskSet.add(tasksInOrder[1]);

		assertOrder(testIndex++, taskSet, tasksInOrder);
	}

	@org.junit.Test
	public void testMultipleConfigAndBundles() throws Exception {
		int testIndex = 1;
		final OsgiInstallerTask [] tasksInOrder = {
			new BundleInstallTask(getRegisteredResource("test:someURIa.nothing")),
            new BundleInstallTask(getRegisteredResource("test:someURIb.nothing")),
			new SynchronousRefreshPackagesTask(),
			new BundleStartTask(0),
		};

		taskSet.clear();
		for(int i = tasksInOrder.length -1 ; i >= 0; i--) {
			taskSet.add(tasksInOrder[i]);
		}

		assertOrder(testIndex++, taskSet, tasksInOrder);

        taskSet.clear();
        for(int i = 0 ; i < tasksInOrder.length; i++) {
            taskSet.add(tasksInOrder[i]);
        }

        assertOrder(testIndex++, taskSet, tasksInOrder);
	}

	@org.junit.Test
	public void testMultipleRefreshAndStart() throws Exception {
		int testIndex = 1;
		final OsgiInstallerTask [] tasksInOrder = {
		    new BundleRemoveTask(getRegisteredResource("test:url")),
			new SynchronousRefreshPackagesTask(),
			new BundleStartTask(0),
			new BundleStartTask(1),
		};

		taskSet.clear();
		taskSet.add(tasksInOrder[3]);
		taskSet.add(tasksInOrder[3]);
		taskSet.add(new SynchronousRefreshPackagesTask());
		taskSet.add(tasksInOrder[2]);
		taskSet.add(tasksInOrder[2]);
		taskSet.add(tasksInOrder[1]);
		taskSet.add(new SynchronousRefreshPackagesTask());
		taskSet.add(new SynchronousRefreshPackagesTask());
		taskSet.add(tasksInOrder[0]);
		taskSet.add(tasksInOrder[3]);
		taskSet.add(new SynchronousRefreshPackagesTask());
		taskSet.add(tasksInOrder[3]);
		taskSet.add(tasksInOrder[2]);
		taskSet.add(new SynchronousRefreshPackagesTask());
		taskSet.add(tasksInOrder[2]);
		taskSet.add(tasksInOrder[1]);
		taskSet.add(new SynchronousRefreshPackagesTask());
		taskSet.add(tasksInOrder[1]);
		taskSet.add(new SynchronousRefreshPackagesTask());

		assertOrder(testIndex++, taskSet, tasksInOrder);
	}

	@org.junit.Test
	public void testBundleStartOrder() {
		int testIndex = 1;
		final OsgiInstallerTask [] tasksInOrder = {
			new BundleStartTask(0),
			new BundleStartTask(1),
			new BundleStartTask(5),
			new BundleStartTask(11),
			new BundleStartTask(51)
		};

		taskSet.clear();
		for(int i = tasksInOrder.length -1 ; i >= 0; i--) {
			taskSet.add(tasksInOrder[i]);
		}
		assertOrder(testIndex++, taskSet, tasksInOrder);

        taskSet.clear();
        for(int i = 0 ; i < tasksInOrder.length; i++) {
            taskSet.add(tasksInOrder[i]);
        }

        assertOrder(testIndex++, taskSet, tasksInOrder);
	}
}