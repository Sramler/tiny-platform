package com.tiny.platform.infrastructure.auth.datascope.repository;

import com.tiny.platform.infrastructure.auth.datascope.domain.RoleDataScopeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleDataScopeItemRepository extends JpaRepository<RoleDataScopeItem, Long> {

    List<RoleDataScopeItem> findByRoleDataScopeId(Long roleDataScopeId);

    List<RoleDataScopeItem> findByRoleDataScopeIdIn(List<Long> roleDataScopeIds);

    void deleteByRoleDataScopeId(Long roleDataScopeId);
}
