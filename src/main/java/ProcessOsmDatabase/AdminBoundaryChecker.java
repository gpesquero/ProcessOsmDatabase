package ProcessOsmDatabase;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.RelationMember;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.utilslibrary.Coord;
import org.utilslibrary.Log;

public class AdminBoundaryChecker extends BaseChecker {
	
	public AdminBoundaryChecker(List<Long> relsToProcess) {
		
		mRelsToProcess = relsToProcess;
	}
	
	protected void addErrorNode(ErrorLevel level, Coord coord,
			long relationId, String description) {
		
		// Get admin boundary name
		
		String adminName = mDatabase.getRelationTagValue(relationId, "name");
						
		if (adminName == null) {
							
			adminName = "????";
		}
		
		String title = "Admin Boundary '" + adminName + "'";
		
		addNodeToGeoJson(level, coord, relationId, title, description);
	}
	
	public void checkRelations(List<Long> relIds) {
		
		Iterator<Long> relIter = relIds.iterator();
		
		while(relIter.hasNext()) {
			
			Long relId = relIter.next();
			
			Log.debug("Checking admin boundary of relation with id=" + relId);
			
			Relation rel = mDatabase.getRelationById(relId);
			
			if (rel != null) {
				
				checkRelation(rel);
			}
		}	
	}
	
	public void checkRelation(Relation relation) {
		
		long relationId = relation.getId();
		
		boolean hasNameTag = false;
		boolean hasAdminLevelTag = false;
		
		String adminLevelValue = null;
		
		Collection<Tag> relTags = relation.getTags();
		
		Iterator<Tag> tagIter = relTags.iterator();
		
		while(tagIter.hasNext()) {
			
			Tag tag = tagIter.next();
			
			if (tag.getKey().compareTo("name")==0) {
				
				hasNameTag = true;
			}
			else if (tag.getKey().compareTo("admin_level")==0) {
				
				hasAdminLevelTag = true;
				
				adminLevelValue = tag.getValue();
			}
		}
		
		if (!hasNameTag) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Admin boundary does not have a 'name' tag";
			
			Log.debug(description);
			
			addErrorNode(ErrorLevel.MEDIUM, coord, relationId, description);
		}
		
		if (!hasAdminLevelTag) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Admin boundary does not have a 'admin_level' tag";
			
			Log.debug(description);
			
			addErrorNode(ErrorLevel.MEDIUM, coord, relationId, description);
		}
		else {
			
			// Check adminLevel value
			
			try {
				
				int value = Integer.parseInt(adminLevelValue);
				
				if ((value<2) || (value>10)) {
					
					Coord coord = mDatabase.getRelationCoord(relation);
					
					String description = "'admin_level' value '" + adminLevelValue +"' out of range 2-10";
					
					addErrorNode(ErrorLevel.HIGH, coord, relationId, description);
				}
			}
			catch(NumberFormatException e) {
				
				Coord coord = mDatabase.getRelationCoord(relation);
				
				String description = "Incorrect 'admin_level' value '" + adminLevelValue +"'";
				
				addErrorNode(ErrorLevel.HIGH, coord, relationId, description);
			}
		}
		
		// Check relation members....
		
		boolean hasAdminCentre = false;
		
		List<RelationMember> members = mDatabase.getRelationMembers(relationId);
		
		if (members.size() == 0) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Relation does not have any member";
			
			Log.warning(description);
			
			addErrorNode(ErrorLevel.HIGH, coord, relationId, description);
		}
		
		Iterator<RelationMember> iter = members.iterator();
		
		int pos = 0; 
		
		while(iter.hasNext()) {
			
			RelationMember member = iter.next();
			
			String role = member.getMemberRole();
			
			if (role.compareTo("admin_centre") == 0) {
				
				hasAdminCentre = true;
				
				EntityType type = member.getMemberType();
				
				if (type != EntityType.Node) {
					
					Coord coord = mDatabase.getRelationCoord(relation);
					
					String description = "Relation member with role 'admin_centre' is not a node";
					
					addErrorNode(ErrorLevel.MEDIUM, coord, relationId, description);
				}
			}
			else if (role.compareTo("outer") == 0) {
				
				// TO-DO
				
			}
			else if (role.compareTo("inner") == 0) {
				
				// TO-DO
				
			}
			else if (role.compareTo("label") == 0) {
				
				// TO-DO
				
			}
			else if (role.compareTo("subarea") == 0) {
				
				// TO-DO
				
			}
			else if (role.isEmpty()) {
				
				Coord coord = mDatabase.getRelationCoord(relation);
				
				String description = "Relation member in pos '" + pos +"' has an empty role";
				
				//Log.warning(description);
				
				addErrorNode(ErrorLevel.MEDIUM, coord, relationId, description);
			}
			else {
				
				Coord coord = mDatabase.getRelationCoord(relation);
				
				String description = "Unknown relation member role '" + role + "' in pos '" + pos +"'";
				
				//Log.warning(description);
				
				addErrorNode(ErrorLevel.MEDIUM, coord, relationId, description);
			}
			
			pos++;
		}
		
		if (!hasAdminCentre) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Admin boundary does not have an 'admin_centre' member";
			
			Log.debug(description);
			
			addErrorNode(ErrorLevel.LOW, coord, relationId, description);
		}
	}
}
