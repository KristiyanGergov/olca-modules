package org.openlca.io.olca;

import org.openlca.core.database.FlowDao;
import org.openlca.core.database.IDatabase;
import org.openlca.core.database.ProcessDao;
import org.openlca.core.database.ProductSystemDao;
import org.openlca.core.model.Exchange;
import org.openlca.core.model.Flow;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.ParameterRedef;
import org.openlca.core.model.Process;
import org.openlca.core.model.ProcessLink;
import org.openlca.core.model.ProductSystem;
import org.openlca.core.model.Unit;
import org.openlca.core.model.descriptors.FlowDescriptor;
import org.openlca.core.model.descriptors.ProcessDescriptor;
import org.openlca.core.model.descriptors.ProductSystemDescriptor;
import org.openlca.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.trove.map.hash.TLongLongHashMap;

class ProductSystemImport {

	private Logger log = LoggerFactory.getLogger(getClass());

	private ProductSystemDao srcDao;
	private IDatabase source;
	private IDatabase dest;
	private RefSwitcher refs;
	private Sequence seq;

	private TLongLongHashMap processMap = new TLongLongHashMap();
	private TLongLongHashMap flowMap = new TLongLongHashMap();

	ProductSystemImport(IDatabase source, IDatabase dest, Sequence seq) {
		this.srcDao = new ProductSystemDao(source);
		this.refs = new RefSwitcher(source, dest, seq);
		this.source = source;
		this.dest = dest;
		this.seq = seq;
	}

	public void run() {
		log.trace("import product systems");
		try {
			buildProcessMap();
			buildFlowMap();
			for (ProductSystemDescriptor descriptor : srcDao.getDescriptors()) {
				if (seq.contains(seq.PRODUCT_SYSTEM, descriptor.getRefId()))
					continue;
				createSystem(descriptor);
			}
		} catch (Exception e) {
			log.error("failed to import product systems", e);
		}
	}

	private void buildProcessMap() {
		ProcessDao srcDao = new ProcessDao(source);
		for (ProcessDescriptor descriptor : srcDao.getDescriptors()) {
			long srcId = descriptor.getId();
			long destId = seq.get(seq.PROCESS, descriptor.getRefId());
			processMap.put(srcId, destId);
		}
	}

	private void buildFlowMap() {
		FlowDao srcDao = new FlowDao(source);
		for (FlowDescriptor descriptor : srcDao.getDescriptors()) {
			long srcId = descriptor.getId();
			long destId = seq.get(seq.FLOW, descriptor.getRefId());
			flowMap.put(srcId, destId);
		}
	}

	private void createSystem(ProductSystemDescriptor descriptor) {
		ProductSystem srcSystem = srcDao.getForId(descriptor.getId());
		ProductSystem destSystem = srcSystem.clone();
		destSystem.setRefId(srcSystem.getRefId());
		destSystem.setCategory(refs.switchRef(srcSystem.getCategory()));
		destSystem.setReferenceProcess(refs.switchRef(srcSystem
				.getReferenceProcess()));
		switchRefExchange(srcSystem, destSystem);
		destSystem.setTargetUnit(refs.switchRef(srcSystem.getTargetUnit()));
		switchRefFlowProp(srcSystem, destSystem);
		switchProcessIds(srcSystem, destSystem);
		switchProcessLinkIds(destSystem);
		switchParameterRedefs(destSystem);
		ProductSystemDao destDao = new ProductSystemDao(dest);
		destSystem = destDao.insert(destSystem);
		seq.put(seq.PRODUCT_SYSTEM, srcSystem.getRefId(), destSystem.getId());
	}

	private void switchRefExchange(ProductSystem srcSystem,
			ProductSystem destSystem) {
		Exchange srcExchange = srcSystem.getReferenceExchange();
		Process destProcess = destSystem.getReferenceProcess();
		if (srcExchange == null || destProcess == null)
			return;
		Exchange destRefExchange = null;
		for (Exchange destExchange : destProcess.getExchanges()) {
			if (sameExchange(srcExchange, destExchange)) {
				destRefExchange = destExchange;
				break;
			}
		}
		destSystem.setReferenceExchange(destRefExchange);
	}

	private boolean sameExchange(Exchange srcExchange, Exchange destExchange) {
		if (srcExchange.isInput() != destExchange.isInput())
			return false;
		Unit srcUnit = srcExchange.getUnit();
		Unit destUnit = destExchange.getUnit();
		Flow srcFlow = srcExchange.getFlow();
		Flow destFlow = destExchange.getFlow();
		return srcUnit != null && destUnit != null && srcFlow != null
				&& destFlow != null
				&& Strings.nullOrEqual(srcUnit.getRefId(), destUnit.getRefId())
				&& Strings.nullOrEqual(srcFlow.getRefId(), destFlow.getRefId());
	}

	private void switchRefFlowProp(ProductSystem srcSystem,
			ProductSystem destSystem) {
		Flow destFlow = destSystem.getReferenceExchange().getFlow();
		if (destFlow == null)
			return;
		destSystem.setTargetFlowPropertyFactor(refs.switchRef(
				srcSystem.getTargetFlowPropertyFactor(), destFlow));
	}

	private void switchProcessIds(ProductSystem srcSystem,
			ProductSystem destSystem) {
		destSystem.getProcesses().clear();
		for (long srcProcessId : srcSystem.getProcesses()) {
			long destProcessId = processMap.get(srcProcessId);
			if (destProcessId == 0L)
				continue;
			destSystem.getProcesses().add(destProcessId);
		}
	}

	private void switchProcessLinkIds(ProductSystem destSystem) {
		for (ProcessLink link : destSystem.getProcessLinks()) {
			long destProviderId = processMap.get(link.providerId);
			long destFlowId = flowMap.get(link.flowId);
			long destRecipientId = processMap.get(link.processId);
			if (destProviderId == 0 || destFlowId == 0 || destRecipientId == 0)
				log.warn("could not translate process link {}", link);
			link.processId = destProviderId;
			link.flowId = destFlowId;
			link.processId = destRecipientId;
			// TODO: exchange link ID
		}
	}

	private void switchParameterRedefs(ProductSystem destSystem) {
		for (ParameterRedef redef : destSystem.getParameterRedefs()) {
			if (redef.getContextId() == null)
				continue;
			if (redef.getContextType() == ModelType.IMPACT_METHOD) {
				Long destMethodId = refs.getDestImpactMethodId(redef
						.getContextId());
				redef.setContextId(destMethodId);
			} else {
				long destProcessId = processMap.get(redef.getContextId());
				redef.setContextId(destProcessId);
			}
		}
	}

}
