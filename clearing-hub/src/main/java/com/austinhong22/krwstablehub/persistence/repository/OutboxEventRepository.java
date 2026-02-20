package com.austinhong22.krwstablehub.persistence.repository;

import com.austinhong22.krwstablehub.persistence.model.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findByStatusAndAvailableAtLessThanEqualOrderByIdAsc(
            String status,
            Instant availableAt,
            Pageable pageable
    );
}
