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
@Table(name = "tributi")
public class TributoEntity {

    @Id
    private Long id;
    
    @Column(name = "tipo_contabilita", length = 1)
    private String tipoContabilita;
    
    @Column(name = "codice_contabilita", length = 255)
    private String codiceContabilita;
    
	@Column(name = "id_iban_accredito")
	private Long idIbanAccredito;
	
	@Column(name = "id_iban_appoggio")
	private Long idIbanAppoggio;
    
    @ManyToOne
    @JoinColumn(name = "id_tipo_tributo", nullable = false)
    private TipoTributoEntity tipoTributo;
    
    @ManyToOne
    @JoinColumn(name = "id_dominio", nullable = false)
    private DominioEntity dominio;
    
}