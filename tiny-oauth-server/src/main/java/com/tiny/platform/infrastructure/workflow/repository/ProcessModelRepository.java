package com.tiny.platform.infrastructure.workflow.repository;

import com.tiny.platform.infrastructure.workflow.model.ProcessModelEntity;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessModelRepository extends JpaRepository<ProcessModelEntity, Long> {

    @Query("""
        select m from ProcessModelEntity m
        where m.scopeType = :scopeType
          and ((:tenantId is null and m.tenantId is null) or m.tenantId = :tenantId)
        order by m.updatedAt desc, m.id desc
        """)
    List<ProcessModelEntity> findAllInScope(
        @Param("scopeType") ProcessModelScopeType scopeType,
        @Param("tenantId") Long tenantId
    );

    @Query("""
        select m from ProcessModelEntity m
        where m.id = :id
          and m.scopeType = :scopeType
          and ((:tenantId is null and m.tenantId is null) or m.tenantId = :tenantId)
        """)
    Optional<ProcessModelEntity> findByIdInScope(
        @Param("id") Long id,
        @Param("scopeType") ProcessModelScopeType scopeType,
        @Param("tenantId") Long tenantId
    );

    @Query("""
        select max(m.version) from ProcessModelEntity m
        where m.scopeType = :scopeType
          and ((:tenantId is null and m.tenantId is null) or m.tenantId = :tenantId)
          and m.modelKey = :modelKey
        """)
    Integer findMaxVersionInScope(
        @Param("scopeType") ProcessModelScopeType scopeType,
        @Param("tenantId") Long tenantId,
        @Param("modelKey") String modelKey
    );

    @Query("""
        select count(m) > 0 from ProcessModelEntity m
        where m.scopeType = :scopeType
          and ((:tenantId is null and m.tenantId is null) or m.tenantId = :tenantId)
          and m.modelKey = :modelKey
          and m.version = :version
        """)
    boolean existsByScopeAndModelKeyAndVersion(
        @Param("scopeType") ProcessModelScopeType scopeType,
        @Param("tenantId") Long tenantId,
        @Param("modelKey") String modelKey,
        @Param("version") Integer version
    );
}
