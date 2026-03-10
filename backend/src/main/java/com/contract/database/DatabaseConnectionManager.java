package com.contract.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库连接管理器
 * 提供数据库连接获取、释放和状态监控功能
 */
@Component
public class DatabaseConnectionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnectionManager.class);
    
    @Autowired
    private DataSource dataSource;
    
    /**
     * 获取数据库连接
     * @return 数据库连接
     * @throws SQLException 连接异常
     */
    public Connection getConnection() throws SQLException {
        try {
            Connection connection = dataSource.getConnection();
            logger.debug("成功获取数据库连接");
            return connection;
        } catch (SQLException e) {
            logger.error("获取数据库连接失败", e);
            throw e;
        }
    }
    
    /**
     * 安全关闭连接
     * @param connection 数据库连接
     */
    public void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    logger.debug("数据库连接已关闭");
                }
            } catch (SQLException e) {
                logger.warn("关闭数据库连接时发生异常", e);
            }
        }
    }
    
    /**
     * 检查数据库连接状态
     * @return 连接是否正常
     */
    public boolean isConnectionValid() {
        try (Connection connection = getConnection()) {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            logger.error("检查数据库连接状态失败", e);
            return false;
        }
    }
    
    /**
     * 测试数据库连接
     * @return 连接测试结果
     */
    public boolean testConnection() {
        try (Connection connection = getConnection()) {
            return connection.isValid(5); // 5秒超时
        } catch (SQLException e) {
            logger.error("数据库连接测试失败", e);
            return false;
        }
    }
    
    /**
     * 获取连接池状态信息
     * @return 连接池状态信息
     */
    public String getConnectionPoolStatus() {
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource) {
            com.zaxxer.hikari.HikariDataSource hikariDataSource = (com.zaxxer.hikari.HikariDataSource) dataSource;
            return String.format(
                "连接池状态: 活跃连接=%d, 空闲连接=%d, 等待线程=%d, 总连接=%d",
                hikariDataSource.getHikariPoolMXBean().getActiveConnections(),
                hikariDataSource.getHikariPoolMXBean().getIdleConnections(),
                hikariDataSource.getHikariPoolMXBean().getThreadsAwaitingConnection(),
                hikariDataSource.getHikariPoolMXBean().getTotalConnections()
            );
        }
        return "连接池状态: 未知数据源类型";
    }
}