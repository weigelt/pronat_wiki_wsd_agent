package edu.kit.ipd.parse.wikiWSD;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.ipd.parse.graphBuilder.GraphBuilder;
import edu.kit.ipd.parse.luna.data.MissingDataException;
import edu.kit.ipd.parse.luna.data.PrePipelineData;
import edu.kit.ipd.parse.luna.graph.IGraph;
import edu.kit.ipd.parse.luna.graph.INode;
import edu.kit.ipd.parse.luna.pipeline.PipelineStageException;
import edu.kit.ipd.parse.luna.tools.ConfigManager;
import edu.kit.ipd.parse.luna.tools.StringToHypothesis;
import edu.kit.ipd.parse.ner.NERTagger;
import edu.kit.ipd.parse.shallownlp.ShallowNLP;
import edu.kit.ipd.parse.wikiWSD.util.TestHelper;
import edu.kit.ipd.parse.wikiWSD.util.Text;

/**
 * @author Jan Keim
 *
 */
@Ignore
public class WordSenseDisambiguationTest {
    private static final String WSD_ATTRIBUTE = "wsd";

    private static final Logger logger = LoggerFactory.getLogger(WordSenseDisambiguationTest.class);

    private static WordSenseDisambiguation wsd;

    private HashMap<String, Text> texts;
    private PrePipelineData ppd;
    private ShallowNLP snlp;
    private GraphBuilder graphBuilder;
    private NERTagger ner;

    @BeforeClass
    public static void beforeClass() {
        logger.info("Loading WSD Classifier");
        WordSenseDisambiguationTest.wsd = new WordSenseDisambiguation();
        WordSenseDisambiguationTest.wsd.init();
    }

    @Before
    public void setUp() {
        graphBuilder = new GraphBuilder();
        graphBuilder.init();
        snlp = new ShallowNLP();
        snlp.init();
        ner = new NERTagger();
        ner.init();

        texts = TestHelper.texts;
    }

    private void executePrepipeline(PrePipelineData ppd) {
        try {
            synchronized (snlp) {
                snlp.exec(ppd);
            }
            synchronized (ner) {
                ner.exec(ppd);
            }
            synchronized (graphBuilder) {
                graphBuilder.exec(ppd);
            }
        } catch (PipelineStageException e) {
            e.printStackTrace();
        }
    }

    @Ignore
    @Test
    public void testConfig() {
        Properties props = ConfigManager.getConfiguration(WordSenseDisambiguationTest.wsd.getClass());
        String isZip = props.getProperty("ISZIP");
        logger.info("Property: ISZIP=" + isZip);
        String classifierStr = props.getProperty("CLASSIFIER");
        logger.info("Property: CLASSIFIER=" + classifierStr);
        Assert.assertNotNull(isZip);
        Assert.assertNotNull(classifierStr);

        logger.info(WordSenseDisambiguationTest.wsd.toString());
    }

    @Test
    public void testAccuracy() {
        AtomicInteger correct = new AtomicInteger(0);
        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger nullVals = new AtomicInteger(0);
        int progress = 0;
        Set<String> textsStrings = texts.keySet();
        for (String id : textsStrings) {
            progress++;
            PrePipelineData ppdL = new PrePipelineData();
            Text text = texts.get(id);
            String input = text.getText()
                               .replace("\n", " ");
            List<String[]> expectedAnnotations = text.getAnnotations();
            ppdL.setMainHypothesis(StringToHypothesis.stringToMainHypothesis(input));
            executePrepipeline(ppdL);
            try {
                IGraph graph = ppdL.getGraph();
                List<INode> nodes = graph.getNodes();
                WordSenseDisambiguationTest.wsd.setGraph(graph);
                WordSenseDisambiguationTest.wsd.exec();

                for (String[] wsdAnnotation : expectedAnnotations) {
                    // position, word, meaning
                    int position = Integer.parseInt(wsdAnnotation[0]);
                    INode node = nodes.get(position);
                    String expectedWsd = wsdAnnotation[2];
                    String classifiedWsd = (String) node.getAttributeValue(WSD_ATTRIBUTE);
                    // if correctly classified or the synonym
                    if (expectedWsd.equals(classifiedWsd) || wsdAnnotation[3].equals(classifiedWsd)) {
                        correct.incrementAndGet();
                    } else if ((classifiedWsd == null) || classifiedWsd.equals("null")) {
                        nullVals.incrementAndGet();
                    }
                    counter.incrementAndGet();
                }
            } catch (MissingDataException | IndexOutOfBoundsException e) {
                logger.warn("EXCEPTION at sentence " + id);
                e.printStackTrace();
            }
            if (logger.isInfoEnabled()) {
                DecimalFormat df = new DecimalFormat("##.##%");
                double currProgress = (double) progress / (double) textsStrings.size();
                double currentAccuracy = ((double) correct.get() / (double) counter.get());
                logger.info("Evaluation at " + df.format(currProgress) + " with an accuracy of currently "
                        + currentAccuracy);
            }
        }
        double evalResult = (double) correct.get() / (double) counter.get();
        logger.info(String.format("Evaluation Result: %d/%d = %.4f", correct.get(), counter.get(), evalResult));
        logger.info("Null values: " + nullVals.get());
    }

