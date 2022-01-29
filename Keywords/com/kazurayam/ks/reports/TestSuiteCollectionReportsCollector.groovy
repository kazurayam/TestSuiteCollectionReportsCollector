package com.kazurayam.ks.reports

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern
import java.util.stream.Collectors

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

import com.kms.katalon.core.configuration.RunConfiguration

public class TestSuiteCollectionReportsCollector {

	private static final Path projectDir = Paths.get(RunConfiguration.getProjectDir())
	private static DocumentBuilder xmlParser

	static {
		DocumentBuilderFactory dbfactory = DocumentBuilderFactory.newInstance()
		xmlParser = dbfactory.newDocumentBuilder()
	}

	public TestSuiteCollectionReportsCollector() {}

	public execute() {
		Path reportsDir = projectDir.resolve("Reports")
		Path latestRCE = findLatestReportCollectionEntity(reportsDir)
		List<Path> xmlReports = findXmlReports(latestRCE)
	}

	public static Optional<Path> findLatestReportCollectionEntity(Path reportsDir) {
		List<Path> dirs = findReportCollectionEntities(reportsDir)
		if (dirs.size() > 0) {
			Path p = dirs.stream()
					.sorted(Comparator.reverseOrder())
					.collect(Collectors.toList())
					.get(0)
			return Optional.of(p)
		} else {
			return Optional.empty()
		}
	}

	public static List<Path> findReportCollectionEntities(Path base) {
		Pattern pattern = Pattern.compile(".+\\.rp\$")
		FileFinder finder = new FileFinder(pattern)
		Files.walkFileTree(base, finder)
		return finder.getResult()
	}

	public static List<Path> findXmlReports(Path latestRCE) {
		Path tscDir = latestRCE.getParent().getParent().getParent()
		assert Files.exists(tscDir)
		Pattern pattern = Pattern.compile("JUnit_Report\\.xml")
		FileFinder finder = new FileFinder(pattern)
		Files.walkFileTree(tscDir, finder)
		return finder.getResult()
	}

	public static List<Document> loadXmlDocuments(List<Path> xmlFiles) {
		List<Document> docs = new ArrayList<>()
		xmlFiles.each { p ->
			FileInputStream fis = new FileInputStream(p.toFile())
			Document doc = xmlParser.parse(fis)
			docs.add(doc)
		}
		return docs
	}

	/**
	 * returns a List of 
	 *     Map<String,Object> stat = ["name": "TS1", "time": 3.717, "tests": 1, "failures": 0, "errors": 0]
	 */
	public static List<Map> getStats(List<Document> docs) {
		XPath xpath =  XPathFactory.newInstance().newXPath()
		List<Map> stats = new ArrayList<>()
		for (Document doc in docs) {
			Node node =
					(Node)xpath
					.compile("/testsuites")
					.evaluate(doc, XPathConstants.NODE)
			Element testsuites = (Element)node
			Map m = new HashMap<>()
			String name = testsuites.getAttribute("name")
			Double time = Double.parseDouble(testsuites.getAttribute("time"))
			Integer tests = Integer.parseInt(testsuites.getAttribute("tests"))
			Integer failures = Integer.parseInt(testsuites.getAttribute("failures"))
			Integer errors = Integer.parseInt(testsuites.getAttribute("errors"))
			stats.add(["name": name, "time": time, "tests": tests, "failures": failures, "errors": errors])
		}
		return stats
	}

	public static Map getSum(List<Map> stats) {
		Double time = 0
		Integer tests = 0
		Integer failures = 0
		Integer errors = 0
		for (Map stat in stats) {
			time += stat.get("time")
			tests += stat.get("tests")
			failures = stat.get("failures")
			errors = stat.get("errors")
		}
		Map sum = ["name": "sum", "time": time, "tests": tests, "failures": failures, "errors": errors ]
		return sum
	}

	public static String stringifySum(Map sum) {
		StringBuilder sb = new StringBuilder()
		sb.append("time: ")
		sb.append(String.format("%.3f", sum.get("time")))
		sb.append(", tests: ")
		sb.append(sum.get("tests"))
		sb.append(", failures: ")
		sb.append(sum.get("failures"))
		sb.append(", errors: ")
		sb.append(sum.get("errors"))
		return sb.toString()
	}



	private Path resolveOutfile(String path) {
		Path p = Paths.get(path)
		if (! p.isAbsolute()) {
			p = projectDir.resolve(p)
		}
		Files.createDirectories(p.getParent())
		return p
	}
}