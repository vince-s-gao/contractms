package com.contract.service.impl;

import com.contract.entity.Role;
import com.contract.entity.User;
import com.contract.repository.RoleRepository;
import com.contract.repository.UserRepository;
import com.contract.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class UserServiceImpl implements UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        
        // 检查用户是否被禁用（enabled字段为false或0时禁用）
        if (user.getEnabled() != null && !user.getEnabled()) {
            throw new UsernameNotFoundException("用户已被禁用: " + username);
        }
        
        // 获取用户角色
        String role = "ROLE_USER";
        if (user.getRole() != null) {
            role = "ROLE_" + user.getRole().getRoleCode();
        }
        
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(role)
                .build();
    }
    
    @Override
    public User createUser(User user) {
        if (existsByUsername(user.getUsername())) {
            throw new RuntimeException("用户名已存在: " + user.getUsername());
        }
        
        if (existsByEmail(user.getEmail())) {
            throw new RuntimeException("邮箱已存在: " + user.getEmail());
        }
        
        // 加密密码
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        
        // 设置默认角色
        Role defaultRole = roleRepository.findByName("USER")
                .orElseGet(() -> {
                    Role role = new Role("USER", "普通用户");
                    role.setRoleCode("ROLE_USER");
                    return roleRepository.save(role);
                });
        user.setRole(defaultRole);
        
        return userRepository.save(user);
    }

    @Override
    public User registerWithUsername(String username, String password) {
        String normalizedUsername = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
        if (existsByUsername(normalizedUsername)) {
            throw new RuntimeException("用户名已存在");
        }

        User user = new User();
        user.setUsername(normalizedUsername);
        user.setPassword(passwordEncoder.encode(password));
        user.setRealName(normalizedUsername);
        user.setEmail(normalizedUsername + "+" + System.currentTimeMillis() + "@local.contractms");
        user.setDepartment("默认部门");
        user.setEnabled(true);
        user.setUpdateTime(java.time.LocalDateTime.now());

        Role defaultRole = roleRepository.findByRoleCodeIgnoreCase("USER")
                .or(() -> roleRepository.findByRoleCodeIgnoreCase("ROLE_USER"))
                .or(() -> roleRepository.findByName("普通用户"))
                .orElseThrow(() -> new RuntimeException("默认角色不存在，请先初始化角色数据"));
        user.setRole(defaultRole);
        return userRepository.save(user);
    }
    
    @Override
    public User updateUser(Long id, User user) {
        User existingUser = getUserById(id);
        
        // 检查用户名是否被其他用户使用
        if (!existingUser.getUsername().equals(user.getUsername()) && 
            existsByUsername(user.getUsername())) {
            throw new RuntimeException("用户名已存在: " + user.getUsername());
        }
        
        // 检查邮箱是否被其他用户使用
        if (!existingUser.getEmail().equals(user.getEmail()) && 
            existsByEmail(user.getEmail())) {
            throw new RuntimeException("邮箱已存在: " + user.getEmail());
        }
        
        existingUser.setUsername(user.getUsername());
        existingUser.setRealName(user.getRealName());
        existingUser.setEmail(user.getEmail());
        existingUser.setPhone(user.getPhone());
        existingUser.setDepartment(user.getDepartment());
        existingUser.setGender(user.getGender());
        existingUser.setRemark(user.getRemark());
        existingUser.setUpdateTime(java.time.LocalDateTime.now());
        
        return userRepository.save(existingUser);
    }
    
    @Override
    public void deleteUser(Long id) {
        User user = getUserById(id);
        userRepository.delete(user);
    }
    
    @Override
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在，ID: " + id));
    }
    
    @Override
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + username));
    }
    
    @Override
    public List<User> getAllUsers() {
        return userRepository.findAllEnabledUsers();
    }
    
    @Override
    public List<User> getUsersByDepartment(String department) {
        return userRepository.findByDepartment(department);
    }
    
    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsernameIgnoreCase(username);
    }
    
    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
    
    @Override
    public void changePassword(Long userId, String newPassword) {
        User user = getUserById(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(java.time.LocalDateTime.now());
        userRepository.save(user);
    }
    
    @Override
    public void enableUser(Long userId) {
        User user = getUserById(userId);
        user.setEnabled(true);
        user.setUpdateTime(java.time.LocalDateTime.now());
        userRepository.save(user);
    }
    
    @Override
    public void disableUser(Long userId) {
        User user = getUserById(userId);
        user.setEnabled(false);
        user.setUpdateTime(java.time.LocalDateTime.now());
        userRepository.save(user);
    }
}
