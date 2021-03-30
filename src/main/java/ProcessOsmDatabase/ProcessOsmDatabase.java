package ProcessOsmDatabase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.openstreetmap.osmosis.core.domain.v0_6.CommonEntityData;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.OsmUser;
import org.openstreetmap.osmosis.core.domain.v0_6.RelationMember;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.utilslibrary.GeoJSONFile;
import org.utilslibrary.Log;
import org.utilslibrary.OsmDatabase;
import org.utilslibrary.Util;

public class ProcessOsmDatabase {
	
	private final static String APP_NAME = "ProcessOsmDatabase";
	
	private final static String APP_VERSION= "0.08";
	
	private final static String APP_DATE= "Mar 30th 2021";
	
	private final static String XML_MAIN_NODE_NAME = APP_NAME;
	
	private final static String XML_ATTRIB_INPUT_FILE_NAME = "inputFileName";
	
	private final static String XML_ATTRIB_OUTPUT_FILE_NAME = "outputFileName";
	
	private static String mPath = null;
	
	private static OsmDatabase mDatabase = null;
	
	private static PTv2Checker mPTv2Checker = null;
	
	private static BusNetworkChecker mBusNetworkChecker = null;
	
	private static AdminBoundaryChecker mAdminBoundaryChecker = null;
	
	private static HikingChecker mHikingChecker = null;
	
	private static GeoJSONFile mOutputGeoJsonFiles[] = null;
	
	private static BusLinesDatabase mBusLinesDatabase = null;
	
	private static boolean mFilter = false;
	
	private static String mDataDir = null;
	
	private static String mDbPassword = null;
	
	private static int LEVEL_HIGH = 0;
	private static int LEVEL_MEDIUM = 1;
	private static int LEVEL_LOW = 2;
	