    private void testOneText(String id) {
        logger.debug(id); // TODO remove
        ppd = new PrePipelineData();
        Text text = texts.get(id);
        String input = text.getText();
        input = input.replace("\n", " "); // TODO improve!
        List<String[]> expectedAnnotations = text.getAnnotations();
        ppd.setMainHypothesis(StringToHypothesis.stringToMainHypothesis(input));
        executePrepipeline(ppd);
        boolean allCorrect = true;
        try {
            IGraph graph = ppd.getGraph();
            List<INode> nodes = graph.getNodes();
            WordSenseDisambiguationTest.wsd.setGraph(graph);
            WordSenseDisambiguationTest.wsd.exec();

            for (String[] wsdAnnotation : expectedAnnotations) {
                // position, word, meaning
                int position = Integer.parseInt(wsdAnnotation[0]);
                INode node = nodes.get(position);
                String expectedWsd = wsdAnnotation[2];
                String classifiedWsd = (String) node.getAttributeValue(WSD_ATTRIBUTE);
                if (!expectedWsd.equals(classifiedWsd) && !wsdAnnotation[3].equals(classifiedWsd)) {
                    // wrong wsd classification
                    allCorrect = false;
                    logger.info(id + ":");
                    logger.info(input);
                    logger.info("Position: " + position + " - Expected '" + expectedWsd + "' but got '" + classifiedWsd
                            + "'");
                    if (classifiedWsd == null) {
                        System.out.println(node.toString());
                        System.out.println(ppd.getTaggedHypothesis(0)
                                              .toString());
                    }
                }
            }
            if (allCorrect) {
                logger.info(id + ": \u2713");
            }
        } catch (MissingDataException e) {
            e.printStackTrace();
        }
    }

    @Ignore
    @Test
    public void testJkZeroOne() {
        testOneText("jk0.1");
    }

    @Ignore
    @Test
    public void testOneOne() {
        testOneText("1.1");
    }

    @Ignore
    @Test
    public void testOneTwo() {
        testOneText("1.2");
    }

    @Ignore
    @Test
    public void testOneThree() {
        testOneText("1.3");
    }

    @Ignore
    @Test
    public void testTwoOne() {
        testOneText("2.1");
    }

    @Ignore
    @Test
    public void testTwoTwo() {
        testOneText("2.2");
    }

    @Ignore
    @Test
    public void testTwoThree() {
        testOneText("2.3");
    }

    @Ignore
    @Test
    public void testThreeOne() {
        testOneText("3.1");
    }

    @Ignore
    @Test
    public void testThreeTwo() {
        testOneText("3.2");
    }

    @Ignore
    @Test
    public void testThreeThree() {
        testOneText("3.3");
    }

    @Ignore
    @Test
    public void testFourOne() {
        testOneText("4.1");
    }

    @Ignore
    @Test
    public void testFourTwo() {
        testOneText("4.2");
    }

    @Ignore
    @Test
    public void testFourThree() {
        testOneText("4.3");
    }

    @Ignore
    @Test
    public void testFiveOne() {
        testOneText("5.1");
    }

    @Ignore
    @Test
    public void testFiveTwo() {
        testOneText("5.2");
    }

