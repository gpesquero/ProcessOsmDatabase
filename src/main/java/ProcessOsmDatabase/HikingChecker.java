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

public class HikingChecker extends BaseChecker {
	
	public HikingChecker(List<Long> relsToProcess) {
		
		mRelsToProcess = relsToProcess;
	}
	
	public void checkRelation(Relation relation) {
		
		boolean processed = false;
		
		Collection<Tag> relTags = relation.getTags();
		
		Iterator<Tag> tagIter = relTags.iterator();
		
		while(tagIter.hasNext()) {
			
			Tag tag = tagIter.next();
			
			if (tag.getKey().compareTo("type") == 0) {
				
				if (tag.getValue().compareTo("route") == 0) {
					
					checkHikingRoute(relation);
					
					processed = true;
					
					break;
				}
				else if (tag.getValue().compareTo("superroute") == 0) {
				
					checkHikingSuperRoute(relation);
					
					processed = true;
					
					break;
				}
				else {
					
					Coord coord = mDatabase.getRelationCoord(relation);
					
					String description = "Hiking: Relation #" + relation.getId() +
							" has an incorrect type '" + tag.getValue()+"'";
					
					//Log.warning(description);
					
					addErrorNode(ErrorLevel.HIGH, coord, relation.getId(), description);
					
					break;
				}
			}				
		}
		
		if (!processed) {
			
			Log.warning("Hiking: Relation #" + relation.getId() + " is not a route or superroute");
		}
	}
	
	public void checkHikingSuperRoute(Relation relation) {
		
		boolean isHikingRoute = false;
		
		Collection<Tag> relTags = relation.getTags();
		
		Iterator<Tag> tagIter = relTags.iterator();
		
		while(tagIter.hasNext()) {
			
			Tag tag = tagIter.next();
			
			if (tag.getKey().compareTo("route") == 0) {
				
				if (tag.getValue().compareTo("hiking") == 0) {
					
					isHikingRoute = true;
				}
			}
		}
				
		if (!isHikingRoute) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Hiking Super Route Relation #" + relation.getId() +
					" is not a hiking route";
			
			Log.warning(description);
			
			addErrorNode(ErrorLevel.HIGH, coord, relation.getId(), description);
		}
		
		int numberOfRoutes = 0;
		
		List<RelationMember> members = relation.getMembers();
		
		for(int pos=0; pos<members.size(); pos++) {
			
			RelationMember member = members.get(pos);
			
			if (!member.getMemberRole().isEmpty()) {
				
				Coord coord = mDatabase.getRelationCoord(relation);
				
				String description = "Hiking: Super Route Relation #" + relation.getId() +
						": Member in pos '" + pos + "' does not have an empty role '" +
						member.getMemberRole() + "'";
				
				//Log.warning(description);
				
				addErrorNode(ErrorLevel.HIGH, coord, relation.getId(), description);		
			}
			
			if (member.getMemberType() == EntityType.Relation) {
				
				Relation routeRel = mDatabase.getRelationById(member.getMemberId());
				
				checkHikingRoute(routeRel);
				
				numberOfRoutes++;				
			}
			else {
				
				Coord coord = mDatabase.getRelationCoord(relation);
				
				String description = "Hiking: Super Route Relation #" + relation.getId() +
						": Member in pos '" + pos + "' is not a relation";
				
				//Log.warning(description);
				
				addErrorNode(ErrorLevel.HIGH, coord, relation.getId(), description);
			}				
		}
		
		if (numberOfRoutes < 1) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Hiking: Super Route Relation #" + relation.getId() +
					": Super Route does not have any child relation";
			
			//Log.warning(description);
			
			addErrorNode(ErrorLevel.HIGH, coord, relation.getId(), description);
		}
		else if (numberOfRoutes < 2) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Hiking: Super Route Relation #" + relation.getId() +
					": Super Route only has 1 child relation";
			
			//Log.warning(description);
			
			addErrorNode(ErrorLevel.HIGH, coord, relation.getId(), description);
		}
	}
	
	public void checkHikingRoute(Relation relation) {
		
		boolean isHikingRoute = false;
		boolean hasRefTag = false;
		boolean hasOsmcSymbolTag = false;
		
		Collection<Tag> relTags = relation.getTags();
		
		Iterator<Tag> tagIter = relTags.iterator();
		
		while(tagIter.hasNext()) {
			
			Tag tag = tagIter.next();
			
			if (tag.getKey().compareTo("route") == 0) {
				
				if (tag.getValue().compareTo("hiking") == 0) {
					
					isHikingRoute = true;
				}
			}
			else if (tag.getKey().compareTo("osmc:symbol") == 0) {
				
				hasOsmcSymbolTag = true;
			}
			else if (tag.getKey().compareTo("ref") == 0) {
				
				hasRefTag = true;
			}
		}
				
		if (!isHikingRoute) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Hiking Route Relation #" + relation.getId() +
					" is not a hiking route";
			
			//Log.warning(description);
			
			addErrorNode(ErrorLevel.HIGH, coord, relation.getId(), description);
		}
		
		if (!hasRefTag) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Hiking Route Relation #" + relation.getId() +
					" does not have 'ref' tag";
			
			//Log.warning(description);
			
			addErrorNode(ErrorLevel.LOW, coord, relation.getId(), description);
		}
		
		if (!hasOsmcSymbolTag) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Hiking Route Relation #" + relation.getId() +
					" does not have 'osmc:symbol' tag";
			
			//Log.warning(description);
			
			addErrorNode(ErrorLevel.LOW, coord, relation.getId(), description);
		}
		
		//////////////////////////////
		// Check route members
	}

	@Override
	protected void addErrorNode(ErrorLevel level, Coord coord, long relationId, String description) {
		
		// Get route ref
		
		String routeRef = mDatabase.getRelationTagValue(relationId, "ref");
				
		if (routeRef == null) {
					
			routeRef = "????";
		}
		
		String title = "Hiking Route Ref '" + routeRef + "'";
		
		addNodeToGeoJson(level, coord, relationId, title, description);
	}
}
