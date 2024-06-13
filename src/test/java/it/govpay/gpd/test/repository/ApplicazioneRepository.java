package it.govpay.gpd.test.repository;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;

import it.govpay.gpd.test.entity.ApplicazioneEntity;

public interface ApplicazioneRepository extends JpaRepositoryImplementation<ApplicazioneEntity, Long>{

	public ApplicazioneEntity findByCodApplicazione(String codApplicazione);
}
