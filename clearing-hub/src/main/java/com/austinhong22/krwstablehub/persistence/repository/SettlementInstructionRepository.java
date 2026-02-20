package com.austinhong22.krwstablehub.persistence.repository;

import com.austinhong22.krwstablehub.persistence.model.SettlementInstructionEntity;
import com.austinhong22.krwstablehub.persistence.model.SettlementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SettlementInstructionRepository extends JpaRepository<SettlementInstructionEntity, Long> {

    Optional<SettlementInstructionEntity> findByEpochId(Long epochId);

    List<SettlementInstructionEntity> findByStatusOrderByIdAsc(SettlementStatus status, Pageable pageable);

    List<SettlementInstructionEntity> findByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(
            SettlementStatus status,
            Instant nextRetryAt,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SettlementInstructionEntity s where s.id = :id")
    Optional<SettlementInstructionEntity> findByIdForUpdate(@Param("id") Long id);
}