	public static void main(String[] args) {
		
		Log.info("Starting " + APP_NAME + " (v" + APP_VERSION + ", " + APP_DATE + ")...");
		
		Instant start = Instant.now();
		
		if (args.length<1) {
			
			Log.error("Missing argument <ProcessFile>. Quitting...");
			
			return;
		}
		
		for(int i=0; i<args.length; i++) {
			
			if (args[i].compareTo("--debug") == 0) {
				
				Log.showDebugLogs(true);
			}
			else if (args[i].compareTo("--filter") == 0) {
				
				mFilter = true;
			}
			else if (args[i].startsWith("--dataDir=")) {
				
				mDataDir = args[i].substring(new String("--dataDir=").length());
			}
			else if (args[i].startsWith("--dbPassword=")) {
				
				mDbPassword = args[i].substring(new String("--dbPassword=").length());
			}
		}
		
		Log.info("Show debug logs: " + Log.isShowDebugEnabled());
		
		Log.info("Data dir: " + mDataDir);
		
		Log.info("DB Password: " + mDbPassword);
		
		// Set input XML file name
		
		String inputXmlFileName = args[0];
		
		Log.info("Input XML file <"+inputXmlFileName+">...");
		
		// Open filter file, if enabled...
		
		Log.info("Filtering is enabled: " + mFilter);
		
		List<Long> relsToProcess = null;
		
		if (mFilter) {
			
			// Open input filter name
			
			int index = inputXmlFileName.indexOf(".xml");
			
			String inputFilterFileName;
			
			if (index < 0) {
				
				inputFilterFileName = inputXmlFileName + ".filter";
			}
			else {
				
				inputFilterFileName = inputXmlFileName.substring(0, index) + ".filter";
			}
			
			relsToProcess = readFilterFile(inputFilterFileName);
		}
		
		mPTv2Checker = new PTv2Checker(relsToProcess);
		
		mBusNetworkChecker = new BusNetworkChecker(relsToProcess);
		
		mAdminBoundaryChecker = new AdminBoundaryChecker(relsToProcess);
		
		mHikingChecker = new HikingChecker(relsToProcess);
		
		mBusLinesDatabase = new BusLinesDatabase(mDbPassword);
		
		Log.info("Processing input XML file <" + inputXmlFileName + ">...");
		
		File file = new File(inputXmlFileName);
		
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
		        .newInstance();
		
		DocumentBuilder documentBuilder;
		
		try {
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			
		} catch (ParserConfigurationException e) {
			
			Log.error("newDocumentBuilder() exception: "+e.getMessage());
			Log.error("Quitting...");
			
			return;
		}
		
		Document document;
		
		try {
			document = documentBuilder.parse(file);
			
		} catch (SAXException e) {
			
			Log.error("documentBuilder.parse() exception: "+e.getMessage());
			Log.error("Quitting...");
			
			return;
			
		} catch (IOException e) {
			
			Log.error("documentBuilder.parse() exception: "+e.getMessage());
			Log.error("Quitting...");
			
			return;
		}
		
		mPath = file.getParent();
		
		if (mDataDir != null) {
			
			mPath += "/" + mDataDir;
		}
		
		Element mainElement = document.getDocumentElement();
		
		Log.info("Processing nodes...");
		
		processElement(mainElement);
		
		if (mOutputGeoJsonFiles != null) {
			
			Log.info("Added " + mOutputGeoJsonFiles[LEVEL_HIGH].getNodeCount() + " nodes to GeoJsonFile (Level High)");
			Log.info("Added " + mOutputGeoJsonFiles[LEVEL_MEDIUM].getNodeCount() + " nodes to GeoJsonFile (Level Medium)");
			Log.info("Added " + mOutputGeoJsonFiles[LEVEL_LOW].getNodeCount() + " nodes to GeoJsonFile (Level Low)");
		
			// Close output GeoJSON files...
			mOutputGeoJsonFiles[LEVEL_HIGH].close();
			mOutputGeoJsonFiles[LEVEL_MEDIUM].close();
			mOutputGeoJsonFiles[LEVEL_LOW].close();
		}
		
		Log.info("Convert has processed " + mBusLinesDatabase.getBusLinesCount() + " bus lines");
		
		if (mDatabase != null) {
			
			createBoundaryFile();
		}
		
		Instant end = Instant.now();
		
		Log.info(APP_NAME + " finished in " + Util.timeFormat(start, end) + "  !!");
	}
	
	private static boolean processElement(Element element) {
		
		return processElement(element, null);
	};
	
