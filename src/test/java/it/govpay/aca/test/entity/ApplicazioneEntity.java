package it.govpay.aca.test.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
