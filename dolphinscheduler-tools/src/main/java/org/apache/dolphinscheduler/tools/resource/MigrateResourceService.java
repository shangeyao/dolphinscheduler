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

package org.apache.dolphinscheduler.tools.resource;

import static org.apache.dolphinscheduler.common.constants.Constants.FORMAT_S_S;

import org.apache.dolphinscheduler.dao.mapper.TenantMapper;
import org.apache.dolphinscheduler.plugin.storage.api.StorageOperator;
import org.apache.dolphinscheduler.spi.enums.ResourceType;

import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MigrateResourceService {

    private static final Logger logger = LoggerFactory.getLogger(MigrateResourceService.class);

    @Autowired
    private StorageOperator storageOperator;

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private DataSource dataSource;

    private static final String MIGRATE_BASE_DIR = ".migrate";

    public void migrateResourceOnce(String targetTenantCode) throws SQLException {
        if (true != tenantMapper.existTenant(targetTenantCode)) {
            logger.error("Tenant not exists!");
            return;
        }

        String resMigrateBasePath = createMigrateDirByType(targetTenantCode, ResourceType.FILE);
        if (StringUtils.isEmpty(resMigrateBasePath)) {
            return;
        }
        // migrate all unmanaged resources once
        List<Map<String, Object>> resources = getAllResources();
        for (Map<String, Object> item : resources) {
            String oriFullName = (String) item.get("full_name");
            int type = (int) item.get("type");
            int id = (int) item.get("id");
            try {
                oriFullName = oriFullName.startsWith("/") ? oriFullName.substring(1) : oriFullName;
                if (ResourceType.FILE.getCode() == type) {
                    storageOperator.copy(oriFullName,
                            String.format(FORMAT_S_S, resMigrateBasePath, oriFullName), true, true);
                }
            } catch (Exception e) {
                logger.error("Migrate resource: {} failed: {}", item, e);
            }
        }
    }

    private List<Map<String, Object>> getAllResources() throws SQLException {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement =
                        connection.prepareStatement("select * from t_ds_resources where user_id != -1");
                ResultSet resultSet = preparedStatement.executeQuery()) {
            List<Map<String, Object>> result = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", resultSet.getInt("id"));
                item.put("full_name", resultSet.getString("full_name"));
                item.put("type", resultSet.getInt("type"));
                result.add(item);
            }
            return result;
        }
    }

    public String createMigrateDirByType(String targetTenantCode, ResourceType type) {
        String migrateBasePath = storageOperator.getStorageBaseDirectory(targetTenantCode, type);
        migrateBasePath += MIGRATE_BASE_DIR;
        try {
            storageOperator.createStorageDir(migrateBasePath);
        } catch (Exception e) {
            logger.error("create migrate base directory {} failed", migrateBasePath);
            return "";
        }
        return migrateBasePath;
    }

}
