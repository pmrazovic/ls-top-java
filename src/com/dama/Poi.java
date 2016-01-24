package com.dama;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by pero on 22/01/16.
 */
public class Poi {

    String poiId;
    double lat;
    double lng;
    double score;
    double consumingBudget;
    Route route;
    double distanceStartEnd;
    Map<String, Double> distanceDictionary;

    Poi(String poiId, double lat, double lng, double score, double consumingBudget) {
        this.poiId = poiId;
        this.lat = lat;
        this.lng = lng;
        this.score = score;
        this.consumingBudget = consumingBudget;
        this.distanceDictionary = new HashMap<String, Double>();
    }

    public double distanceFrom(double lat, double lng, double walkingSpeed) {
        double dlon = toRadians(lng) - toRadians(this.lng);
        double dlat = toRadians(lat) - toRadians(this.lat);

        double a = Math.pow(Math.sin(dlat/2),2) + Math.cos(toRadians(this.lat)) * Math.cos(toRadians(lat)) * Math.pow(Math.sin(dlon/2),2);
        double c = 2 * Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
        double d = 6373 * c * 1000;

        if (d > 1500) {
            // simulating speed of public transport (200 m/min)
            // http://www.tmb.cat/en/transports-en-xifres
            d = d / 200.0;
        } else {
            d = d / walkingSpeed;
        }

        return d;
    }

    public void setDistanceFromPoi(String idToPoi, double lat, double lng, double walkingSpeed) {

        double distance = distanceFrom(lat,lng,walkingSpeed);
        this.distanceDictionary.put(idToPoi,distance);
    }

    public double getDistanceFromPoi(String idToPoi) {
        return this.distanceDictionary.get(idToPoi);
    }

    private double toRadians(double degrees) {
        return degrees * (Math.PI/ 180.0);
    }



}
