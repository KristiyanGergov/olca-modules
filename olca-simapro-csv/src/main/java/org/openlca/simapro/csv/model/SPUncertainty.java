package org.openlca.simapro.csv.model;

import org.openlca.simapro.csv.CsvUtils;
import org.openlca.simapro.csv.model.enums.DistributionParameter;
import org.openlca.simapro.csv.model.enums.DistributionType;

/**
 * Stores the parameters of an uncertainty distribution. In SimaPro, depending
 * on the distribution function, 3 parameters can be used to store this
 * information: <br>
 * <br>
 * parameter 1:
 * <ul>
 * <li>Normal: doubled standard deviation
 * <li>Lognormal: squared standard deviation
 * <li>Triangle: empty
 * <li>Uniform: empty
 * </ul>
 * <br>
 * parameter 2:
 * <ul>
 * <li>Normal: empty
 * <li>Lognormal: empty
 * <li>Triangle: minimum
 * <li>Uniform: minimum
 * </ul>
 * parameter 3:
 * <ul>
 * <li>Normal: empty
 * <li>Lognormal: empty
 * <li>Triangle: maximum
 * <li>Uniform: maximum
 * </ul>
 */
public class SPUncertainty {

	private DistributionType type;
	private Double param1;
	private Double param2;
	private Double param3;

	public static SPUncertainty normal(double doubledSD) {
		SPUncertainty distribution = new SPUncertainty();
		distribution.param1 = doubledSD;
		distribution.type = DistributionType.NORMAL;
		return distribution;
	}

	public static SPUncertainty logNormal(double squaredSD) {
		SPUncertainty distribution = new SPUncertainty();
		distribution.param1 = squaredSD;
		distribution.type = DistributionType.LOG_NORMAL;
		return distribution;
	}

	public static SPUncertainty uniform(double min, double max) {
		SPUncertainty distribution = new SPUncertainty();
		distribution.param2 = min;
		distribution.param3 = max;
		distribution.type = DistributionType.UNIFORM;
		return distribution;
	}

	public static SPUncertainty triangle(double min, double max) {
		SPUncertainty distribution = new SPUncertainty();
		distribution.param2 = min;
		distribution.param3 = max;
		distribution.type = DistributionType.TRIANGLE;
		return distribution;
	}

	public static SPUncertainty undefined() {
		SPUncertainty distribution = new SPUncertainty();
		distribution.type = DistributionType.UNDEFINED;
		return distribution;
	}

	public DistributionType getType() {
		return type;
	}

	public double getParameterValue(DistributionParameter param) {
		if (param == null)
			return 0;
		switch (param) {
		case DOUBLED_SD:
			return param1 == null ? 0 : param1;
		case MAXIMUM:
			return param3 == null ? 0 : param3;
		case MINIMUM:
			return param2 == null ? 0 : param2;
		case SQUARED_SD:
			return param1 == null ? 0 : param1;
		default:
			return 0;
		}
	}

	/**
	 * Reads the uncertainty distribution from the given CSV line starting at
	 * the given offset position.
	 */
	public static SPUncertainty fromCsv(String[] line, int offset) {
		String typeString = CsvUtils.get(line, offset);
		if (typeString == null || typeString.isEmpty())
			return SPUncertainty.undefined();
		DistributionType type = DistributionType.fromValue(typeString);
		if (type == null)
			return SPUncertainty.undefined();
		return fromCsv(line, offset, type);
	}

	private static SPUncertainty fromCsv(String[] line, int offset,
			DistributionType type) {
		SPUncertainty dist = new SPUncertainty();
		dist.type = type;
		switch (type) {
		case LOG_NORMAL:
		case NORMAL:
			Double sd = CsvUtils.getDouble(line, offset + 1);
			dist.param1 = sd != null ? sd : 0;
			break;
		case TRIANGLE:
		case UNIFORM:
			Double min = CsvUtils.getDouble(line, offset + 2);
			dist.param2 = min != null ? min : 0;
			Double max = CsvUtils.getDouble(line, offset + 3);
			dist.param3 = max != null ? max : 0;
		default:
			break;
		}
		return dist;
	}

	/**
	 * Writes the uncertainty distribution information to the given CSV line
	 * starting at the given offset index.
	 */
	public void toCsv(String[] line, int offset) {
		String typeString = type != null ? type.getValue()
				: DistributionType.UNDEFINED.getValue();
		String param1Str = param1 != null ? param1.toString() : "0";
		String param2Str = param2 != null ? param2.toString() : "0";
		String param3Str = param3 != null ? param3.toString() : "0";
		CsvUtils.set(typeString, line, offset);
		CsvUtils.set(param1Str, line, offset + 1);
		CsvUtils.set(param2Str, line, offset + 2);
		CsvUtils.set(param3Str, line, offset + 3);
	}

	public static void undefinedToCsv(String[] line, int offset) {
		CsvUtils.set(DistributionType.UNDEFINED.getValue(), line, offset);
		CsvUtils.set("0", line, offset + 1);
		CsvUtils.set("0", line, offset + 2);
		CsvUtils.set("0", line, offset + 3);
	}

}
