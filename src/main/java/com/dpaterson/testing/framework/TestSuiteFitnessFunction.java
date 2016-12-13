package com.dpaterson.testing.framework;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import com.sheffield.instrumenter.instrumentation.objectrepresentation.Line;

public class TestSuiteFitnessFunction implements ToDoubleFunction<TestCaseChromosome> {

	private Map<CUTChromosome, List<Integer>> coverableLines = new HashMap<>();
	private int totalCoverableLines;

	public TestSuiteFitnessFunction(Map<CUTChromosome, List<Integer>> coverableLines) {
		this.coverableLines = coverableLines;
		totalCoverableLines = coverableLines.entrySet().stream().mapToInt(entry -> entry.getValue().size()).sum();
	}

	@Override
	public double applyAsDouble(TestCaseChromosome value) {
		Map<CUTChromosome, List<Line>> coveredLines = value.getLineNumbersCovered();
		int newlyCoveredLines = coveredLines.entrySet().stream().mapToInt(f -> {
			CUTChromosome cut = f.getKey();
			List<Line> cov = f.getValue();
			if (coverableLines.containsKey(cut)) {
				return cov.stream().filter(a -> coverableLines.get(cut).contains(a)).collect(Collectors.toList())
						.size();
			} else {
				return 0;
			}
		}).sum();
		coveredLines.forEach((cut, covered) -> {
			if (coverableLines.containsKey(cut)) {
				coverableLines.get(cut).removeAll(covered);
				// if there are no uncovered lines, remove the class
				if (coverableLines.get(cut).size() == 0) {
					coverableLines.remove(cut);
				}
			}
		});
		return (double) newlyCoveredLines / totalCoverableLines;
	}

}