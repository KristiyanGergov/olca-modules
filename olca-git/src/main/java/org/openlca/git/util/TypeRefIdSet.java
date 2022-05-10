package org.openlca.git.util;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

import org.openlca.core.model.ModelType;
import org.openlca.git.model.Reference;

public class TypeRefIdSet {

	private final EnumMap<ModelType, Set<String>> map = new EnumMap<>(ModelType.class);

	public TypeRefIdSet() {
	}
	
	public TypeRefIdSet(Collection<Reference> refs) {
		refs.forEach(r -> add(r.type, r.refId));
	}
	
	public void add(ModelType type, String refId) {
		map.computeIfAbsent(type, t -> new HashSet<>()).add(refId);
	}

	public boolean contains(ModelType type, String refId) {
		var refIds = map.get(type);
		if (refIds == null)
			return false;
		return refIds.contains(refId);
	}
	
	public void remove(ModelType type, String refId) {
		var refIds = map.get(type);
		if (refIds == null)
			return;
		refIds.remove(refId);		
	}

	public void clear() {
		map.clear();
	}

	public void forEach(BiConsumer<ModelType, String> forEach) {
		map.keySet().forEach(type -> {
			var refIds = map.get(type);
			if (refIds == null)
				return;
			refIds.forEach(refId -> forEach.accept(type, refId));
		});
	}

	public Set<String> get(ModelType type) {
		var refIds = map.get(type);
		if (refIds == null)
			return new HashSet<>();
		return refIds;
	}

	public Set<String> refIds() {
		var refIds = new HashSet<String>();
		map.values().forEach(set -> refIds.addAll(set));
		return refIds;
	}
	
	public Set<ModelType> types() {
		return new HashSet<>(map.keySet());
	}
	
	public ModelType getTypeFor(String refId) {
		for (var type : map.keySet())
			if (map.get(type) != null && map.get(type).contains(refId))
				return type;
		return null;
	}

	public interface ForEach {

		void apply(ModelType type, String refId);

	}

}
