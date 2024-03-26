package it.govpay.aca.test.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter	
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "applicazioni")
public class ApplicazioneEntity {

	@Id
	private Long id;
	
	@Column(name = "cod_applicazione", nullable = false)
	private String codApplicazione;
}