    @Ignore
    @Test
    public void testFiveThree() {
        testOneText("5.3");
        // Position: 4 - Expected 'fridge' but got 'null'
        // Explanation: 'fridge' gets classified as VB
    }

    @Ignore
    @Test
    public void testSixOne() {
        testOneText("6.1");
    }

    @Ignore
    @Test
    public void testSixTwo() {
        testOneText("6.2");
    }

    @Ignore
    @Test
    public void testSixThree() {
        testOneText("6.3");
    }

    @Ignore
    @Test
    public void testSevenOne() {
        testOneText("7.1");
    }

    @Ignore
    @Test
    public void testSevenTwo() {
        testOneText("7.2");
    }

    @Ignore
    @Test
    public void testSevenThree() {
        testOneText("7.3");
    }

    @Ignore
    @Test
    public void testEightOne() {
        testOneText("8.1");
    }

    @Ignore
    @Test
    public void testEightTwo() {
        testOneText("8.2");
    }

    @Ignore
    @Test
    public void testEightThree() {
        testOneText("8.3");
    }

    @Ignore
    @Test
    public void testNineOne() {
        testOneText("9.1");
    }

    @Ignore
    @Test
    public void testNineTwo() {
        testOneText("9.2");
        // TODO Position: 23 - Expected 'shelf' but got 'continental shelf'
    }

    @Ignore
    @Test
    public void testNineThree() {
        testOneText("9.3");
    }

    @Ignore
    @Test
    public void testTenOne() {
        testOneText("10.1");
    }

    @Ignore
    @Test
    public void testTenTwo() {
        testOneText("10.2");
    }

    @Ignore
    @Test
    public void testTenThree() {
        testOneText("10.3");
    }

    @Ignore
    @Test
    public void testElevenOne() {
        testOneText("11.1");
    }

    @Ignore
    @Test
    public void testElevenTwo() {
        testOneText("11.2");
    }

    @Ignore
    @Test
    public void testElevenThree() {
        testOneText("11.3");
    }

    @Ignore
    @Test
    public void testTwelveOne() {
        testOneText("12.1");
    }

    @Ignore
    @Test
    public void testTwelveTwo() {
        testOneText("12.2");
    }

    @Ignore
    @Test
    public void testTwelveThree() {
        testOneText("12.3");
        // Position: 12 - Expected 'orange (fruit)' but got 'null'
        // Explanation: 'orange (fruit)' is classified as 'JJ'
    }

    @Ignore
    @Test
    public void testThirteenOne() {
        testOneText("13.1");
        // TODO Position: 5 - Expected 'front' but got 'front (military)'
    }

    @Ignore
    @Test
    public void testThirteenTwo() {
        testOneText("13.2");
    }

    @Ignore
    @Test
    public void testThirteenThree() {
        testOneText("13.3");
    }

    @Ignore
    @Test
    public void testFourteenOne() {
        testOneText("14.1");
    }

    @Ignore
    @Test
    public void testFourteenTwo() {
        testOneText("14.2");
    }

    @Ignore
    @Test
    public void testFourteenThree() {
        testOneText("14.3");
    }

    @Ignore
    @Test
    public void testFifteenOne() {
        testOneText("15.1");
    }

    @Ignore
    @Test
    public void testFifteenTwo() {
        testOneText("15.2");
    }

    @Ignore
    @Test
    public void testFifteenThree() {
        testOneText("15.3");
    }

    @Ignore
    @Test
    public void testSixteenOne() {
        testOneText("16.1");
    }

    @Ignore
    @Test
    public void testSixteenTwo() {
        testOneText("16.2");
    }

    @Ignore
    @Test
    public void testSixteenThree() {
        testOneText("16.3");
    }

    @Ignore
    @Test
    public void testSeventeenOne() {
        testOneText("17.1");
    }

    @Ignore
    @Test
    public void testSeventeenTwo() {
        testOneText("17.2");
    }

    @Ignore
    @Test
    public void testSeventeenThree() {
        testOneText("17.3");
    }

    @Ignore
    @Test
    public void testEighteenOne() {
        testOneText("18.1");
    }

    @Ignore
    @Test
    public void testEighteenTwo() {
        testOneText("18.2");
    }

