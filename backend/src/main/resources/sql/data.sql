-- 合同管理系统基础数据初始化脚本
-- 版本: 1.0
-- 创建时间: 2026-03-04

-- 1. 初始化角色数据
INSERT IGNORE INTO roles (id, role_code, role_name, description, status, created_by, created_time) VALUES
(1, 'SUPER_ADMIN', '超级管理员', '系统最高权限管理员', 1, 1, NOW()),
(2, 'ADMIN', '管理员', '系统管理员', 1, 1, NOW()),
(3, 'CONTRACT_MANAGER', '合同管理员', '负责合同管理相关操作', 1, 1, NOW()),
(4, 'FINANCE_MANAGER', '财务管理员', '负责财务管理相关操作', 1, 1, NOW()),
(5, 'APPROVER', '审批人', '负责合同审批', 1, 1, NOW()),
(6, 'VIEWER', '查看者', '只能查看合同信息', 1, 1, NOW());

-- 2. 初始化权限数据
INSERT IGNORE INTO permissions (id, permission_code, permission_name, resource_type, resource_path, description, parent_id, sort_order, status, created_by, created_time) VALUES
-- 系统管理权限
(1, 'system:user:view', '查看用户', 'menu', '/system/user', '查看用户列表', 0, 1, 1, 1, NOW()),
(2, 'system:user:add', '新增用户', 'button', NULL, '新增用户', 1, 1, 1, 1, NOW()),
(3, 'system:user:edit', '编辑用户', 'button', NULL, '编辑用户', 1, 2, 1, 1, NOW()),
(4, 'system:user:delete', '删除用户', 'button', NULL, '删除用户', 1, 3, 1, 1, NOW()),
(5, 'system:role:view', '查看角色', 'menu', '/system/role', '查看角色列表', 0, 2, 1, 1, NOW()),
(6, 'system:role:add', '新增角色', 'button', NULL, '新增角色', 5, 1, 1, 1, NOW()),
(7, 'system:role:edit', '编辑角色', 'button', NULL, '编辑角色', 5, 2, 1, 1, NOW()),
(8, 'system:role:delete', '删除角色', 'button', NULL, '删除角色', 5, 3, 1, 1, NOW()),

-- 合同管理权限
(9, 'contract:view', '查看合同', 'menu', '/contract/list', '查看合同列表', 0, 10, 1, 1, NOW()),
(10, 'contract:add', '新增合同', 'button', NULL, '新增合同', 9, 1, 1, 1, NOW()),
(11, 'contract:edit', '编辑合同', 'button', NULL, '编辑合同', 9, 2, 1, 1, NOW()),
(12, 'contract:delete', '删除合同', 'button', NULL, '删除合同', 9, 3, 1, 1, NOW()),
(13, 'contract:approve', '审批合同', 'button', NULL, '审批合同', 9, 4, 1, 1, NOW()),
(14, 'contract:export', '导出合同', 'button', NULL, '导出合同', 9, 5, 1, 1, NOW()),

-- 财务管理权限
(15, 'finance:payment:view', '查看付款', 'menu', '/finance/payment', '查看付款计划', 0, 20, 1, 1, NOW()),
(16, 'finance:payment:add', '新增付款', 'button', NULL, '新增付款计划', 15, 1, 1, 1, NOW()),
(17, 'finance:payment:edit', '编辑付款', 'button', NULL, '编辑付款计划', 15, 2, 1, 1, NOW()),
(18, 'finance:payment:confirm', '确认付款', 'button', NULL, '确认付款', 15, 3, 1, 1, NOW()),

-- 报表权限
(19, 'report:view', '查看报表', 'menu', '/report', '查看各类报表', 0, 30, 1, 1, NOW()),
(20, 'report:export', '导出报表', 'button', NULL, '导出报表', 19, 1, 1, 1, NOW());

-- 3. 初始化角色权限关联数据
INSERT IGNORE INTO role_permissions (role_id, permission_id, created_by, created_time) VALUES
-- 超级管理员拥有所有权限
(1, 1, 1, NOW()),
(1, 2, 1, NOW()),
(1, 3, 1, NOW()),
(1, 4, 1, NOW()),
(1, 5, 1, NOW()),
(1, 6, 1, NOW()),
(1, 7, 1, NOW()),
(1, 8, 1, NOW()),
(1, 9, 1, NOW()),
(1, 10, 1, NOW()),
(1, 11, 1, NOW()),
(1, 12, 1, NOW()),
(1, 13, 1, NOW()),
(1, 14, 1, NOW()),
(1, 15, 1, NOW()),
(1, 16, 1, NOW()),
(1, 17, 1, NOW()),
(1, 18, 1, NOW()),
(1, 19, 1, NOW()),
(1, 20, 1, NOW()),

-- 管理员拥有大部分权限
(2, 1, 1, NOW()),
(2, 2, 1, NOW()),
(2, 3, 1, NOW()),
(2, 4, 1, NOW()),
(2, 9, 1, NOW()),
(2, 10, 1, NOW()),
(2, 11, 1, NOW()),
(2, 12, 1, NOW()),
(2, 13, 1, NOW()),
(2, 14, 1, NOW()),
(2, 15, 1, NOW()),
(2, 16, 1, NOW()),
(2, 17, 1, NOW()),
(2, 18, 1, NOW()),
(2, 19, 1, NOW()),
(2, 20, 1, NOW()),

-- 合同管理员权限
(3, 9, 1, NOW()),
(3, 10, 1, NOW()),
(3, 11, 1, NOW()),
(3, 12, 1, NOW()),
(3, 13, 1, NOW()),
(3, 14, 1, NOW()),

