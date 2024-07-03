package it.govpay.gpd.test.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.query.Param;

import it.govpay.gpd.test.entity.IbanAccreditoEntity;

public interface IbanAccreditoRepository extends JpaRepositoryImplementation<IbanAccreditoEntity, Long>{

	public IbanAccreditoEntity findByCodIban(String codIban);
	
	@Query("SELECT ibanAccredito FROM IbanAccreditoEntity ibanAccredito JOIN ibanAccredito.dominio d WHERE ibanAccredito.codIban = :codIban AND d.codDominio = :codDominio")
	public IbanAccreditoEntity findByCodIbanAndCodDominio(@Param("codIban") String codIban, @Param("codDominio") String codDominio);
}
