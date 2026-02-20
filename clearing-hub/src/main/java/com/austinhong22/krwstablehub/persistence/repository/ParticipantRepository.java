package com.austinhong22.krwstablehub.persistence.repository;

import com.austinhong22.krwstablehub.persistence.model.ParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<ParticipantEntity, Long> {

    Optional<ParticipantEntity> findByParticipantCode(String participantCode);

    Optional<ParticipantEntity> findByLedgerAddress(String ledgerAddress);
}
