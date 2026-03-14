-- 合同管理系统数据库初始化脚本
-- 创建数据库和用户
CREATE DATABASE IF NOT EXISTS contract_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建应用用户（如果不存在）
CREATE USER IF NOT EXISTS 'contract_user'@'%' IDENTIFIED BY '${MYSQL_PASSWORD}';
GRANT ALL PRIVILEGES ON contract_management.* TO 'contract_user'@'%';
FLUSH PRIVILEGES;

-- 使用数据库
USE contract_management;

-- 创建基础表结构
-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    real_name VARCHAR(50) NOT NULL,
    phone VARCHAR(20),
    department VARCHAR(100),
    role ENUM('ADMIN', 'MANAGER', 'USER') DEFAULT 'USER',
    status ENUM('ACTIVE', 'INACTIVE', 'LOCKED') DEFAULT 'ACTIVE',
    last_login_time DATETIME,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_department (department)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 合同表
CREATE TABLE IF NOT EXISTS contracts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_no VARCHAR(50) NOT NULL UNIQUE,
    contract_name VARCHAR(200) NOT NULL,
    contract_type ENUM('SALES', 'PURCHASE', 'SERVICE', 'OTHER') NOT NULL,
    signing_year INT NULL,
    tax_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    party_a VARCHAR(200) NOT NULL,
    party_b VARCHAR(200) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'CNY',
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status ENUM('DRAFT', 'PENDING', 'APPROVED', 'EXECUTING', 'COMPLETED', 'TERMINATED') DEFAULT 'DRAFT',
    description TEXT,
    attachment_path VARCHAR(500),
    created_by BIGINT NOT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id),
    FOREIGN KEY (updated_by) REFERENCES users(id),
    INDEX idx_contract_no (contract_no),
    INDEX idx_status (status),
    INDEX idx_type (contract_type),
    INDEX idx_dates (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 审批记录表
CREATE TABLE IF NOT EXISTS approval_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    approver_id BIGINT NOT NULL,
    approval_result TINYINT NOT NULL DEFAULT 0,
    approval_opinion TEXT,
    approval_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    next_approver_id BIGINT,
    FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE,
    FOREIGN KEY (approver_id) REFERENCES users(id),
    FOREIGN KEY (next_approver_id) REFERENCES users(id),
    INDEX idx_contract_id (contract_id),
    INDEX idx_approver_id (approver_id),
    INDEX idx_approval_time (approval_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 合同付款记录表
CREATE TABLE IF NOT EXISTS contract_payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    payment_no VARCHAR(50) NOT NULL UNIQUE,
    payment_amount DECIMAL(15,2) NOT NULL,
    payment_date DATE NOT NULL,
    payment_method ENUM('BANK_TRANSFER', 'CASH', 'CHECK', 'OTHER') NOT NULL,
    status ENUM('PENDING', 'PAID', 'CANCELLED') DEFAULT 'PENDING',
    description TEXT,
    created_by BIGINT NOT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id),
    FOREIGN KEY (updated_by) REFERENCES users(id),
    INDEX idx_contract_id (contract_id),
    INDEX idx_payment_date (payment_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 系统日志表
CREATE TABLE IF NOT EXISTS system_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    action VARCHAR(100) NOT NULL,
    module VARCHAR(50) NOT NULL,
    description TEXT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_action (action),
    INDEX idx_module (module),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入初始管理员用户
INSERT IGNORE INTO users (username, password, email, real_name, phone, department, role, status) VALUES
('admin', '$2a$10$rOzJqKqY7yY7yY7yY7yY7e', 'admin@contract-system.com', '系统管理员', '13800138000', 'IT部门', 'ADMIN', 'ACTIVE');

-- 创建视图：合同统计视图
CREATE OR REPLACE VIEW contract_stats AS
SELECT 
    COUNT(*) as total_contracts,
    COUNT(CASE WHEN status = 'APPROVED' THEN 1 END) as approved_contracts,
    COUNT(CASE WHEN status = 'EXECUTING' THEN 1 END) as executing_contracts,
    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as completed_contracts,
    SUM(amount) as total_amount,
    AVG(amount) as avg_amount
FROM contracts
WHERE status != 'DRAFT';

-- 输出初始化完成信息
SELECT '数据库初始化完成' as message;
