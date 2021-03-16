package ProcessOsmDatabase;

import java.io.File;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.openstreetmap.osmosis.core.domain.v0_6.CommonEntityData;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.OsmUser;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.RelationMember;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.utilslibrary.Coord;
import org.utilslibrary.GeoJSONFile;
import org.utilslibrary.Log;
import org.utilslibrary.MyWay;
import org.utilslibrary.OsmDatabase;

public class BusLinesDatabase {
	
	private OsmDatabase mDatabase = null;
	
	private String mOutputDir = null;
	
	private int mBusLinesCount = 0;
	
	private final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
	
	private final String DB_URL = "jdbc:mysql://localhost/osm_db";
	
	private final String DB_USER = "osm_writer";
	
	public BusLinesDatabase(String dbPassword) {
		
		initSqlDatabase(dbPassword);
	}
	
	public void setOsmDatabase(OsmDatabase database) {
		
		mDatabase = database;
	}
	
	public void setOutputDir(String outputDir) {
		
		mOutputDir = outputDir;
		
		File dir = new File(outputDir);
		
		if (!dir.exists()) {
			
			if (!dir.mkdirs()) {
				
				Log.error("Error creating outputDir '" + outputDir + "'");
			}
		}
	}
	
	public int getBusLinesCount() {
		
		return mBusLinesCount;
	}
	
	public void processRels(List<Long> relIds) {
		
		Iterator<Long> iter = relIds.iterator();
		
		while(iter.hasNext()) {
			
			Long relId = iter.next();
			
			Relation rel = mDatabase.getRelationById(relId);
			
			processRelation(rel);
		}
	}
	
	private void processRelation(Relation relation) {
		
		boolean processed = false;
		
		Collection<Tag> relTags = relation.getTags();
	
		Iterator<Tag> tagIter = relTags.iterator();
		
		while(tagIter.hasNext()) {
			
			Tag tag = tagIter.next();
			
			if (tag.getKey().compareTo("type")==0) {
				
				if ((tag.getValue().compareTo("network") == 0) ||
					(tag.getValue().compareTo("route_master") == 0)) {
					
					processParentRelation(relation);
					
					processed = true;
					
					break;
				}
				else if (tag.getValue().compareTo("route") == 0) {
					
					processRouteRelation(relation);
					
					processed = true;
					
					break;
				}
				
				else {
					
					//Coord coord = mDatabase.getRelationCoord(relation);
					
					//String description = "Relation has an incorrect type '" + tag.getValue() + "'";
					
					Log.warning("Unknown 'type' tag in relation #" + relation.getId());
					
					break;
				}
			}				
		}
		
		if (!processed) {
			
			//Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Relation is not a 'network', 'route_master' or 'route'";
			
			Log.warning(description);
			
			//addErrorNode(ErrorLevel.HIGH, coord, relation.getId(), description);
		}
	}
	
	private void processParentRelation(Relation relation) {
		
		Collection<RelationMember> relMembers = relation.getMembers();
	
		Iterator<RelationMember> iter = relMembers.iterator();
		
		while(iter.hasNext()) {
			
			RelationMember member = iter.next();
			
			EntityType type = member.getMemberType();
			
			if (type == EntityType.Relation) {
				
				long childRelId = member.getMemberId();
				
				Relation childRelation = mDatabase.getRelationById(childRelId);
				
				processRelation(childRelation);
			}
			else {
				
				Log.warning("Incorrect parent rel member type: " + type.toString());
			}
		}
	}
	
	private void processRouteRelation(Relation relation) {
		
		String ref = null;
		String name = null;
		
		Collection<Tag> relTags = relation.getTags();
	
		Iterator<Tag> tagIter = relTags.iterator();
		
		while(tagIter.hasNext()) {
			
			Tag tag = tagIter.next();
			
			String key = tag.getKey();
			
			String value = tag.getValue();
			
			if (key.compareTo("ref") == 0) {
				
				ref = value;
			}
			else if (key.compareTo("name") == 0) {
				
				name = value;
			} 
		}
		
		if (ref == null) {
			
			Log.warning("Bus Line does not have 'ref' tag");
		}
		
		if (name == null) {
			
			Log.warning("Bus Line does not have 'name' tag");
		}
		
		//Log.info("Bus relId=" + relation.getId() + " [" + ref + "] " + name);
		
		String outFileName = mOutputDir + "bus_line_" + relation.getId() + ".geojson";
		
		GeoJSONFile outFile = new GeoJSONFile(outFileName, "ProcessOsmDatabase");
		
		Collection<RelationMember> relMembers = relation.getMembers();
		
		Iterator<RelationMember> iter = relMembers.iterator();
		
		while(iter.hasNext()) {
			
			RelationMember member = iter.next();
			
			long memberId = member.getMemberId();
			
			String memberRole = member.getMemberRole();
			
			EntityType type = member.getMemberType();
			
			if (type == EntityType.Node) {
				
				String nodeRole = null;
				
				if (memberRole.compareTo("stop") == 0) {
					
					nodeRole = "stop";
				}
				else if (memberRole.compareTo("platform") == 0) {
					
					nodeRole = "platform";
				}
				else {
					
					Log.warning("Bus line node has incorrect rol '" + memberRole + "'");
					
					nodeRole = "";
				}
				
				Coord nodeCoord = mDatabase.getNodeCoord(memberId);
				
				double nodeLon = nodeCoord.mLon;
				double nodeLat = nodeCoord.mLat;
				
				Collection<Tag> nodeTags = new ArrayList<Tag>();
				
				nodeTags.add(new Tag("role", nodeRole));
				
				long nodeId = 0;
				int version = 0;
				Date timeStamp = null;
				OsmUser user = null;
				long changesetId = 0;
				
				CommonEntityData nodeData = new CommonEntityData(nodeId, version, timeStamp,
						user, changesetId, nodeTags);
				
				Node busNode = new Node(nodeData, nodeLat, nodeLon);
				
				outFile.addNode(busNode);
			}
			else if (type == EntityType.Way) {
				
				Way way = mDatabase.getWayById(memberId);
				
				//Log.warning("Bus line member is a relation!!");
				
				long wayId = 0;
				int version = 0;
				Date timeStamp = null;
				OsmUser user = null;
				long changesetId = 0;
				
				Collection<Tag> wayTags = new ArrayList<Tag>();
				
				CommonEntityData wayData = new CommonEntityData(wayId, version, timeStamp,
						user, changesetId, wayTags);
				
				MyWay otherWay = new MyWay(wayData, way.getWayNodes());
				
				otherWay.updateWayNodeCoords(mDatabase);
				
				outFile.addWay(otherWay.getWay());
				
			}
			else if (type == EntityType.Relation) {
				
				Log.warning("Bus line member is a relation!!");
			}
		}
		
		outFile.close();
		
		mBusLinesCount++;
	}
	
	private void initSqlDatabase(String dbPassword) {
		
		try {
			
			// Register JDBC driver
			Class.forName(JDBC_DRIVER);
		}
		catch(ClassNotFoundException e) {
			
			Log.error("ClassNotFoundException for: " + JDBC_DRIVER);
			
			return;
		}
		
		try {
			DriverManager.getConnection(DB_URL, DB_USER, dbPassword);
			
		} catch (SQLException e) {
			
			Log.error("SqlException: " + e.getMessage());
			
			return;
		}
		
	}
}