	private static boolean processElement(Element element, Collection<Tag> tags) {
		
		if (element.getNodeName().compareTo(XML_MAIN_NODE_NAME)==0) {
			
			if (!openDataBase(element)) {
				
				return false;
			}
			else {
				
				String outputFileName = element.getAttribute(XML_ATTRIB_OUTPUT_FILE_NAME);
				
				if (outputFileName == null) {
					
					outputFileName = "";
				}
				
				openOutputGeoJsonFiles(outputFileName);
				
				mPTv2Checker.setOsmDatabase(mDatabase);
				mPTv2Checker.setGeoJSONFiles(mOutputGeoJsonFiles);
				
				mBusNetworkChecker.setOsmDatabase(mDatabase);
				mBusNetworkChecker.setGeoJSONFiles(mOutputGeoJsonFiles);
				
				mAdminBoundaryChecker.setOsmDatabase(mDatabase);
				mAdminBoundaryChecker.setGeoJSONFiles(mOutputGeoJsonFiles);
				
				mHikingChecker.setOsmDatabase(mDatabase);
				mHikingChecker.setGeoJSONFiles(mOutputGeoJsonFiles);
				
				mBusLinesDatabase.setOsmDatabase(mDatabase);
				mBusLinesDatabase.setOutputDir(mPath + "/bus_lines/");
			}
		}
		else if (element.getNodeName().compareTo("relation")==0) {
			
			String relType=element.getAttribute("type");
			
			if (relType.isEmpty()) {
				
				Log.warning("XML relation has no type. Skipping");
				
				return true;
			}
			
			Log.info("Processing XML relation of type <"+relType+">");
			
			if (mDatabase==null) {
				
				Log.error("mDatabase==null. Cannot process relation");
				
				return false;				
			}
			
			tags=new ArrayList<Tag>();
			
			tags.add(new Tag("type", relType));
			
		}
		else if (element.getNodeName().compareTo("filter")==0) {
			
			if (tags==null) {
				
				Log.error("tags==null. Cannot process filter");
				
				return false;	
			}
			
			if (!element.hasAttributes()) {
				
				Log.error("Filter has no attributes");
				
				return false;
			}
			
			NamedNodeMap map = element.getAttributes();
			
			for (int i=0; i<map.getLength(); i++) {
				
				Node n = map.item(i);
				
				String key = n.getNodeName();
				String value = n.getNodeValue();
				
				tags.add(new Tag(key, value));
				
			}
		}
		else if (element.getNodeName().compareTo("check") == 0) {
			
			String text = "Check with tags: ";
			
			Iterator<Tag> iter = tags.iterator();
			
			while(iter.hasNext()) {
				
				Tag tag = iter.next();
				
				text+="'"+tag.getKey()+"'='"+tag.getValue()+"' ";
			}
			
			Log.info(text);
			
			Instant start = Instant.now();
			
			List<Long> relIds = mDatabase.getRelationsIdsByTags(tags);
			
			Instant end = Instant.now();
		
			Duration time = Duration.between(start, end);
			
			if (relIds==null) {
				
				Log.error("Check error!!!");
			}
			else
				Log.info("Check found " + relIds.size() + " relation(s) in " + time.toMillis() + " ms");
			
			if (!element.hasAttributes()) {
				
				Log.error("Check has no attributes");
				
				return false;
			}
			
			NamedNodeMap nodeMap = element.getAttributes();
			
			for (int i=0; i<nodeMap.getLength(); i++) {
				
				Node n=nodeMap.item(i);
				
				String key=n.getNodeName();
				String value=n.getNodeValue();
				
				if (key.compareTo("count")==0) {
					
					int count = Integer.valueOf(value);
					
					if (count == relIds.size()) {
						
						Log.info("Detected relations match with count <" + count + ">");
					}
					else {
						
						Log.warning("Number of relations <" + relIds.size() +
								"> do not match count check <" + count + ">");
						
						/*
						Iterator<Long> iterRelIds = relIds.iterator();
						
						ArrayList<MyPair<Long, String>> pairs = new ArrayList<MyPair<Long, String>>();
						
						while (iterRelIds.hasNext()) {
							
							Long relId = iterRelIds.next();
							
							Relation relation = mDatabase.getRelationById(relId);
							
							Collection<Tag> relTags = relation.getTags();
							
							Iterator<Tag> iterRelTags = relTags.iterator();
							
							String ref="<noref>";
							
							while (iterRelTags.hasNext()) {
								
								Tag tag=iterRelTags.next();
								
								if (tag.getKey().compareTo("ref")==0) {
									
									ref=tag.getValue();
									
									pairs.add(new MyPair<Long, String>(relId, ref));
									
									break;
								}
							}
						}
						
						Collections.sort(pairs, new Comparator<MyPair<Long, String>>() {
							
							@Override
							public int compare(MyPair<Long, String> o1, MyPair<Long, String> o2) {
								
								return o1.getValue().compareTo(o2.getValue());
							}
						});
						
						Iterator<MyPair<Long, String>> iterPairs=pairs.iterator();
						
						int pos=1;
						
						while(iterPairs.hasNext()) {
							
							MyPair<Long, String> pair=iterPairs.next();
							
							Log.warning("   "+String.format("[%02d]", pos)+" Ref <"+pair.getValue()+"> of relation with id #"+pair.getKey());
							
							pos++;
						}
						*/						
					}				
				}
				else if (key.compareTo("route") == 0) {
					
					if (value.compareTo("PTv2") == 0) {
						
						Log.info("Checking route <PTv2>");
						
						mPTv2Checker.checkRelations(relIds);					
					}
					else if (value.compareTo("BusNetwork") == 0) {
						
						Log.info("Checking route <BusNetwork>");
						
						mBusNetworkChecker.checkRelations(relIds);						
					}
					else if (value.compareTo("hiking") == 0) {
						
						Log.info("Checking route <Hiking>");
						
						mHikingChecker.checkRelations(relIds);						
					}
					else {
						
						Log.error("Unknown value '"+value+"' for check <route>");						
					}
						
				}
				else if (key.compareTo("boundary") == 0) {
					
					if (value.compareTo("admin") == 0) {
						
						Log.info("Checking boundary <admin>");
						
						mAdminBoundaryChecker.checkRelations(relIds);					
					}
					else {
						
						Log.error("Unknown value <"+value+"> for check <boundary>");						
					}
						
				}
				else {
					
					Log.error("Unknown check key <"+key+"> and value <"+value+">");
				}				
			}			
		}
		else if (element.getNodeName().compareTo("list") == 0) {
			
			String text = "List with tags: ";
			
			Iterator<Tag> iter = tags.iterator();
			
			while(iter.hasNext()) {
				
				Tag tag = iter.next();
				
				text+="'"+tag.getKey()+"'='"+tag.getValue()+"' ";
			}
			
			Log.info(text);
			
			Instant start = Instant.now();
			
			List<Long> relIds = mDatabase.getRelationsIdsByTags(tags);
			
			Instant end = Instant.now();
		
			Duration time = Duration.between(start, end);
			
			if (relIds == null) {
				
				Log.error("List error!!!");
			}
			else
				Log.info("List found "+relIds.size()+" relation(s) in "+time.toMillis()+" ms");
			
			if (!element.hasAttributes()) {
				
				Log.error("List has no attributes");
				
				return false;
			}
			
			NamedNodeMap nodeMap = element.getAttributes();
			
			for (int i=0; i<nodeMap.getLength(); i++) {
				
				Node n=nodeMap.item(i);
				
				String key=n.getNodeName();
				String value=n.getNodeValue();
				
				if (key.compareTo("fileName") == 0) {
					
					String fileName = value;
					
					Log.info("List to fileName='" + fileName + "'");
					
					listRelMembersToFile(relIds.get(0), fileName);
					
				}
				else {
					
					Log.error("Unknown list key <"+key+"> and value <"+value+">");
				}	
			}
		}
		else if (element.getNodeName().compareTo("convert") == 0) {
			
			String text = "Convert with tags: ";
			
			Iterator<Tag> iter = tags.iterator();
			
			while(iter.hasNext()) {
				
				Tag tag = iter.next();
				
				text+="'"+tag.getKey()+"'='"+tag.getValue()+"' ";
			}
			
			Log.info(text);
			
			Instant start = Instant.now();
			
			List<Long> relIds = mDatabase.getRelationsIdsByTags(tags);
			
			Instant end = Instant.now();
		
			Duration time = Duration.between(start, end);
			
			if (relIds == null) {
				
				Log.error("Convert error! relIds==null");
			}
			else
				Log.info("Convert found "+relIds.size()+" relation(s) in "+time.toMillis()+" ms");
			
			mBusLinesDatabase.processRels(relIds);
			
		}
		else {
			
			Log.warning("Unknown XML element <" + element.getNodeName() + ">");
			
			return true;
		}
		
		NodeList childNodes = element.getChildNodes();
		
		for(int i=0; i<childNodes.getLength(); i++) {
			
			Node childNode = childNodes.item(i);
			
			if (childNode.getNodeType() != Node.ELEMENT_NODE)
				continue;
			
			ArrayList<Tag> childTags=null;
			
			if (tags!=null) {
				childTags=new ArrayList<Tag>(tags);
			}
			
			if (!processElement((Element)childNode, childTags)) {
				
				return false;
			}
		}
		
		return true;
	}
	
