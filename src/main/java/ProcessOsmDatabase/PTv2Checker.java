package ProcessOsmDatabase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.RelationMember;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.utilslibrary.Attributes;
import org.utilslibrary.Coord;
import org.utilslibrary.Log;
import org.utilslibrary.MyWay;
import org.utilslibrary.OsmDatabase;

public class PTv2Checker {
	
	private OsmDatabase mDatabase = null;
	
	private GeoJSONFile mOutFile = null;
	
	private static final int UNKNOWN=-1;
	private static final int ASCENDING=0;
	private static final int DESCENDING=1;
	
	public PTv2Checker() {
		
	}
	
	public void setOsmDatabase(OsmDatabase database) {
		
		mDatabase = database;
	}
	
	public void setGeoJSONFile(GeoJSONFile outFile) {
		
		mOutFile = outFile;
	}
	
	public void checkRelations(List<Long> relIds) {
		
		Iterator<Long> relIter=relIds.iterator();
		
		while(relIter.hasNext()) {
			
			Long relId=relIter.next();
			
			Log.debug("Checking PTv2 of relation with id="+relId);
			
			Relation rel=mDatabase.getRelationById(relId);
			
			if (rel != null) {
				
				checkRelation(rel);
			}
		}		
	}
	
	public void checkRelation(Relation relation) {
		
		boolean processed=false;
		
		Collection<Tag> relTags=relation.getTags();
		
		Iterator<Tag> tagIter=relTags.iterator();
		
		while(tagIter.hasNext()) {
			
			Tag tag=tagIter.next();
			
			if (tag.getKey().compareTo("type")==0) {
				
				if (tag.getValue().compareTo("route")==0) {
					
					checkRoute(relation);
					
					processed=true;
					
					break;
				}
				else if (tag.getValue().compareTo("route_master")==0) {
				
					checkRouteMaster(relation);
					
					processed=true;
					
					break;
				}
				else {
					
					Log.warning("PTv2: Relation #"+relation.getId()+" has an incorrect type <"+tag.getValue()+">");
					
					break;
				}
			}				
		}
		
		if (!processed) {
			
			Log.warning("PTv2: Relation #"+relation.getId()+" is not a route or route_master");
		}
	}
	
	public void checkRouteMaster(Relation relation) {
		
		//boolean hasPTv2Tag=false;
		boolean isBusRoute=false;
		
		Collection<Tag> relTags=relation.getTags();
		
		Iterator<Tag> tagIter=relTags.iterator();
		
		while(tagIter.hasNext()) {
			
			Tag tag=tagIter.next();
			
			if (tag.getKey().compareTo("route_master")==0) {
				
				if (tag.getValue().compareTo("bus")==0) {
					
					isBusRoute=true;
				}
			}
			/*
			else if (tag.getKey().compareTo("public_transport:version")==0) {
				
				if (tag.getValue().compareTo("2")==0) {
					
					hasPTv2Tag=true;						
				}
			}
			*/
		}
		
		/*
		if (!hasPTv2Tag) {
			
			Log.warning("PTv2: Master Route Relation #"+relation.getId()+" has no <public_transport:version=2> tag");
		}
		*/
		
		if (!isBusRoute) {
			
			Log.warning("PTv2: Master Route Relation #"+relation.getId()+" is not a bus route");
		}
		
		int numberOfRoutes=0;
		
		List<RelationMember> members=relation.getMembers();
		
		for(int pos=0; pos<members.size(); pos++) {
			
			RelationMember member=members.get(pos);
			
			if (!member.getMemberRole().isEmpty()) {
				
				Log.warning("PTv2: Master Route Relation #"+relation.getId()+": Member in pos <"+pos+
						"> does not have an empty role <"+member.getMemberRole()+">");
			}
			
			if (member.getMemberType()==EntityType.Relation) {
				
				Relation routeRel=mDatabase.getRelationById(member.getMemberId());
				
				checkRoute(routeRel);
				
				numberOfRoutes++;				
			}
			else {
				
				Log.warning("PTv2: Master Route Relation #"+relation.getId()+": Member in pos <"+pos+
						"> is not a relation");
			}				
		}
		
		if (numberOfRoutes<1) {
			
			Log.warning("PTv2: Master Route Relation #"+relation.getId()+": Master Route does not have any relation");			
		}
		else if (numberOfRoutes<2) {
			
			Log.warning("PTv2: Master Route Relation #"+relation.getId()+": Master Route only has 1 relation");			
		}
	}
	
