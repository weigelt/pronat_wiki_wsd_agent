package edu.kit.ipd.parse.wiki_wsd;

import java.util.List;

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
import edu.kit.ipd.parse.luna.tools.StringToHypothesis;
import edu.kit.ipd.parse.ner.NERTagger;
import edu.kit.ipd.parse.shallownlp.ShallowNLP;

/**
 * @author Jan Keim
 *
 */
@Ignore
public class SimpleWordSenseDisambiguationTest {
	private static final Logger logger = LoggerFactory.getLogger(SimpleWordSenseDisambiguationTest.class);

	private static WordSenseDisambiguation wsd;

	private PrePipelineData ppd;
	private static ShallowNLP snlp;
	private static GraphBuilder graphBuilder;
	private static NERTagger ner;

	@BeforeClass
	public static void beforeClass() {
		logger.info("Loading WSD Classifier");
		SimpleWordSenseDisambiguationTest.wsd = new WordSenseDisambiguation();
		SimpleWordSenseDisambiguationTest.wsd.init();
		graphBuilder = new GraphBuilder();
		graphBuilder.init();
		snlp = new ShallowNLP();
		snlp.init();
		ner = new NERTagger();
		ner.init();
	}

	private static void executePrepipeline(PrePipelineData ppd) {
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

	private void testOneText(String id, String text) {
		logger.info(id + "\n" + text);
		ppd = new PrePipelineData();
		text = text.replace("\n", " ");
		ppd.setMainHypothesis(StringToHypothesis.stringToMainHypothesis(text));
		executePrepipeline(ppd);
		try {
			IGraph graph = ppd.getGraph();
			SimpleWordSenseDisambiguationTest.wsd.setGraph(graph);
			SimpleWordSenseDisambiguationTest.wsd.exec();

			// get nodes and extract the info for display
			List<INode> nodes = graph.getNodesOfType(graph.getNodeType("token"));
			for (INode node : nodes) {
				String value = node.getAttributeValue("value").toString();
				String pos = node.getAttributeValue("pos").toString();
				if (!pos.startsWith("NN") || WordSenseDisambiguation.nodeIsNamedEntity(node)) {
					continue;
				}
				String wsd = node.getAttributeValue(WordSenseDisambiguation.WSD_ATTRIBUTE_NAME).toString();
				logger.info(value + " -> " + wsd);
			}
		} catch (

		MissingDataException e) {
			e.printStackTrace();
		}
	}

	// v
	@Test
	public void testOneOne() {
		String id = "1.1";
		String text = "okay Armar go to the table grab popcorn come to me give me the popcorn which is in your hand";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testTwoOne() {
		String id = "2.1";
		String text = "Armar can you please bring me the popcorn bag";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testEighteenTwo() {
		String id = "18.2";
		String text = "hello Armar could you go to the table and take the green cup please put it in the dishwasher and close it";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testThirtyoneTwo() {
		String id = "31.2";
		String text = "Hey Armar please place the green cup from the kitchen table into the dishwasher";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testThreeThree() {
		String id = "3.3";
		String text = "Armar would you please go to the fridge and open it take out the orange juice and bring it to me<";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testTwentyfiveThree() {
		String id = "25.3";
		String text = "armar open the fridge and take the orange juice afterwards close the fridge and bring me the orange juice";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testIfFourOne() {
		String id = "if.4.1";
		String text = "hey armar could you please have a look at these dishes if they are dirty put them into the dishwasher if they are not dirty put them into the cupboard";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testIfFourTen() {
		String id = "if.4.10";
		String text = "robo go to the table if there are any dirty dishes "
				+ "grab the dirty dishes and go to the dishwasher open the dishwasher"
				+ " and put the dirty dishes into the dishwasher close the dishwasher"
				+ " and return to the table if there are any clean dishes grab the clean dishes"
				+ " and go to the cupboard open the cupboard and put the clean dishes into the cupboard";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testIfFiveFive() {
		String id = "if.5.5";
		String text = "hello armar I want to make some drinks go to the fridge"
				+ " and if there fresh oranges bring me the fresh oranges together with vodka"
				+ " otherwise bring me just orange juice and the vodka";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testIfFiveTwelve() {
		String id = "if.5.12";
		String text = "hello armar I would want to have some vodka with fresh orange please go to the fridge"
				+ " and check if there are some fresh orange if there are not any please make me a vodka with orange juice";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testSSixPThree() {
		String id = "s6p03";
		String text = "Go to the table take the green cup standing on the table and go to the fridge"
				+ " open the fridge right in front of you there is a water bottle take the bottle"
				+ " open it fill water in the cup and put the bottle back in the fridge close the fridge"
				+ " and then bring me the cup afterwards go to the dishwasher open the dishwasher take two red cups"
				+ " em from inside bring them to the cupboard and open the cupboard put the cups on the shelf"
				+ " and close the cupboard again";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testSSixPTen() {
		String id = "s6p10";
		String text = "Go to the fridge open the fridge door then take the water bottle out"
				+ " close the fridge door then go to the table and open the water bottle"
				+ " fill the green cup with the water and then bring the cup to me"
				+ " go to the dishwasher ahm take the two red cups out of the dishwasher" + " and put them on the cupboard";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testSSevenPEight() {
		String id = "s7p08";
		String text = "Hey Armar I'm hungry please take eh a plate from the dishwasher and rinse it with water at the sink"
				+ " afterwards go to the fridge open the fridge and put the instant food you find into in there on to the plate"
				+ " close the fridge and warm the plate in the eh microwave" + " therefore you have to open the door first"
				+ " afterwards when it's warm please bring it eh to me at the table";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testSSevenPTen() {
		String id = "s7p10";
		String text = "Go to the dishwasher and take one plate out wash this plate and "
				+ "then go to the fridge put the em meal instant meal on the plate"
				+ " and bring it to the mac microwave put it into the microwave"
				+ " then after it is warmed up put the ehm meal on the plate and bring the plate to the table";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testSEightPOne() {
		String id = "s8p01";
		String text = "go to the washing machine and open it take out the laundry put the laundry into the dryer start the dryer";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testSEightPSix() {
		String id = "s8p06";
		String text = "go to the dryer open the dryer go to the front side of the washing machine grab the washing machine window handle"
				+ " put the handle to open the washing machine put your arms into the washing machine grab the laundry"
				+ " take the laundry out of the washing machine go to the dryer put the laundry into the dryer close the dryer"
				+ " push the start button of the dryer";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testDroneOneOne() {
		String id = "drone1.1";
		String text = "start and accelerate as fast as possible until you fly through the gate"
				+ " then slow down and turn left by sixty degree accelerate again"
				+ " and dodge the table by ascending first and descending afterwards fly through the greenhouse"
				+ " then turn left and fly above the pond if you crossed the pond break and descend down to the lawn"
				+ " and finally turn off";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testDroneOneTwo() {
		String id = "drone1.2";
		String text = "ascend and fly as fast as possible to the gate turn left"
				+ " and ascend and start flying to the greenhouse after you dodged the table"
				+ "by ascending descend again after passing through the greenhouse turn left again"
				+ " and fly over the pond afterwards slow down and descend until you are on the lawn";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testMindstormOneOne() {
		String id = "mindstorm1.1";
		String text = "follow the line on the carpet at the end of the carpet turn until you see the rattle grab the rattle and afterwards release it again";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testMindstormOneTwo() {
		String id = "mindstorm1.2";
		String text = "move along the line until you are at the end of the carpet turn right until you see the rattle grab it and release it";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testAlexaOneOne() {
		String id = "alexa1.1";
		String text = "alexa turn up the temperature of the radiator by two degrees then start playing my favorite playlist";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testAlexaOneTwo() {
		String id = "alexa1.2";
		String text = "alexa before you play my favorite playlist turn on the radiator because it is getting cold in here";
		testOneText(id, text);
	}
	// ^

	@Ignore
	@Test
	public void testSEightPTwo() {
		String id = "s8p02";
		String text = "go to the washing machine and press the open button open the washing machine door"
				+ " grab the laundry in the washing machine and move backwards go to the dryer"
				+ " and press the open button open the dryer door and put the laundry in it close the dryer door press the start button";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testSEightPThree() {
		String id = "s8p03";
		String text = "armar open the washing machine and take the laundry out"
				+ " open the dryer and put the laundry into the dryer now close the dryer and start the program";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testSEightPFour() {
		String id = "s8p04";
		String text = "armar go to the basement and go inside the laundry room take out the laundry in the washing machine"
				+ " open the dryer and put in the clothes and close the door and start a program with ninety minutes";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testSEightPFive() {
		String id = "s8p05";
		String text = "armar go to the washing machine check if the washing machine has completed the washing process"
				+ " if it has completed the process check if the dryer can be used if it can be used"
				+ " open its door open the door of the washing machine grab the whole laundry and put it in the dryer"
				+ " close the door of the dryer start the dryer";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testSEightPSeven() {
		String id = "s8p07";
		String text = "open the washing washing machine take out the clothes"
				+ " bring them to the dryer open the dryer and put the clothes into the dryer";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testSEightPEight() {
		String id = "s8p08";
		String text = "go to the washing machine and open it take out the laundry of the washing machine"
				+ " go to the dryer and open it put the laundry into the dryer close the dryer";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testSEightPNine() {
		String id = "s8p09";
		String text = "armar please go to the washing machine then open the washing machine and take out the laundry"
				+ " place the laundry in the washing basket open the dryer place the laundry in the dryer and then start the program";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testSEightPTen() {
		String id = "s8p10";
		String text = "open the washing machine door and take the clothes and put it into the dryer and then start the dryer";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testHeatingOneOne() {
		String id = "heating1.1";
		String text = "alexa turn on the radiator";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testHeatingOneTwo() {
		String id = "heating1.2";
		String text = "alexa turn down the temperature of the air conditioning by two degree";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testHeatingTwoOne() {
		String id = "heating2.1";
		String text = "go to the radiator and use the thermostat to increase the temperature";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testBedroomOneOne() {
		String id = "bedroom1.1";
		String text = "go to the closet open it and grab the sweater and the trousers and bring them to me";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testBedroomOneTwo() {
		String id = "bedroom1.2";
		String text = "move to the desk and clean it afterwards go the the nightstand and clean it as well if you finished these tasks take the book out of the shelf and bring it to me";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testBedroomOneThree() {
		String id = "bedroom1.3";
		String text = "robot go to the bed grab the pillow and bring it to me afterwards go back to the bed grab the sheet and also bring it to me";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testMusicOneOne() {
		String id = "music1.1";
		String text = "alexa please play my metal playlist in a random order";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testMusicOneTwo() {
		String id = "music1.2";
		String text = "play some songs by the famous composer hans zimmer";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testMusicOneThree() {
		String id = "music1.3";
		String text = "alexa what are the most famous songwriters who use a piano";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testChildrensRoomOneOne() {
		String id = "childrensroom1.1";
		String text = "tidy up by grabbing the dolls and action figures and putting them on top of the cabinet";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testChildrensRoomOneTwo() {
		String id = "childrensroom1.2";
		String text = "follow the line on the carpet at the end of the carpet turn until you see the rattle grab the rattle and afterwards release it again";
		testOneText(id, text);
	}

	@Ignore
	@Test
	public void testChildrensRoomOneThree() {
		String id = "childrensroom1.3";
		String text = "grab two lego bricks and put them on top of each afterwards grab another brick and put that brick on top of the others repeat this until the structure is bigger than the dollhouse";
		testOneText(id, text);
	}
}
