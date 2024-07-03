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
@Table(name = "iban_accredito")
public class IbanAccreditoEntity {
    
    @Id
    private Long id;
    
    @Column(name = "cod_iban", nullable = false, length = 255)
    private String codIban;
    
    @Column(name = "postale", nullable = false)
    private Boolean postale;
    
	@ManyToOne
	@JoinColumn(name = "id_dominio", nullable = false)
	private DominioEntity dominio;
}