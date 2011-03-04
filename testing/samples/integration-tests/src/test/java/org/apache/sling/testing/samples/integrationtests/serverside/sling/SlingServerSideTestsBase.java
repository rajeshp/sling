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
package org.apache.sling.testing.samples.integrationtests.serverside.sling;

import org.apache.sling.testing.tools.http.RetryingContentChecker;
import org.apache.sling.testing.tools.sling.SlingClient;
import org.apache.sling.testing.tools.sling.SlingTestBase;
import org.apache.sling.testing.tools.sling.TimeoutsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.fail;

/** Test server-side tests using the Sling JUnit servlet, as opposed
 *  to the plain JUnit servlet.
 */
public class SlingServerSideTestsBase extends SlingTestBase {
    /** Path to node that maps to Sling JUnit servlet */
    public static final String SERVLET_NODE_PATH =  "/test/sling/" + System.currentTimeMillis();
    
    /** Path used to request the Sling JUnit servlet */
    public static final String SLING_JUNIT_SERVLET_PATH = SERVLET_NODE_PATH + ".junit";
    
    private final Logger log = LoggerFactory.getLogger(getClass());
    private RetryingContentChecker servletChecker;
    private boolean servletCheckFailed;
    private boolean servletOk;
    private SlingClient slingClient;
    private static boolean servletNodeCreated;
    
    /** At startup, setup a node that gives access to the Sling JUnit servlet,
     *  and check (with timeout) that the servlet is ready */
    @Override
    protected void onServerReady(boolean serverStartedByThisClass) throws Exception {
        super.onServerReady(serverStartedByThisClass);
        
        if(!servletNodeCreated) {
            slingClient = new SlingClient(getServerBaseUrl(), ADMIN, ADMIN);
            try {
                slingClient.createNode(SERVLET_NODE_PATH, "sling:resourceType", "sling/junit/testing");
                servletNodeCreated = true;
            } catch(Exception e) {
                fail("Exception while setting up Sling JUnit servlet: " + e);
            }
        }
        
        if(servletCheckFailed) {
            fail(SLING_JUNIT_SERVLET_PATH + " check failed previously, cannot run tests");
        }
        
        if(!servletOk) {
            if(servletChecker == null) {
                servletChecker = new RetryingContentChecker(getRequestExecutor(), getRequestBuilder()) {
                    @Override
                    public void onTimeout() {
                        servletCheckFailed = true;
                    }
                };
            }

            final String path = SLING_JUNIT_SERVLET_PATH;
            final int status = 200;
            final int timeout = TimeoutsProvider.getInstance().getTimeout(30);
            final int intervalMsec = TimeoutsProvider.getInstance().getTimeout(500);
            log.info("Checking that {} returns status {}, timeout={} seconds",
                    new Object[] { path, status, timeout });
            servletChecker.check(path, status, timeout, intervalMsec);
            servletOk = true;
        }
    }
}