package com.contract.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * 基础数据访问对象类
 * 提供通用的数据库操作方法
 */
@Repository
public class BaseDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(BaseDAO.class);
    
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    
    /**
     * 执行插入操作并返回自增主键
     * @param sql SQL语句
     * @param params 参数数组
     * @return 自增主键
     */
    protected Long insertAndReturnKey(String sql, Object... params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                return ps;
            }
        }, keyHolder);
        
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }
    
    /**
     * 执行更新操作
     * @param sql SQL语句
     * @param params 参数数组
     * @return 影响的行数
     */
    protected int update(String sql, Object... params) {
        try {
            return jdbcTemplate.update(sql, params);
        } catch (Exception e) {
            logger.error("执行更新操作失败: {}", sql, e);
            throw e;
        }
    }
    
    /**
     * 执行插入操作
     * @param sql SQL语句
     * @param params 参数数组
     * @return 影响的行数
     */
    protected int insert(String sql, Object... params) {
        return update(sql, params);
    }
    
    /**
     * 执行删除操作
     * @param sql SQL语句
     * @param params 参数数组
     * @return 影响的行数
     */
    protected int delete(String sql, Object... params) {
        return update(sql, params);
    }
    
    /**
     * 查询单条记录
     * @param sql SQL语句
     * @param rowMapper 行映射器
     * @param params 参数数组
     * @param <T> 返回类型
     * @return 查询结果
     */
    protected <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... params) {
        try {
            List<T> results = jdbcTemplate.query(sql, rowMapper, params);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            logger.error("查询单条记录失败: {}", sql, e);
            throw e;
        }
    }
    
    /**
     * 查询多条记录
     * @param sql SQL语句
     * @param rowMapper 行映射器
     * @param params 参数数组
     * @param <T> 返回类型
     * @return 查询结果列表
     */
    protected <T> List<T> queryForList(String sql, RowMapper<T> rowMapper, Object... params) {
        try {
            return jdbcTemplate.query(sql, rowMapper, params);
        } catch (Exception e) {
            logger.error("查询多条记录失败: {}", sql, e);
            throw e;
        }
    }
    
    /**
     * 查询Map结果
     * @param sql SQL语句
     * @param params 参数数组
     * @return Map结果
     */
    protected Map<String, Object> queryForMap(String sql, Object... params) {
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, params);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            logger.error("查询Map结果失败: {}", sql, e);
            throw e;
        }
    }
    
    /**
     * 查询列表Map结果
     * @param sql SQL语句
     * @param params 参数数组
     * @return Map结果列表
     */
    protected List<Map<String, Object>> queryForListMap(String sql, Object... params) {
        try {
            return jdbcTemplate.queryForList(sql, params);
        } catch (Exception e) {
            logger.error("查询列表Map结果失败: {}", sql, e);
            throw e;
        }
    }
    
    /**
     * 查询数量
     * @param sql SQL语句
     * @param params 参数数组
     * @return 数量
     */
    protected Integer queryForCount(String sql, Object... params) {
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class, params);
        } catch (Exception e) {
            logger.error("查询数量失败: {}", sql, e);
            throw e;
        }
    }
    
    /**
     * 批量插入操作
     * @param sql SQL语句
     * @param batchArgs 批量参数
     * @return 影响的行数数组
     */
    protected int[] batchInsert(String sql, List<Object[]> batchArgs) {
        try {
            return jdbcTemplate.batchUpdate(sql, batchArgs);
        } catch (Exception e) {
            logger.error("批量插入操作失败: {}", sql, e);
            throw e;
        }
    }
    
    /**
     * 执行存储过程
     * @param procedureName 存储过程名称
     * @param params 参数数组
     * @return 执行结果
     */
    protected Map<String, Object> callProcedure(String procedureName, Object... params) {
        try {
            // 构建存储过程调用SQL
            StringBuilder sql = new StringBuilder("CALL ");
            sql.append(procedureName).append("(");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sql.append(",");
                sql.append("?");
            }
            sql.append(")");
            
            return queryForMap(sql.toString(), params);
        } catch (Exception e) {
            logger.error("执行存储过程失败: {}", procedureName, e);
            throw e;
        }
    }
}