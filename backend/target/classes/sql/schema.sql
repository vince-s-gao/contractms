-- 合同管理系统数据库表结构
-- 版本: 1.0
-- 创建时间: 2026-03-04

-- 创建数据库
-- CREATE DATABASE IF NOT EXISTS contract_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE contract_management;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    email VARCHAR(100) UNIQUE COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    real_name VARCHAR(50) NOT NULL COMMENT '真实姓名',
    department VARCHAR(100) COMMENT '部门',
    position VARCHAR(50) COMMENT '职位',
    role_id BIGINT COMMENT '角色ID',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    last_login_time DATETIME COMMENT '最后登录时间',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT COMMENT '更新人',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除，1-已删除',
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_role_id (role_id),
    INDEX idx_status (status),
    INDEX idx_enabled (enabled),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 2. 角色表
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    description VARCHAR(200) COMMENT '角色描述',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT COMMENT '更新人',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除，1-已删除',
    INDEX idx_role_code (role_code),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 3. 用户角色关联表
CREATE TABLE IF NOT EXISTS user_roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 4. 权限表
CREATE TABLE IF NOT EXISTS permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '权限ID',
    permission_code VARCHAR(100) NOT NULL UNIQUE COMMENT '权限编码',
    permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
    resource_type VARCHAR(50) COMMENT '资源类型',
    resource_path VARCHAR(200) COMMENT '资源路径',
    description VARCHAR(200) COMMENT '权限描述',
    parent_id BIGINT DEFAULT 0 COMMENT '父权限ID',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT COMMENT '更新人',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除，1-已删除',
    INDEX idx_permission_code (permission_code),
    INDEX idx_parent_id (parent_id),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- 5. 角色权限关联表
