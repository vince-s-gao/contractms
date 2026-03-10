package com.contract.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    @Column(unique = true, nullable = false)
    private String username;
    
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6-100个字符之间")
    @Column(nullable = false)
    private String password;
    
    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过50个字符")
    @Column(nullable = false)
    private String realName;
    
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    @Column(unique = true, nullable = false)
    private String email;
    
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Column
    private String phone;
    
    @NotBlank(message = "部门不能为空")
    @Size(max = 100, message = "部门长度不能超过100个字符")
    @Column(nullable = false)
    private String department;
    
    @Pattern(regexp = "^[男女]$", message = "性别只能为男或女")
    @Column
    private String gender;
    
    @Size(max = 200, message = "备注长度不能超过200个字符")
    @Column
    private String remark;
    
    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;
    
    @Column(nullable = false)
    private Boolean enabled = true;
    
    @Column(name = "created_time", nullable = false)
    private LocalDateTime createTime = LocalDateTime.now();
    
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updateTime = LocalDateTime.now();
    
    // 构造函数
    public User() {}
    
    public User(String username, String password, String realName, String email, String department) {
        this.username = username;
        this.password = password;
        this.realName = realName;
        this.email = email;
        this.department = department;
    }
    
    // Getter和Setter方法
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
