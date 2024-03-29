package it.govpay.aca.entity;

import java.time.OffsetDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

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
