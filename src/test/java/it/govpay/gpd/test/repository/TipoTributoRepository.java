package it.govpay.gpd.test.repository;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;

import it.govpay.gpd.test.entity.TipoTributoEntity;

public interface TipoTributoRepository extends JpaRepositoryImplementation<TipoTributoEntity, Long>{

	public TipoTributoEntity findByCodTributo(String codTributo);
}
