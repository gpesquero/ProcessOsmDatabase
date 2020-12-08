package ProcessOsmDatabase;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.math3.util.Pair;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.utilslibrary.Log;
import org.utilslibrary.OsmDatabase;

public class ProcessOsmDatabase {
	
	private final static String APP_NAME = "ProcessOsmDatabase";
	
	private final static String XML_MAIN_NODE_NAME = APP_NAME;
	
	private final static String XML_ATTRIB_FILE_NAME="filename";
	
	private static String mPath = null;
	
	private static OsmDatabase mDatabase = null;
	
	private static PTv2Checker mPTv2Checker = null;
	
	private static GeoJSONFile mOutputGeoJsonFile = null;
	
	public static void main(String[] args) {
		
		Log.info("Starting ProcessOsmDatabase...");
		
		if (args.length<1) {
			
			Log.error("Missing argument <ProcessFile>. Quitting...");
			
			return;
		}
		
		mPTv2Checker = new PTv2Checker();
		
		// Set input XML file name
		
		String inputXmlFileName = args[0];
		
		Log.info("Input XML file <"+inputXmlFileName+">...");
		
		// Set output GeoJSON file name
		
		int index = inputXmlFileName.indexOf(".xml");
		
		String outputGeoJsonFileName;
		
		if (index < 0) {
			
			outputGeoJsonFileName = inputXmlFileName + ".geojson";
		}
		else {
			
			outputGeoJsonFileName = inputXmlFileName.substring(0, index) + ".geojson";
		}
		
		Log.info("Output GeoJSON file <" + outputGeoJsonFileName + ">");
		
		mOutputGeoJsonFile = new GeoJSONFile(outputGeoJsonFileName, APP_NAME);
		
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
		
		mPath=file.getParent();
		
		Element mainElement=document.getDocumentElement();
		
		Log.info("Processing nodes...");
		
		processElement(mainElement);
		
		// Close output GeoJSON file...
		mOutputGeoJsonFile.close();
		
		Log.info("ProcessOsmDatabase finished...");
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
				
				mPTv2Checker.setOsmDatabase(mDatabase);
				
				mPTv2Checker.setGeoJSONFile(mOutputGeoJsonFile);
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
			
			NamedNodeMap map=element.getAttributes();
			
			for (int i=0; i<map.getLength(); i++) {
				
				Node n=map.item(i);
				
				String key=n.getNodeName();
				String value=n.getNodeValue();
				
				tags.add(new Tag(key, value));
				
			}
		}
		else if (element.getNodeName().compareTo("check")==0) {
			
			String text="Check with tags: ";
			
			Iterator<Tag> iter=tags.iterator();
			
			while(iter.hasNext()) {
				
				Tag tag=iter.next();
				
				text+="'"+tag.getKey()+"'='"+tag.getValue()+"' ";
			}
			
			Log.info(text);
			
			Instant start=Instant.now();
			
			List<Long> relIds=mDatabase.getRelationsIdsByTags(tags);
			
			Instant end=Instant.now();
		
			Duration time=Duration.between(start, end);
			
			if (relIds==null) {
				
				Log.error("Check error!!!");
			}
			else
				Log.info("Check found "+relIds.size()+" relations in "+time.toMillis()+" ms");
			
			if (!element.hasAttributes()) {
				
				Log.error("Check has no attributes");
				
				return false;
			}
			
			NamedNodeMap nodeMap=element.getAttributes();
			
			for (int i=0; i<nodeMap.getLength(); i++) {
				
				Node n=nodeMap.item(i);
				
				String key=n.getNodeName();
				String value=n.getNodeValue();
				
				if (key.compareTo("count")==0) {
					
					int count=Integer.valueOf(value);
					
					if (count==relIds.size()) {
						
						Log.info("Detected relations match with count <"+count+">");
					}
					else {
						
						Log.warning("Number of relations <"+relIds.size()+"> do not match count check <"+count+">");
						
						Iterator<Long> iterRelIds=relIds.iterator();
						
						ArrayList<Pair<Long, String>> pairs=new ArrayList<Pair<Long, String>>();
						
						while (iterRelIds.hasNext()) {
							
							Long relId=iterRelIds.next();
							
							Relation relation=mDatabase.getRelationById(relId);
							
							Collection<Tag> relTags=relation.getTags();
							
							Iterator<Tag> iterRelTags=relTags.iterator();
							
							String ref="<noref>";
							
							while (iterRelTags.hasNext()) {
								
								Tag tag=iterRelTags.next();
								
								if (tag.getKey().compareTo("ref")==0) {
									
									ref=tag.getValue();
									
									pairs.add(new Pair<Long, String>(relId, ref));
									
									break;
								}
							}
						}
						
						Collections.sort(pairs, new Comparator<Pair<Long, String>>() {
							
							@Override
							public int compare(Pair<Long, String> o1, Pair<Long, String> o2) {
								
								return o1.getValue().compareTo(o2.getValue());
							}
						});
						
						Iterator<Pair<Long, String>> iterPairs=pairs.iterator();
						
						int pos=1;
						
						while(iterPairs.hasNext()) {
							
							Pair<Long, String> pair=iterPairs.next();
							
							Log.warning("   "+String.format("[%02d]", pos)+" Ref <"+pair.getValue()+"> of relation with id #"+pair.getKey());
							
							pos++;
						}						
					}					
					
				}
				else if (key.compareTo("route")==0) {
					
					if (value.compareTo("PTv2")==0) {
						
						Log.info("Checking route <PTv2>");
						
						mPTv2Checker.checkRelations(relIds);					
					}
					else if (value.compareTo("hiking")==0) {
						
						Log.info("Checking route <Hiking>");
						
						mDatabase.checkHiking(relIds);						
					}
					else {
						
						Log.error("Unknown value <"+value+"> for check <route>");						
					}
						
				}
				else {
					
					Log.error("Unknown check key <"+key+"> and value <"+value+">");
				}
				
			}
			
		}
		else {
			
			Log.warning("Unknown XML element <"+element.getNodeName()+">");
			
			return true;
		}
		
