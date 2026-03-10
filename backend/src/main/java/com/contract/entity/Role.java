package com.contract.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "roles")
public class Role {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称长度不能超过100个字符")
    @Column(name = "role_name", nullable = false)
    private String name;
    
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码长度不能超过50个字符")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "角色编码只能包含字母、数字和下划线")
    @Column(name = "role_code", unique = true, nullable = false)
    private String roleCode;
    
    @Size(max = 500, message = "角色描述长度不能超过500个字符")
    @Column
    private String description;
    
    @Size(max = 500, message = "权限列表长度不能超过500个字符")
    @Column
    private String permissions;
    
    @Column(nullable = false)
    private Integer status = 1;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createTime = LocalDateTime.now();
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updateTime = LocalDateTime.now();
    
    // 构造函数
    public Role() {}
    
    public Role(String name, String description) {
        this.name = name;
        this.description = description;
    }
    
    // Getter和Setter方法
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getPermissions() { return permissions; }
    public void setPermissions(String permissions) { this.permissions = permissions; }
    
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
