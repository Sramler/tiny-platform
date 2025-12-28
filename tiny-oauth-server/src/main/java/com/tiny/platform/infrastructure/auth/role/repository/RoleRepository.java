package com.tiny.platform.infrastructure.auth.role.repository;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long>, JpaSpecificationExecutor<Role> {

    @Query("select r from Role r left join fetch r.users where r.id = :id")
    Optional<Role> findByIdFetchUsers(@Param("id") Long id);

    /**
     * 查询角色已经分配的所有资源ID
     */
    @Query("select res.id from Role role join role.resources res where role.id = :roleId")
    List<Long> findResourceIdsByRoleId(@Param("roleId") Long roleId);
}