		NodeList childNodes=element.getChildNodes();
		
		for(int i=0; i<childNodes.getLength(); i++) {
			
			Node childNode=childNodes.item(i);
			
			if (childNode.getNodeType()!=Node.ELEMENT_NODE)
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
		
		String mapFileName=element.getAttribute(XML_ATTRIB_FILE_NAME);
		
		if (mapFileName.isEmpty()) {
			
			Log.error("No attrib <"+XML_ATTRIB_FILE_NAME+
					"> in XML node <"+XML_MAIN_NODE_NAME+">");
			
			return false;			
		}
		
		Log.info("Map FileName: <"+mapFileName+">");
		
		Log.info("Map Folder: <"+mPath+">");
		
		File folder=new File(mPath);
		
		FilenameFilter filter = new FilenameFilter() {
			
	        public boolean accept(File directory, String fileName) {
	        	
	        	if (!fileName.endsWith(".db"))
	        		return false;
	        	
	        	if (!fileName.startsWith(mapFileName))
	        		return false;
	        	
	        	return true;
		        
	        }
	    };
		
		File[] mapDatabaseFiles=folder.listFiles(filter);
		
		if (mapDatabaseFiles.length<1) {
		
			Log.error("No <"+mapFileName+"*.db> map files were found!");
			
			return false;	
		}
		
		File mapDatabaseFile=mapDatabaseFiles[0];
		
		Log.info("Opening map database <"+mapDatabaseFile.getAbsolutePath()+">");
		
		mDatabase = new OsmDatabase();
		
		if (!mDatabase.openDatabase(mapDatabaseFile.getAbsolutePath())) {
			
			Log.error("Error opening OSM Database");
			
			return false;			
		}
		
		return true;		
	}
	
	/*
	private static void checkBoundaries(OsmDatabase db) {
		
		ArrayList<ArrayList<Relation>> levels=new ArrayList<ArrayList<Relation>>();
		
		for (int i=0; i<=MAX_ADMIN_LEVEL; i++) {
			
			levels.add(new ArrayList<Relation>());
		}
		
		List<Long> relIds=db.getRelationsIdsByType("boundary");
		
		if (relIds==null)
			return;
		
		Log.info("Found "+relIds.size()+" relations of type <boundary>");
		
		Iterator<Long> iter=relIds.iterator();
		
		while(iter.hasNext()) {
			
			long relId=iter.next();
			
			Relation relation=db.getRelationById(relId);
			
			Collection<Tag> tags=relation.getTags();
			
			String boundaryType=null;
			boolean isAdministrative=false;
			boolean hasAdminLevel=false;
			int adminLevel=-1;
			
			Iterator<Tag> tagIter=tags.iterator();
			
			while(tagIter.hasNext()) {
				
				Tag tag=tagIter.next();
				
				if (tag.getKey().compareTo("boundary")==0) {
					
					boundaryType=tag.getValue();
					
					if (boundaryType.compareTo("administrative")==0)
						isAdministrative=true;
				}
				else if (tag.getKey().compareTo("admin_level")==0) {
					
					adminLevel=Integer.parseInt(tag.getValue());
					
					hasAdminLevel=true;
				}
			}
			
			if (!isAdministrative) {
					
				Log.warning("Boundary relation #"+relId+" is not administrative <"+
						boundaryType+">");
					
				continue;
			}
			
			if (!hasAdminLevel) {
					
				Log.warning("Boundary relation #"+relId+" has no admin level");
					
				continue;
			}
				
			if ((adminLevel<0) || (adminLevel>MAX_ADMIN_LEVEL)) {
					
				Log.warning("Boundary relation #"+relId+" adminLevel <"+
						adminLevel+"> invalid");
					
				continue;
			}
			
			levels.get(adminLevel).add(relation);			
		}
		
		for(int level=0; level<=MAX_ADMIN_LEVEL; level++) {
			
			ArrayList<Relation> rels=levels.get(level);
			
			Log.info("Admin Level "+level+": Found "+rels.size()+" administrative boundaries");
			
			if (rels.size()>100)
				continue;
			
			Iterator<Relation> relIter=rels.iterator();
			
			while(relIter.hasNext()) {
				
				Relation rel=relIter.next();
				
				String name=null;
				
				Collection<Tag> tags=rel.getTags();
				
				Iterator<Tag> tagIter=tags.iterator();
				
				while(tagIter.hasNext()) {
					
					Tag tag=tagIter.next();
					
					if (tag.getKey().compareTo("name")==0) {
						
						name=tag.getValue();
					}
				}
				
				//System.out.println("   Relation "+rel.getId()+" Name: "+name);
			}
			
		}
	}
	*/
}
