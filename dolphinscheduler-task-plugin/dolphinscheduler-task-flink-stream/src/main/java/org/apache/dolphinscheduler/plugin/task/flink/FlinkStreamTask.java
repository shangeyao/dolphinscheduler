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

package org.apache.dolphinscheduler.plugin.task.flink;

import org.apache.dolphinscheduler.common.thread.ThreadUtils;
import org.apache.dolphinscheduler.common.utils.CommonUtils;
import org.apache.dolphinscheduler.plugin.task.api.AbstractRemoteTask;
import org.apache.dolphinscheduler.plugin.task.api.TaskConstants;
import org.apache.dolphinscheduler.plugin.task.api.TaskException;
import org.apache.dolphinscheduler.plugin.task.api.TaskExecutionContext;
import org.apache.dolphinscheduler.plugin.task.api.enums.TaskExecutionStatus;
import org.apache.dolphinscheduler.plugin.task.api.model.ResourceInfo;
import org.apache.dolphinscheduler.plugin.task.api.parameters.AbstractParameters;
import org.apache.dolphinscheduler.plugin.task.api.stream.StreamTask;
import org.apache.dolphinscheduler.plugin.task.flink.entity.ParamsInfo;
import org.apache.dolphinscheduler.plugin.task.flink.entity.ResultInfo;
import org.apache.dolphinscheduler.plugin.task.flink.enums.ClusterClient;
import org.apache.dolphinscheduler.plugin.task.flink.enums.YarnTaskStatus;
import org.apache.dolphinscheduler.spi.utils.JSONUtils;

import java.util.*;

import org.apache.dolphinscheduler.spi.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.dolphinscheduler.plugin.task.api.TaskConstants.*;
import static org.apache.dolphinscheduler.plugin.task.flink.FlinkStreamConstants.*;

public class FlinkStreamTask extends AbstractRemoteTask implements StreamTask {

    /**
     * flink parameters
     */
    private FlinkStreamParameters flinkParameters;

    /**
     * taskExecutionContext
     */
    private TaskExecutionContext taskExecutionContext;



    private ResultInfo flinkStreamResultInfo;

    protected final Logger logger =
            LoggerFactory.getLogger(String.format(TaskConstants.TASK_LOG_LOGGER_NAME_FORMAT, getClass()));

    public FlinkStreamTask(TaskExecutionContext taskExecutionContext) {
        super(taskExecutionContext);
        this.taskExecutionContext = taskExecutionContext;
    }

    @Override
    public void init() {
        logger.info("flink task params {}", taskExecutionContext.getTaskParams());

        flinkParameters = JSONUtils.parseObject(taskExecutionContext.getTaskParams(), FlinkStreamParameters.class);

        if (flinkParameters == null || !flinkParameters.checkParameters()) {
            throw new RuntimeException("flink stream task params is not valid");
        }
        flinkParameters.setQueue(taskExecutionContext.getQueue());
        setMainJarName();
    }

    @Override
    public List<String> getApplicationIds() throws TaskException {
        return Collections.emptyList();
    }

    @Override
    public void cancelApplication() throws TaskException {
        String hadoopConfDir = System.getenv(HADOOP_CONF_DIR);

        initResultInfo();

        ParamsInfo jobParamsInfo = ParamsInfo.builder()
                .setHadoopConfDir(hadoopConfDir)
                .setApplicationId(flinkStreamResultInfo.getAppId())
                .setFlinkJobId(flinkStreamResultInfo.getJobId())
                .build();

        try {
            ClusterClient clusterClient = ClusterClient.INSTANCE;
            ResultInfo jobResult = clusterClient.cancelFlinkJob(jobParamsInfo);
            setExitStatusCode(EXIT_CODE_KILL);
            logger.info(
                    String.format(
                            "job cancel result, appId:%s, jobId:%s",
                            jobResult.getAppId(), jobResult.getJobId()));
        } catch (Exception e) {
            logger.error("cancel flink stream task failure", e);
            throw new TaskException("cancel flink stream task error", e);
        }
    }

