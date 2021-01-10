package ProcessOsmDatabase;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

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
		
		Collection<Tag> relTags = relation.getTags();
		
		Iterator<Tag> tagIter = relTags.iterator();
		
		while(tagIter.hasNext()) {
			
			Tag tag = tagIter.next();
			
			if (tag.getKey().compareTo("name")==0) {
				
				hasNameTag = true;
			}
			else if (tag.getKey().compareTo("admin_level")==0) {
				
				hasAdminLevelTag = true;
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
		
		// Check relation members....
		
		boolean hasAdminCentre = false;
		
		List<RelationMember> members = mDatabase.getRelationMembers(relationId);
		
		Iterator<RelationMember> iter = members.iterator();
		
		while(iter.hasNext()) {
			
			RelationMember member = iter.next();
			
			String role = member.getMemberRole();
			
			if (role.compareTo("admin_centre") == 0) {
				
				hasAdminCentre = true;
			}
			
		}
		
		if (!hasAdminCentre) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Admin boundary does not have an 'admin_centre' member";
			
			Log.debug(description);
			
			addErrorNode(ErrorLevel.LOW, coord, relationId, description);
		}
	}
}
