/**
 *
 */
package edu.kit.ipd.pronat.wiki_wsd;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.ipd.parse.luna.agent.AbstractAgent;
import edu.kit.ipd.parse.luna.data.MissingDataException;
import edu.kit.ipd.parse.luna.graph.IArc;
import edu.kit.ipd.parse.luna.graph.IArcType;
import edu.kit.ipd.parse.luna.graph.INode;
import edu.kit.ipd.parse.luna.graph.INodeType;
import edu.kit.ipd.parse.luna.graph.Pair;
import edu.kit.ipd.parse.luna.graph.ParseGraph;
import edu.kit.ipd.parse.luna.tools.ConfigManager;
import edu.kit.ipd.pronat.wiki_wsd.classifier.Classification;
import edu.kit.ipd.pronat.wiki_wsd.classifier.ClassifierService;
import edu.kit.ipd.pronat.wiki_wsd.classifier.SerializationHelper;
import weka.classifiers.Classifier;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Stopwords;
import weka.filters.Filter;

/**
 * @author Sebastian Weigelt
 * @author Jan Keim
 *
 */
@MetaInfServices(AbstractAgent.class)
public class WordSenseDisambiguation extends AbstractAgent {
	private static final Logger logger = LoggerFactory.getLogger(WordSenseDisambiguation.class);

	private static final String NONE_VAL = "NONE";
	private static final String LEMMA_ATTRIBUTE = "lemma";
	private static final String POS_ATTRIBUTE = "pos";
	private static final String NER_ATTRIBUTE = "ner";
	private static final String ID = "WordSenseDisambiguation";

	// private static final String RELATION_ARC_TYPE = "relation";
	private static final String TOKEN_NODE_TYPE = "token";
	static final String WSD_ATTRIBUTE_NAME = "wsd";
	static final String WSD_TOP_X_ATTRIBUTE_NAME = "wsd-top-x";

	private static final String classifierPathDefault = "/EfficientNaiveBayes.classifier";
	private static final String filterPathDefault = "/EfficientNaiveBayes.filter";
	private static final String headerPathDefault = "/EfficientNaiveBayes.instanceheader";

	/**
	 * Indicator to the maximum amount of classifications to be stored into the
	 * node.
	 */
	private int storeTopX;

	private ClassifierService classifierService;

	/*
	 * (non-Javadoc)
	 *
	 * @see edu.kit.ipd.parse.luna.agent.LunaObserver#init()
	 */
	@Override
	public void init() {
		setId(WordSenseDisambiguation.ID);
		Properties props = ConfigManager.getConfiguration(getClass());

		// Set store Top-X (negative indicates no storage)
		storeTopX = Integer.parseInt((String) props.getOrDefault("STORE_TOP_X", "-1"));

		// load classifier and filter

		Object loader = new Object() {
		};

		String classifierPath = props.getProperty("CLASSIFIER");
		InputStream classifier;
		if ((classifierPath == null) || classifierPath.trim().isEmpty() || classifierPath.isBlank()) {
			classifier = loader.getClass().getResourceAsStream(WordSenseDisambiguation.classifierPathDefault);
		} else {
			classifier = getStreamFromPath(classifierPath);
		}

		String filterPath = props.getProperty("FILTER");
		InputStream filter;
		if ((filterPath == null) || filterPath.isEmpty() || filterPath.isBlank()) {
			filter = loader.getClass().getResourceAsStream(WordSenseDisambiguation.filterPathDefault);
		} else {
			filter = getStreamFromPath(filterPath);
		}

		String headerPath = props.getProperty("INSTANCESHEADER");
		InputStream header;
		if ((headerPath == null) || headerPath.isEmpty() || headerPath.isBlank()) {
			header = loader.getClass().getResourceAsStream(WordSenseDisambiguation.headerPathDefault);
		} else {
			header = getStreamFromPath(headerPath);
		}

		load(classifier, filter, header);
	}

	private InputStream getStreamFromPath(String path) {
		try {
			File file = new File(path);
			if (!file.exists()) {
				throw new IllegalArgumentException("File does not exist: " + path);
			}
			return new FileInputStream(file);
		} catch (FileNotFoundException e) {
			WordSenseDisambiguation.logger.warn(e.getMessage(), e.getCause());
		}
		return null;
	}