	private static boolean openDataBase(Element element) {
		
		String mapFileName = element.getAttribute(XML_ATTRIB_INPUT_FILE_NAME);
		
		if (mapFileName.isEmpty()) {
			
			Log.error("No attrib <" + XML_ATTRIB_INPUT_FILE_NAME +
					"> in XML node <" + XML_MAIN_NODE_NAME + ">");
			
			return false;
		}
		
		Log.info("Map FileName: <" + mapFileName + ">");
		
		Log.info("Map Folder: <" + mPath + ">");
		
		File folder = new File(mPath);
		
		FilenameFilter filter = new FilenameFilter() {
			
	        public boolean accept(File directory, String fileName) {
	        	
	        	if (!fileName.endsWith(".db"))
	        		return false;
	        	
	        	if (!fileName.startsWith(mapFileName))
	        		return false;
	        	
	        	return true;
		        
	        }
	    };
		
		File[] mapDatabaseFiles = folder.listFiles(filter);
		
		if ((mapDatabaseFiles == null) || (mapDatabaseFiles.length < 1)) {
		
			Log.error("No <" + mapFileName + "*.db> map files were found!");
			
			return false;	
		}
		
		File mapDatabaseFile = mapDatabaseFiles[0];
		
		Log.info("Opening map database <" + mapDatabaseFile.getAbsolutePath() + ">");
		
		mDatabase = new OsmDatabase();
		
		if (!mDatabase.openDatabase(mapDatabaseFile.getAbsolutePath())) {
			
			Log.error("Error opening OSM Database");
			
			return false;
		}
		
		return true;		
	}
	