    @Ignore
    @Test
    public void testEighteenThree() {
        testOneText("18.3");
    }

    @Ignore
    @Test
    public void testNineteenOne() {
        testOneText("19.1");
        // TODO Position: 11 - Expected 'front' but got 'front (military)'
    }

    @Ignore
    @Test
    public void testNineteenTwo() {
        testOneText("19.2");
    }

    @Ignore
    @Test
    public void testNineteenThree() {
        testOneText("19.3");
    }

    @Ignore
    @Test
    public void testTwentyOne() {
        testOneText("20.1");
    }

    @Ignore
    @Test
    public void testTwentyTwo() {
        testOneText("20.2");
    }

    @Ignore
    @Test
    public void testTwentyThree() {
        testOneText("20.3");
        // TODO Position: 8 - Expected 'orange (fruit)' but got 'null'
    }

    @Ignore
    @Test
    public void testTwentyoneOne() {
        testOneText("21.1");
    }

    @Ignore
    @Test
    public void testTwentyoneTwo() {
        testOneText("21.2");
    }

    @Ignore
    @Test
    public void testTwentyoneThreeA() {
        testOneText("21.3a");
    }

    @Ignore
    @Test
    public void testTwentyoneThreeB() {
        testOneText("21.3b");
        // TODO Position: 22 - Expected 'front' but got 'front (military)'
    }

    @Ignore
    @Test
    public void testTwentytwoOne() {
        testOneText("22.1");
    }

    @Ignore
    @Test
    public void testTwentytwoTwo() {
        testOneText("22.2");
    }

    @Ignore
    @Test
    public void testTwentytwoThree() {
        testOneText("22.3");
        // TODO Position: 22 - Expected 'front' but got 'front (military)'
        // TODO Position: 29 - Expected 'fridge' but got 'null'
    }

    @Ignore
    @Test
    public void testIfFourOne() {
        testOneText("if.4.1");
    }

    @Ignore
    @Test
    public void testIfFourTwo() {
        testOneText("if.4.2");
    }

    @Ignore
    @Test
    public void testIfFourThree() {
        testOneText("if.4.3");
    }

    @Ignore
    @Test
    public void testIfFourFour() {
        testOneText("if.4.4");
    }

    @Ignore
    @Test
    public void testIfFourFive() {
        testOneText("if.4.5");
    }

    @Ignore
    @Test
    public void testIfFourSix() {
        testOneText("if.4.6");
    }

    @Ignore
    @Test
    public void testIfFourSeven() {
        testOneText("if.4.7");
    }

    @Ignore
    @Test
    public void testIfFourEight() {
        testOneText("if.4.8");
    }

    @Ignore
    @Test
    public void testIfFourNine() {
        testOneText("if.4.9");
    }

    @Ignore
    @Test
    public void testIfFourTen() {
        testOneText("if.4.10");
    }

    @Ignore
    @Test
    public void testIfFourEleven() {
        testOneText("if.4.11");
    }

    @Ignore
    @Test
    public void testIfFourTwelve() {
        testOneText("if.4.12");
    }

    @Ignore
    @Test
    public void testIfFourThirteen() {
        testOneText("if.4.13");
    }

    @Ignore
    @Test
    public void testIfFourFourteen() {
        testOneText("if.4.14");
    }

    @Ignore
    @Test
    public void testIfFourFifteen() {
        testOneText("if.4.15");
    }

    @Ignore
    @Test
    public void testIfFourSixteen() {
        testOneText("if.4.16");
    }

    @Ignore
    @Test
    public void testIfFourSeventeen() {
        testOneText("if.4.17");
    }

    @Ignore
    @Test
    public void testIfFourEighteen() {
        testOneText("if.4.18");
    }

    @Ignore
    @Test
    public void testIfFourNineteen() {
        testOneText("if.4.19");
    }

    @Ignore
    @Test
    public void testIfFiveOne() {
        testOneText("if.5.1");
    }

    @Ignore
    @Test
    public void testIfFiveTwo() {
        testOneText("if.5.2");
    }

    @Ignore
    @Test
    public void testIfFiveThree() {
        testOneText("if.5.3");
    }

