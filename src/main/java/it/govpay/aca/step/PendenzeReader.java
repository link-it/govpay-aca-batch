package it.govpay.aca.step;

import java.util.Iterator;

import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import it.govpay.aca.entity.VersamentoAcaEntity;
import it.govpay.aca.entity.VersamentoAcaEntity_;
import it.govpay.aca.repository.VersamentoAcaRepository;

@Component
public class PendenzeReader implements ItemReader<VersamentoAcaEntity> {
	
	@Value("${it.govpay.aca.batch.dbreader.numeroPendenze.limit:100}")
	private Integer limit;
	
	private Integer offset= 0;

	@Autowired
	VersamentoAcaRepository versamentoAcaRepository;

    private Iterator<VersamentoAcaEntity> iterator;

    @Override
    public VersamentoAcaEntity read() throws Exception {
        if (iterator == null || !iterator.hasNext()) {
            Page<VersamentoAcaEntity> page = versamentoAcaRepository.findAll(PageRequest.of(this.offset, this.limit, Sort.by(VersamentoAcaEntity_.DATA_ULTIMA_COMUNICAZIONE_ACA).descending()));
            iterator = page.iterator();
        }

        return iterator.hasNext() ? iterator.next() : null;
    }
}
