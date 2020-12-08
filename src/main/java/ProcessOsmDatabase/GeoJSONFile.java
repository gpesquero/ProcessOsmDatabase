package ProcessOsmDatabase;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

import org.utilslibrary.Attributes;
import org.utilslibrary.Coord;
import org.utilslibrary.Log;

public class GeoJSONFile {
	
	private static String HEADER_LINE_1  = "{";
	private static String HEADER_LINE_2  = "  \"type\": \"FeatureCollection\",";
	private static String HEADER_LINE_3  = "  \"generator\": \"%s\",";
	private static String HEADER_LINE_4  = "  \"features\": [";
	
	private static String FEATURE_LINE_01 = "    {";
	private static String FEATURE_LINE_02 = "      \"type\": \"Feature\",";
	private static String FEATURE_LINE_03 = "      \"properties\": {";
	private static String FEATURE_LINE_04 = "      },";
	private static String FEATURE_LINE_05 = "      \"geometry\": {";
	private static String FEATURE_LINE_06 = "        \"type\": \"%s\",";
	private static String FEATURE_LINE_07 = "        \"coordinates\": [";
	private static String FEATURE_LINE_08 = "        ]";
	private static String FEATURE_LINE_09 = "      }";
	private static String FEATURE_LINE_10 = "    }";
	
	private static String PROPERTY_LINE_1 = "          \"%s\": \"%s\"";
	
	private static String COORD_LINE_1    = "          %.12f,";
	private static String COORD_LINE_2    = "          %.12f";
	
	private static String END_LINE_1 = "  ]";
	private static String END_LINE_2 = "}";
	
	private PrintWriter mWriter = null;
	
	private boolean mFirstFeature = true;
	
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
	
	public void addNode(Coord nodeCoord, Attributes attribs) {
		
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
		
		boolean firstAttrib = true;
		
		for (String key : attribs.keySet()) {
			
			if (firstAttrib) {
				
				firstAttrib = false;
			}
			else {
				
				mWriter.println(",");
			}
			
			String value = attribs.get(key);
			
			String line = String.format(PROPERTY_LINE_1, key, value);
			
			mWriter.print(line);
		}
		
		mWriter.println("");
		
		mWriter.println(FEATURE_LINE_04);
		mWriter.println(FEATURE_LINE_05);
		
		String line = String.format(FEATURE_LINE_06, "Point");
		mWriter.println(line);
		
		mWriter.println(FEATURE_LINE_07);
		
		// Write coordinates...
		
		line = String.format(Locale.US, COORD_LINE_1, nodeCoord.mLon);
		mWriter.println(line);
		
		line = String.format(Locale.US, COORD_LINE_2, nodeCoord.mLat);
		mWriter.println(line);
		
		mWriter.println(FEATURE_LINE_08);
		mWriter.println(FEATURE_LINE_09);
		mWriter.print(FEATURE_LINE_10);
		
	}
}
