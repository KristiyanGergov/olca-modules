package org.openlca.core.library;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.openlca.core.database.CategorizedEntityDao;
import org.openlca.core.database.FlowDao;
import org.openlca.core.database.IDatabase;
import org.openlca.core.database.ImpactCategoryDao;
import org.openlca.core.database.LocationDao;
import org.openlca.core.database.ProcessDao;
import org.openlca.core.matrix.index.EnviIndex;
import org.openlca.core.matrix.index.ImpactIndex;
import org.openlca.core.matrix.index.EnviFlow;
import org.openlca.core.matrix.MatrixData;
import org.openlca.core.matrix.index.TechFlow;
import org.openlca.core.matrix.index.TechIndex;
import org.openlca.core.matrix.format.MatrixReader;
import org.openlca.core.model.Exchange;
import org.openlca.core.model.ImpactFactor;
import org.openlca.core.model.Version;
import org.openlca.core.model.descriptors.CategorizedDescriptor;
import org.openlca.core.model.descriptors.ImpactDescriptor;
import org.openlca.jsonld.Json;
import org.openlca.util.Exceptions;
import org.slf4j.LoggerFactory;

public class Library {

	/**
	 * The folder where the library files are stored.
	 */
	public final File folder;
	private LibraryInfo _info;

	public Library(File folder) {
		this.folder = folder;
	}

	/**
	 * Creates an empty library in the given library folder.
	 */
	public static Library create(LibraryDir dir, LibraryInfo info) {
		var libDir = dir.getFolder(info);
		if (!libDir.exists()) {
			try {
				Files.createDirectories(libDir.toPath());
			} catch (IOException e) {
				Exceptions.unchecked("failed to create library folder " + libDir, e);
			}
		}
		var lib = new Library(libDir);
		info.writeTo(lib);
		return lib;
	}

	/**
	 * Creates a new library by writing the given matrix data into the given
	 * folder. It will try to derive the name and version of the library from
	 * the name of the given folder which should follow the pattern
	 * `<lib-name>_<version>`. Note that this method will modify the data of the
	 * matrices A and B if these are not normalized 1 | -1. Thus, you should not
	 * reuse the matrices after calling this method are provide a copy of the
	 * data.
	 */
	public static Library create(IDatabase db, MatrixData data, File folder) {
		var fullName = folder.getName();
		var nameParts = fullName.split("_");
		var versionPart = nameParts.length == 1
			? null
			: nameParts[nameParts.length - 1];

		var name = fullName;
		var version = "";
		if (versionPart != null) {
			name = fullName.substring(0,
				fullName.length() - versionPart.length() - 1);
			version = Version.format(versionPart);
		}

		var regionalized = data.enviIndex != null
			&& data.enviIndex.isRegionalized();
		var info = LibraryInfo.of(name, version)
			.isRegionalized(regionalized);
		new LibraryExport(db, folder)
			.withConfig(info)
			.withData(data)
			.run();
		return new Library(folder);
	}

	public LibraryInfo getInfo() {
		if (_info != null)
			return _info;
		var file = new File(folder, "library.json");
		var obj = Json.readObject(file);
		if (obj.isEmpty())
			throw new RuntimeException("failed to read " + file);
		_info = LibraryInfo.fromJson(obj.get());
		return _info;
	}

	public String id() {
		return getInfo().id();
	}

	/**
	 * Get the dependencies of this library (which includes dependencies of
	 * dependencies etc.).
	 */
	public Set<Library> getDependencies() {
		var info = getInfo();
		if (info.dependencies().isEmpty())
			return Collections.emptySet();
		var libDir = new LibraryDir(folder.getParentFile());

		var deps = new HashSet<Library>();
		var queue = new ArrayDeque<Library>();
		queue.add(this);
		while (!queue.isEmpty()) {
			queue.poll()
				.getInfo()
				.dependencies().stream()
				.map(libDir::get)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.forEach(dep -> {
					if (!deps.contains(dep)) {
						deps.add(dep);
						queue.add(dep);
					}
				});
		}
		return deps;
	}

