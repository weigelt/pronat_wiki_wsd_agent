package edu.kit.ipd.parse.wikiWSD.util;

import java.io.IOException;
import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * This is an adapted version of the CorefTestHelper class in the CorefAnalyzer
 * by Tobias Hey
 *
 * @author Jan Keim
 *
 */
public class TestHelper {
	public static HashMap<String, Text> texts;

	static {
		TestHelper.texts = new HashMap<>();

		ObjectMapper objectMapper = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);
		objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker() //
				.withFieldVisibility(Visibility.ANY)//
				.withGetterVisibility(Visibility.NONE)//
				.withSetterVisibility(Visibility.NONE)//
				.withIsGetterVisibility(Visibility.NONE));

		try {
			TypeReference<HashMap<String, Text>> type = new TypeReference<HashMap<String, Text>>() {};
			TestHelper.texts = objectMapper.readValue(TestHelper.class.getResourceAsStream("/korpus.json"), type);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * static { TestHelper.texts = new HashMap<>(); try { File file = new
	 * File(TestHelper.class.getResource("/korpus.xml").toURI());
	 * DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
	 * DocumentBuilder dBuilder = dbFactory.newDocumentBuilder(); Document doc =
	 * dBuilder.parse(file); NodeList nl = doc.getElementsByTagName("text"); for
	 * (int i = 0; i < nl.getLength(); i++) { Element node = (Element)
	 * nl.item(i); String name = node.getAttribute("name"); String text =
	 * node.getTextContent().trim(); List<String[]> refs = new ArrayList<>();
	 * NodeList wsd = node.getElementsByTagName("wsd"); for (int j = 0; j <
	 * wsd.getLength(); j++) { Element wsdNode = (Element) wsd.item(j); String[]
	 * ref = new String[] { wsdNode.getAttribute("position"),
	 * wsdNode.getAttribute("word"), wsdNode.getAttribute("meaning"),
	 * wsdNode.getAttribute("meaningSyn") }; refs.add(ref); }
	 * TestHelper.texts.put(name, new Text(text, refs)); } } catch
	 * (URISyntaxException | ParserConfigurationException | SAXException |
	 * IOException e) { e.printStackTrace(); } }
	 */
}
