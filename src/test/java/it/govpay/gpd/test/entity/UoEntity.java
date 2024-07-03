package it.govpay.gpd.test.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "uo")
public class UoEntity {

	@Id
	private Long id;

	@Column(name = "cod_uo", nullable = false, length = 35)
	private String codUo;

	@Column(name = "uo_denominazione", length = 70)
	private String uoDenominazione;

	@ManyToOne
	@JoinColumn(name = "id_dominio", nullable = false)
	private DominioEntity dominio;

}