-- 财务管理员权限
(4, 15, 1, NOW()),
(4, 16, 1, NOW()),
(4, 17, 1, NOW()),
(4, 18, 1, NOW()),
(4, 19, 1, NOW()),
(4, 20, 1, NOW()),

-- 审批人权限
(5, 9, 1, NOW()),
(5, 13, 1, NOW()),

-- 查看者权限
(6, 9, 1, NOW());

-- 4. 初始化合同类型数据
INSERT IGNORE INTO contract_types (id, type_code, type_name, description, status, created_by, created_time) VALUES
(1, 'SALES_CONTRACT', '销售合同', '产品销售相关合同', 1, 1, NOW()),
(2, 'PURCHASE_CONTRACT', '采购合同', '产品采购相关合同', 1, 1, NOW()),
(3, 'SERVICE_CONTRACT', '服务合同', '服务提供相关合同', 1, 1, NOW()),
(4, 'EMPLOYMENT_CONTRACT', '劳动合同', '员工雇佣相关合同', 1, 1, NOW()),
(5, 'LEASE_CONTRACT', '租赁合同', '资产租赁相关合同', 1, 1, NOW()),
(6, 'CONSULTING_CONTRACT', '咨询合同', '咨询服务相关合同', 1, 1, NOW());

-- 5. 初始化提醒规则数据
INSERT IGNORE INTO reminder_rules (id, rule_code, rule_name, rule_type, trigger_condition, reminder_template, status, created_by, created_time) VALUES
(1, 'CONTRACT_EXPIRE_30', '合同到期前30天提醒', 1, '{"days_before": 30}', '合同【{contract_name}】将于{expire_date}到期，请及时处理', 1, 1, NOW()),
(2, 'CONTRACT_EXPIRE_7', '合同到期前7天提醒', 1, '{"days_before": 7}', '合同【{contract_name}】将于{expire_date}到期，请尽快处理', 1, 1, NOW()),
(3, 'PAYMENT_DUE_7', '付款到期前7天提醒', 2, '{"days_before": 7}', '付款计划【{plan_name}】将于{due_date}到期，请准备付款', 1, 1, NOW()),
(4, 'APPROVAL_PENDING', '审批待处理提醒', 3, '{"pending_hours": 24}', '您有合同【{contract_name}】待审批，请及时处理', 1, 1, NOW());

-- 6. 初始化系统配置数据
INSERT IGNORE INTO system_configs (config_key, config_value, config_description, config_type, status, created_by, created_time) VALUES
('system.name', '合同管理系统', '系统名称', 'system', 1, 1, NOW()),
('system.version', '1.0.0', '系统版本', 'system', 1, 1, NOW()),
('system.company', '示例公司', '公司名称', 'system', 1, 1, NOW()),
('file.upload.max_size', '10485760', '文件上传最大大小（字节）', 'system', 1, 1, NOW()),
('file.upload.allowed_types', 'pdf,doc,docx,xls,xlsx,jpg,jpeg,png', '允许上传的文件类型', 'system', 1, 1, NOW()),
('contract.auto_number.prefix', 'CONTRACT', '合同编号前缀', 'business', 1, 1, NOW()),
('contract.auto_number.format', 'YYYYMMDD{seq}', '合同编号格式', 'business', 1, 1, NOW()),
('reminder.enabled', 'true', '是否启用提醒功能', 'business', 1, 1, NOW());

-- 7. 初始化默认租户数据
INSERT IGNORE INTO tenants (id, tenant_code, tenant_name, contact_person, contact_phone, contact_email, status, expire_time, created_time) VALUES
(1, 'DEFAULT', '默认租户', '系统管理员', '13800138000', 'admin@example.com', 1, DATE_ADD(NOW(), INTERVAL 365 DAY), NOW());

-- 8. 初始化租户配置数据
INSERT IGNORE INTO tenant_configs (tenant_id, config_key, config_value, config_description, created_time) VALUES
(1, 'theme.color.primary', '#1890ff', '主题主色调', NOW()),
(1, 'theme.color.success', '#52c41a', '成功颜色', NOW()),
(1, 'theme.color.warning', '#faad14', '警告颜色', NOW()),
(1, 'theme.color.error', '#f5222d', '错误颜色', NOW()),
(1, 'ui.logo.url', '/static/logo.png', 'Logo地址', NOW()),
(1, 'ui.footer.text', '© 2026 合同管理系统', '页脚文本', NOW());

-- 9. 创建默认管理员用户（密码哈希，实际口令请在部署时覆盖）
INSERT IGNORE INTO users (id, username, password, email, phone, real_name, department, position, role_id, status, enabled, created_by, created_time) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTV5UiC', 'admin@example.com', '13800138000', '系统管理员', '技术部', '系统管理员', 1, 1, 1, 1, NOW());

-- 10. 将默认管理员关联到超级管理员角色
INSERT IGNORE INTO user_roles (user_id, role_id, created_by, created_time) VALUES
(1, 1, 1, NOW());

-- 11. 初始化自定义字段定义
INSERT IGNORE INTO custom_field_definitions (entity_type, field_code, field_name, field_type, field_options, is_required, sort_order, status, created_by, created_time) VALUES
('contract', 'project_code', '项目编号', 'text', NULL, 0, 1, 1, 1, NOW()),
('contract', 'contract_category', '合同分类', 'select', '["长期","短期","临时"]', 0, 2, 1, 1, NOW()),
('contract', 'risk_level', '风险等级', 'select', '["低","中","高"]', 0, 3, 1, 1, NOW()),
('contract', 'important_level', '重要程度', 'select', '["一般","重要","非常重要"]', 0, 4, 1, 1, NOW());

-- 完成基础数据初始化
SELECT '基础数据初始化完成' AS message;