    @Override
    public void submitApplication() throws TaskException {
        String flinkHome = System.getenv(FLINK_HOME);

        if (flinkHome.isEmpty()) {
            logger.error("Please make sure to set the FLINK_HOME environment variable.");
        }

        String hadoopConfDir = System.getenv(HADOOP_CONF_DIR);

        if (hadoopConfDir.isEmpty()) {
            logger.error("Please make sure to set the HADOOP_CONF_DIR environment variable.");
        }

        Properties confProperties = new Properties();
        confProperties.setProperty("parallelism.default", String.valueOf(flinkParameters.getParallelism()));
        confProperties.setProperty("jobmanager.memory.process.size", flinkParameters.getJobManagerMemory());
        confProperties.setProperty("taskmanager.memory.process.size", flinkParameters.getTaskManagerMemory());
        int slot = flinkParameters.getSlot();
        if (slot > 0) {
            confProperties.setProperty("taskmanager.numberOfTaskSlots", String.valueOf(flinkParameters.getTaskManager()));
        }

        if (flinkParameters.getQueue() == null) {
            flinkParameters.setQueue("root.default");
        }

        ParamsInfo jobParamsInfo = ParamsInfo.builder()
                .setExecArgs(new String[]{})
                .setName(flinkParameters.getAppName())
                .setRunJarPath(taskExecutionContext.getExecutePath().concat(flinkParameters.getMainJar().getResourceName()))
                .setFlinkConfDir(flinkHome.concat(FLINK_CONF_DIR))
                .setConfProperties(confProperties)
                .setFlinkJarPath(flinkHome.concat(FLINK_LIB_DIR))
                .setHadoopConfDir(hadoopConfDir)
                .setQueue(flinkParameters.getQueue())
                .setRunMode(flinkParameters.getDeployMode())
                .setOpenSecurity(CommonUtils.getKerberosStartupState())
                .build();

        try {
            ClusterClient clusterClient = ClusterClient.INSTANCE;
            ResultInfo jobResult = clusterClient.submitFlinkJob(jobParamsInfo);
            setAppIds(JSONUtils.toJsonString(jobResult));
            logger.info(
                    String.format(
                            "job submit result, appId:%s, jobId:%s",
                            jobResult.getAppId(), jobResult.getJobId()));
        } catch (Exception e) {
            logger.error("run flink stream task failure", e);
            setExitStatusCode(EXIT_CODE_FAILURE);
            throw new TaskException("run flink stream task error", e);
        }
    }

    @Override
    public void trackApplicationStatus() throws TaskException {
        String hadoopConfDir = System.getenv(HADOOP_CONF_DIR);

        initResultInfo();

        ParamsInfo jobParamsInfo = ParamsInfo.builder()
                .setHadoopConfDir(hadoopConfDir)
                .setApplicationId(flinkStreamResultInfo.getAppId())
                .setOpenSecurity(CommonUtils.getKerberosStartupState())
                .build();
        checkYarnExecutionStatus(jobParamsInfo);
    }

    @Override
    public AbstractParameters getParameters() {
        return flinkParameters;
    }

    @Override
    public void savePoint() throws Exception {
        String hadoopConfDir = System.getenv(HADOOP_CONF_DIR);

        initResultInfo();

        ParamsInfo jobParamsInfo = ParamsInfo.builder()
                .setHadoopConfDir(hadoopConfDir)
                .setFlinkJobId(flinkStreamResultInfo.getJobId())
                .setApplicationId(flinkStreamResultInfo.getAppId())
                .setOpenSecurity(CommonUtils.getKerberosStartupState())
                .build();

        try {
            ClusterClient clusterClient = ClusterClient.INSTANCE;
            ResultInfo jobResult = clusterClient.savePointFlinkJob(jobParamsInfo);
            logger.info(
                    String.format(
                            "job save point result, appId:%s, jobId:%s",
                            jobResult.getAppId(), jobResult.getJobId()));
        } catch (Exception e) {
            logger.error("flink stream task save point failure", e);
            throw new TaskException("flink stream task save point error", e);
        }
    }

    protected void setMainJarName() {
        // main jar
        ResourceInfo mainJar = flinkParameters.getMainJar();
        String resourceName = getResourceNameOfMainJar(mainJar);
        mainJar.setRes(resourceName);
        flinkParameters.setMainJar(mainJar);
    }

    protected String getResourceNameOfMainJar(ResourceInfo mainJar) {
        if (null == mainJar) {
            throw new RuntimeException("The jar for the task is required.");
        }

        return mainJar.getId() == null
                ? mainJar.getRes()
                // when update resource maybe has error
                : mainJar.getResourceName().replaceFirst("/", "");
    }

    private void initResultInfo() {
        if (flinkStreamResultInfo == null) {
            if (StringUtils.isNotEmpty(getAppIds())) {
                flinkStreamResultInfo = JSONUtils.parseObject(getAppIds(), ResultInfo.class);
            }
        }
        if (flinkStreamResultInfo == null) {
            throw new TaskException("flink stream applicationID and jobID is null");
        }
    }

    private void checkYarnExecutionStatus(ParamsInfo paramsInfo) {
        try {
            ClusterClient clusterClient = ClusterClient.INSTANCE;
            YarnTaskStatus taskStatus = clusterClient.getYarnJobStatus(paramsInfo);
            logger.info("track yarn job status: {}", taskStatus);
            switch (taskStatus) {
                case FAILED:
                case NOT_FOUND:
                    setExitStatusCode(EXIT_CODE_FAILURE);
                    break;
                case SUCCEEDED:
                    setExitStatusCode(EXIT_CODE_SUCCESS);
                    break;
                case KILLED:
                    setExitStatusCode(EXIT_CODE_KILL);
                    break;
                case RUNNING:
                case ACCEPTED:
                default:
                    ThreadUtils.sleep(CHECK_YARN_JOB_STATUS_EXECUTION_STATUS_INTERVAL);
                    checkYarnExecutionStatus(paramsInfo);
            }
        } catch (Exception e) {
            logger.error("track flink stream task status failure", e);
            setExitStatusCode(EXIT_CODE_FAILURE);
            throw new TaskException("track flink stream task status error", e);
        }
    }
}
