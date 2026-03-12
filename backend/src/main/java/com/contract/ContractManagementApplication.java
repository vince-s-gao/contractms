package com.contract;

import com.contract.entity.Role;
import com.contract.entity.User;
import com.contract.repository.RoleRepository;
import com.contract.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class ContractManagementApplication implements CommandLineRunner {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.demo-data:false}")
    private boolean bootstrapDemoData;

    @Value("${app.bootstrap.admin-password:ChangeMe123!}")
    private String adminPassword;

    @Value("${app.bootstrap.user-password:ChangeMe123!}")
    private String userPassword;
    
    public static void main(String[] args) {
        SpringApplication.run(ContractManagementApplication.class, args);
    }
    
    @Override
    public void run(String... args) throws Exception {
        if (!bootstrapDemoData) {
            return;
        }
        if (isBlank(adminPassword) || isBlank(userPassword)) {
            throw new IllegalStateException(
                    "APP_BOOTSTRAP_ADMIN_PASSWORD and APP_BOOTSTRAP_USER_PASSWORD are required when APP_BOOTSTRAP_DEMO_DATA=true");
        }

        // 初始化管理员角色
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> {
                    Role role = new Role("ADMIN", "系统管理员");
                    role.setRoleCode("ROLE_ADMIN");
                    role.setPermissions("user:read,user:write,contract:read,contract:write");
                    return roleRepository.save(role);
                });
        
        // 初始化用户角色
        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> {
                    Role role = new Role("USER", "普通用户");
                    role.setRoleCode("ROLE_USER");
                    role.setPermissions("contract:read");
                    return roleRepository.save(role);
                });
        
        // 初始化管理员用户
        if (!userRepository.existsByUsername("admin")) {
            User adminUser = new User();
            adminUser.setUsername("admin");
            adminUser.setPassword(passwordEncoder.encode(adminPassword));
            adminUser.setRealName("系统管理员");
            adminUser.setEmail("admin@contract.com");
            adminUser.setDepartment("IT部门");
            adminUser.setPhone("13800138000");
            adminUser.setGender("男");
            adminUser.setRole(adminRole);
            adminUser.setEnabled(true);
            userRepository.save(adminUser);
        }
        
        // 初始化测试用户
        if (!userRepository.existsByUsername("user")) {
            User testUser = new User();
            testUser.setUsername("user");
            testUser.setPassword(passwordEncoder.encode(userPassword));
            testUser.setRealName("测试用户");
            testUser.setEmail("user@contract.com");
            testUser.setDepartment("业务部门");
            testUser.setPhone("13900139000");
            testUser.setGender("女");
            testUser.setRole(userRole);
            testUser.setEnabled(true);
            userRepository.save(testUser);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