    @Ignore
    @Test
    public void testIfFiveFour() {
        testOneText("if.5.4");
    }

    @Ignore
    @Test
    public void testIfFiveFive() {
        testOneText("if.5.5");
    }

    @Ignore
    @Test
    public void testIfFiveSix() {
        testOneText("if.5.6");
    }

    @Ignore
    @Test
    public void testIfFiveSeven() {
        testOneText("if.5.7");
    }

    @Ignore
    @Test
    public void testIfFiveEight() {
        testOneText("if.5.8");
    }

    @Ignore
    @Test
    public void testIfFiveNine() {
        testOneText("if.5.9");
    }

    @Ignore
    @Test
    public void testIfFiveTen() {
        testOneText("if.5.10");
    }

    @Ignore
    @Test
    public void testIfFiveEleven() {
        testOneText("if.5.11");
    }

    @Ignore
    @Test
    public void testIfFiveTwelve() {
        testOneText("if.5.12");
    }

    @Ignore
    @Test
    public void testIfFiveThirteen() {
        testOneText("if.5.13");
    }

    @Ignore
    @Test
    public void testIfFiveFourteen() {
        testOneText("if.5.14");
    }

    @Ignore
    @Test
    public void testIfFiveFifteen() {
        testOneText("if.5.15");
    }

    @Ignore
    @Test
    public void testIfFiveSixteen() {
        testOneText("if.5.16");
    }

    @Ignore
    @Test
    public void testIfFiveSeventeen() {
        testOneText("if.5.17");
    }

    @Ignore
    @Test
    public void testIfFiveEighteen() {
        testOneText("if.5.18");
    }

    @Ignore
    @Test
    public void testIfFiveNineteen() {
        testOneText("if.5.19");
    }

    @Ignore
    @Test
    public void testTwentythreeOne() {
        testOneText("23.1");
    }

    @Ignore
    @Test
    public void testTwentythreeTwo() {
        testOneText("23.2");
    }

    @Ignore
    @Test
    public void testTwentythreeThree() {
        testOneText("23.3");
    }

    @Ignore
    @Test
    public void testTwentyfourOne() {
        testOneText("24.1");
    }

    @Ignore
    @Test
    public void testTwentyfourTwo() {
        testOneText("24.2");
    }

    @Ignore
    @Test
    public void testTwentyfourThree() {
        testOneText("24.3");
    }

    @Ignore
    @Test
    public void testTwentyfiveOne() {
        testOneText("25.1");
    }

    @Ignore
    @Test
    public void testTwentyfiveTwo() {
        testOneText("25.2");
    }

    @Ignore
    @Test
    public void testTwentyfiveThree() {
        testOneText("25.3");
    }

    @Ignore
    @Test
    public void testTwentysixOne() {
        testOneText("26.1");
    }

    @Ignore
    @Test
    public void testTwentysixTwo() {
        testOneText("26.2");
    }

    @Ignore
    @Test
    public void testTwentysixThree() {
        testOneText("26.3");
    }

    @Ignore
    @Test
    public void testTwentysevenOne() {
        testOneText("27.1");
    }

    @Ignore
    @Test
    public void testTwentysevenTwo() {
        testOneText("27.2");
    }

    @Ignore
    @Test
    public void testTwentysevenThree() {
        testOneText("27.3");
    }

    @Ignore
    @Test
    public void testTwentyeightOne() {
        testOneText("28.1");
    }

    @Ignore
    @Test
    public void testTwentyeightTwo() {
        testOneText("28.2");
    }

    @Ignore
    @Test
    public void testTwentyeightThree() {
        testOneText("28.3");
    }

    @Ignore
    @Test
    public void testTwentynineOne() {
        testOneText("29.1");
    }

    @Ignore
    @Test
    public void testTwentynineTwo() {
        testOneText("29.2");
    }

    @Ignore
    @Test
    public void testTwentynineThree() {
        testOneText("29.3");
    }

    @Ignore
    @Test
    public void testThirtyOne() {
        testOneText("30.1");
    }

    @Ignore
    @Test
    public void testThirtyTwo() {
        testOneText("30.2");
    }

    @Ignore
    @Test
    public void testThirtyThree() {
        testOneText("30.3");
    }

