package ProcessOsmDatabase;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.utilslibrary.Coord;
import org.utilslibrary.Log;

public class GeoJSONFile {
	
	private static String HEADER_LINE_1  = "{";
	private static String HEADER_LINE_2  = "  \"type\": \"FeatureCollection\",";
	private static String HEADER_LINE_3  = "  \"generator\": \"%s\",";
	private static String HEADER_LINE_4  = "  \"features\": [";
	
	private static String FEATURE_LINE_01   = "    {";
	private static String FEATURE_LINE_02   = "      \"type\": \"Feature\",";
	private static String FEATURE_LINE_03   = "      \"properties\": {";
	private static String FEATURE_LINE_04   = "      },";
	private static String FEATURE_LINE_05   = "      \"geometry\": {";
	private static String FEATURE_LINE_06   = "        \"type\": \"%s\",";
	private static String FEATURE_LINE_07   = "        \"coordinates\": [";
	private static String FEATURE_LINE_08   = "        ]";
	private static String FEATURE_LINE_09   = "      }";
	private static String FEATURE_LINE_10   = "    }";
	
	private static String PROPERTY_LINE_1   = "          \"%s\": \"%s\"";
	
	private static String SINGLE_COORD_LINE = "          %.12f, %.12f";
	private static String MULT_COORD_LINE   = "          [ %.12f, %.12f ]";
	
	private static String END_LINE_1        = "  ]";
	private static String END_LINE_2        = "}";
	
	private PrintWriter mWriter = null;
	
	private boolean mFirstFeature = true;
	
	private int mNodeCount;
	private int mWayCount;
	
	public GeoJSONFile(String fileName, String generator) {
		
		try {
			
			FileWriter writer = new FileWriter(fileName);
			
			BufferedWriter bw = new BufferedWriter(writer);
			
			mWriter = new PrintWriter(bw);
			
			mWriter.println(HEADER_LINE_1);
			mWriter.println(HEADER_LINE_2);
			
			String line = String.format(HEADER_LINE_3, generator);
			mWriter.println(line);
			
			mWriter.println(HEADER_LINE_4);
			
		} catch (IOException e) {
			
			Log.error("GeoJSONFile. FileWriter error: "+e.getMessage());
		}
		
		mNodeCount = 0;
		mWayCount = 0;
	}
	
	public void close() {
		
		if (mWriter != null) {
			
			mWriter.println("");
			mWriter.println(END_LINE_1);
			mWriter.println(END_LINE_2);
			
			mWriter.flush();
			
			mWriter.close();
			
			mWriter = null;
		}
	}
	
	private void writeFeatureProperties(Collection<Tag> tags) {
		
		if (mWriter == null) {
			
			return;
		}
		
		if (mFirstFeature) {
			
			mFirstFeature = false;
		}
		else {
			
			mWriter.println(",");
		}
		
		mWriter.println(FEATURE_LINE_01);
		mWriter.println(FEATURE_LINE_02);
		mWriter.println(FEATURE_LINE_03);
		
		// Write attributes...
		
		Iterator<Tag> iter = tags.iterator();
		
		boolean firstAttrib = true;
		
		while(iter.hasNext()) {
			
			Tag tag = iter.next();
			
			if (firstAttrib) {
				
				firstAttrib = false;
			}
			else {
				
				mWriter.println(",");
			}
			
			String key = tag.getKey();
			
			String value = tag.getValue();
			
			String line = String.format(PROPERTY_LINE_1, key, value);
			
			mWriter.print(line);
		}
		
		// At the end, write a return line
		mWriter.println("");
		
		mWriter.println(FEATURE_LINE_04);
	}
	
	private void writeFeatureGeometry(String geometryString) {
		
		mWriter.println(FEATURE_LINE_05);
	
		String line = String.format(FEATURE_LINE_06, geometryString);
		mWriter.println(line);
	}
	
	private void writeCoordinate(double lat, double lon) {
		
		mWriter.println(FEATURE_LINE_07);
		
		// Write coordinate...
		
		String line = String.format(Locale.US, SINGLE_COORD_LINE, lon, lat);
		mWriter.println(line);
		
		mWriter.println(FEATURE_LINE_08);
		mWriter.println(FEATURE_LINE_09);
		mWriter.print(FEATURE_LINE_10);	
	}
	
	private void writeCoordinates(List<WayNode> nodes) {
		
		mWriter.println(FEATURE_LINE_07);
		
		// Write coordinates...
		
		Iterator<WayNode> iter = nodes.iterator();
		
		boolean firstCoord = true;
		
		while(iter.hasNext()) {
			
			WayNode node = iter.next();
			
			if (firstCoord) {
				
				firstCoord = false;
			}
			else {
				
				mWriter.println(",");
			}
			
			String line = String.format(Locale.US, MULT_COORD_LINE, node.getLongitude(), node.getLatitude());
			mWriter.print(line);
		}
		
		// At the end, write a return line
		mWriter.println("");
		
		mWriter.println(FEATURE_LINE_08);
		mWriter.println(FEATURE_LINE_09);
		mWriter.print(FEATURE_LINE_10);
	}
	
	public void addNode(Coord nodeCoord, Collection<Tag> tags) {
		
		writeFeatureProperties(tags);
		
		writeFeatureGeometry("Point");
		
		writeCoordinate(nodeCoord.mLat, nodeCoord.mLon);
				
		mNodeCount++;
	}
	
	public void addWay(Way way) {
		
		writeFeatureProperties(way.getTags());
		
		writeFeatureGeometry("LineString");
		
		writeCoordinates(way.getWayNodes());
				
		mWayCount++;
	}
	
	public int getNodeCount() {
		
		return mNodeCount;
	}
	
	public int getWayCount() {
		
		return mWayCount;
	}
}
