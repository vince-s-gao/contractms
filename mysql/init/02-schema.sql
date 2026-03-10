-- 合同管理系统数据库表结构创建脚本
-- 创建完整的数据库表结构

USE contract_management;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    real_name VARCHAR(50) NOT NULL COMMENT '真实姓名',
    department VARCHAR(100) COMMENT '部门',
    position VARCHAR(100) COMMENT '职位',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-激活,INACTIVE-禁用,LOCKED-锁定',
    last_login_time DATETIME COMMENT '最后登录时间',
    deleted_flag TINYINT DEFAULT 0 COMMENT '逻辑删除标记：0-正常,1-删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT COMMENT '创建人ID',
    updated_by BIGINT COMMENT '更新人ID',
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_department (department),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    role_name VARCHAR(100) NOT NULL COMMENT '角色名称',
    description VARCHAR(500) COMMENT '角色描述',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-激活,INACTIVE-禁用',
    deleted_flag TINYINT DEFAULT 0 COMMENT '逻辑删除标记：0-正常,1-删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT COMMENT '创建人ID',
    updated_by BIGINT COMMENT '更新人ID',
    INDEX idx_role_code (role_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 权限表
CREATE TABLE IF NOT EXISTS permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '权限ID',
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    permission_code VARCHAR(50) NOT NULL UNIQUE COMMENT '权限编码',
    permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
    resource_type VARCHAR(50) COMMENT '资源类型',
    resource_path VARCHAR(500) COMMENT '资源路径',
    description VARCHAR(500) COMMENT '权限描述',
    parent_id BIGINT DEFAULT 0 COMMENT '父权限ID',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-激活,INACTIVE-禁用',
    deleted_flag TINYINT DEFAULT 0 COMMENT '逻辑删除标记：0-正常,1-删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT COMMENT '创建人ID',
    updated_by BIGINT COMMENT '更新人ID',
    INDEX idx_permission_code (permission_code),
    INDEX idx_parent_id (parent_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS user_roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT COMMENT '创建人ID',
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 角色权限关联表
CREATE TABLE IF NOT EXISTS role_permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT COMMENT '创建人ID',
    INDEX idx_role_id (role_id),
    INDEX idx_permission_id (permission_id),
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- 合同表
CREATE TABLE IF NOT EXISTS contracts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '合同ID',
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    contract_no VARCHAR(50) NOT NULL UNIQUE COMMENT '合同编号',
    contract_name VARCHAR(200) NOT NULL COMMENT '合同名称',
    contract_type VARCHAR(50) NOT NULL COMMENT '合同类型：SALES-销售,PURCHASE-采购,SERVICE-服务,OTHER-其他',
    signing_year INT COMMENT '签约年份（从合同编号提取）',
    party_a VARCHAR(200) NOT NULL COMMENT '甲方单位',
    party_b VARCHAR(200) NOT NULL COMMENT '乙方单位',
    amount DECIMAL(15,2) NOT NULL COMMENT '合同金额',
    currency VARCHAR(10) DEFAULT 'CNY' COMMENT '币种',
    start_date DATE NOT NULL COMMENT '开始日期',
    end_date DATE NOT NULL COMMENT '结束日期',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT-草稿,PENDING-待审批,APPROVED-已审批,EXECUTING-执行中,COMPLETED-已完成,TERMINATED-已终止',
    description TEXT COMMENT '合同描述',
    attachment_path VARCHAR(500) COMMENT '附件路径',
    deleted_flag TINYINT DEFAULT 0 COMMENT '逻辑删除标记：0-正常,1-删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL COMMENT '创建人ID',
    updated_by BIGINT COMMENT '更新人ID',
    INDEX idx_contract_no (contract_no),
    INDEX idx_status (status),
    INDEX idx_contract_type (contract_type),
    INDEX idx_dates (start_date, end_date),
    INDEX idx_created_by (created_by),
    FOREIGN KEY (created_by) REFERENCES users(id),
    FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同表';

-- 合同附件表
CREATE TABLE IF NOT EXISTS contract_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '附件ID',
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    attachment_name VARCHAR(200) NOT NULL COMMENT '附件名称',
    file_path VARCHAR(500) NOT NULL COMMENT '文件路径',
    file_size BIGINT NOT NULL COMMENT '文件大小',
    file_type VARCHAR(100) COMMENT '文件类型',
    attachment_type VARCHAR(50) COMMENT '附件类型：CONTRACT-合同,APPROVAL-审批,OTHER-其他',
    description VARCHAR(500) COMMENT '附件描述',
    uploader_id BIGINT NOT NULL COMMENT '上传人ID',
    deleted_flag TINYINT DEFAULT 0 COMMENT '逻辑删除标记：0-正常,1-删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_contract_id (contract_id),
    INDEX idx_uploader_id (uploader_id),
    FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE,
    FOREIGN KEY (uploader_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同附件表';

-- 合同审批记录表
CREATE TABLE IF NOT EXISTS contract_approvals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '审批记录ID',
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    approver_id BIGINT NOT NULL COMMENT '审批人ID',
    approval_step INT NOT NULL COMMENT '审批步骤',
    approval_status VARCHAR(20) DEFAULT 'PENDING' COMMENT '审批状态：PENDING-待审批,APPROVED-通过,REJECTED-拒绝',
    approval_comment TEXT COMMENT '审批意见',
    approval_time DATETIME COMMENT '审批时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_contract_id (contract_id),
    INDEX idx_approver_id (approver_id),
    INDEX idx_approval_status (approval_status),
    UNIQUE KEY uk_contract_approver (contract_id, approver_id),
    FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE,
    FOREIGN KEY (approver_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同审批记录表';

-- 付款计划表
CREATE TABLE IF NOT EXISTS payment_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '付款计划ID',
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    plan_name VARCHAR(200) NOT NULL COMMENT '计划名称',
    plan_amount DECIMAL(15,2) NOT NULL COMMENT '计划金额',
    plan_date DATE NOT NULL COMMENT '计划付款日期',
    actual_amount DECIMAL(15,2) COMMENT '实际付款金额',
    actual_date DATE COMMENT '实际付款日期',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态：PENDING-待付款,PAID-已付款,OVERDUE-已逾期,CANCELLED-已取消',
    description TEXT COMMENT '付款说明',
    deleted_flag TINYINT DEFAULT 0 COMMENT '逻辑删除标记：0-正常,1-删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL COMMENT '创建人ID',
    updated_by BIGINT COMMENT '更新人ID',
    INDEX idx_contract_id (contract_id),
    INDEX idx_plan_date (plan_date),
    INDEX idx_status (status),
    FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id),
    FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='付款计划表';

-- 操作日志表
CREATE TABLE IF NOT EXISTS operation_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    user_id BIGINT COMMENT '用户ID',
    username VARCHAR(50) COMMENT '用户名',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型',
    module VARCHAR(50) NOT NULL COMMENT '操作模块',
    description VARCHAR(500) COMMENT '操作描述',
    request_method VARCHAR(10) COMMENT '请求方法',
    request_url VARCHAR(500) COMMENT '请求URL',
    request_params TEXT COMMENT '请求参数',
    response_result TEXT COMMENT '响应结果',
    ip_address VARCHAR(45) COMMENT '操作IP',
    user_agent TEXT COMMENT '用户代理',
    status VARCHAR(20) NOT NULL COMMENT '操作状态：SUCCESS-成功,FAILED-失败',
    error_message TEXT COMMENT '错误信息',
    execution_time BIGINT COMMENT '执行时间（毫秒）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_operation_type (operation_type),
    INDEX idx_module (module),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 自定义字段表
CREATE TABLE IF NOT EXISTS custom_fields (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '字段ID',
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    field_code VARCHAR(50) NOT NULL UNIQUE COMMENT '字段编码',
    field_name VARCHAR(100) NOT NULL COMMENT '字段名称',
    field_type VARCHAR(50) NOT NULL COMMENT '字段类型：TEXT-文本,NUMBER-数字,DATE-日期,DATETIME-日期时间,SELECT-下拉选择,MULTI_SELECT-多选,RADIO-单选,CHECKBOX-复选框,TEXTAREA-多行文本,RICH_TEXT-富文本,FILE-文件上传,IMAGE-图片上传,BOOLEAN-布尔值',
    description VARCHAR(500) COMMENT '字段描述',
    required TINYINT DEFAULT 0 COMMENT '是否必填：0-否,1-是',
    default_value VARCHAR(500) COMMENT '默认值',
    validation_rule VARCHAR(500) COMMENT '验证规则',
    options TEXT COMMENT '选项值（用于下拉选择、多选等类型）',
    sort_order INT DEFAULT 0 COMMENT '排序序号',
    field_group VARCHAR(100) COMMENT '字段分组',
    display_condition VARCHAR(500) COMMENT '显示条件',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-激活,INACTIVE-禁用',
    deleted_flag TINYINT DEFAULT 0 COMMENT '逻辑删除标记：0-正常,1-删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT COMMENT '创建人ID',
    updated_by BIGINT COMMENT '更新人ID',
    INDEX idx_field_code (field_code),
    INDEX idx_field_group (field_group),
    INDEX idx_field_type (field_type),
    INDEX idx_status (status),
    INDEX idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自定义字段表';

-- 合同版本表
CREATE TABLE IF NOT EXISTS contract_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '版本ID',
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    version_number INT NOT NULL COMMENT '版本号',
    version_name VARCHAR(100) COMMENT '版本名称',
    version_description VARCHAR(500) COMMENT '版本描述',
    contract_snapshot JSON COMMENT '合同数据快照（JSON格式）',
    change_summary TEXT COMMENT '变更摘要',
    change_type VARCHAR(50) NOT NULL COMMENT '变更类型：CREATE-创建,UPDATE-修改,APPROVE-审批,SIGN-签署,TERMINATE-终止,RENEW-续签,CORRECT-更正,ROLLBACK-回滚',
    change_reason VARCHAR(500) COMMENT '变更原因',
    created_by BIGINT NOT NULL COMMENT '创建人ID',
    created_by_name VARCHAR(50) COMMENT '创建人姓名',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    is_current TINYINT DEFAULT 0 COMMENT '是否当前版本：0-否,1-是',
    version_tag VARCHAR(50) COMMENT '版本标签：DRAFT-草稿,APPROVED-已审批,SIGNED-已签署,EFFECTIVE-已生效,TERMINATED-已终止,ARCHIVED-已归档',
    deleted_flag TINYINT DEFAULT 0 COMMENT '逻辑删除标记：0-正常,1-删除',
    INDEX idx_contract_id (contract_id),
    INDEX idx_version_number (version_number),
    INDEX idx_change_type (change_type),
    INDEX idx_created_at (created_at),
    INDEX idx_is_current (is_current),
    UNIQUE KEY uk_contract_version (contract_id, version_number),
    FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同版本表';

-- 系统配置表
CREATE TABLE IF NOT EXISTS system_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID',
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value TEXT NOT NULL COMMENT '配置值',
    config_type VARCHAR(50) DEFAULT 'STRING' COMMENT '配置类型：STRING-字符串,INTEGER-整数,BOOLEAN-布尔,JSON-JSON',
    description VARCHAR(500) COMMENT '配置描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT COMMENT '创建人ID',
    updated_by BIGINT COMMENT '更新人ID',
    INDEX idx_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 创建视图：合同统计视图
CREATE OR REPLACE VIEW contract_stats AS
SELECT 
    COUNT(*) as total_contracts,
    COUNT(CASE WHEN status = 'APPROVED' THEN 1 END) as approved_contracts,
    COUNT(CASE WHEN status = 'EXECUTING' THEN 1 END) as executing_contracts,
    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as completed_contracts,
    SUM(amount) as total_amount,
    AVG(amount) as avg_amount
FROM contracts;

-- 输出表结构创建完成信息
SELECT '数据库表结构创建完成' as message;
