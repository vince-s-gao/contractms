-- 合同管理系统基础数据初始化脚本
-- 创建系统管理员、基础角色和权限数据

USE contract_management;

-- 插入系统管理员用户（密码：admin123，已使用BCrypt加密）
INSERT INTO users (username, password, email, real_name, phone, department, role, status) 
VALUES 
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3pPTCGNljPe.ZdWuoLPm', 'admin@contract.com', '系统管理员', '13800138000', 'IT部门', 'ADMIN', 'ACTIVE')
ON DUPLICATE KEY UPDATE updated_time = CURRENT_TIMESTAMP;

-- 插入基础角色
INSERT INTO roles (role_code, role_name, description, status) 
VALUES 
('ADMIN', '系统管理员', '拥有系统所有权限', 'ACTIVE'),
('CONTRACT_MANAGER', '合同管理员', '合同管理相关权限', 'ACTIVE'),
('APPROVAL_MANAGER', '审批管理员', '合同审批相关权限', 'ACTIVE'),
('USER', '普通用户', '基础用户权限', 'ACTIVE')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

-- 插入基础权限
INSERT INTO permissions (permission_code, permission_name, resource_type, resource_path, description, parent_id, sort_order, status) 
VALUES 
-- 用户管理权限
('USER_VIEW', '查看用户', 'MENU', '/users', '查看用户列表', 0, 1, 'ACTIVE'),
('USER_CREATE', '创建用户', 'BUTTON', '/users', '创建新用户', 1, 2, 'ACTIVE'),
('USER_EDIT', '编辑用户', 'BUTTON', '/users', '编辑用户信息', 1, 3, 'ACTIVE'),
('USER_DELETE', '删除用户', 'BUTTON', '/users', '删除用户', 1, 4, 'ACTIVE'),

-- 合同管理权限
('CONTRACT_VIEW', '查看合同', 'MENU', '/contracts', '查看合同列表', 0, 5, 'ACTIVE'),
('CONTRACT_CREATE', '创建合同', 'BUTTON', '/contracts', '创建新合同', 5, 6, 'ACTIVE'),
('CONTRACT_EDIT', '编辑合同', 'BUTTON', '/contracts', '编辑合同信息', 5, 7, 'ACTIVE'),
('CONTRACT_DELETE', '删除合同', 'BUTTON', '/contracts', '删除合同', 5, 8, 'ACTIVE'),
('CONTRACT_APPROVE', '审批合同', 'BUTTON', '/contracts', '审批合同', 5, 9, 'ACTIVE'),
('CONTRACT_BATCH_UPLOAD', '批量上传合同', 'BUTTON', '/contracts/import', '批量上传合同', 5, 10, 'ACTIVE'),
('CONTRACT_EXPORT', '导出合同', 'BUTTON', '/contracts/export', '导出合同', 5, 11, 'ACTIVE'),
('CONTRACT_TYPE_MANAGE', '合同类型管理', 'BUTTON', '/contracts/types', '管理合同类型', 5, 12, 'ACTIVE'),

-- 审批管理权限
('APPROVAL_VIEW', '查看审批', 'MENU', '/approvals', '查看审批任务', 0, 13, 'ACTIVE'),
('APPROVAL_PROCESS', '处理审批', 'BUTTON', '/approvals', '处理审批任务', 13, 14, 'ACTIVE'),

-- 文件管理权限
('FILE_UPLOAD', '文件上传', 'BUTTON', '/files', '上传文件', 0, 15, 'ACTIVE'),
('FILE_DOWNLOAD', '文件下载', 'BUTTON', '/files', '下载文件', 0, 16, 'ACTIVE'),
('FILE_DELETE', '文件删除', 'BUTTON', '/files', '删除文件', 0, 17, 'ACTIVE')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

-- 为用户分配角色
INSERT INTO user_roles (user_id, role_id) 
SELECT u.id, r.id 
FROM users u, roles r 
WHERE u.username = 'admin' AND r.role_code = 'ADMIN'
ON DUPLICATE KEY UPDATE created_at = CURRENT_TIMESTAMP;

-- 为角色分配权限（系统管理员拥有所有权限）
INSERT INTO role_permissions (role_id, permission_id) 
SELECT r.id, p.id 
FROM roles r, permissions p 
WHERE r.role_code = 'ADMIN'
ON DUPLICATE KEY UPDATE created_at = CURRENT_TIMESTAMP;

-- 为合同管理员分配合同相关权限
INSERT INTO role_permissions (role_id, permission_id) 
SELECT r.id, p.id 
FROM roles r, permissions p 
WHERE r.role_code = 'CONTRACT_MANAGER' 
AND p.permission_code IN ('CONTRACT_VIEW', 'CONTRACT_CREATE', 'CONTRACT_EDIT', 'CONTRACT_DELETE', 'CONTRACT_BATCH_UPLOAD', 'CONTRACT_EXPORT', 'CONTRACT_TYPE_MANAGE', 'FILE_UPLOAD', 'FILE_DOWNLOAD')
ON DUPLICATE KEY UPDATE created_at = CURRENT_TIMESTAMP;

-- 为审批管理员分配审批相关权限
INSERT INTO role_permissions (role_id, permission_id) 
SELECT r.id, p.id 
FROM roles r, permissions p 
WHERE r.role_code = 'APPROVAL_MANAGER' 
AND p.permission_code IN ('CONTRACT_VIEW', 'APPROVAL_VIEW', 'APPROVAL_PROCESS')
ON DUPLICATE KEY UPDATE created_at = CURRENT_TIMESTAMP;

-- 为普通用户分配基础权限
INSERT INTO role_permissions (role_id, permission_id) 
SELECT r.id, p.id 
FROM roles r, permissions p 
WHERE r.role_code = 'USER' 
AND p.permission_code IN ('CONTRACT_VIEW', 'FILE_UPLOAD', 'FILE_DOWNLOAD')
ON DUPLICATE KEY UPDATE created_at = CURRENT_TIMESTAMP;

-- 插入系统配置
INSERT INTO system_configs (config_key, config_value, config_type, description) 
VALUES 
('system.name', '合同管理系统', 'STRING', '系统名称'),
('system.version', '1.0.0', 'STRING', '系统版本'),
('file.max.size', '10485760', 'INTEGER', '文件最大大小(字节)'),
('contract.auto.number', 'true', 'BOOLEAN', '合同自动编号'),
('approval.workflow.enabled', 'true', 'BOOLEAN', '启用审批流程')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

-- 输出初始化完成信息
SELECT '基础数据初始化完成' as message;
SELECT 
    (SELECT COUNT(*) FROM users) as user_count,
    (SELECT COUNT(*) FROM roles) as role_count,
    (SELECT COUNT(*) FROM permissions) as permission_count;