    @Ignore
    @Test
    public void testThirtyoneOne() {
        testOneText("31.1");
    }

    @Ignore
    @Test
    public void testThirtyoneTwo() {
        testOneText("31.2");
    }

    @Ignore
    @Test
    public void testThirtyoneThree() {
        testOneText("31.3");
    }

    @Ignore
    @Test
    public void testThirtytwoOne() {
        testOneText("32.1");
    }

    @Ignore
    @Test
    public void testThirtytwoTwo() {
        testOneText("32.2");
    }

    @Ignore
    @Test
    public void testThirtytwoThree() {
        testOneText("32.3");
    }

    @Ignore
    @Test
    public void testThirtythreeOne() {
        testOneText("33.1");
    }

    @Ignore
    @Test
    public void testThirtythreeTwo() {
        testOneText("33.2");
    }

    @Ignore
    @Test
    public void testThirtythreeThree() {
        testOneText("33.3");
    }

    @Ignore
    @Test
    public void testThirtyfourOne() {
        testOneText("34.1");
    }

    @Ignore
    @Test
    public void testThirtyfourTwo() {
        testOneText("34.2");
    }

    @Ignore
    @Test
    public void testThirtyfourThree() {
        testOneText("34.3");
    }

    @Ignore
    @Test
    public void testThirtyfiveOne() {
        testOneText("35.1");
    }

    @Ignore
    @Test
    public void testThirtyfiveTwo() {
        testOneText("35.2");
    }

    @Ignore
    @Test
    public void testThirtyfiveThree() {
        testOneText("35.3");
    }

    @Ignore
    @Test
    public void testThirtysixOne() {
        testOneText("36.1");
    }

    @Ignore
    @Test
    public void testThirtysixTwo() {
        testOneText("36.2");
    }

    @Ignore
    @Test
    public void testThirtysixThree() {
        testOneText("36.3");
    }

    @Ignore
    @Test
    public void testSSixPZeroOne() {
        testOneText("s6p01");
    }

    @Ignore
    @Test
    public void testSSixPZeroTwo() {
        testOneText("s6p02");
    }

    @Ignore
    @Test
    public void testSSixPZeroThree() {
        testOneText("s6p03");
    }

    @Ignore
    @Test
    public void testSSixPZeroFour() {
        testOneText("s6p04");
    }

    @Ignore
    @Test
    public void testSSixPZeroFive() {
        testOneText("s6p05");
    }

    @Ignore
    @Test
    public void testSSixPZeroSix() {
        testOneText("s6p06");
    }

    @Ignore
    @Test
    public void testSSixPZeroSeven() {
        testOneText("s6p07");
    }

    @Ignore
    @Test
    public void testSSixPZeroEight() {
        testOneText("s6p08");
    }

    @Ignore
    @Test
    public void testSSixPZeroNine() {
        testOneText("s6p09");
    }

    @Ignore
    @Test
    public void testSSixPTen() {
        testOneText("s6p10");
    }

    @Ignore
    @Test
    public void testSSevenPZeroOne() {
        testOneText("s7p01");
    }

    @Ignore
    @Test
    public void testSSevenPZeroTwo() {
        testOneText("s7p02");
    }

    @Ignore
    @Test
    public void testSSevenPZeroThree() {
        testOneText("s7p03");
    }

    @Ignore
    @Test
    public void testSSevenPZeroFour() {
        testOneText("s7p04");
    }

    @Ignore
    @Test
    public void testSSevenPZeroFiveA() {
        testOneText("s7p05a");
    }

    @Ignore
    @Test
    public void testSSevenPZeroFiveB() {
        testOneText("s7p05b");
    }

    @Ignore
    @Test
    public void testSSevenPZeroSix() {
        testOneText("s7p06");
    }

    @Ignore
    @Test
    public void testSSevenPZeroSeven() {
        testOneText("s7p07");
    }

    @Ignore
    @Test
    public void testSSevenPZeroEight() {
        testOneText("s7p08");
    }

    @Ignore
    @Test
    public void testSSevenPZeroNine() {
        testOneText("s7p09");
    }

    @Ignore
    @Test
    public void testSSevenPTen() {
        testOneText("s7p10");
    }
}
