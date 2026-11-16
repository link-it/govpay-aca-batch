package it.govpay.gpd.repository;

import java.util.List;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;

import it.govpay.gpd.entity.PendenzaGpdEntity;

public interface PendenzaGpdRepository extends JpaRepositoryImplementation<PendenzaGpdEntity, Long> {

	List<PendenzaGpdEntity> findByIdOpzionePagamentoOrderByNumeroRataAsc(Long idOpzionePagamento);

	// Tutte le rate della posizione, qualunque sia lo stato dell'opzione a cui
	// appartengono - il writer marca come comunicate anche quelle di opzioni
	// ATTIVATA/ANNULLATA, non solo quelle DISPONIBILE spedite nel payload.
	List<PendenzaGpdEntity> findByIdDocumento(Long idDocumento);
}
