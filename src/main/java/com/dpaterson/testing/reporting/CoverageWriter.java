package com.dpaterson.testing.reporting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.dpaterson.testing.algorithms.SearchAlgorithm;
import com.dpaterson.testing.framework.CUTChromosome;
import com.dpaterson.testing.framework.Chromosome;
import com.dpaterson.testing.framework.TestCaseChromosome;
import com.dpaterson.testing.framework.TestSuiteChromosome;
import com.dpaterson.testing.util.HashSetCollector;
import com.sheffield.instrumenter.instrumentation.objectrepresentation.Branch;
import com.sheffield.instrumenter.instrumentation.objectrepresentation.Line;

public class CoverageWriter extends CsvWriter {
  private Chromosome problem;
  private SearchAlgorithm algorithm;

  public CoverageWriter(Chromosome problem, SearchAlgorithm algorithm) {
    this.problem = problem;
    this.algorithm = algorithm;
  }

  @Override
  public String getDir() {
    return "coverage";
  }

  @Override
  public void prepareCsv() {
    String[] headers = new String[] { "Class", "LinesCovered", "LinesMissed", "PercentageLineCoverage",
        "Total Branches", "BranchesCovered", "BranchesMissed", "PercentageBranchCoverage" };
    setHeaders(headers);
    List<CUTChromosome> cuts = ((TestSuiteChromosome) problem).getSUT().getClassesUnderTest();
    List<TestCaseChromosome> testCases = algorithm.getCurrentOptimal().getTestCases();
    for (CUTChromosome cut : cuts) {
      if (!cut.getCUT().isInterface()) {
        Set<Line> linesCovered = testCases.stream().map(testCase -> testCase.getAllLinesCovered(cut))
            .collect(new HashSetCollector<>());
        Set<Line> linesMissed = cut.getCoverableLines().stream().filter(line -> !linesCovered.contains(line))
            .collect(HashSet::new, HashSet::add, HashSet::addAll);

        Set<Branch> branchesFullyCovered = new HashSet<>();
        HashMap<Branch, Boolean> branchesPartiallyCovered = new HashMap<>();
        for (TestCaseChromosome testCase : testCases) {
          for (Branch b : testCase.getAllBranchesFullyCovered(cut)) {
            branchesFullyCovered.add(b);
            branchesPartiallyCovered.remove(b);
          }
          for (Branch b : testCase.getAllBranchesPartiallyCovered(cut)) {
            if (!branchesFullyCovered.contains(b)) {
              if (!branchesPartiallyCovered.containsKey(b)) {
                branchesPartiallyCovered.put(b, b.getTrueHits() > 0);
              } else {
                if (branchesPartiallyCovered.get(b) && b.getFalseHits() > 0) {
                  branchesPartiallyCovered.remove(b);
                  branchesFullyCovered.add(b);
                } else if (!branchesPartiallyCovered.get(b) && b.getTrueHits() > 0) {
                  branchesPartiallyCovered.remove(b);
                  branchesFullyCovered.add(b);
                }
              }
            }
          }
        }
        Set<Branch> branchesMissed = cut.getCoverableBranches().stream()
            .filter(branch -> !branchesFullyCovered.contains(branch) && !branchesPartiallyCovered.containsKey(branch))
            .collect(HashSet::new, HashSet::add, HashSet::addAll);
        double percentageCoverage = (double) linesCovered.size() / cut.getTotalLines();
        double percentageBranch = (double) (2 * branchesFullyCovered.size() + branchesPartiallyCovered.size())
            / (cut.getTotalBranches());
        String[] csv = new String[] { cut.getCUT().getName(), Integer.toString(linesCovered.size()),
            Integer.toString(linesMissed.size()), Double.toString(percentageCoverage),
            Integer.toString(cut.getTotalBranches()),
            Double.toString((2 * branchesFullyCovered.size() + branchesPartiallyCovered.size())),
            Double.toString((2 * branchesMissed.size() + branchesPartiallyCovered.size())),
            Double.toString(percentageBranch) };
        addRow(csv);
      }
    }
  }

}