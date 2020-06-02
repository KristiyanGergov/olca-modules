package org.openlca.core.database.usage;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openlca.core.Tests;
import org.openlca.core.database.FlowDao;
import org.openlca.core.database.IDatabase;
import org.openlca.core.database.ImpactCategoryDao;
import org.openlca.core.database.ProcessDao;
import org.openlca.core.model.Exchange;
import org.openlca.core.model.Flow;
import org.openlca.core.model.ImpactCategory;
import org.openlca.core.model.ImpactFactor;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.Process;
import org.openlca.core.model.descriptors.BaseDescriptor;
import org.openlca.core.model.descriptors.CategorizedDescriptor;
import org.openlca.core.model.descriptors.Descriptors;
import org.openlca.core.model.descriptors.FlowDescriptor;

public class FlowUseSearchTest {

	private IDatabase database = Tests.getDb();
	private IUseSearch<FlowDescriptor> search;
	private Flow flow;

	@Before
	public void setup() {
		flow = new Flow();
		flow.name = "flow";
		flow = new FlowDao(database).insert(flow);
		search = IUseSearch.FACTORY.createFor(ModelType.FLOW, database);
	}

	@After
	public void tearDown() {
		new FlowDao(database).delete(flow);
	}

	@Test
	public void testFindNoUsage() {
		List<CategorizedDescriptor> models = search.findUses(Descriptors
				.toDescriptor(flow));
		Assert.assertNotNull(models);
		Assert.assertTrue(models.isEmpty());
	}

	@Test
	public void testFindInImpactCategories() {
		ImpactFactor iFactor = new ImpactFactor();
		iFactor.flow = flow;
		ImpactCategory ic = new ImpactCategory();
		ic.impactFactors.add(iFactor);
		ImpactCategoryDao dao = new ImpactCategoryDao(database);
		dao.insert(ic);
		List<CategorizedDescriptor> results = search.findUses(
				Descriptors.toDescriptor(flow));
		dao.delete(ic);
		Assert.assertEquals(1, results.size());
		Assert.assertEquals(
				Descriptors.toDescriptor(ic), results.get(0));
	}


	@Test
	public void testFindInProcesses() {
		Process process = createProcess();
		List<CategorizedDescriptor> results = search.findUses(Descriptors
				.toDescriptor(flow));
		new ProcessDao(database).delete(process);
		BaseDescriptor expected = Descriptors.toDescriptor(process);
		Assert.assertEquals(1, results.size());
		Assert.assertEquals(expected, results.get(0));
	}

	private Process createProcess() {
		Process process = new Process();
		process.name = "process";
		Exchange exchange = new Exchange();
		exchange.flow = flow;
		process.exchanges.add(exchange);
		return new ProcessDao(database).insert(process);
	}
}
