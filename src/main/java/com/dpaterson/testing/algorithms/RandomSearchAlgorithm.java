package com.dpaterson.testing.algorithms;

import java.util.ArrayList;
import java.util.List;

import com.dpaterson.testing.Properties;
import com.dpaterson.testing.commandline.ProgressBar;
import com.dpaterson.testing.framework.TestCaseChromosome;
import com.dpaterson.testing.framework.TestSuiteChromosome;
import com.dpaterson.testing.reporting.FitnessWriter;
import com.dpaterson.testing.util.RandomInstance;
import com.sheffield.instrumenter.analysis.ClassAnalyzer;

public class RandomSearchAlgorithm extends AbstractSearchAlgorithm {
    static {
        Properties.POPULATION_SIZE = 1;
    }

    private FitnessWriter writer = new FitnessWriter(this);

    @Override
    public void generateSolution() {

        List<TestCaseChromosome> testCases = problem.getTestCases();
        ProgressBar bar = new ProgressBar(ClassAnalyzer.out);
        bar.setTitle("Running Random Search");
        while (!shouldFinish()) {
            age++;
            TestSuiteChromosome clone = getCurrentOptimal().clone();
            List<TestCaseChromosome> randomOrdering = generateRandomOrder(testCases);
            clone.setTestCases(randomOrdering);
            fitnessEvaluations++;
            if (clone.fitter(getCurrentOptimal()).equals(clone)) {
                setCurrentOptimal(clone);
            }
            if (Properties.TRACK_GENERATION_FITNESS) {
                writer.addRow(age, getCurrentOptimal().getFitness());
            } else {
                writer.addRow(age, clone.getFitness());
            }
            bar.reportProgress(Math.min((double) System.currentTimeMillis() - startTime, Properties.MAX_EXECUTION_TIME),
                    Properties.MAX_EXECUTION_TIME);
        }
        bar.complete();
        writer.write();
    }

    private List<TestCaseChromosome> generateRandomOrder(List<TestCaseChromosome> testCases) {
        List<TestCaseChromosome> unorderedCases = new ArrayList<TestCaseChromosome>(testCases);
        List<TestCaseChromosome> orderedCases = new ArrayList<TestCaseChromosome>();
        while (unorderedCases.size() > 0) {
            int index = RandomInstance.nextInt(unorderedCases.size());
            TestCaseChromosome chr = unorderedCases.get(index);
            orderedCases.add(chr);
            unorderedCases.remove(chr);
        }
        return orderedCases;
    }

}