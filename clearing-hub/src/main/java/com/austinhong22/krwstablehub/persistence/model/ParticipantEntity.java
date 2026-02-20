package com.austinhong22.krwstablehub.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "participants")
public class ParticipantEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "participant_code", nullable = false, length = 32, unique = true)
    private String participantCode;

    @Column(name = "ledger_address", nullable = false, length = 42, unique = true)
    private String ledgerAddress;

    @Column(name = "net_debit_cap_krw", nullable = false)
    private long netDebitCapKrw;
}
