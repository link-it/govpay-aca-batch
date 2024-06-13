package it.govpay.gpd.test.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.query.Param;

import it.govpay.gpd.test.entity.TributoEntity;

public interface TributoRepository extends JpaRepositoryImplementation<TributoEntity, Long>{

	@Query("SELECT t FROM TributoEntity t JOIN t.tipoTributo tt JOIN t.dominio d WHERE tt.codTributo = :codTributo AND d.codDominio = :codDominio")
	public TributoEntity findByCodTributoAndCodDominio(@Param("codTributo") String codTributo, @Param("codDominio") String codDominio);

}
