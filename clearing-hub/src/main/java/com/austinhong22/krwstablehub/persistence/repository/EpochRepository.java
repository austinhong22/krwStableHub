package com.austinhong22.krwstablehub.persistence.repository;

import com.austinhong22.krwstablehub.persistence.model.EpochEntity;
import com.austinhong22.krwstablehub.persistence.model.EpochStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EpochRepository extends JpaRepository<EpochEntity, Long> {

    Optional<EpochEntity> findByEpochNo(long epochNo);

    Optional<EpochEntity> findFirstByStatusOrderByEpochNoAsc(EpochStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EpochEntity e where e.id = :id")
    Optional<EpochEntity> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EpochEntity e where e.status = :status order by e.epochNo asc")
    List<EpochEntity> findByStatusForUpdate(@Param("status") EpochStatus status, Pageable pageable);
}