	private void prepareGraph() {
		// add graph attribute
		INodeType tokenType;
		if (graph.hasNodeType(WordSenseDisambiguation.TOKEN_NODE_TYPE)) {
			tokenType = graph.getNodeType(WordSenseDisambiguation.TOKEN_NODE_TYPE);
		} else {
			tokenType = graph.createNodeType(WordSenseDisambiguation.TOKEN_NODE_TYPE);
		}
		if (!tokenType.containsAttribute(WordSenseDisambiguation.WSD_ATTRIBUTE_NAME, "String")) {
			tokenType.addAttributeToType("String", WordSenseDisambiguation.WSD_ATTRIBUTE_NAME);
		}
		if (storeTopX > 0 && !tokenType.containsAttribute(WordSenseDisambiguation.WSD_TOP_X_ATTRIBUTE_NAME, List.class.getName())) {
			tokenType.addAttributeToType(List.class.getName(), WordSenseDisambiguation.WSD_TOP_X_ATTRIBUTE_NAME);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see edu.kit.ipd.parse.luna.agent.AbstractAgent#exec()
	 */
	@Override
	protected void exec() {
		prepareGraph();
		List<INode> nodes;
		try {
			nodes = getNodesInOrder();
		} catch (MissingDataException e) {
			return;
		}
		for (int i = 0; i < nodes.size(); i++) {
			INode node = nodes.get(i);
			String pos = node.getAttributeValue(WordSenseDisambiguation.POS_ATTRIBUTE).toString();
			if (pos.startsWith("NN")) {
				if (WordSenseDisambiguation.nodeIsNamedEntity(node)) {
					continue;
				}
				// TODO maybe multi-thread here
				Classification clazz = disambiguateNoun(nodes, i);
				WordSenseDisambiguation.addClassificationToGraph(node, clazz);
				if (WordSenseDisambiguation.logger.isDebugEnabled()) {
					WordSenseDisambiguation.logger.debug(node.toString());
				}

				// check for compound noun
				// TODO improve!
				// prbly problem: Classifier is not trained on
				// compound nouns, needs to be!
				// TODO how to set compound word wsd
				// if (i > 0) {
				// INode prevNode = nodes.get(i - 1);
				// String prevPos =
				// prevNode.getAttributeValue(POS_ATTRIBUTE).toString();
				// if (prevPos.startsWith("NN")) {
				// Classification cnClazz = disambiguateCompoundNoun(nodes, i -
				// 1, i);
				// logger.debug(cnClazz.toString());
				// }
				// }
			}
		}
	}

	private static void addClassificationToGraph(INode node, Classification clazz) {
		node.setAttributeValue(WordSenseDisambiguation.WSD_ATTRIBUTE_NAME, clazz.getClassificationString());
	}

	// TODO maybe speed this up!
	private List<INode> getNodesInOrder() throws MissingDataException {
		if (!(graph instanceof ParseGraph)) {
			WordSenseDisambiguation.logger.error("Graph is no ParseGraph!");
			throw new MissingDataException("Graph is no ParseGraph!");
		}
		ParseGraph parseGraph = (ParseGraph) graph;
		String arcTypeName = "relation";
		String arcTypeValue = "NEXT";
		IArcType arcType = graph.getArcType(arcTypeName);

		List<INode> orderedNodes = new ArrayList<>();
		INode node = parseGraph.getFirstUtteranceNode();
		if (node == null) {
			return orderedNodes;
		}

		// TODO check if this always works
		while (orderedNodes.add(node)) {
			List<? extends IArc> arcs = node.getOutgoingArcsOfType(arcType);
			if (node.getNumberOfOutgoingArcs() < 1) {
				break;
			}
			for (IArc arc : arcs) {
				Object value = arc.getAttributeValue("value");
				if (value.equals(arcTypeValue)) {
					node = arc.getTargetNode();
					break;
				}
			}
		}
		return orderedNodes;
	}

	static boolean nodeIsNamedEntity(INode node) {
		// use NER from parse
		String nodeString = node.getAttributeValue(WordSenseDisambiguation.NER_ATTRIBUTE).toString();
		String val = node.getAttributeValue("value").toString();
		return (!nodeString.equals("O") || val.equalsIgnoreCase("armar") || val.equalsIgnoreCase("alexa"));
	}

	private Classification disambiguateNoun(List<INode> nodes, int index) {
		return disambiguateCompoundNoun(nodes, index, index);
	}

	// does not actually disambiguate a compound noun properly...yet
	private Classification disambiguateCompoundNoun(List<INode> nodes, int indexFirst, int indexSecond) {
		INode firstNode = nodes.get(indexFirst);
		Instance instance = createBaseClassificationInstance();
		// fill instance ...
		// ... with actual Word
		String lemma = firstNode.getAttributeValue(WordSenseDisambiguation.LEMMA_ATTRIBUTE).toString().toLowerCase();
		String pos = firstNode.getAttributeValue(WordSenseDisambiguation.POS_ATTRIBUTE).toString();
		if (indexFirst != indexSecond) {
			INode scndNode = nodes.get(indexSecond);
			lemma += " " + scndNode.getAttributeValue(WordSenseDisambiguation.LEMMA_ATTRIBUTE).toString().toLowerCase();
			pos = scndNode.getAttributeValue(WordSenseDisambiguation.POS_ATTRIBUTE).toString();
		}
		instance.setValue(1, lemma);
		instance.setValue(2, pos);
		// ... with left and right three words
		WordSenseDisambiguation.processLeftWords(nodes, indexFirst, instance);
		WordSenseDisambiguation.processRightWords(nodes, indexSecond, instance);
		// ... with left and right NN+VB
		WordSenseDisambiguation.processLeftNounAndVerb(nodes, indexFirst, instance);
		WordSenseDisambiguation.processRightNounAndVerb(nodes, indexSecond, instance);

		// Store top-x disambiguation
		storeTopXDisambiguation(instance, lemma, nodes, indexFirst);

		// disambiguate
		return classifierService.classifyInstanceWithLemma(instance, lemma);
		// return classifierService.classifyInstance(instance);
	}

	/**
	 * Stores the top-x classification to node
	 *
	 * @author Dominik Fuchss
	 * @param instance
	 *            the instance
	 * @param lemma
	 *            the lemma
	 * @param nodes
	 *            all token nodes
	 * @param idx
	 *            the index of the current node
	 * @see #storeTopX
	 */
	private void storeTopXDisambiguation(Instance instance, String lemma, List<INode> nodes, int idx) {
		if (storeTopX <= 0) {
			return;
		}

		List<Classification> raw = classifierService.classifyInstanceWithLemma(instance, lemma, storeTopX);

		List<Pair<String, Double>> cls = //
				raw.stream().map(c -> new Pair<>(c.getClassificationString(), c.getProbability())).collect(Collectors.toList());

		nodes.get(idx).setAttributeValue(WordSenseDisambiguation.WSD_TOP_X_ATTRIBUTE_NAME, cls);
	}

	private Instance createBaseClassificationInstance() {
		Instances header = classifierService.getHeader().orElse(ClassifierService.getEmptyInstancesHeader());
		Instance instance = new DenseInstance(header.numAttributes());
		instance.setDataset(header);
		instance.setWeight(2);
		instance.attribute(1).setWeight(10d);
		return instance;
	}

	private static void processLeftWords(List<INode> nodes, int index, Instance instance) {
		int leftAdd = 0;
		for (int i = 1; i <= 3; i++) {
			// left
			String leftLemma = WordSenseDisambiguation.NONE_VAL;
			String leftPos = WordSenseDisambiguation.NONE_VAL;
			int leftIndex = index - i;
			if (leftIndex >= 0) {
				INode leftNode = nodes.get(leftIndex);
				leftLemma = leftNode.getAttributeValue(WordSenseDisambiguation.LEMMA_ATTRIBUTE).toString();
				leftLemma = (leftLemma != null) ? leftLemma.toLowerCase() : WordSenseDisambiguation.NONE_VAL;
				// when word is a word, that should be filtered, skip it!
				while (Stopwords.isStopword(leftLemma) || ClassifierService.filterWords.contains(leftLemma)) {
					leftAdd += 1;
					if ((leftIndex - leftAdd) < 0) {
						leftLemma = WordSenseDisambiguation.NONE_VAL;
						break;
					}
					leftNode = nodes.get(leftIndex - leftAdd);
					leftLemma = leftNode.getAttributeValue(WordSenseDisambiguation.LEMMA_ATTRIBUTE).toString();
					leftLemma = (leftLemma != null) ? leftLemma.toLowerCase() : WordSenseDisambiguation.NONE_VAL;
				}
				leftPos = leftNode.getAttributeValue(WordSenseDisambiguation.POS_ATTRIBUTE).toString();
			}
			// word-3 is at index 3
			// word-1 is at index 7
			int attributeIndex = 9 - (2 * i);
			if (!leftLemma.equals(WordSenseDisambiguation.NONE_VAL)) {
				instance.setValue(attributeIndex, leftLemma);
				instance.setValue(attributeIndex + 1, leftPos);
			}
		}
	}

	private static void processRightWords(List<INode> nodes, int index, Instance instance) {
		int rightAdd = 0;
		for (int i = 1; i <= 3; i++) {
			// right
			String rightLemma = WordSenseDisambiguation.NONE_VAL;
			String rightPos = WordSenseDisambiguation.NONE_VAL;
			int rightIndex = index + i;
			if (rightIndex < nodes.size()) {
				INode rightNode = nodes.get(rightIndex);
				rightLemma = rightNode.getAttributeValue(WordSenseDisambiguation.LEMMA_ATTRIBUTE).toString();
				rightLemma = (rightLemma != null) ? rightLemma.toLowerCase() : WordSenseDisambiguation.NONE_VAL;
				while (Stopwords.isStopword(rightLemma) || ClassifierService.filterWords.contains(rightLemma)) {
					rightAdd += 1;
					// when word is a word, that should be filtered, skip it!
					if ((rightIndex + rightAdd) >= nodes.size()) {
						rightLemma = WordSenseDisambiguation.NONE_VAL;
						break;
					}
					rightNode = nodes.get(rightIndex + rightAdd);
					rightLemma = rightNode.getAttributeValue(WordSenseDisambiguation.LEMMA_ATTRIBUTE).toString();
					rightLemma = (rightLemma != null) ? rightLemma.toLowerCase() : WordSenseDisambiguation.NONE_VAL;
				}
				rightPos = rightNode.getAttributeValue(WordSenseDisambiguation.POS_ATTRIBUTE).toString();
			}
			// word+1 starts at 9
			int attributeIndex = 7 + (2 * i);
			if (!rightLemma.equals(WordSenseDisambiguation.NONE_VAL)) {
				instance.setValue(attributeIndex, rightLemma);
				instance.setValue(attributeIndex + 1, rightPos);
			}

		}

	}

	private static void processLeftNounAndVerb(List<INode> nodes, int index, Instance instance) {
		String leftNN = WordSenseDisambiguation.NONE_VAL;
		String leftVB = WordSenseDisambiguation.NONE_VAL;
		for (int i = 1; i < nodes.size(); i++) {
			int leftIndex = index - i;
			if (leftIndex >= 0) {
				INode leftNode = nodes.get(leftIndex);
				String leftPOS = leftNode.getAttributeValue(WordSenseDisambiguation.POS_ATTRIBUTE).toString();
				if (leftNN.equals(WordSenseDisambiguation.NONE_VAL) && leftPOS.startsWith("NN")) {
					// we found left NN*
					leftNN = leftNode.getAttributeValue(WordSenseDisambiguation.LEMMA_ATTRIBUTE).toString();
					leftNN = (leftNN != null) ? leftNN.toLowerCase() : WordSenseDisambiguation.NONE_VAL;
					if (ClassifierService.filterWords.contains(leftNN)) {
						leftNN = WordSenseDisambiguation.NONE_VAL;
					}
				} else if (leftVB.equals(WordSenseDisambiguation.NONE_VAL) && leftPOS.startsWith("VB")) {
					// we found left VB*
					leftVB = leftNode.getAttributeValue(WordSenseDisambiguation.LEMMA_ATTRIBUTE).toString();
					leftVB = (leftVB != null) ? leftVB.toLowerCase() : WordSenseDisambiguation.NONE_VAL;
					if (ClassifierService.filterWords.contains(leftVB)) {
						leftVB = WordSenseDisambiguation.NONE_VAL;
					}
				}
			}
			if (!leftNN.equals(WordSenseDisambiguation.NONE_VAL) && !leftVB.equals(WordSenseDisambiguation.NONE_VAL)) {
				break;
			}
		}
		WordSenseDisambiguation.addAttributeToInstance(instance, 15, leftNN);
		WordSenseDisambiguation.addAttributeToInstance(instance, 16, leftVB);
	}

	private static void processRightNounAndVerb(List<INode> nodes, int index, Instance instance) {
		String rightNN = WordSenseDisambiguation.NONE_VAL;
		String rightVB = WordSenseDisambiguation.NONE_VAL;
		for (int i = 1; i < nodes.size(); i++) {
			int rightIndex = index + i;
			if (rightIndex < nodes.size()) {
				INode rightNode = nodes.get(rightIndex);
				String rightPOS = rightNode.getAttributeValue(WordSenseDisambiguation.POS_ATTRIBUTE).toString();
				if (rightNN.equals(WordSenseDisambiguation.NONE_VAL) && rightPOS.startsWith("NN")) {
					// we found right NN*
					rightNN = rightNode.getAttributeValue(WordSenseDisambiguation.LEMMA_ATTRIBUTE).toString();
					rightNN = (rightNN != null) ? rightNN.toLowerCase() : WordSenseDisambiguation.NONE_VAL;
					if (ClassifierService.filterWords.contains(rightNN)) {
						rightNN = WordSenseDisambiguation.NONE_VAL;
					}
				} else if (rightVB.equals(WordSenseDisambiguation.NONE_VAL) && rightPOS.startsWith("VB")) {
					// we found right VB*
					rightVB = rightNode.getAttributeValue(WordSenseDisambiguation.LEMMA_ATTRIBUTE).toString();
					rightVB = (rightVB != null) ? rightVB.toLowerCase() : WordSenseDisambiguation.NONE_VAL;
					if (ClassifierService.filterWords.contains(rightVB)) {
						rightVB = WordSenseDisambiguation.NONE_VAL;
					}
				}
			}

			if (!rightNN.equals(WordSenseDisambiguation.NONE_VAL) && !rightVB.equals(WordSenseDisambiguation.NONE_VAL)) {
				break;
			}
		}
		WordSenseDisambiguation.addAttributeToInstance(instance, 17, rightNN);
		WordSenseDisambiguation.addAttributeToInstance(instance, 18, rightVB);
	}

	private static void addAttributeToInstance(Instance instance, int attrIndex, String attrValue) {
		if (!attrValue.equals(WordSenseDisambiguation.NONE_VAL)) {
			instance.setValue(attrIndex, attrValue);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "WordSenseDisambiguation [classifierService=" + classifierService + "]";
	}

	private void load(InputStream classifierStream, InputStream filterStream, InputStream headerStream) {
		Classifier classifier = SerializationHelper.deserializeEfficientNaiveBayesClassifier(classifierStream).orElseThrow();
		Filter filter = SerializationHelper.deserializeFilter(filterStream).orElseThrow();
		Instances header = SerializationHelper.deserializeInstances(headerStream).orElseThrow();

		classifierService = new ClassifierService(classifier, filter, header);
	}

	private void loadWithNative(InputStream classifierStream, InputStream filterStream, InputStream headerStream) {
		Classifier classifier = SerializationHelper.deserializeEfficientNaiveBayesClassifierNative(classifierStream).orElseThrow();
		Filter filter = SerializationHelper.deserializeFilterNative(filterStream).orElseThrow();
		Instances header = SerializationHelper.deserializeInstancesNative(headerStream).orElseThrow();

		classifierService = new ClassifierService(classifier, filter, header);
	}
}
