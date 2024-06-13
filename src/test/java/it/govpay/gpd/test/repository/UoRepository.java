package it.govpay.gpd.test.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.query.Param;

import it.govpay.gpd.test.entity.UoEntity;

public interface UoRepository extends JpaRepositoryImplementation<UoEntity, Long>{

	
	@Query("SELECT uo FROM UoEntity uo JOIN uo.dominio d WHERE uo.codUo = :codUo AND d.codDominio = :codDominio")
	public UoEntity findByCodUoAndCodDominio(@Param("codUo") String codUo, @Param("codDominio") String codDominio);
}
