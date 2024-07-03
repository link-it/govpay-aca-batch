package it.govpay.gpd.test.entity;

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
@Table(name = "tipi_tributo")
public class TipoTributoEntity {
	
    @Id
    private Long id;
    
    @Column(name = "cod_tributo", nullable = false, unique = true, length = 255)
    private String codTributo;
    
    @Column(name = "tipo_contabilita", length = 1)
    private String tipoContabilita;
    
    @Column(name = "cod_contabilita", length = 255)
    private String codContabilita;
}
