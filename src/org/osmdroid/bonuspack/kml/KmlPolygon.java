package org.osmdroid.bonuspack.kml;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.bonuspack.kml.KmlFeature.Styler;
import org.osmdroid.bonuspack.overlays.Polygon;
import org.osmdroid.bonuspack.overlays.Polyline;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

import android.content.Context;
import android.graphics.Paint;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * KML and/or GeoJSON Polygon
 * @author M.Kergall
 */
public class KmlPolygon extends KmlGeometry {
	
	/** Polygon holes (can be null if none) */
	public ArrayList<ArrayList<GeoPoint>> mHoles;
	
	static int mDefaultLayoutResId; 
	
	public KmlPolygon(){
		super();
	}
	
	public void applyDefaultStyling(Polygon polygonOverlay, Style defaultStyle, KmlPlacemark kmlPlacemark,
			KmlDocument kmlDocument, MapView map){
		Context context = map.getContext();
		Style style = kmlDocument.getStyle(kmlPlacemark.mStyle);
		if (style != null){
			Paint outlinePaint = style.getOutlinePaint();
			polygonOverlay.setStrokeColor(outlinePaint.getColor());
			polygonOverlay.setStrokeWidth(outlinePaint.getStrokeWidth());
			int fillColor = style.mPolyStyle.getFinalColor();
			polygonOverlay.setFillColor(fillColor);
		} else if (defaultStyle!=null){
			Paint outlinePaint = defaultStyle.getOutlinePaint();
			polygonOverlay.setStrokeColor(outlinePaint.getColor());
			polygonOverlay.setStrokeWidth(outlinePaint.getStrokeWidth());
			int fillColor = defaultStyle.mPolyStyle.getFinalColor();
			polygonOverlay.setFillColor(fillColor);
		}
		if ((kmlPlacemark.mName!=null && !"".equals(kmlPlacemark.mName)) 
				|| (kmlPlacemark.mDescription!=null && !"".equals(kmlPlacemark.mDescription))){
			if (mDefaultLayoutResId == 0){
				String packageName = context.getPackageName();
				mDefaultLayoutResId = context.getResources().getIdentifier("layout/bonuspack_bubble", null, packageName);
			}
			polygonOverlay.setInfoWindow(mDefaultLayoutResId, map);
		}
		polygonOverlay.setEnabled(kmlPlacemark.mVisibility);
	}
	
	/** Build the corresponding Polygon overlay */
	@Override public Overlay buildOverlay(MapView map, Style defaultStyle, Styler styler, KmlPlacemark kmlPlacemark, 
			KmlDocument kmlDocument){
		Context context = map.getContext();
		Polygon polygonOverlay = new Polygon(context);
		polygonOverlay.setPoints(mCoordinates);
		if (mHoles != null)
			polygonOverlay.setHoles(mHoles);
		polygonOverlay.setTitle(kmlPlacemark.mName);
		polygonOverlay.setSnippet(kmlPlacemark.mDescription);
		if (styler == null)
			applyDefaultStyling(polygonOverlay, defaultStyle, kmlPlacemark, kmlDocument, map);
		else
			styler.onPolygon(polygonOverlay, kmlPlacemark, this);
		return polygonOverlay;
	}
	
	/** GeoJSON constructor */
	public KmlPolygon(JSONObject json){
		this();
		JSONArray rings = json.optJSONArray("coordinates");
		//ring #0 is the polygon border:
		mCoordinates = KmlGeometry.parseGeoJSONPositions(rings.optJSONArray(0));
		//next rings are the holes:
		if (rings.length() > 1){
			mHoles = new ArrayList<ArrayList<GeoPoint>>(rings.length()-1);
			for (int i=1; i<rings.length(); i++){
				ArrayList<GeoPoint> hole = KmlGeometry.parseGeoJSONPositions(rings.optJSONArray(i));
				mHoles.add(hole);
			}
		}
	}
	
	@Override public void saveAsKML(Writer writer){
		try {
			writer.write("<Polygon>\n");
			writer.write("<outerBoundaryIs>\n<LinearRing>\n");
			writeKMLCoordinates(writer, mCoordinates);
			writer.write("</LinearRing>\n</outerBoundaryIs>\n");
			if (mHoles != null){
				for (ArrayList<GeoPoint> hole:mHoles){
					writer.write("<innerBoundaryIs>\n<LinearRing>\n");
					writeKMLCoordinates(writer, hole);
					writer.write("</LinearRing>\n</innerBoundaryIs>\n");
				}
			}
			writer.write("</Polygon>\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override public JSONObject asGeoJSON(){
		try {
			JSONObject json = new JSONObject();
			json.put("type", "Polygon");
			JSONArray coords = new JSONArray();
			coords.put(KmlGeometry.geoJSONCoordinates(mCoordinates));
			if (mHoles != null) {
				for (ArrayList<GeoPoint> hole:mHoles){
					coords.put(KmlGeometry.geoJSONCoordinates(hole));
				}
			}
			json.put("coordinates", coords);
			return json;
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	//Cloneable implementation ------------------------------------

	@Override public KmlPolygon clone(){
		KmlPolygon kmlPolygon = (KmlPolygon)super.clone();
		if (mHoles != null){
			kmlPolygon.mHoles = new ArrayList<ArrayList<GeoPoint>>(mHoles.size());
			for (ArrayList<GeoPoint> hole:mHoles){
				kmlPolygon.mHoles.add(cloneArrayOfGeoPoint(hole));
			}
		}
		return kmlPolygon;
	}
	
	//Parcelable implementation ------------
	
	@Override public int describeContents() {
		return 0;
	}

	@Override public void writeToParcel(Parcel out, int flags) {
		super.writeToParcel(out, flags);
		if (mHoles != null){
			out.writeInt(mHoles.size());
			for (ArrayList<GeoPoint> l:mHoles)
				out.writeList(l);
		} else 
			out.writeInt(0);
	}
	
	public static final Parcelable.Creator<KmlPolygon> CREATOR = new Parcelable.Creator<KmlPolygon>() {
		@Override public KmlPolygon createFromParcel(Parcel source) {
			return new KmlPolygon(source);
		}
		@Override public KmlPolygon[] newArray(int size) {
			return new KmlPolygon[size];
		}
	};
	
	public KmlPolygon(Parcel in){
		super(in);
		int holes = in.readInt();
		if (holes != 0){
			mHoles = new ArrayList<ArrayList<GeoPoint>>(holes);
			for (int i=0; i<holes; i++){
				ArrayList<GeoPoint> l = in.readArrayList(GeoPoint.class.getClassLoader());
				mHoles.add(l);
			}
		}
	}
	
}
