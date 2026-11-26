package it.govpay.gpd.test.repository;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.stereotype.Repository;

import it.govpay.common.entity.DominioEntity;

@Repository("dominioTestRepository")
public interface DominioRepository extends JpaRepositoryImplementation<DominioEntity, Long>{

	public DominioEntity findByCodDominio(String codDominio);
}
