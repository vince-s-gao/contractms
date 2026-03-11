package com.contract.service;

import com.contract.entity.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

public interface UserService extends UserDetailsService {
    
    User createUser(User user);
    User registerWithUsername(String username, String password);
    
    User updateUser(Long id, User user);
    
    void deleteUser(Long id);
    
    User getUserById(Long id);
    
    User getUserByUsername(String username);
    
    List<User> getAllUsers();
    
    List<User> getUsersByDepartment(String department);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    void changePassword(Long userId, String newPassword);
    
    void enableUser(Long userId);
    
    void disableUser(Long userId);
}
