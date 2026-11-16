package it.govpay.gpd.repository;

import java.util.List;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;

import it.govpay.gpd.entity.OpzionePagamentoGpdEntity;
import it.govpay.pendenze.model.StatoOpzionePagamento;

public interface OpzionePagamentoGpdRepository extends JpaRepositoryImplementation<OpzionePagamentoGpdEntity, Long> {

	List<OpzionePagamentoGpdEntity> findByIdDocumento(Long idDocumento);

	List<OpzionePagamentoGpdEntity> findByIdDocumentoAndStato(Long idDocumento, StatoOpzionePagamento stato);
}