	private static List<Long> readFilterFile(String inputFilterFileName) {
		
		ArrayList<Long> relsToProcess = new ArrayList<Long>();
		
		Log.info("Input filter file name: <" + inputFilterFileName + ">");
		
		try {
			
			BufferedReader reader = new BufferedReader(new FileReader(inputFilterFileName));
			
			String line;
			
			while((line = reader.readLine()) != null) {
				
				line = line.trim();
				
				if (line.isEmpty()) {
					
					continue;
				}
				
				Log.debug("Filter line: " + line);
				
				try {
					
					long id = Long.parseLong(line.trim());
					
					Log.info("Add relation #" + id + " to filter");
					
					relsToProcess.add(id);
				}
				catch (NumberFormatException e) {
					
					Log.error("Parsing filter line <"+line.trim()+"> error: " + e.getMessage());
				}
			}
			
			reader.close();
			
		} catch (FileNotFoundException e) {
			
			Log.error("Filter FileReader error: " + e.getMessage());
			
			relsToProcess = null;
		}
		catch (IOException e) {
		
			Log.error("Filter FileReader error: " + e.getMessage());
			
			relsToProcess = null;
		}
		
		return relsToProcess;
	}
	
	private static void createBoundaryFile() {
	
		// Create boundary GeoJSON file...
		
		mDatabase.readDatabaseInfo();
	
		GeoJSONFile boundaryGeoJsonFile = new GeoJSONFile(mPath + "/" + "boundary.geojson", APP_NAME);
		
		long wayId = 0;
		int version = 0;
		Date timeStamp = null;
		OsmUser user = null;
		long changesetId = 0;
		
		Collection<Tag> wayTags = new ArrayList<Tag>();
		
		wayTags.add(new Tag("pbf_date", mDatabase.mPbfDateStamp));
		wayTags.add(new Tag("pbf_time", mDatabase.mPbfTimeStamp));
		
		ArrayList<WayNode> wayNodes = new ArrayList<WayNode>();
		
		wayNodes.add(new WayNode(1, mDatabase.mMaxLat, mDatabase.mMinLon));
		wayNodes.add(new WayNode(2, mDatabase.mMaxLat, mDatabase.mMaxLon));
		wayNodes.add(new WayNode(3, mDatabase.mMinLat, mDatabase.mMaxLon));
		wayNodes.add(new WayNode(4, mDatabase.mMinLat, mDatabase.mMinLon));
		wayNodes.add(new WayNode(5, mDatabase.mMaxLat, mDatabase.mMinLon));
		
		CommonEntityData entityData = new CommonEntityData(wayId, version,
				timeStamp, user, changesetId, wayTags);
		
		Way boundaryWay = new Way(entityData, wayNodes);
		
		boundaryGeoJsonFile.addWay(boundaryWay);
		
		boundaryGeoJsonFile.close();
	}
	
