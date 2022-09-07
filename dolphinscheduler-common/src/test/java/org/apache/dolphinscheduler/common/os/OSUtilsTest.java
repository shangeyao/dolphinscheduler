/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dolphinscheduler.common.os;


import org.apache.commons.lang3.SystemUtils;
import org.apache.dolphinscheduler.common.utils.OSUtils;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * OSUtilsTest
 */
public class OSUtilsTest {

    private static Logger logger = LoggerFactory.getLogger(OSUtilsTest.class);

    @Test
    public void memoryUsage() {
        double memoryUsage = OSUtils.memoryUsage();
        logger.info("memoryUsage : {}", memoryUsage);
        Assert.assertTrue(memoryUsage >= 0.0);
    }

    @Test
    public void diskAvailable() {
        double diskAvailable = OSUtils.diskAvailable();
        logger.info("diskAvailable : {}", diskAvailable);
        Assert.assertTrue(diskAvailable >= 0.0);
    }

    @Test
    public void loadAverage() {
        double loadAverage = OSUtils.loadAverage();
        logger.info("loadAverage : {}", loadAverage);
        Assert.assertTrue(loadAverage >= 0.0);
    }

    @Test
    public void cpuUsage() {
        double cpuUsage = OSUtils.cpuUsage();
        logger.info("cpuUsage : {}", cpuUsage);
        Assert.assertTrue(cpuUsage >= 0.0);
    }

    @Test
    public void availablePhysicalMemorySize(){
        double physicalMemorySize = OSUtils.availablePhysicalMemorySize();
        logger.info("physicalMemorySize : {}", physicalMemorySize);
        Assert.assertTrue(physicalMemorySize >= 0.0);

    }

    @Test
    public void existTenantCodeInLinux(){
        if (SystemUtils.IS_OS_LINUX){
            boolean test = OSUtils.existTenantCodeInLinux("root");
            Assert.assertTrue(test);
            boolean test1 = OSUtils.existTenantCodeInLinux("xxxtt");
            Assert.assertFalse(test1);
        }else{
            Assert.assertFalse("system must be linux",false);
        }

    }

    @Test
    public void existOSTenandCode(){
        if (SystemUtils.IS_OS_LINUX){
            List<String> userList = OSUtils.getUserList();
            Assert.assertTrue(userList.contains("root"));
            Assert.assertFalse(userList.contains("xxxtt"));
        }else{
            Assert.assertFalse("system must be linux",false);

        }

    }



}
