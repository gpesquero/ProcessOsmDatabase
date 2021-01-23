package ProcessOsmDatabase;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.utilslibrary.Coord;
import org.utilslibrary.GeoJSONFile;
import org.utilslibrary.Log;
import org.utilslibrary.OsmDatabase;

public abstract class BaseChecker {
	
	protected OsmDatabase mDatabase = null;
	
	protected GeoJSONFile mErrorFileLevelHigh = null;
	protected GeoJSONFile mErrorFileLevelMedium = null;
	protected GeoJSONFile mErrorFileLevelLow = null;
	
	protected List<Long> mRelsToProcess = null;
	
	protected enum ErrorLevel {
		
		HIGH,
		MEDIUM,
		LOW
	}
	
	public abstract void checkRelation(Relation relation);
	
	protected abstract void addErrorNode(ErrorLevel level, Coord coord,
			long relationId, String description);
	
	public void checkRelations(List<Long> relIds) {
		
		Iterator<Long> relIter = relIds.iterator();
		
		while(relIter.hasNext()) {
			
			Long relId = relIter.next();
			
			Relation rel = mDatabase.getRelationById(relId);
			
			if (rel != null)
				checkRelation(rel);			
		}		
	}
	
	public void setGeoJSONFiles(GeoJSONFile[] errorFiles) {
		
		mErrorFileLevelHigh = errorFiles[0];
		mErrorFileLevelMedium = errorFiles[1];
		mErrorFileLevelLow = errorFiles[2];
	}
	
	public void setOsmDatabase(OsmDatabase database) {
		
		mDatabase = database;
	}
	
	protected void addNodeToGeoJson(ErrorLevel level, long relationId,
			String title, String description) {
		
		
	}
	
	protected void addNodeToGeoJson(ErrorLevel level, Coord coord, long relationId,
			String title, String description) {
		
		ArrayList<Tag> tags = new ArrayList<Tag>();
		
		// Set error level
		
		GeoJSONFile outFile = null;
		
		switch (level) {
		
		case HIGH:
			outFile = mErrorFileLevelHigh;
			break;
		
		case MEDIUM:
			outFile = mErrorFileLevelMedium;
			break;
		
		case LOW:
			outFile = mErrorFileLevelLow;
			break;
		
		default:
			outFile = mErrorFileLevelLow;
			break;
		}
		
		tags.add(new Tag("title", title));
		
		String relation = String.format(Locale.US,
				"<a href='https://www.openstreetmap.org/relation/%d' target='_blank'>Rel #%d</a>",
				relationId, relationId);
		
		tags.add(new Tag("relation", relation));
		
		// Replace string '<Node #' with link
		int startPos = description.indexOf("<Node #");
		
		if (startPos>=0) {
			
			int endPos = description.indexOf(">");
			
			if (endPos < 0) {
				
				Log.error("No endPos while searching for '>' in description");
			}
			else {
				
				long nodeId = Long.parseLong(description.substring(startPos+7, endPos));
				
				String newDescription = description.substring(0, startPos);
				
				newDescription += String.format(Locale.US,
						"<a href='https://www.openstreetmap.org/node/%d' target='_blank'>Node #%d</a>",
						nodeId, nodeId);
				
				newDescription += description.substring(endPos+1);
				
				description = newDescription;
			}
		}
		
		// Replace string '<Way #' with link
		startPos = description.indexOf("<Way #");
		
		if (startPos>=0) {
			
			int endPos = description.indexOf(">");
			
			if (endPos < 0) {
				
				Log.error("No endPos while searching for '>' in description");
			}
			else {
				
				long wayId = Long.parseLong(description.substring(startPos+6, endPos));
				
				String newDescription = description.substring(0, startPos);
				
				newDescription += String.format(Locale.US,
						"<a href='https://www.openstreetmap.org/way/%d' target='_blank'>Way #%d</a>",
						wayId, wayId);
				
				newDescription += description.substring(endPos+1);
				
				description = newDescription;
			}
		}
		
		tags.add(new Tag("description", description));
		
		if (coord == null) {
			
			Log.warning("Rel. #" + relationId + ", addNodeToGeoJson() coord==null. Setting coord to (0.0, 0.0)");
			
			coord = new Coord(0.0, 0.0);
		}
		
		if (outFile != null) {
			
			outFile.addNode(coord, tags);
		}
	}
}
