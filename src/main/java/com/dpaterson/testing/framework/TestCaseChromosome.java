package com.dpaterson.testing.framework;

import com.dpaterson.testing.framework.instrumentation.Instrumented;
import com.dpaterson.testing.junit.TestingUtils;
import com.dpaterson.testing.Properties;
import com.sheffield.instrumenter.InstrumentationProperties;
import com.sheffield.instrumenter.analysis.ClassAnalyzer;
import com.sheffield.instrumenter.analysis.task.AbstractTask;
import com.sheffield.instrumenter.analysis.task.Task;
import com.sheffield.instrumenter.analysis.task.TaskTimer;
import com.sheffield.instrumenter.instrumentation.objectrepresentation.Branch;
import com.sheffield.instrumenter.instrumentation.objectrepresentation.Line;
import com.sheffield.instrumenter.testcase.TestCaseWrapper;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class TestCaseChromosome extends Chromosome implements Instrumented {
    private static final long TIMEOUT = Properties.TIMEOUT;
    private static final TimeUnit UNIT = TimeUnit.MILLISECONDS;
    private Class<?> testClass;
    private Method testMethod;
    private int testSize;
    private Map<CUTChromosome, TestCaseExecutionData> executionData = new HashMap<CUTChromosome, TestCaseExecutionData>();
    private static JUnitCore core = new JUnitCore();
    private long executionTime;
    private List<Failure> failures = new ArrayList<>();
    private static int count = 0;
    private int id = ++count;
    private TestCaseWrapper testCase;
    private Result result;

    public int getId() {
        return id;
    }

    /**
     * Executes a single test method on the JUnitCore class, using default Runners and configuration. This method must reload the class from the class loader as it will have been instrumented since it
     * is first created. If the instrumented version is not loaded, code coverage goes a little bit funky.
     *
     * @throws ClassNotFoundException if the ClassLoader can't find the {@link #testClass} by name
     */
    public void run() throws InitializationError {
        long startTime = System.currentTimeMillis();
        // reload testclass from memory class loader to get the instrumented
        // version
        Task timerTask = new TestCaseExecutionTimer(testClass.getName(), testMethod.getName());
        if (InstrumentationProperties.LOG) {
            TaskTimer.taskStart(timerTask);
        }
        if(Properties.USE_TIMEOUT) {
            if (TestingUtils.isJUnit4Test(testMethod)) {
                FrameworkMethod m = new FrameworkMethod(testMethod);
                ParentRunner r = new BlockJUnit4ClassRunner(testClass) {
                    @Override
                    protected List<TestRule> getTestRules(Object target) {
                        List<TestRule> testRules = super.getTestRules(target);
                        Timeout.Builder builder = Timeout.builder().withTimeout(TIMEOUT, UNIT);
                        testRules.add(builder.build());
                        return testRules;
                    }
                };
                Description desc = Description.createTestDescription(testClass, m.getName());
                try {
                    r.filter(Filter.matchMethodDescription(desc));
                } catch (NoTestsRemainException e) {
                    e.printStackTrace(ClassAnalyzer.out);
                }
                RunNotifier notifier = new RunNotifier();
                notifier.addListener(new RunListener() {
                    @Override
                    public void testRunFinished(Result result) throws Exception {
                        setResult(result);
                    }
                });
                r.run(notifier);

            } else {
                Request req = Request.method(testClass, testMethod.getName());
                ExecutorService service = Executors.newSingleThreadExecutor();
                Future<Result> res = service.submit(new Callable<Result>() {
                    @Override
                    public Result call() {
                        return core.run(req);
                    }
                });
                try {
                    setResult(res.get(TIMEOUT, UNIT));
                } catch (TimeoutException e) {
                    ClassAnalyzer.out.println("Test " + testMethod.getName() + " timed out.");
                    return;
                } catch (InterruptedException e) {
                    e.printStackTrace(ClassAnalyzer.out);
                } catch (ExecutionException e) {
                    e.printStackTrace(ClassAnalyzer.out);
                }
            }
        } else {
            Request r = Request.method(testClass, testMethod.getName());
            Result res = core.run(r);
            setResult(res);
        }
        if (InstrumentationProperties.LOG) {
            TaskTimer.taskEnd(timerTask);
        }
        if (result.getFailureCount() > 0) {
            failures.addAll(result.getFailures());
        }
        executionTime = System.currentTimeMillis() - startTime;
    }

    private void setResult(Result result){
        this.result = result;
    }
    public boolean hasFailures() {
        return failures.size() > 0;
    }

    public List<Failure> getFailures() {
        return Collections.unmodifiableList(failures);
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public void setTestClass(Class<?> testClass) {
        this.testClass = testClass;
        this.testCase = new TestCaseWrapper(testClass, testMethod);
    }

    public Class<?> getTestClass() {
        return testClass;

    }

    public void setMethod(Method method) {
        this.testMethod = method;
    }

    public TestCaseWrapper getTestCase() {
        return testCase;
    }

    public Method getMethod() {
        return testMethod;
    }

    @Override
    public TestCaseChromosome mutate() {
        // do nothing, we shouldn't be changing test cases in TCP
        throw new UnsupportedOperationException("Test Cases cannot be mutated in TCP");
    }

    @Override
    public void crossover(Chromosome chr, int point1, int point2) {
        // do nothing, we shouldn't be changing test cases in TCP
        throw new UnsupportedOperationException("Test Cases cannot be crossed over in TCP");
    }

    public Map<CUTChromosome, Double> getBranchesCovered() {
        Map<CUTChromosome, Double> branches = new HashMap<CUTChromosome, Double>();
        executionData.entrySet().stream()
                .forEach(entry -> branches.put(entry.getKey(), entry.getValue().getBranchCoverage()));
        return branches;
    }

    public Map<CUTChromosome, Double> getLinesCovered() {
        Map<CUTChromosome, Double> lines = new HashMap<CUTChromosome, Double>();
        executionData.entrySet().stream().forEach(entry -> lines.put(entry.getKey(), entry.getValue().getLineCoverage()));
        return lines;
    }

    public Map<CUTChromosome, List<Line>> getLineNumbersCovered() {
        Map<CUTChromosome, List<Line>> linesCovered = new HashMap<CUTChromosome, List<Line>>();
        executionData.entrySet().stream()
                .forEach((entry) -> linesCovered.put(entry.getKey(), entry.getValue().getLinesCovered()));
        return linesCovered;
    }

    public double getLinesCovered(CUTChromosome c) {
        if (executionData.containsKey(c)) {
            return executionData.get(c).getLineCoverage();
        }
        return 0.0;
    }

    public double getBranchesCovered(CUTChromosome c) {
        if (executionData.containsKey(c)) {
            return executionData.get(c).getBranchCoverage();
        }
        return 0.0;
    }

    public List<Line> getAllLinesCovered(CUTChromosome c) {
        if (executionData.containsKey(c)) {
            return executionData.get(c).getLinesCovered();
        }
        return Collections.emptyList();
    }

    public List<Branch> getAllBranchesFullyCovered(CUTChromosome c) {
        if (executionData.containsKey(c)) {
            return executionData.get(c).getBranchesFullyCovered();
        }
        return Collections.emptyList();
    }

    public Map<CUTChromosome, List<Branch>> getAllBranchesFullyCovered() {
        Map<CUTChromosome, List<Branch>> branchesCovered = new HashMap<>();
        executionData.entrySet().stream()
                .forEach(entry -> branchesCovered.put(entry.getKey(), entry.getValue().getBranchesFullyCovered()));
        return branchesCovered;
    }

    public List<Branch> getAllBranchesPartiallyCovered(CUTChromosome c) {
        if (executionData.containsKey(c)) {
            return executionData.get(c).getBranchesPartiallyCovered();
        }
        return Collections.emptyList();
    }

    public Map<CUTChromosome, List<Branch>> getAllBranchesPartiallyCovered() {
        Map<CUTChromosome, List<Branch>> branchesCovered = new HashMap<>();
        executionData.entrySet().stream()
                .forEach(entry -> branchesCovered.put(entry.getKey(), entry.getValue().getBranchesPartiallyCovered()));
        return branchesCovered;
    }

    public double getSize() {
        return testSize;
    }

    public void setSize(int testSize) {
        this.testSize = testSize;
    }

    @Override
    public void instrumentationFinished() {
        List<Class<?>> changedClasses = ClassAnalyzer.getChangedClasses();
        for (Class<?> cl : changedClasses) {
            if (CUTChromosomeStore.get(cl.getName()) != null) {
                List<Line> lines = ClassAnalyzer.getCoverableLines(cl.getName());
                List<Branch> branches = ClassAnalyzer.getCoverableBranches(cl.getName());
                int totalLines = lines.size();
                int totalBranches = 2 * branches.size();
                lines = lines.stream().filter(line -> line.getHits() > 0).collect(Collectors.toList());
                List<Branch> fullyCovered = branches.stream()
                        .filter(branch -> branch.getTrueHits() > 0 && branch.getFalseHits() > 0).map(Branch::clone)
                        .collect(Collectors.toList());
                List<Branch> partiallyCovered = branches.stream()
                        .filter(branch -> !fullyCovered.contains(branch) && (branch.getTrueHits() > 0 || branch.getFalseHits() > 0))
                        .map(Branch::clone).collect(Collectors.toList());
                double lineCoverage = lines.size() / (double) totalLines;
                double branchCoverage = branches.stream().mapToDouble(branch -> {
                    if (branch.getTrueHits() == 0 && branch.getFalseHits() == 0) {
                        return 0;
                    } else if (branch.getTrueHits() > 0 && branch.getFalseHits() > 0) {
                        return 2;
                    } else {
                        return 1;
                    }
                }).sum() / totalBranches;
                executionData.put(CUTChromosomeStore.get(cl.getName()),
                        new TestCaseExecutionData(branchCoverage, lineCoverage, lines, fullyCovered, partiallyCovered));
            }
        }
    }

    @Override
    public TestCaseChromosome clone() {
        TestCaseChromosome clone = new TestCaseChromosome();
        clone.testMethod = testMethod;
        clone.testClass = testClass;
        clone.testSize = testSize;
        clone.executionData = new HashMap<>();
        executionData.forEach((cut, cov) -> clone.executionData.put(cut, cov.clone()));
        return clone;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TestCaseChromosome && testClass.equals(((TestCaseChromosome) other).testClass)
                && testMethod.equals(((TestCaseChromosome) other).testMethod);
    }

    @Override
    public String toString() {
        return testClass.getName() + "." + testMethod.getName();
    }

    @Override
    public int size() {
        return 1;
    }

    private static final class TestCaseExecutionTimer extends AbstractTask {
        private String testClass;
        private String testMethod;

        private TestCaseExecutionTimer(String testClass, String testMethod) {
            this.testClass = testClass;
            this.testMethod = testMethod;
        }

        @Override
        public String asString() {
            return "Executing " + testClass + "." + testMethod;
        }

    }
}
