package it.govpay.gpd.test.repository;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;

import it.govpay.gpd.test.entity.VersamentoFullEntity;

public interface VersamentoFullRepository extends JpaRepositoryImplementation<VersamentoFullEntity, Long>{

	public VersamentoFullEntity findFirstByOrderByIdDesc();
}