	public void addDependency(Library dependency) {
		if (dependency == null)
			return;
		var info = getInfo();
		var depID = dependency.id();
		if (info.dependencies().contains(depID))
			return;
		info.dependencies().add(depID);
		info.writeTo(this);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		var other = (Library) o;
		return Objects.equals(getInfo(), other.getInfo());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getInfo());
	}

	/**
	 * Get the product index of this library.
	 */
	public Proto.ProductIndex getProductIndex() {
		var file = new File(folder, "index_A.bin");
		if (!file.exists())
			return Proto.ProductIndex.getDefaultInstance();
		try (var stream = new FileInputStream(file)) {
			return Proto.ProductIndex.parseFrom(stream);
		} catch (Exception e) {
			var log = LoggerFactory.getLogger(getClass());
			log.error("failed to read product index from " + file, e);
			return Proto.ProductIndex.getDefaultInstance();
		}
	}

	/**
	 * Returns the products of the library in matrix order. If this library has
	 * no product index or if this index is not in sync with the database, an
	 * empty option is returned.
	 */
	public Optional<TechIndex> syncProducts(IDatabase db) {
		var processes = descriptors(new ProcessDao(db));
		var products = descriptors(new FlowDao(db));
		TechIndex index = null;
		var proto = getProductIndex();
		int size = proto.getProductCount();
		for (int i = 0; i < size; i++) {
			var entry = proto.getProduct(i);
			var process = processes.get(entry.getProcess().getId());
			var product = products.get(entry.getProduct().getId());
			if (process == null || product == null)
				return Optional.empty();
			if (index == null) {
				index = new TechIndex(TechFlow.of(process, product));
			} else {
				index.add(TechFlow.of(process, product));
			}
		}
		return Optional.ofNullable(index);
	}

	/**
	 * Get the elementary flow index of this library.
	 */
	public Proto.ElemFlowIndex getElemFlowIndex() {
		var file = new File(folder, "index_B.bin");
		if (!file.exists())
			return Proto.ElemFlowIndex.getDefaultInstance();
		try (var stream = new FileInputStream(file)) {
			return Proto.ElemFlowIndex.parseFrom(stream);
		} catch (Exception e) {
			var log = LoggerFactory.getLogger(getClass());
			log.error("failed to read elem. flow index from " + file, e);
			return Proto.ElemFlowIndex.getDefaultInstance();
		}
	}

	/**
	 * Returns the elementary flows of the library in matrix order. If this
	 * information is not present or something went wrong while synchronizing
	 * the flow index with the database, an empty option is returned.
	 */
	public Optional<EnviIndex> syncElementaryFlows(IDatabase db) {
		var proto = getElemFlowIndex();
		int size = proto.getFlowCount();
		if (size == 0)
			return Optional.empty();

		var info = getInfo();
		var index = info.isRegionalized()
			? EnviIndex.createRegionalized()
			: EnviIndex.create();

		var flows = descriptors(new FlowDao(db));
		var locations = descriptors(new LocationDao(db));
		for (int i = 0; i < size; i++) {
			var entry = proto.getFlow(i);
			var flow = flows.get(entry.getFlow().getId());
			var location = locations.get(entry.getLocation().getId());
			if (flow == null)
				return Optional.empty();
			if (entry.getIsInput()) {
				index.add(EnviFlow.inputOf(flow, location));
			} else {
				index.add(EnviFlow.outputOf(flow, location));
			}
		}
		return Optional.of(index);
	}

	/**
	 * Get the impact category index of this library. Note that an
	 * empty index instead of `null` is returned if this information
	 * is not present in this library.
	 */
	public Proto.ImpactIndex getImpactIndex() {
		var file = new File(folder, "index_C.bin");
		if (!file.exists())
			return Proto.ImpactIndex.getDefaultInstance();
		try (var stream = new FileInputStream(file)) {
			return Proto.ImpactIndex.parseFrom(stream);
		} catch (Exception e) {
			var log = LoggerFactory.getLogger(getClass());
			log.error("failed to read impact index from " + file, e);
			return Proto.ImpactIndex.getDefaultInstance();
		}
	}

	/**
	 * Returns the impact categories of the library in matrix order. If this
	 * information is not present or something went wrong while synchronizing
	 * the impact index with the database, an empty option is returned.
	 */
	public Optional<ImpactIndex> syncImpacts(IDatabase db) {
		var proto = getImpactIndex();
		int size = proto.getImpactCount();
		if (size == 0)
			return Optional.empty();

		var index = new ImpactIndex();
		var impacts = descriptors(new ImpactCategoryDao(db));
		for (int i = 0; i < size; i++) {
			var entry = proto.getImpact(i);
			var impact = impacts.get(entry.getImpact().getId());
			if (impact == null)
				return Optional.empty();
			index.add(impact);
		}
		return Optional.of(index);
	}

	private <T extends CategorizedDescriptor> Map<String, T> descriptors(
		CategorizedEntityDao<?, T> dao) {
		return dao.getDescriptors()
			.stream()
			.collect(Collectors.toMap(
				d -> d.refId,
				d -> d,
				(d1, d2) -> d1));
	}

	public boolean hasMatrix(LibMatrix m) {
		return m.isPresentIn(this);
	}

	public Optional<MatrixReader> getMatrix(LibMatrix m) {
		return m.readFrom(this);
	}

	public Optional<double[]> getColumn(LibMatrix m, int column) {
		return m.readColumnFrom(this, column);
	}

	/**
	 * Get the diagonal of the given library matrix.
	 */
	public Optional<double[]> getDiagonal(LibMatrix m) {
		return m.readDiagonalFrom(this);
	}

	/**
	 * Creates a list of exchanges from the library matrices that describe the
	 * inputs and outputs of the given library product. The meta-data of the
	 * exchanges are synchronized with the given databases. Thus, this library
	 * needs to be mounted to the given database.
	 */
	public List<Exchange> getExchanges(TechFlow product, IDatabase db) {
		return Exchanges.join(this, db).getFor(product);
	}

	public List<ImpactFactor> getImpactFactors(
		ImpactDescriptor impact, IDatabase db) {
		return ImpactFactors.join(this, db).getFor(impact);
	}
}
