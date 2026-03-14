package com.contract.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库初始化器
 * 在应用启动时执行数据库初始化脚本
 */
@Component
public class DatabaseInitializer implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private DatabaseConnectionManager connectionManager;
    
    @Override
    public void run(String... args) throws Exception {
        logger.info("开始数据库初始化检查...");
        
        // 检查数据库连接
        if (!connectionManager.testConnection()) {
            logger.error("数据库连接测试失败，无法进行初始化");
            return;
        }
        
        // 检查数据库是否存在
        if (!isDatabaseExists()) {
            logger.info("数据库不存在，开始创建数据库...");
            createDatabase();
        }
        
        // 检查表结构是否存在
        if (!isTableStructureExists()) {
            logger.info("表结构不存在，开始执行初始化脚本...");
            executeInitializationScript();
        } else {
            logger.info("数据库表结构已存在，跳过初始化");
        }

        ensureApprovalRecordsCompatibility();
        
        logger.info("数据库初始化检查完成");
    }
    
    /**
     * 检查数据库是否存在
     */
    private boolean isDatabaseExists() {
        try (Connection connection = connectionManager.getConnection()) {
            // 尝试查询系统表来验证数据库存在
            connection.createStatement().executeQuery("SELECT 1 FROM DUAL");
            return true;
        } catch (SQLException e) {
            logger.warn("数据库不存在或连接异常: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 创建数据库
     */
    private void createDatabase() {
        // 这里可以执行创建数据库的SQL
        // 由于权限问题，通常由DBA手动创建数据库
        logger.info("请手动创建数据库: contract_management");
    }
    
    /**
     * 检查表结构是否存在
     */
    private boolean isTableStructureExists() {
        try (Connection connection = connectionManager.getConnection()) {
            // 检查是否存在核心表
            String checkTableSql = "SELECT COUNT(*) FROM information_schema.tables " +
                                 "WHERE table_schema = DATABASE() AND table_name = 'users'";

            try (var statement = connection.createStatement();
                 var resultSet = statement.executeQuery(checkTableSql)) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }
            return false;
        } catch (SQLException e) {
            logger.warn("检查表结构失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 执行初始化脚本
     */
    private void executeInitializationScript() {
        try (Connection connection = connectionManager.getConnection()) {
            logger.info("开始执行数据库初始化脚本...");
            
            // 执行表结构创建脚本
            executeTableCreationScript(connection);
            
            // 执行基础数据插入脚本
            executeDataInsertionScript(connection);
            
            logger.info("数据库初始化脚本执行完成");
        } catch (Exception e) {
            logger.error("执行初始化脚本失败", e);
        }
    }
    
    /**
     * 执行表结构创建脚本
     */
    private void executeTableCreationScript(Connection connection) {
        try {
            ClassPathResource resource = new ClassPathResource("sql/schema.sql");
            if (resource.exists()) {
                ScriptUtils.executeSqlScript(connection, resource);
                logger.info("表结构创建脚本执行成功");
            } else {
                logger.warn("表结构创建脚本不存在: sql/schema.sql");
            }
        } catch (Exception e) {
            logger.error("执行表结构创建脚本失败", e);
        }
    }
    
    /**
     * 执行基础数据插入脚本
     */
    private void executeDataInsertionScript(Connection connection) {
        try {
            ClassPathResource resource = new ClassPathResource("sql/data.sql");
            if (resource.exists()) {
                ScriptUtils.executeSqlScript(connection, resource);
                logger.info("基础数据插入脚本执行成功");
            } else {
                logger.warn("基础数据插入脚本不存在: sql/data.sql");
            }
        } catch (Exception e) {
            logger.error("执行基础数据插入脚本失败", e);
        }
    }

    private void ensureApprovalRecordsCompatibility() {
        try (Connection connection = connectionManager.getConnection()) {
            if (tableExists(connection, "approval_records")) {
                return;
            }

            if (tableExists(connection, "contract_approvals")) {
                logger.info("检测到旧表 contract_approvals，开始迁移到 approval_records");
                createApprovalRecordsTable(connection);
                try (var statement = connection.createStatement()) {
                    statement.execute("""
                            INSERT INTO approval_records (
                                id, contract_id, approver_id, approval_result, approval_opinion, approval_time, next_approver_id
                            )
                            SELECT ca.id,
                                   ca.contract_id,
                                   ca.approver_id,
                                   CASE UPPER(COALESCE(ca.approval_status, 'PENDING'))
                                       WHEN 'APPROVED' THEN 1
                                       WHEN 'REJECTED' THEN 2
                                       ELSE 0
                                   END,
                                   ca.approval_comment,
                                   COALESCE(ca.approval_time, ca.created_at, ca.created_time, CURRENT_TIMESTAMP),
                                   NULL
                            FROM contract_approvals ca
                            WHERE NOT EXISTS (
                                SELECT 1 FROM approval_records ar WHERE ar.id = ca.id
                            )
                            """);
                }
                logger.info("approval_records 兼容迁移完成");
                return;
            }

            logger.info("approval_records 不存在且未检测到旧表，开始补建审批记录表");
            createApprovalRecordsTable(connection);
        } catch (Exception e) {
            logger.error("修复 approval_records 兼容性失败", e);
        }
    }

    private void createApprovalRecordsTable(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS approval_records (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '审批记录ID',
                        contract_id BIGINT NOT NULL COMMENT '合同ID',
                        approver_id BIGINT NOT NULL COMMENT '审批人ID',
                        approval_result TINYINT NOT NULL DEFAULT 0 COMMENT '审批结果：0-待审批，1-通过，2-拒绝',
                        approval_opinion TEXT COMMENT '审批意见',
                        approval_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '审批时间',
                        next_approver_id BIGINT COMMENT '下一审批人ID',
                        INDEX idx_contract_id (contract_id),
                        INDEX idx_approver_id (approver_id),
                        INDEX idx_approval_time (approval_time),
                        FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE,
                        FOREIGN KEY (approver_id) REFERENCES users(id),
                        FOREIGN KEY (next_approver_id) REFERENCES users(id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批记录表'
                    """);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }
    
    /**
     * 获取数据库初始化状态
     */
    public String getInitializationStatus() {
        StringBuilder status = new StringBuilder();
        status.append("数据库初始化状态:\n");
        status.append("数据库连接: ").append(connectionManager.testConnection() ? "正常" : "异常").append("\n");
        status.append("数据库存在: ").append(isDatabaseExists() ? "是" : "否").append("\n");
        status.append("表结构存在: ").append(isTableStructureExists() ? "是" : "否").append("\n");
        status.append(connectionManager.getConnectionPoolStatus());
        
        return status.toString();
    }
}
