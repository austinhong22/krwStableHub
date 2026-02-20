package com.austinhong22.krwstablehub.persistence.repository;

import com.austinhong22.krwstablehub.persistence.model.ObligationEntity;
import com.austinhong22.krwstablehub.persistence.model.ObligationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ObligationRepository extends JpaRepository<ObligationEntity, Long> {

    Optional<ObligationEntity> findByTxId(String txId);

    List<ObligationEntity> findByEpochIdAndStatus(Long epochId, ObligationStatus status);

    List<ObligationEntity> findByEpochIsNullAndStatus(ObligationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ObligationEntity o where o.id = :id")
    Optional<ObligationEntity> findByIdForUpdate(@Param("id") Long id);
}
