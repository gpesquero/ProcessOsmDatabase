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
import org.utilslibrary.Coord;
import org.utilslibrary.Log;
import org.utilslibrary.MyWay;
import org.utilslibrary.MyWay.Oneway;

public class PTv2Checker extends BaseChecker {
	
	private static final int UNKNOWN = -1;
	private static final int ASCENDING = 0;
	private static final int DESCENDING = 1;
	
	private static final double MAX_STOP_PLATFORM_DISTANCE = 20.0;
	
	public PTv2Checker(List<Long> relsToProcess) {
		
		mRelsToProcess = relsToProcess;
	}
	
	/*
	public void checkRelations(List<Long> relIds) {
		
		Iterator<Long> relIter = relIds.iterator();
		
		while(relIter.hasNext()) {
			
			Long relId = relIter.next();
			
			Log.debug("Checking PTv2 of relation with id="+relId);
			
			Relation rel = mDatabase.getRelationById(relId);
			
			if (rel != null) {
				
				checkRelation(rel);
			}
		}		
	}
	*/
	
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
				else if (tag.getValue().compareTo("network") == 0) {
					
					checkNetwork(relation);
					
					processed = true;
					
					break;
				}
				else {
					
					Coord coord = mDatabase.getRelationCoord(relation);
					
					String description = "Relation has an incorrect type '"+tag.getValue()+"'";
					
					Log.warning("Unknown 'type' tag in relation #" + relation.getId());
					
					addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
					
					break;
				}
			}				
		}
		
		if (!processed) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Relation is not a 'route', 'route_master' or 'network'";
			
			Log.warning(description);
			
			addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
		}
	}
	
	public void checkNetwork(Relation relation) {
		
		int numberOfRoutes = 0;
		
		List<RelationMember> members = relation.getMembers();
		
		for(int pos = 0; pos < members.size(); pos++) {
			
			RelationMember member = members.get(pos);
			
			if (!member.getMemberRole().isEmpty()) {
				
				Coord coord = mDatabase.getRelationCoord(relation);
				
				String description = "Member of Network Relation in pos '"+pos+
						"' does not have an empty role '"+member.getMemberRole()+"'";
				
				addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
			}
			
			if (member.getMemberType() == EntityType.Relation) {
				
				Relation routeRel = mDatabase.getRelationById(member.getMemberId());
				
				checkRelation(routeRel);
				
				numberOfRoutes++;				
			}
			else {
				
				Coord coord = mDatabase.getRelationCoord(relation);
				
				String description = "Member of Network Relation in pos '" + pos + "' is not a relation";
				
				addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
			}				
		}
		
		if (numberOfRoutes<1) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Network Relation does not have any relation";
			
			addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
		}
	}
	
	public void checkRouteMaster(Relation relation) {
		
		boolean isBusRoute = false;
		boolean hasRefTag = false;
		boolean hasColorTag = false;
		
		Collection<Tag> relTags=relation.getTags();
		
		Iterator<Tag> tagIter=relTags.iterator();
		
		while(tagIter.hasNext()) {
			
			Tag tag=tagIter.next();
			
			if (tag.getKey().compareTo("route_master")==0) {
				
				if (tag.getValue().compareTo("bus")==0) {
					
					isBusRoute = true;
				}
			}
			else if (tag.getKey().compareTo("ref") == 0) {
				
				hasRefTag = true;
			}
			else if (tag.getKey().compareTo("colour") == 0) {
				
				hasColorTag = true;
			}
			else if (tag.getKey().compareTo("color") == 0) {
				
				hasColorTag = true;
			}
		}
		
		if (!isBusRoute) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Master Route Relation is not a bus route";
			
			addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
		}
		
		if (!hasRefTag) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Master Route Relation has no 'ref' tag";
			
			addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
		}
		
		if (!hasColorTag) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Master Route Relation has no 'color/colour' tag";
			
			addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
		}
		
		int numberOfRoutes = 0;
		
		List<RelationMember> members=relation.getMembers();
		
		for(int pos=0; pos<members.size(); pos++) {
			
			RelationMember member=members.get(pos);
			
			if (!member.getMemberRole().isEmpty()) {
				
				Coord coord = mDatabase.getRelationCoord(relation);
				
				String description = "Member of Master Route Relation in pos '"+pos+
						"' does not have an empty role '"+member.getMemberRole()+"'";
				
				addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
			}
			
			if (member.getMemberType()==EntityType.Relation) {
				
				Relation routeRel=mDatabase.getRelationById(member.getMemberId());
				
				checkRoute(routeRel);
				
				numberOfRoutes++;				
			}
			else {
				
				Coord coord = mDatabase.getRelationCoord(relation);
				
				String description = "Member of Master Route Relation in pos '"+pos+"' is not a relation";
				
				addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
			}				
		}
		
		if (numberOfRoutes<1) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Master Route does not have any relation";
			
			addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
		}
		else if (numberOfRoutes<2) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Master Route only has 1 relation";
			
			addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
		}
	}
	
	public void checkRoute(Relation relation) {
		
		Log.debug("PTv2: Checking relation <"+relation.getId()+">");
		
		if (mRelsToProcess != null) {
			
			if (!mRelsToProcess.contains(relation.getId())) {
				
				// Relation not included in rels to process...
				return;
			}
			else {
				
				Log.info("Processing filtered relation with id #" + relation.getId());
			}
		}
		
		// Step #1: Check for relation tags...
		
		boolean isBusRoute = false;
		boolean hasRefTag = false;
		boolean hasColorTag = false;
		boolean hasPTv2Tag = false;
		
		Collection<Tag> relTags = relation.getTags();
		
		Iterator<Tag> tagIter = relTags.iterator();
		
		while(tagIter.hasNext()) {
			
			Tag tag = tagIter.next();
			
			if (tag.getKey().compareTo("route")==0) {
				
				if (tag.getValue().compareTo("bus")==0) {
					
					isBusRoute = true;
				}
			}
			else if (tag.getKey().compareTo("ref") == 0) {
				
				hasRefTag = true;
			}
			else if (tag.getKey().compareTo("public_transport:version") == 0) {
				
				if (tag.getValue().compareTo("2") == 0) {
					
					hasPTv2Tag = true;
				}
			}
			else if (tag.getKey().compareTo("colour") == 0) {
				
				hasColorTag = true;
			}
			else if (tag.getKey().compareTo("color") == 0) {
				
				hasColorTag = true;
			}
		}
		
		if (!isBusRoute) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Route Relation is not a bus route";
			
			addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
		}
		
		if (!hasRefTag) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Relation has no 'ref' tag";
			
			addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
		}
		
		if (!hasPTv2Tag) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Relation has no 'public_transport:version=2' tag";
			
			addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
		}
		
		if (!hasColorTag) {
			
			Coord coord = mDatabase.getRelationCoord(relation);
			
			String description = "Relation has no 'color/colour' tag";
			
			addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
		}
		
		// Step #2: Check list of stops and platforms
		
		List<Long> stopNodesIds = new ArrayList<Long>();
		List<Long> waysIds = new ArrayList<Long>();
		List<Long> linkNodesIds = new ArrayList<Long>();
				
		List<RelationMember> members = relation.getMembers();
		
		boolean foundEmptyRole = false;
		boolean detectedIncorrectStopPos = false;
		boolean detectedIncorrectPlatformPos = false;
		
		Coord stopNodeCoord = null;
		
		for(int pos=0; pos<members.size(); pos++) {
		
			RelationMember member = members.get(pos);
			
			if (member.getMemberRole().compareTo("stop") == 0) {
				
				if (foundEmptyRole) {
					
					// Detected a stop member in an incorrect position (shall be located
					// at the beginning of the relation)
					
					detectedIncorrectStopPos = true;
				}
				
				if (member.getMemberType() == EntityType.Node) {
					
					if (pos == (members.size()-1)) {
						
						Coord coord = mDatabase.getNodeCoord(member.getMemberId());
						
						String description = "Stop member <Node #"+member.getMemberId()+"> in pos '"+pos+
								"' is not followed by a 'platform' member";
						
						addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
						
						stopNodeCoord=null;
					}
					else {
						
						String nextMemberRole = members.get(pos+1).getMemberRole();
						
						if (nextMemberRole.compareTo("platform") != 0) {
							
							Coord coord = mDatabase.getNodeCoord(member.getMemberId());
							
							String description = "Stop member <Node #" + member.getMemberId() + "> in pos '" +
									pos + "' is not followed by a 'platform' member";
							
							addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
						}					
					}
					
					// Check that stop node has correct attributes
					
					boolean foundPublicTransportTag = false;
					
					Node stopNode = mDatabase.getNodeById(member.getMemberId());
					
					if (stopNode == null) {
						
						Log.warning("stopNode with id #" + member.getMemberId() + "not found!!");
						
						continue;
					}
					
					Collection<Tag> tags=stopNode.getTags();
					
					Iterator<Tag> nodeTagIter=tags.iterator();
					
					while(nodeTagIter.hasNext()) {
						
						Tag tag=nodeTagIter.next();
						
						if (tag.getKey().compareTo("public_transport") == 0) {
							
							foundPublicTransportTag = true;
							
							if (tag.getValue().compareTo("stop_position") != 0) {
								
								Coord coord = mDatabase.getNodeCoord(member.getMemberId());
								
								String description = "Stop <Node #"+member.getMemberId()+"> in pos '"+pos+
										"' has incorrect 'public_transport' tag='"+tag.getValue()+"'";
								
								addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
							}
						}
					}
					
					if (!foundPublicTransportTag) {
						
						Coord coord = mDatabase.getNodeCoord(member.getMemberId());
						
						String description = "Stop <Node #"+member.getMemberId()+"> in pos '"+pos+
								"' does not have the 'public_transport' tag";
						
						addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
					}
					
					Long nodeId = member.getMemberId();
					
					stopNodesIds.add(nodeId);
					
					stopNodeCoord = new Coord(stopNode.getLatitude(), stopNode.getLongitude());
				}
				else {
					
					// Stop is not a node. Weird....
					
					Coord coord = mDatabase.getRelationCoord(relation);
					
					String description = "Stop member in pos '"+pos+"' is not a node...";
					
					addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
				}				
			}
			else if (member.getMemberRole().compareTo("platform") == 0) {
				
				if (foundEmptyRole) {
					
					// Detected a platform member in an incorrect position (shall be located
					// at the beginning of the relation)
					
					detectedIncorrectPlatformPos = true;
				}
				
				if (member.getMemberType() == EntityType.Node) {
					
					Node platformNode = mDatabase.getNodeById(member.getMemberId());
					
					if (platformNode == null) {
						
						Log.warning("platformNode with id #" + member.getMemberId() + "not found!!");
					
						continue;
					}
					
					if (pos == 0) {
						
						Coord coord = mDatabase.getNodeCoord(member.getMemberId());
						
						String description = "Platform member <Node #" + member.getMemberId() + "> in pos '" +
								pos + "' does not follow a 'stop' member";
						
						addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
						
						stopNodeCoord=null;
					}
					else {
						
						String previousMemberRole = members.get(pos-1).getMemberRole();
						
						if (previousMemberRole.compareTo("stop") != 0) {
							
							Coord coord = mDatabase.getNodeCoord(member.getMemberId());
							
							String description = "Platform member <Node #"+member.getMemberId() + "> in pos '" +
									pos + "' does not follow a 'stop' member";
							
							addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
						}
						else {
							
							// Check that distance between stop and platform nodes is less than 20 meters
							
							Coord platCoord = new Coord(platformNode.getLatitude(), platformNode.getLongitude());
							
							double distance = stopNodeCoord.distanceTo(platCoord);
							
							if (distance > MAX_STOP_PLATFORM_DISTANCE) {
								
								Coord coord = mDatabase.getNodeCoord(member.getMemberId());
								
								String description = "Distance between platform member <Node #"+member.getMemberId()+
										"> and stop position is too big ("+
										String.format(Locale.US, "%.1f", distance)+" meters)";
								
								addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);							
							}					
						}
					}
					
					// Check that platform node has correct attributes
					
					boolean foundPublicTransportTag = false;
					boolean foundRefTag = false;
					
					Collection<Tag> tags = platformNode.getTags();
					
					Iterator<Tag> nodeTagIter = tags.iterator();
					
					while(nodeTagIter.hasNext()) {
						
						Tag tag = nodeTagIter.next();
						
						if (tag.getKey().compareTo("public_transport") == 0) {
							
							foundPublicTransportTag = true;
							
							if (tag.getValue().compareTo("platform") != 0) {
								
								Coord coord = mDatabase.getNodeCoord(member.getMemberId());
								
								String description = "Platform <Node #" + member.getMemberId() + "> in pos '" +
										pos + "' has incorrect 'public_transport' tag='" + tag.getValue() + "'";
								
								addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);	
							}
						}
						else if (tag.getKey().compareTo("ref") == 0) {
							
							foundRefTag = true;
						}
					}
					
					if (!foundPublicTransportTag) {
						
						Coord coord = mDatabase.getNodeCoord(member.getMemberId());
						
						String description = "Platform <Node #" + member.getMemberId() + "> in pos <" + pos +
								"> does not have the <public_transport> tag";
						
						addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
					}
					
					if (!foundRefTag) {
						
						Coord coord = mDatabase.getNodeCoord(member.getMemberId());
						
						String description = "Platform <Node #" + member.getMemberId() + "> in pos <" + pos +
								"> does not have the 'ref' tag";
						
						addErrorNode(ErrorLevel.LOW, coord, relation.getId(), description);
					}
				}
				else {
					
					// Platform is not a node. Weird....
					
					Coord coord = mDatabase.getRelationCoord(relation.getId());
					
					String description = "Platform <Node #" + member.getMemberId() + "> in pos '" + pos +
							"' does not have the 'public_transport' tag";
					
					addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
				}				
			}
			else if (member.getMemberRole().isEmpty()) {
				
				// Found an empty role member. It shall be a way
				
				if (member.getMemberType() != EntityType.Way) {
					
					Coord coord = mDatabase.getRelationCoord(relation.getId());
					
					String description = "Empty role relation member in pos '" + pos + "' is not a way";
					
					addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
					
					continue;
				}
				else {
					
					waysIds.add(member.getMemberId());
					
					foundEmptyRole=true;					
				}
			}
			else {
				
				Coord coord = mDatabase.getRelationCoord(relation.getId());
				
				String description = "Relation member in pos '" + pos + "' has incorrect role '" +
						member.getMemberRole() + "'";
				
				addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
			}
		}
		
		if (detectedIncorrectStopPos || detectedIncorrectPlatformPos) {
			
			Coord nodeCoord = mDatabase.getRelationCoord(relation);
			
			String description = "Detected incorrect stop or platform position(s)" +
									" (They shall be located at the beginning of the relation)";
			
			addErrorNode(ErrorLevel.MEDIUM, nodeCoord, relation.getId(), description);
		}
		
		// Step #3: Check continuity between route ways
		
		boolean orderedRoute = true;
		
		if (waysIds.size() == 0) {
			
			// No ways detected. Nothing else to check..
			
			Coord nodeCoord = mDatabase.getRelationCoord(relation);
			
			String description = "Relation has no ways";
			
			addErrorNode(ErrorLevel.MEDIUM, nodeCoord, relation.getId(), description);
			
			orderedRoute = false;
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
					
					Coord nodeCoord = mDatabase.getWayCoord(way);
					
					String description = "<Way #"+way.getId()+"> has less than 2 nodes";
					
					addErrorNode(ErrorLevel.MEDIUM, nodeCoord, relation.getId(), description);
					
					orderedRoute=false;
					
					continue;
				}
			
				long nodeId1 = myWay.getFirstNodeId();
				long nodeId2 = myWay.getLastNodeId();
				
				long nextLinkNode = -1;
				
				Log.debug("PTv2: Way #" + pos + ", Node1=" + nodeId1 + ", Node2=" + nodeId2);
					
				if (pos == 0) {
					
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
							
							Coord coord = mDatabase.getWayCoord(way);
							
							String description = "Detected non continuity in <Way #" + way.getId() + ">";
							
							addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
							
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
							
							String description = "Detected non continuity in <Node #"+prevLinkNode+">";
							
							addErrorNode(ErrorLevel.MEDIUM, nodeCoord, relation.getId(), description);
							
							Log.debug("PTv2: Pos="+pos+", wayId="+way.getId());							
							Log.debug("PTv2: PrevLinkNode="+prevLinkNode+", nodeId1="+nodeId1+", nodeId2="+nodeId2);							
							
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
		
		// Step #4: Check number of stop nodes
		
		if (stopNodesIds.size()==0) {
			
			Coord nodeCoord = mDatabase.getRelationCoord(relation);
			
			String description = "Relation does not have any stop node";
			
			addErrorNode(ErrorLevel.MEDIUM, nodeCoord, relation.getId(), description);
			
			return;
		}
		
		// Check that there are at least 2 stop nodes
		if (stopNodesIds.size()<2) {
			
			Coord nodeCoord = mDatabase.getRelationCoord(relation);
			
			String description = "Relation has only 1 stop node. It must have at least 2 (start and stop)";
			
			addErrorNode(ErrorLevel.MEDIUM, nodeCoord, relation.getId(), description);
		}
		
		// Check that numWays == (numLinkNodes-1)
		if ((waysIds.size() != 0) && (waysIds.size() != (linkNodesIds.size() + 1))) {
			
			Coord nodeCoord = mDatabase.getRelationCoord(relation);
			
			String description = "Num ways=" + waysIds.size() + " is not num links=" +
					linkNodesIds.size() + "+1";
			
			addErrorNode(ErrorLevel.MEDIUM, nodeCoord, relation.getId(), description);
			
			return;
		}
		
		// Step #5: Check first & last stop node position and correct direction of ways...
		
		for(int i=0; i < waysIds.size(); i++) {
			
			Way way = mDatabase.getWayById(waysIds.get(i));
			
			MyWay myWay = new MyWay(way);
			
			long nodeId1 = myWay.getFirstNodeId();
			long nodeId2 = myWay.getLastNodeId();
			
			long nextLinkId;
			
			int wayDir = UNKNOWN;			
			
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
				
				Log.debug("PTv2: startNodeId: "+startNodeId);
				Log.debug("PTv2: first way Id: "+startNodeId);
				
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
					
					Coord nodeCoord = mDatabase.getRelationCoord(relation);
					
					String description = "First stop node not detected in first way";
					
					addErrorNode(ErrorLevel.MEDIUM, nodeCoord, relation.getId(), description);
				}
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
					
					Coord nodeCoord = mDatabase.getRelationCoord(relation);
					
					String description = "End stop node not detected in last way";
					
					addErrorNode(ErrorLevel.MEDIUM, nodeCoord, relation.getId(), description);
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
			
			String type = myWay.getHighwayType();
			
			if (type == null) {
				
				Coord nodeCoord = mDatabase.getWayCoord(way);
				
				String description = "<Way #" + way.getId() + "> is not a highway";
				
				addErrorNode(ErrorLevel.MEDIUM, nodeCoord, relation.getId(), description);
				
				break;
			}
			else if (type.compareTo("motorway") == 0 ||
				type.compareTo("motorway_link") == 0 ||
				type.compareTo("trunk") == 0 ||
				type.compareTo("trunk_link") == 0 ||
				type.compareTo("primary") == 0 ||
				type.compareTo("primary_link") == 0 ||
				type.compareTo("secondary") == 0 ||
				type.compareTo("secondary_link") == 0 ||
				type.compareTo("tertiary") == 0 ||
				type.compareTo("tertiary_link") == 0 ||
				type.compareTo("unclassified") == 0 ||
				type.compareTo("residential") == 0 ||
				type.compareTo("living_street") == 0 ||
				type.compareTo("service") == 0 ||
				type.compareTo("track") == 0) {
				
				// This is a correct highway type
			}
			else {
				
				Coord nodeCoord = mDatabase.getWayCoord(way);
				
				String description = "Incorrect 'highway' tag '" + type + "' of <Way #" +
						way.getId() + "> is not correct";
				
				addErrorNode(ErrorLevel.MEDIUM, nodeCoord, relation.getId(), description);
			}
			
			boolean isLink = type.endsWith("_link");
			
			boolean isRoundabout = myWay.isRoundabout();
			
			// Check way direction
			
			if (orderedRoute) {
				
				if (wayDir == UNKNOWN) {
					
					Coord coord = mDatabase.getWayCoord(way);
					
					String description = "Direction of <Way #" + way.getId() + "> is unknown";
					
					addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
				}
				else if ((wayDir == ASCENDING) || (wayDir == DESCENDING)) {
					
					Oneway oneway = myWay.getOneway();
					
					if (oneway == Oneway.UNDEF) {
						
						if (isLink || isRoundabout) {
							
							oneway = Oneway.FORWARD;
						}
					}
					
					if ((wayDir == ASCENDING) && (oneway == Oneway.BACKWARD)) {
						
						Coord coord = mDatabase.getWayCoord(way);
						
						String description = "Direction of <Way #" + way.getId() + "> is backward";
						
						addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
					}
					else if ((wayDir == DESCENDING) && (oneway == Oneway.FORWARD)) {
						
						Coord coord = mDatabase.getWayCoord(way);
						
						String description = "Direction of <Way #" + way.getId() + "> is forward";
						
						addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
					}			
				}
				else {
					
					Coord coord = mDatabase.getWayCoord(way);
					
					String description = "Direction of <Way #" + way.getId() + "> is not correct";
					
					addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
				}
			}
		}
		
		// Step #6: Check order of stop nodes
		
		if (!orderedRoute) {
			
			// This is not an ordered route. Skip check...			
			return;
		}
		
		// First, get list of all the nodes of the route
		
		List<Long> routeNodes = new ArrayList<Long>();
		
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
			
			if (wayDir == UNKNOWN) {
				
				Coord coord = mDatabase.getWayCoord(way);
				
				String description = "Order stop nodes. Link node not found in <Way #" + way.getId() + ">";
				
				addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
				
				continue;				
			}
			
			List<WayNode> wayNodes = way.getWayNodes();
			
			int offset;
			
			if (isLastWay) {
				
				// For the last way, add all the nodes
				
				offset=0;
			}
			else {
				
				// For the rest of ways, add all the nodes but not the last one
				
				offset=1;
			}
			
			if (wayDir == ASCENDING) {
				
				for(int j=0; j<(wayNodes.size()-offset); j++) {
					
					routeNodes.add(wayNodes.get(j).getNodeId());					
				}
			}
			else if (wayDir == DESCENDING) {
				
				for(int j=(wayNodes.size()-1); j>=offset; j--) {
					
					routeNodes.add(wayNodes.get(j).getNodeId());					
				}
				
			}
			else {
				
				Coord coord = mDatabase.getWayCoord(way);
				
				String description = "Order of <Way #" + way.getId() + "> is unknown";
				
				addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
			}
		}
		
		Log.debug("PTv2: Relation #"+relation.getId() + ": Number of route nodes: " + routeNodes.size());
		
		// Go through all stop nodes and check that they are ordered...
		
		int startPos = 0;
		
		for(int i=0; i < stopNodesIds.size(); i++) {
			
			long stopNodeId = stopNodesIds.get(i);
			
			// First, check that stop node belongs to route...
			
			boolean stopNodeFound = false;
			
			for(int j=0; j<routeNodes.size(); j++) {
				
				if (routeNodes.get(j) == stopNodeId) {
					
					stopNodeFound = true;
					
					break;
				}
			}
			
			if (!stopNodeFound) {
				
				Coord coord = mDatabase.getNodeCoord(stopNodeId);
				
				String description = "Stop <Node #" + stopNodeId + "> does not belong to route";
				
				addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
				
				// Go to next stop node...
				continue;
			}
			
			int pos = startPos;
			
			stopNodeFound = false;
			
			while (pos < routeNodes.size()) {
				
				if (stopNodeId == routeNodes.get(pos)) {
					
					stopNodeFound = true;
					
					break;
				}
				
				pos++;
			}
			
			if (stopNodeFound) {
				
				// Stop node has been found in total list of nodes
				// Set next search start position
				startPos = pos;
			}
			else {
				
				// Stop node has not been found, then it's not ordered...
				
				Coord coord = mDatabase.getNodeCoord(stopNodeId);
				
				String description = "Stop <Node #" + stopNodeId + "> is not ordered";
				
				addErrorNode(ErrorLevel.MEDIUM, coord, relation.getId(), description);
				
				/*
				pos=0;
				
				while (pos < startPos) {
					
					if (stopNodeId == routeNodes.get(pos)) {
						
						stopNodeFound = true;
						
						break;
					}
					
					pos++;
				}
				
				if (stopNodeFound) {
					
					startPos = pos;
				}
				else {
					
					coord = mDatabase.getNodeCoord(stopNodeId);
					
					description = "Stop <Node #"+stopNodeId+"> not found in route";
					
					addNodeToGeoJson(ErrorLevel.MEDIUM, coord, relation.getId(), description);
				}
				*/
			}			
		}		
	}
	
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
