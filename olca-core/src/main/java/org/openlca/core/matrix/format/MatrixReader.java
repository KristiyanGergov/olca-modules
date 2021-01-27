package org.openlca.core.matrix.format;

public interface MatrixReader {

	/** Returns the number of rows of the matrix. */
	int rows();

	/** Returns the number of columns of the matrix. */
	int columns();

	/** Get the value of the given row and column. */
	double get(int row, int col);

	/** Get the row values of the given column. */
	double[] getColumn(int i);

	/** Get the column values of the given row. */
	double[] getRow(int i);

	/** Creates a copy of this matrix and returns it */
	MatrixReader copy();

	/**
	 * Iterates over the non-zero values in this matrix. There is no defined
	 * order in which the matrix entries are processed. Specifically sparse
	 * matrix layouts should overwrite this function with faster implementations.
	 */
	default void iterate(EntryFunction fn) {
		if (fn == null)
			return;
		for (int col = 0; col < columns(); col++) {
			for (int row = 0; row < rows(); row++) {
				double val = get(row, col);
				if (val != 0) {
					fn.value(row, col, val);
				}
			}
		}
	}

	/**
	 * Performs a matrix-vector multiplication with the given vector v. It uses
	 * the iterate function which can be fast for sparse matrices. For dense
	 * matrices it can be much faster to call into native code instead of using
	 * this method.
	 */
	default double[] multiply(double[] v) {
		double[] x = new double[rows()];
		iterate((row, col, val) -> x[row] += val * v[col]);
		return x;
	}

	/**
	 * Returns the diagonal of this matrix.
	 */
	default double[] diag() {
		var rows = rows();
		var cols = columns();
		var n = Math.min(rows, cols);
		var diag = new double[n];
		for (int i = 0; i < n; i++) {
			diag[i] = get(i, i);
		}
		return diag;
	}
}