	private static void listRelMembersToFile(Long relId, String fileName) {
		
		List<RelationMember> members = mDatabase.getRelationMembers(relId);
		
		if (members == null) {
			
			Log.error("Error while getting members of relation #" + relId);
			
			return;
		}
		
		ArrayList<String> memberNames = new ArrayList<String>();
		
		Iterator<RelationMember> iterMember = members.iterator();
		
		while(iterMember.hasNext()) {
			
			RelationMember member = iterMember.next();
			
			EntityType memberType = member.getMemberType();
			
			Collection<Tag> memberTags = null;
			
			String memberName;
			
			if (memberType == EntityType.Node) {
				
				memberTags = mDatabase.getNodeTags(member.getMemberId());
				
				memberName = "Node #" + member.getMemberId();
			}
			else if (memberType == EntityType.Way) {
				
				memberTags = mDatabase.getWayTags(member.getMemberId());
				
				memberName = "Way #" + member.getMemberId();
			}
			else if (memberType == EntityType.Relation) {
				
				memberTags = mDatabase.getRelationTags(member.getMemberId());
				
				memberName = "Relation #" + member.getMemberId();
			}
			else {
				
				Log.warning("ListRelMembersToFile. Unknown memberType");
				
				memberName = "Unknown #" + member.getMemberId();
			}
			
			if (memberTags != null) {
				
				//String name = null;
				
				Iterator<Tag> iterTags = memberTags.iterator();
				
				while(iterTags.hasNext()) {
					
					Tag tag = iterTags.next();
					
					if (tag.getKey().compareTo("name") == 0) {
						
						memberName = tag.getValue();
					}
				}
			}
			
			memberNames.add(memberName);
		}
		
		Collections.sort(memberNames);
		
		int count = 0;
		
		Iterator<String> iterMemberNames = memberNames.iterator();
		
		while(iterMemberNames.hasNext()) {
			
			String memberName = iterMemberNames.next();
			
			count++;
			
			Log.debug(String.format(Locale.US,  "Pos %4d: ", count) + memberName);		
		}
	}
	
	private static void openOutputGeoJsonFiles(String outputFileName) {
		
		mOutputGeoJsonFiles = new GeoJSONFile[3];
		
		String baseFileName = mPath + "/" + "errors";
		
		if (outputFileName != null) {
			
			if (!outputFileName.isEmpty()) {
				
				baseFileName += "_" + outputFileName;
			}
		}
		
		String outputGeoJsonFileNameHigh = baseFileName + "_high.geojson";
		mOutputGeoJsonFiles[LEVEL_HIGH] = new GeoJSONFile(outputGeoJsonFileNameHigh, APP_NAME);
		Log.info("Output GeoJSON file <" + outputGeoJsonFileNameHigh + ">");
		
		String outputGeoJsonFileNameMedium = baseFileName + "_medium.geojson";
		mOutputGeoJsonFiles[LEVEL_MEDIUM] = new GeoJSONFile(outputGeoJsonFileNameMedium, APP_NAME);
		Log.info("Output GeoJSON file <" + outputGeoJsonFileNameMedium + ">");
		
		String outputGeoJsonFileNameLow = baseFileName + "_low.geojson";
		mOutputGeoJsonFiles[LEVEL_LOW] = new GeoJSONFile(outputGeoJsonFileNameLow, APP_NAME);
		Log.info("Output GeoJSON file <" + outputGeoJsonFileNameLow + ">");
	}
}
