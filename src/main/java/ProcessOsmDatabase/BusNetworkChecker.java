package ProcessOsmDatabase;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.utilslibrary.Coord;
import org.utilslibrary.Log;

import ProcessOsmDatabase.BaseChecker.ErrorLevel;

public class BusNetworkChecker extends BaseChecker {
	
	public BusNetworkChecker(List<Long> relsToProcess) {
		
		mRelsToProcess = relsToProcess;
	}

	@Override
	public void checkRelation(Relation relation) {
		
		boolean processed = false;
		
		Collection<Tag> relTags = relation.getTags();
		
		Iterator<Tag> tagIter = relTags.iterator();
		
		while(tagIter.hasNext()) {
			
			Tag tag = tagIter.next();
			
			if (tag.getKey().compareTo("type")==0) {
				
				if (tag.getValue().compareTo("route") == 0) {
					
					checkRoute(relation);
					
					processed = true;
					
					break;
				}
				else if (tag.getValue().compareTo("route_master") == 0) {
				
					checkRouteMaster(relation);
					
					processed = true;
					
					break;
				}
				else {
					
					Coord coord = mDatabase.getRelationCoord(relation);
					
					String description = "Relation has an incorrect type '" + tag.getValue() + "'";
					
					Log.warning("Unknown 'type' tag in relation #" + relation.getId());
					
					addErrorNode(ErrorLevel.HIGH, coord, relation.getId(), description);
					
					break;
				}
			}				
		}
		
		if (!processed) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Relation is not a 'route' or 'route_master'";
			
			Log.warning(description);
			
			addErrorNode(ErrorLevel.HIGH, coord, relation.getId(), description);
		}
	}
	
	public void checkRouteMaster(Relation relation) {
		
		// Check if master route belongs to a network...
		
		List<Long> parentRels = mDatabase.getParentRelIdsOfRelation(relation.getId());
		
		if (parentRels.size() == 0) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Route Master Relation does not belong to any parent relation";
			
			addErrorNode(ErrorLevel.HIGH, coord, relation.getId(), description);
		}
		else if (parentRels.size() > 1) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Route Master Relation belongs to more than 1 relation";
			
			addErrorNode(ErrorLevel.HIGH, coord, relation.getId(), description);
		}
		else {
			
			long parentRelId = parentRels.get(0);
			
			Relation parentRel = mDatabase.getRelationById(parentRelId);
			
			// Check that parent rel is a bus network
			
			boolean isNetwork = false;
			
			Collection<Tag> relTags = parentRel.getTags();
			
			Iterator<Tag> tagIter = relTags.iterator();
			
			while(tagIter.hasNext()) {
				
				Tag tag = tagIter.next();
				
				if (tag.getKey().compareTo("type")==0) {
					
					if (tag.getValue().compareTo("network") == 0) {
						
						isNetwork = true;
						
						break;
					}
				}
			}
			
			if (!isNetwork) {
				
				Coord coord = mDatabase.getRelationCoord(relation);
				
				String description = "Parent of Route Master Relation is not a network";
				
				addErrorNode(ErrorLevel.HIGH, coord, relation.getId(), description);
			}
		}
	}

	public void checkRoute(Relation relation) {
		
		// Check if route belongs to a route master...
		
		List<Long> parentRels = mDatabase.getParentRelIdsOfRelation(relation.getId());
		
		if (parentRels.size() == 0) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Route Relation does not belong to any parent relation";
			
			addErrorNode(ErrorLevel.HIGH, coord, relation.getId(), description);
		}
		else if (parentRels.size() > 1) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Route Relation belongs to more than 1 relation";
			
			addErrorNode(ErrorLevel.HIGH, coord, relation.getId(), description);
		}
		else {
			
			long parentRelId = parentRels.get(0);
			
			Relation parentRel = mDatabase.getRelationById(parentRelId);
			
			// Check that parent rel is a bus network
			
			boolean isNetwork = false;
			boolean isRouteMaster= false;
			
			Collection<Tag> relTags = parentRel.getTags();
			
			Iterator<Tag> tagIter = relTags.iterator();
			
			while(tagIter.hasNext()) {
				
				Tag tag = tagIter.next();
				
				if (tag.getKey().compareTo("type")==0) {
					
					if (tag.getValue().compareTo("network") == 0) {
						
						isNetwork = true;
						
						break;
					}
					else if (tag.getValue().compareTo("route_master") == 0) {
						
						isRouteMaster = true;
						
						break;
					}
				}
			}
			
			if ((!isNetwork) && (!isRouteMaster)) {
				
				Coord coord = mDatabase.getRelationCoord(relation);
				
				String description = "Parent of Master Route Relation is not a network/route_master";
				
				addErrorNode(ErrorLevel.HIGH, coord, relation.getId(), description);
			}
		}
	}

	@Override
	protected void addErrorNode(ErrorLevel level, Coord coord, long relationId, String description) {
		
		// Get bus line ref
		
		String busLineRef = mDatabase.getRelationTagValue(relationId, "ref");
						
		if (busLineRef == null) {
							
			busLineRef = "????";
		}
				
		String title = "Bus Ref '" + busLineRef + "'";
				
		addNodeToGeoJson(level, coord, relationId, title, description);
	}

}