	public void checkRoute(Relation relation) {
		
		Log.debug("PTv2: Checking relation <"+relation.getId()+">");
		
		// Step #1: Check for tag 'public_transport:version=2'
		
		boolean hasPTv2Tag=false;
		
		Collection<Tag> relTags=relation.getTags();
		
		Iterator<Tag> tagIter=relTags.iterator();
		
		while(tagIter.hasNext()) {
			
			Tag tag=tagIter.next();
			
			if (tag.getKey().compareTo("public_transport:version")==0) {
				
				if (tag.getValue().compareTo("2")==0) {
					
					hasPTv2Tag=true;						
				}
			}				
		}
		
		if (!hasPTv2Tag) {
			
			Log.warning("PTv2: Relation #"+relation.getId()+" has no <public_transport:version=2> tag");
		}
		
		// Step #2: Check list of stops and platforms
		
		List<Long> stopNodesIds=new ArrayList<Long>();
		List<Long> waysIds=new ArrayList<Long>();
		List<Long> linkNodesIds=new ArrayList<Long>();
				
		List<RelationMember> members=relation.getMembers();
		
		boolean foundEmptyRole=false;
		boolean detectedIncorrectStopPos=false;
		boolean detectedIncorrectPlatformPos=false;
		
		Coord stopNodeCoord=null;
		
		for(int pos=0; pos<members.size(); pos++) {
		
			RelationMember member=members.get(pos);
			
			if (member.getMemberRole().compareTo("stop")==0) {
				
				if (foundEmptyRole) {
					
					// Detected a stop member in an incorrect position (shall be located
					// at the beginning of the relation)
					
					detectedIncorrectStopPos=true;
				}
				
				if (member.getMemberType()==EntityType.Node) {
					
					if (pos==(members.size()-1)) {
						
						Log.data("PTv2: Relation #"+relation.getId()+": Stop member node <"+member.getMemberId()+
								"> in pos <"+pos+"> is not followed by a <platform> member");
						
						stopNodeCoord=null;
					}
					else {
						
						String nextMemberRole=members.get(pos+1).getMemberRole();
						
						if (nextMemberRole.compareTo("platform")!=0) {
							
							Log.warning("PTv2: Relation #"+relation.getId()+": Stop member node <"+member.getMemberId()+
									"> in pos <"+pos+"> is not followed by a <platform> member");
						}
						
					}
					
					// Check that stop node has correct attributes
					
					boolean foundPublicTransportTag=false;
					
					Node stopNode=mDatabase.getNodeById(member.getMemberId());
					
					if (stopNode==null)
						continue;
					
					Collection<Tag> tags=stopNode.getTags();
					
					Iterator<Tag> nodeTagIter=tags.iterator();
					
					while(nodeTagIter.hasNext()) {
						
						Tag tag=nodeTagIter.next();
						
						if (tag.getKey().compareTo("public_transport")==0) {
							
							foundPublicTransportTag=true;
							
							if (tag.getValue().compareTo("stop_position")!=0) {
								
								Log.warning("PTv2: Relation #"+relation.getId()+": Stop node <"+member.getMemberId()+
										"> in pos <"+pos+"> has incorrect <public_transport> tag="+tag.getValue());								
							}
						}
					}
					
					if (!foundPublicTransportTag) {
						
						Log.warning("PTv2: Relation #"+relation.getId()+": Stop node <"+member.getMemberId()+
								"> in pos <"+pos+"> does not have the <public_transport> tag");
					}
					
					Long nodeId=member.getMemberId();
					
					stopNodesIds.add(nodeId);
					
					stopNodeCoord=new Coord(stopNode.getLatitude(), stopNode.getLongitude());
				}
				else {
					
					// Stop is not a node. Weird....
					
					Log.warning("PTv2: Relation #"+relation.getId()+": Stop member in pos <"+pos+"> is not a node...");
				}				
			}
			else if (member.getMemberRole().compareTo("platform")==0) {
				
				if (foundEmptyRole) {
					
					// Detected a platform member in an incorrect position (shall be located
					// at the beginning of the relation)
					
					detectedIncorrectPlatformPos=true;
				}
				
				if (member.getMemberType()==EntityType.Node) {
					
					Node platformNode = mDatabase.getNodeById(member.getMemberId());
					
					if (platformNode==null)
						continue;
					
					if (pos==0) {
						
						Log.warning("PTv2: Relation #"+relation.getId()+": Platform member node <"+
								member.getMemberId()+"> in pos <"+pos+"> does not follow a <stop> member");
						
						stopNodeCoord=null;
					}
					else {
						
						String previousMemberRole=members.get(pos-1).getMemberRole();
						
						if (previousMemberRole.compareTo("stop")!=0) {
							
							Log.warning("PTv2: Relation #"+relation.getId()+": Platform member node <"+
									member.getMemberId()+"> in pos <"+pos+"> does not follow a <stop> member");
						}
						else {
							
							// Check that distance between stop and platform nodes is less than 20 meters
							
							Coord platCoord=new Coord(platformNode.getLatitude(), platformNode.getLongitude());
							
							double distance=stopNodeCoord.distanceTo(platCoord);
							
							if (distance>20.0) {
								
								Log.warning("PTv2: Relation #"+relation.getId()+": Distance between platform member node <"+
										member.getMemberId()+"> and stop position is too big <"+
										String.format("%.1f", distance)+" meters>");
								
							}
							
						}
					}
					
					// Check that platform node has correct attributes
					
					boolean foundPublicTransportTag=false;
					
					Collection<Tag> tags=platformNode.getTags();
					
					Iterator<Tag> nodeTagIter=tags.iterator();
					
					while(nodeTagIter.hasNext()) {
						
						Tag tag=nodeTagIter.next();
						
						if (tag.getKey().compareTo("public_transport")==0) {
							
							foundPublicTransportTag=true;
							
							if (tag.getValue().compareTo("platform")!=0) {
								
								Log.warning("PTv2: Relation #"+relation.getId()+": Platform node <"+member.getMemberId()+
										"> in pos <"+pos+"> has incorrect <public_transport> tag="+tag.getValue());								
							}
						}
					}
					
					if (!foundPublicTransportTag) {
						
						Log.warning("PTv2: Relation #"+relation.getId()+": Platform node <"+member.getMemberId()+
								"> in pos <"+pos+"> does not have the <public_transport> tag");
					}
				}
				else {
					
					// Platform is not a node. Weird....
					
					Log.warning("PTv2: Relation #"+relation.getId()+": Platform member in pos <"+pos+
							"> is not a node");
				}				
			}
			else if (member.getMemberRole().isEmpty()) {
				
				// Found an empty role member. It shall be a way
				
				if (member.getMemberType()!=EntityType.Way) {
					
					Log.warning("PTv2: Empty role relation member in pos <"+pos+"> of relation <"+relation.getId()+"> is not a way");
						
					continue;
				}
				else {
					
					waysIds.add(member.getMemberId());
					
					foundEmptyRole=true;					
				}
			}
			else {
				
				Log.warning("PTv2: Relation #"+relation.getId()+": Relation member in pos <"+pos+"> has incorrect role <"+
						member.getMemberRole()+">");
			}
		}
		
		if (detectedIncorrectStopPos || detectedIncorrectPlatformPos) {
			
			Log.warning("PTv2: Relation #"+relation.getId()+": Detected incorrect stop or platform position"+
					" (Shall be located at the beginning)");
		}
		
		// Step #3: Check continuity between route ways
		
		boolean orderedRoute=true;
		
		if (waysIds.size()==0) {
			
			// No ways detected. Nothing else to check..
			
			Log.warning("PTv2: Relation <"+relation.getId()+"> has no ways");
			
			orderedRoute=false;
		}
		else if (waysIds.size()==1) {
			
			// Route has only one way. There's no need to check continuity
			
			orderedRoute=false;			
		}
		else {
			
			long prevLinkNode1=-1;
			long prevLinkNode2=-1;
			long prevLinkNode=-1;
			
			for(int pos=0; pos<waysIds.size(); pos++) {
			
				Way way = mDatabase.getWayById(waysIds.get(pos));
			
				MyWay myWay=new MyWay(way);
			
				List<WayNode> wayNodes=way.getWayNodes();
				
				if (wayNodes.size()<2) {
					
					Log.warning("PTv2: Way <"+way.getId()+"> has less than 2 nodes");
					
					orderedRoute=false;
					
					continue;
				}
			
				//long nodeId1=wayNodes.get(0).getNodeId();
				//long nodeId2=wayNodes.get(wayNodes.size()-1).getNodeId();
				
				long nodeId1=myWay.getFirstNodeId();
				long nodeId2=myWay.getLastNodeId();
				
				long nextLinkNode=-1;
				
				//Log.debug("PTv2: Way #"+pos+", Node1="+nodeId1+", Node2="+nodeId2);
					
				if (pos==0) {
					
					// This is the first way
					
					prevLinkNode1=nodeId1;
					prevLinkNode2=nodeId2;
					prevLinkNode=-1;
				}
				else {
					
					if (prevLinkNode<0) {
					
						// We come the first way or from a non-continuity situation
						
						if ((nodeId1==prevLinkNode1) || (nodeId1==prevLinkNode2)) {
						
							prevLinkNode=nodeId1;
							
							nextLinkNode=nodeId2;
						}
						else if ((nodeId2==prevLinkNode1) || (nodeId2==prevLinkNode2)) {
						
							prevLinkNode=nodeId2;
							
							nextLinkNode=nodeId1;
						}
						else {
							
							long firstNodeId = myWay.getFirstNodeId();
							
							Coord nodeCoord = mDatabase.getNodeCoord(firstNodeId);
							
							String description = "Detected non continuity in ";
							
							description += String.format(Locale.US,
								"<a href='https://www.openstreetmap.org/way/%d' target='_blank'>Way #%d</a>",
								way.getId(), way.getId());
							
							addNodeToGeoJson(nodeCoord, relation.getId(), description);
							
							orderedRoute=false;
							
							prevLinkNode1=nodeId1;
							prevLinkNode2=nodeId2;
							prevLinkNode=-1;
							nextLinkNode=-1;
						}
					}
					else {
						
						// We come from a good continuity situation
						
						if (nodeId1==prevLinkNode) {
							
							prevLinkNode=nodeId1;
							
							nextLinkNode=nodeId2;
						}
						else if (nodeId2==prevLinkNode) {
							
							prevLinkNode=nodeId2;
							
							nextLinkNode=nodeId1;
						}
						else {
							
							Coord nodeCoord = mDatabase.getNodeCoord(prevLinkNode);
							
							String description = "Detected non continuity in ";
							
							description += String.format(Locale.US,
									"<a href='https://www.openstreetmap.org/node/%d' target='_blank'>Node #%d</a>",
									prevLinkNode, prevLinkNode); 
							
							addNodeToGeoJson(nodeCoord, relation.getId(), description);
							
							//Log.debug("PTv2: Pos="+pos+", wayId="+way.getId());							
							//Log.debug("PTv2: PrevLinkNode="+prevLinkNode+", nodeId1="+nodeId1+", nodeId2="+nodeId2);							
							
							orderedRoute=false;
							
							prevLinkNode1=nodeId1;
							prevLinkNode2=nodeId2;
							prevLinkNode=-1;
							nextLinkNode=-1;
						}
					}
					
					linkNodesIds.add(prevLinkNode);
					
					prevLinkNode=nextLinkNode;
				}
			}
		}
		
		// Step #4: Check stop nodes
		
		if (stopNodesIds.size()==0) {
			
			Log.warning("PTv2: Relation #"+relation.getId()+" does not have any stop node");
			
			return;
		}
		
		if (stopNodesIds.size()<2) {
			
			Log.warning("PTv2: Relation #"+relation.getId()+" has only 1 stop node. It must have at least 2 (start and stop)");
		}
		
		if ((waysIds.size()!=0) && (waysIds.size()!=(linkNodesIds.size()+1))) {
			
			Log.warning("PTv2: Relation #"+relation.getId()+": num ways="+
					waysIds.size()+" is not num links="+linkNodesIds.size()+"+1");
		
		}
		
		for(int i=0; i<waysIds.size(); i++) {
			
			Way way=mDatabase.getWayById(waysIds.get(i));
			
			MyWay myWay=new MyWay(way);
			
			long nodeId1=myWay.getFirstNodeId();
			long nodeId2=myWay.getLastNodeId();
			
			//long prevLinkId;
			long nextLinkId;
			
			int wayDir=UNKNOWN;			
			
			if (i==0) {
				
				// This is the first way
				
				if (linkNodesIds.size()==0) {
					
					// There's only one way in the relation 
					
					nextLinkId=-1;
				}
				else {
					
					nextLinkId=linkNodesIds.get(i);
				}
				
				long startNodeId=stopNodesIds.get(0);
				
				//Log.debug("PTv2: startNodeId: "+startNodeId);
				//Log.debug("PTv2: first way Id: "+startNodeId);
				
				boolean startNodeFound;
				
				if (nextLinkId<0) {
					
					// There's no link id. The first stop node can be nodeId1 o nodeId2
					
					if (startNodeId==nodeId1) {
						
						// Start node detected in nodeId1...
						startNodeFound=true;

						wayDir=ASCENDING;
					}
					else if (startNodeId==nodeId2) {
						
						// Start node detected in nodeId2...
						startNodeFound=true;
						
						wayDir=DESCENDING;
					}
					else {
						
						startNodeFound=false;
						
						wayDir=UNKNOWN;
					}
				}
				else {
					
					if (nextLinkId==nodeId1) {
						
						// Start node shall be in node2
						
						if (startNodeId==nodeId2) {
							
							startNodeFound=true;
							
							wayDir=DESCENDING;
						}
						else {
							
							startNodeFound=false;
							
							wayDir=ASCENDING;
						}						
					}
					else if (nextLinkId==nodeId2) {
						
						// Start node shall be in node1
						
						if (startNodeId==nodeId1) {
							
							startNodeFound=true;
							
							wayDir=ASCENDING;
						}
						else {
							
							startNodeFound=false;
							
							wayDir=DESCENDING;
						}						
					}
					else {
						
						startNodeFound=false;
						
						wayDir=UNKNOWN;
					}
				}
				
				if (!startNodeFound) {
					
					Log.warning("PTv2: Relation #"+relation.getId()+": First stop node not detected in first way");
				}
				
								
				//Log.debug("PTv2 way order is "+(ascending? "ascending":"descending"));
			}
			else if (i==(waysIds.size()-1)) {
				
				// This is the last way. Check for final stop position
				
				long lastLinkId=linkNodesIds.get(linkNodesIds.size()-1);
				
				long endNodeId=stopNodesIds.get(stopNodesIds.size()-1);
				
				//Log.debug("PTv2: startNodeId: "+startNodeId);
				//Log.debug("PTv2: first way Id: "+startNodeId);
				
				boolean endNodeFound;
				
				if (lastLinkId<0) {
					
					// There's no link id. The first stop node can be nodeId1 o nodeId2
					
					if (endNodeId==nodeId1) {
						
						// End node detected in nodeId1...
						endNodeFound=true;

						wayDir=DESCENDING;
					}
					else if (endNodeId==nodeId2) {
						
						// End node detected in nodeId2...
						endNodeFound=true;
						
						wayDir=ASCENDING;						
					}
					else {
						
						endNodeFound=false;
						
						wayDir=UNKNOWN;
					}
				}
				else {
					
					if (lastLinkId==nodeId1) {
						
						// End node shall be in node2
						
						if (endNodeId==nodeId2) {
							
							endNodeFound=true;
							
							wayDir=ASCENDING;
						}
						else {
							
							endNodeFound=false;
							
							wayDir=ASCENDING;
						}						
					}
					else if (lastLinkId==nodeId2) {
						
						// End node shall be in node1
						
						if (endNodeId==nodeId1) {
							
							endNodeFound=true;
							
							wayDir=DESCENDING;
						}
						else {
							
							endNodeFound=false;
							
							wayDir=DESCENDING;
						}						
					}
					else {
						
						endNodeFound=false;
						
						wayDir=UNKNOWN;
					}
				}
				
				if (!endNodeFound) {
					
					Log.warning("PTv2: Relation #"+relation.getId()+": End stop node not detected in last way");
				}
			}
			else {
				
				// This is not the first nor the last way
				
				long prevLink=linkNodesIds.get(i-1);
				long nextLink=linkNodesIds.get(i);
				
				if (prevLink==nodeId1) {
					
					if (nextLink==nodeId2) {
						
						wayDir=ASCENDING;						
					}
					else {
						
						wayDir=UNKNOWN;
					}
				}
				else if (prevLink==nodeId2) {
					
					if (nextLink==nodeId1) {
						
						wayDir=DESCENDING;						
					}
					else {
						
						wayDir=UNKNOWN;
					}
				}
				else {
					
					wayDir=UNKNOWN;
				}				
			}
			
			// Check if it's a valid way for public transport
			
			String type=myWay.getHighwayType();
			
			if (type==null) {
				
				Log.warning("PTv2: Relation #"+relation.getId()+": Way <"+way.getId()+"> is not a highway");
				
				break;
			}
			else if (type.compareTo("motorway")==0 ||
				type.compareTo("motorway_link")==0 ||
				type.compareTo("trunk")==0 ||
				type.compareTo("trunk_link")==0 ||
				type.compareTo("primary")==0 ||
				type.compareTo("primary_link")==0 ||
				type.compareTo("secondary")==0 ||
				type.compareTo("secondary_link")==0 ||
				type.compareTo("tertiary")==0 ||
				type.compareTo("tertiary_link")==0 ||
				type.compareTo("unclassified")==0 ||
				type.compareTo("residential")==0 ||
				type.compareTo("service")==0 ||
				type.compareTo("track")==0) {
				
				// This is a correct highway type
			}
			else {
				
				Log.warning("PTv2: Relation #"+relation.getId()+": incorrect <highway> tag <"+type+"> of way <"+
						way.getId()+"> is not correct");
			}
			
			boolean isLink=type.endsWith("_link");
			
			boolean isRoundabout=myWay.isRoundabout();
			
			// Check way direction
			
			if (orderedRoute) {
				
				if (wayDir==UNKNOWN) {
					
					Log.warning("PTv2: Relation #"+relation.getId()+": Direction of way <"+way.getId()+"> is unknown");
				}
				else if (wayDir==ASCENDING || wayDir==DESCENDING) {
					
					int oneway=myWay.getOneway();
					
					if (oneway==myWay.NO_ONEWAY) {
						
						if (isLink || isRoundabout) {
							
							oneway=myWay.ONEWAY_FORWARD;
						}
					}
					
					if (wayDir==ASCENDING && oneway==myWay.ONEWAY_BACKWARD) {
						
						Log.warning("PTv2: Relation #"+relation.getId()+": Direction of way <"+way.getId()+"> is backward");						
					}
					else if (wayDir==DESCENDING && oneway==myWay.ONEWAY_FORWARD) {
						
						Log.warning("PTv2: Relation #"+relation.getId()+": Direction of way <"+way.getId()+"> is forward");						
					}					
					
				}
				else {
					
					Log.warning("PTv2: Relation #"+relation.getId()+": Direction of way <"+way.getId()+"> is not correct");
				}
			}
		}
		
		// Step #5: Check order of stop nodes
		
		if (!orderedRoute) {
			
			// This is not an ordered route. Skip check
			
			return;
		}
		
		// First, get list of all the nodes of the route
		
		List<Long> routeNodes=new ArrayList<Long>();
		
		for(int i=0; i<waysIds.size(); i++) {
			
			Way way=mDatabase.getWayById(waysIds.get(i));
			
			MyWay myWay=new MyWay(way);
			
			long nodeId1=myWay.getFirstNodeId();
			long nodeId2=myWay.getLastNodeId();
			
			int wayDir=UNKNOWN;
			
			boolean isLastWay;
			
			if (i==(waysIds.size()-1)) {
				
				// This is the last way
				
				isLastWay=true;
				
				if (nodeId1==linkNodesIds.get(i-1)) {
					
					wayDir=ASCENDING;
				}
				else if (nodeId2==linkNodesIds.get(i-1)) {
					
					wayDir=DESCENDING;
				}

			}
			else {
				
				// This is NOT the last way
				
				isLastWay=false;
				
				if (nodeId1==linkNodesIds.get(i)) {
					
					wayDir=DESCENDING;
				}
				else if (nodeId2==linkNodesIds.get(i)) {
					
					wayDir=ASCENDING;
				}
		
			}
			
			if (wayDir==UNKNOWN) {
				
				Log.warning("PTv2: Relation #"+relation.getId()+": Order stop nodes. Link node not found in way <"+way.getId()+">");
				
				continue;				
			}
			
			List<WayNode> wayNodes=way.getWayNodes();
			
			//int numNodesToAdd;
			
			int offset;
			
			if (isLastWay) {
				
				// For the last way, add all the nodes
				
				//numNodesToAdd=wayNodes.size();
				offset=0;
			}
			else {
				
				// For the rest of ways, add all the nodes but the last one
				
				//numNodesToAdd=wayNodes.size()-1;
				
				offset=1;
			}
			
			if (wayDir==ASCENDING) {
				
				for(int j=0; j<(wayNodes.size()-offset); j++) {
					
					routeNodes.add(wayNodes.get(j).getNodeId());					
				}
			}
			else if (wayDir==DESCENDING) {
				
				for(int j=(wayNodes.size()-1); j>=offset; j--) {
					
					routeNodes.add(wayNodes.get(j).getNodeId());					
				}
				
			}
			else {
				
				Log.warning("PTv2: Relation #"+relation.getId()+": Order of way <"+way.getId()+"> is unknown");
				
			}
		}
		
		//Log.info("PTv2: Relation #"+relation.getId()+": Number of route nodes: "+routeNodes.size());
		
		int startPos=0;
		
		for(int i=0; i<stopNodesIds.size(); i++) {
			
			long stopNodeId=stopNodesIds.get(i);
			
			int pos=startPos;
			
			boolean stopNodeFound=false;
			
			while (pos<routeNodes.size()) {
				
				if (stopNodeId==routeNodes.get(pos)) {
					
					stopNodeFound=true;
					
					break;
				}
				
				pos++;
			}
			
			if (stopNodeFound) {
				
				startPos=pos;
			}
			else {
				
				Log.warning("PTv2: Relation #"+relation.getId()+": Stop node <"+stopNodeId+"> is not ordered");
				
				pos=0;
				
				while (pos<startPos) {
					
					if (stopNodeId==routeNodes.get(pos)) {
						
						stopNodeFound=true;
						
						break;
					}
					
					pos++;
				}
				
				if (stopNodeFound) {
					
					startPos=pos;
				}
				else {
					
					Log.warning("PTv2: Relation #"+relation.getId()+": Stop node <"+stopNodeId+"> not found in route");					
				}
		
			}
			
		}
		
		/*
		List<Integer> stopNodesOrder=new ArrayList<Integer>();
		
		// Reset stop nodes order index
		
		while(stopNodesOrder.size()<stopNodesIds.size()) {
			
			stopNodesOrder.add(-1);
		}
		
		int currentNodePos=0;
		*/
		
		
	}
	
	private void addNodeToGeoJson(Coord coord, long relationId, String description) {
		
		Attributes attribs = new Attributes();
		
		String title = "PTv2 ERROR";
		
		String relation = String.format(Locale.US,
				"<a href='https://www.openstreetmap.org/relation/%d' target='_blank'>Rel #%d</a>",
				relationId, relationId);
		
		attribs.put("title", title);
		attribs.put("relation", relation);
		attribs.put("description", description);
		
		if (mOutFile != null) {
			
			mOutFile.addNode(coord, attribs);
		}
		
		Log.data(title+": Rel #"+relationId+": "+description);
	}

}