CREATE TABLE IF NOT EXISTS role_permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_role_id (role_id),
    INDEX idx_permission_id (permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- 6. 合同类型表
CREATE TABLE IF NOT EXISTS contract_types (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '类型ID',
    type_code VARCHAR(50) NOT NULL UNIQUE COMMENT '类型编码',
    type_name VARCHAR(100) NOT NULL COMMENT '类型名称',
    description VARCHAR(200) COMMENT '类型描述',
    template_path VARCHAR(500) COMMENT '模板路径',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT COMMENT '更新人',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除，1-已删除',
    INDEX idx_type_code (type_code),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同类型表';

-- 7. 合同表
CREATE TABLE IF NOT EXISTS contracts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '合同ID',
    contract_number VARCHAR(100) NOT NULL UNIQUE COMMENT '合同编号',
    contract_name VARCHAR(200) NOT NULL COMMENT '合同名称',
    contract_type_id BIGINT NOT NULL COMMENT '合同类型ID',
    contract_content TEXT COMMENT '合同内容',
    contract_amount DECIMAL(15,2) COMMENT '合同金额',
    currency VARCHAR(10) DEFAULT 'CNY' COMMENT '币种',
    start_date DATE COMMENT '开始日期',
    end_date DATE COMMENT '结束日期',
    sign_date DATE COMMENT '签署日期',
    status TINYINT DEFAULT 0 COMMENT '合同状态：0-草稿，1-审批中，2-已生效，3-已终止，4-已过期',
    current_approver BIGINT COMMENT '当前审批人',
    approval_status TINYINT DEFAULT 0 COMMENT '审批状态：0-未提交，1-审批中，2-已通过，3-已拒绝',
    created_by BIGINT NOT NULL COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT COMMENT '更新人',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除，1-已删除',
    INDEX idx_contract_number (contract_number),
    INDEX idx_contract_type_id (contract_type_id),
    INDEX idx_status (status),
    INDEX idx_approval_status (approval_status),
    INDEX idx_start_date (start_date),
    INDEX idx_end_date (end_date),
    INDEX idx_created_by (created_by),
    INDEX idx_deleted (deleted),
    FOREIGN KEY (contract_type_id) REFERENCES contract_types(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    FOREIGN KEY (current_approver) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同表';

-- 8. 合同参与人员表
CREATE TABLE IF NOT EXISTS contract_participants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '参与人员ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    participant_type TINYINT NOT NULL COMMENT '参与类型：1-甲方，2-乙方，3-丙方，4-见证人，5-审批人',
    participant_role VARCHAR(50) COMMENT '参与角色',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_contract_id (contract_id),
    INDEX idx_user_id (user_id),
    INDEX idx_participant_type (participant_type),
    FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同参与人员表';

-- 9. 合同附件表
CREATE TABLE IF NOT EXISTS contract_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '附件ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_path VARCHAR(500) NOT NULL COMMENT '文件路径',
    file_size BIGINT COMMENT '文件大小',
    file_type VARCHAR(50) COMMENT '文件类型',
    upload_user_id BIGINT NOT NULL COMMENT '上传用户ID',
    description VARCHAR(200) COMMENT '附件描述',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_contract_id (contract_id),
    INDEX idx_upload_user_id (upload_user_id),
    FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE,
    FOREIGN KEY (upload_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同附件表';

-- 10. 付款计划表
CREATE TABLE IF NOT EXISTS payment_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '付款计划ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    plan_name VARCHAR(100) NOT NULL COMMENT '计划名称',
    plan_amount DECIMAL(15,2) NOT NULL COMMENT '计划金额',
    plan_date DATE NOT NULL COMMENT '计划付款日期',
    actual_amount DECIMAL(15,2) COMMENT '实际付款金额',
    actual_date DATE COMMENT '实际付款日期',
    status TINYINT DEFAULT 0 COMMENT '状态：0-未付款，1-已付款，2-已逾期',
    description VARCHAR(200) COMMENT '付款说明',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT COMMENT '更新人',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_contract_id (contract_id),
    INDEX idx_plan_date (plan_date),
    INDEX idx_status (status),
    FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='付款计划表';

-- 11. 付款凭证表
CREATE TABLE IF NOT EXISTS payment_vouchers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '付款凭证ID',
    payment_plan_id BIGINT NOT NULL COMMENT '付款计划ID',
    voucher_number VARCHAR(100) NOT NULL COMMENT '凭证编号',
    voucher_amount DECIMAL(15,2) NOT NULL COMMENT '凭证金额',
    voucher_date DATE NOT NULL COMMENT '凭证日期',
    voucher_file_path VARCHAR(500) COMMENT '凭证文件路径',
    created_by BIGINT NOT NULL COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_payment_plan_id (payment_plan_id),
    INDEX idx_voucher_number (voucher_number),
    FOREIGN KEY (payment_plan_id) REFERENCES payment_plans(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='付款凭证表';

-- 12. 自定义字段定义表
CREATE TABLE IF NOT EXISTS custom_field_definitions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '字段定义ID',
    entity_type VARCHAR(50) NOT NULL COMMENT '实体类型：contract, user等',
    field_code VARCHAR(50) NOT NULL COMMENT '字段编码',
    field_name VARCHAR(100) NOT NULL COMMENT '字段名称',
    field_type VARCHAR(20) NOT NULL COMMENT '字段类型：text, number, date, select等',
    field_options TEXT COMMENT '字段选项（JSON格式）',
    is_required TINYINT DEFAULT 0 COMMENT '是否必填：0-否，1-是',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT COMMENT '更新人',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_entity_field (entity_type, field_code),
    INDEX idx_entity_type (entity_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自定义字段定义表';

-- 13. 自定义字段值表
CREATE TABLE IF NOT EXISTS custom_field_values (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '字段值ID',
    field_definition_id BIGINT NOT NULL COMMENT '字段定义ID',
    entity_id BIGINT NOT NULL COMMENT '实体ID',
    field_value TEXT COMMENT '字段值',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_field_entity (field_definition_id, entity_id),
    INDEX idx_field_definition_id (field_definition_id),
    INDEX idx_entity_id (entity_id),
    FOREIGN KEY (field_definition_id) REFERENCES custom_field_definitions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自定义字段值表';

-- 14. 审批记录表
CREATE TABLE IF NOT EXISTS approval_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '审批记录ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    approver_id BIGINT NOT NULL COMMENT '审批人ID',
    approval_result TINYINT NOT NULL COMMENT '审批结果：1-通过，2-拒绝',
    approval_opinion TEXT COMMENT '审批意见',
    approval_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '审批时间',
    next_approver_id BIGINT COMMENT '下一审批人ID',
    INDEX idx_contract_id (contract_id),
    INDEX idx_approver_id (approver_id),
    INDEX idx_approval_time (approval_time),
    FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE,
    FOREIGN KEY (approver_id) REFERENCES users(id),
    FOREIGN KEY (next_approver_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批记录表';

-- 15. 提醒规则表
CREATE TABLE IF NOT EXISTS reminder_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '规则ID',
    rule_code VARCHAR(50) NOT NULL UNIQUE COMMENT '规则编码',
    rule_name VARCHAR(100) NOT NULL COMMENT '规则名称',
    rule_type TINYINT NOT NULL COMMENT '规则类型：1-合同到期提醒，2-付款提醒，3-审批提醒',
    trigger_condition TEXT NOT NULL COMMENT '触发条件（JSON格式）',
    reminder_template TEXT COMMENT '提醒模板',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT COMMENT '更新人',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_rule_code (rule_code),
    INDEX idx_rule_type (rule_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒规则表';

-- 16. 提醒记录表
CREATE TABLE IF NOT EXISTS reminder_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '提醒记录ID',
    rule_id BIGINT NOT NULL COMMENT '规则ID',
    contract_id BIGINT COMMENT '合同ID',
    reminder_title VARCHAR(200) NOT NULL COMMENT '提醒标题',
    reminder_content TEXT NOT NULL COMMENT '提醒内容',
    reminder_target BIGINT NOT NULL COMMENT '提醒目标用户ID',
    reminder_time DATETIME NOT NULL COMMENT '提醒时间',
    is_read TINYINT DEFAULT 0 COMMENT '是否已读：0-未读，1-已读',
    read_time DATETIME COMMENT '阅读时间',
    status TINYINT DEFAULT 0 COMMENT '状态：0-待发送，1-已发送，2-已处理',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_rule_id (rule_id),
    INDEX idx_contract_id (contract_id),
    INDEX idx_reminder_target (reminder_target),
    INDEX idx_reminder_time (reminder_time),
    INDEX idx_status (status),
    FOREIGN KEY (rule_id) REFERENCES reminder_rules(id),
    FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒记录表';

-- 17. 操作日志表
CREATE TABLE IF NOT EXISTS operation_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    user_id BIGINT NOT NULL COMMENT '操作用户ID',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型',
    operation_target VARCHAR(100) NOT NULL COMMENT '操作目标',
    target_id BIGINT COMMENT '目标ID',
    operation_content TEXT COMMENT '操作内容',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    user_agent TEXT COMMENT '用户代理',
    operation_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_user_id (user_id),
    INDEX idx_operation_type (operation_type),
    INDEX idx_operation_target (operation_target),
    INDEX idx_operation_time (operation_time),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 18. 系统配置表
CREATE TABLE IF NOT EXISTS system_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    config_description VARCHAR(200) COMMENT '配置描述',
    config_type VARCHAR(20) DEFAULT 'system' COMMENT '配置类型：system-系统，business-业务',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT COMMENT '更新人',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_config_key (config_key),
    INDEX idx_config_type (config_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 19. 租户表（多租户支持）
CREATE TABLE IF NOT EXISTS tenants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '租户ID',
    tenant_code VARCHAR(50) NOT NULL UNIQUE COMMENT '租户编码',
    tenant_name VARCHAR(100) NOT NULL COMMENT '租户名称',
    contact_person VARCHAR(50) COMMENT '联系人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    contact_email VARCHAR(100) COMMENT '联系邮箱',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    expire_time DATETIME COMMENT '过期时间',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_tenant_code (tenant_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';

-- 20. 租户配置表
CREATE TABLE IF NOT EXISTS tenant_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    config_key VARCHAR(100) NOT NULL COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    config_description VARCHAR(200) COMMENT '配置描述',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_tenant_config (tenant_id, config_key),
    INDEX idx_tenant_id (tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户配置表';
