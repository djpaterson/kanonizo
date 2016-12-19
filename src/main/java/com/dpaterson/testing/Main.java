package com.dpaterson.testing;

import com.dpaterson.testing.algorithms.RandomSearchAlgorithm;
import com.dpaterson.testing.algorithms.SearchAlgorithm;
import com.dpaterson.testing.algorithms.heuristics.*;
import com.dpaterson.testing.algorithms.metaheuristics.EpistaticGeneticAlgorithm;
import com.dpaterson.testing.algorithms.metaheuristics.GeneticAlgorithm;
import com.dpaterson.testing.algorithms.metaheuristics.HillClimbAlgorithm;
import com.dpaterson.testing.algorithms.metaheuristics.HypervolumeGeneticAlgorithm;
import com.dpaterson.testing.algorithms.stoppingconditions.FitnessStoppingCondition;
import com.dpaterson.testing.algorithms.stoppingconditions.IterationsStoppingCondition;
import com.dpaterson.testing.algorithms.stoppingconditions.StagnationStoppingCondition;
import com.dpaterson.testing.algorithms.stoppingconditions.TimeStoppingCondition;
import com.dpaterson.testing.framework.SUTChromosome;
import com.dpaterson.testing.framework.TestCaseChromosome;
import com.dpaterson.testing.framework.TestSuiteChromosome;
import com.dpaterson.testing.framework.instrumentation.Instrumenter;
import com.dpaterson.testing.util.Util;
import com.sheffield.instrumenter.PropertySource;
import com.sheffield.instrumenter.analysis.ClassAnalyzer;
import com.sheffield.instrumenter.instrumentation.ClassReplacementTransformer;
import com.sheffield.instrumenter.instrumentation.InstrumentingClassLoader;
import com.sheffield.instrumenter.mutation.MutationProperties;
import com.sheffield.util.ArrayUtils;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static final Logger logger = LogManager.getLogger(Main.class);
    private static final String[] forbiddenPackages = new String[] { "com/dpaterson", "org/junit",
            "org/apache/commons/cli", "junit", "org/apache/bcel", "org/apache/logging/log4j", "org/objectweb/asm",
            "javax/swing", "javax/servlet", "org/xml" };

    public static void main(String[] args) {
        // org.evosuite.Properties.TT = true;
        ClassAnalyzer.setOut(System.out);
        Arrays.asList(forbiddenPackages).stream().forEach(s -> ClassReplacementTransformer.addForbiddenPackage(s));
        Options options = TestSuitePrioritisation.getCommandLineOptions();
        Framework fw = new Framework();
        try {
            CommandLine line = new DefaultParser().parse(options, args, false);
            if (TestSuitePrioritisation.hasHelpOption(line)) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("Search Algorithms", options);
                return;
            }
            List<PropertySource> sources = ArrayUtils.createList(Properties.instance(),
                    com.sheffield.instrumenter.InstrumentationProperties.instance(), MutationProperties.instance());
            TestSuitePrioritisation.handleProperties(line, sources);
            if (MutationProperties.VISIT_MUTANTS) {
                InstrumentingClassLoader.getInstance().setVisitMutants(true);
            }
            setupFramework(line, fw);
            fw.run();
        } catch (final ParseException | ClassNotFoundException e) {
            logger.error(e);
        }
        // necessary due to random thread creation during test cases (don't do
        // this ever again)
        System.exit(0);
    }

    /**
     * Takes options from the {@link CommandLine} and creates a {@link TestSuiteChromosome} instance containing all of the test cases and a {@link SUTChromosome} object containing all of the source
     * classes. The command line must contain a -s/--sourceFolder option for the source classes and a -t/--testFolder option for the test cases. Both of these folders will be added to the classpath,
     * and all nested .class files will be loaded in as either source or test cases ready for instrumentation. Currently, instrumentation and JUnit execution takes place in the constructor of a
     * {@link TestSuiteChromosome}, but this may well be changed as fitness functions are introduced that are not reliant on code coverage.
     *
     * @param line
     *            - the {@link CommandLine} instance which must contain -s and -t options for source and test folders respectively
     * @return a {@link TestSuiteChromosome} object containing a {@link SUTChromosome} with all of the source classes and a list of {@link TestCaseChromosome} objects for all of the test cases
     *         contained within the specified location
     */
    public static void setupFramework(CommandLine line, Framework fw) throws MissingOptionException {
        Instrumenter.getNullOut();
        File file;
        String folder;
        String[] libFolders;
        if (!line.hasOption("s") || !line.hasOption("t")) {
            throw new MissingOptionException(
                    "In order to run the test suite prioritisation, a source folder must be given using -s or --sourceFolder and a test folder must be given using -t or --testFolder");
        }
        if (line.hasOption("l")) {
            // lib folder
            libFolders = line.getOptionValues("l");
            for (String libFolder : libFolders) {
                file = Util.getFile(libFolder);
                fw.addLibFolder(file);
            }
        }
        if (!MutationProperties.MAJOR_ROOT.equals("")) {
            File majorJar = Util.getFile(MutationProperties.MAJOR_ROOT + "/config/config.jar");
            Util.addToClassPath(majorJar);
        }
        folder = line.getOptionValue("s");
        // relative path
        file = Util.getFile(folder);
        fw.setSourceFolder(file);
        // test folder
        folder = line.getOptionValue("t");
        // relative path
        file = Util.getFile(folder);
        fw.setTestFolder(file);
        String algorithmChoice = line.hasOption("a") ? line.getOptionValue("a") : "";
        fw.setAlgorithm(getAlgorithm(algorithmChoice));
    }

    private static SearchAlgorithm getAlgorithm(String algorithmChoice) {
        SearchAlgorithm algorithm = new GreedyAlgorithm();
        switch (algorithmChoice.toLowerCase()) {
            case "greedy":
                break;
            case "additionalgreedy":
                algorithm = new AdditionalGreedyAlgorithm();
                break;
            case "koptimal":
                algorithm = new KOptimalAlgorithm();
                break;
            case "irreplaceability":
                algorithm = new IrreplaceabilityAlgorithm();
                break;
            case "eirreplaceability":
                algorithm = new EIrreplaceabilityAlgorithm();
                break;
            case "hillclimb":
                algorithm = new HillClimbAlgorithm();
                break;
            case "geneticalgorithm":
                algorithm = new GeneticAlgorithm();
                algorithm.addStoppingCondition(new FitnessStoppingCondition());
                break;
            case "hypervolumega":
                algorithm = new HypervolumeGeneticAlgorithm();
                break;
            case "epistaticga":
                algorithm = new EpistaticGeneticAlgorithm();
                break;
            case "randomsearch":
                algorithm = new RandomSearchAlgorithm();
                break;
            case "feptotal":
                algorithm = new TotalFEPAlgorithm();
                break;
            case "random":
                algorithm = new RandomAlgorithm();
                break;
            default:
                break;
        }
        if (Properties.USE_TIME) {
            algorithm.addStoppingCondition(new TimeStoppingCondition());
        }
        if (Properties.USE_ITERATIONS) {
            algorithm.addStoppingCondition(new IterationsStoppingCondition());
        }
        if (Properties.USE_STAGNATION) {
            algorithm.addStoppingCondition(new StagnationStoppingCondition());
        }
        return algorithm;
    }

}
