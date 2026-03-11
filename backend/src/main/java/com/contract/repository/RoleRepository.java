package com.contract.repository;

import com.contract.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    
    Optional<Role> findByName(String name);
    Optional<Role> findByRoleCodeIgnoreCase(String roleCode);
    
    boolean existsByName(String name);
    
    @Query("SELECT r FROM Role r WHERE r.status = 1")
    List<Role> findAllEnabledRoles();
    
    @Query("SELECT r FROM Role r WHERE r.name IN :names AND r.status = 1")
    List<Role> findByNames(@Param("names") List<String> names);
}
