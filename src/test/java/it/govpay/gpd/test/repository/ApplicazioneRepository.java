package it.govpay.gpd.test.repository;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.stereotype.Repository;

import it.govpay.gpd.test.entity.ApplicazioneEntity;

@Repository("applicazioneTestRepository")
public interface ApplicazioneRepository extends JpaRepositoryImplementation<ApplicazioneEntity, Long>{

	public ApplicazioneEntity findByCodApplicazione(String codApplicazione);
}
