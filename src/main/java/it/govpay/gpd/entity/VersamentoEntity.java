package it.govpay.gpd.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter	
@NoArgsConstructor
@Entity
@Table(name = "versamenti")
public class VersamentoEntity {
	
	@Id
	@SequenceGenerator(name="seq_versamenti",sequenceName="seq_versamenti", initialValue=1, allocationSize=1)
	@GeneratedValue(strategy= GenerationType.SEQUENCE, generator="seq_versamenti")
	private Long id;

	@Column(name = "data_ultima_modifica_aca")
	private OffsetDateTime dataUltimaModificaAca;
	
	@Column(name = "data_ultima_comunicazione_aca")
	private OffsetDateTime dataUltimaComunicazioneAca;
}
