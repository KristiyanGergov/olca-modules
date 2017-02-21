@XmlSchema(namespace = "http://lca.jrc.it/ILCD/UnitGroup", elementFormDefault = XmlNsForm.QUALIFIED, xmlns = {
		@XmlNs(prefix = "", namespaceURI = "http://lca.jrc.it/ILCD/Process"),
		@XmlNs(prefix = "c", namespaceURI = "http://lca.jrc.it/ILCD/Contact"),
		@XmlNs(prefix = "s", namespaceURI = "http://lca.jrc.it/ILCD/Source"),
		@XmlNs(prefix = "f", namespaceURI = "http://lca.jrc.it/ILCD/Flow"),
		@XmlNs(prefix = "fp", namespaceURI = "http://lca.jrc.it/ILCD/FlowProperty"),
		@XmlNs(prefix = "u", namespaceURI = "http://lca.jrc.it/ILCD/UnitGroup"),
		@XmlNs(prefix = "m", namespaceURI = "http://lca.jrc.it/ILCD/LCIAMethod"),
		@XmlNs(prefix = "common", namespaceURI = "http://lca.jrc.it/ILCD/Common") })
package org.openlca.ilcd.units;

import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlSchema;