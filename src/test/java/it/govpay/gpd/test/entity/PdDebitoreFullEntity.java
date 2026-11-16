package it.govpay.gpd.test.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Fixture scrivibile su {@code pd_debitori} (modello v3): il debitore di una posizione. */
@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pd_debitori")
public class PdDebitoreFullEntity {

	@Id
	private Long id;

	@Column(name = "id_documento", nullable = false)
	private long idDocumento;

	@Column(name = "id_debitore", nullable = false)
	private long idDebitore;

	@Column(name = "posizione", nullable = false)
	private Integer posizione;

	@Column(name = "data_rimozione")
	private OffsetDateTime dataRimozione;
}
