package com.dama;

import java.lang.reflect.Array;
import java.util.ArrayList;

/**
 * Created by pero on 22/01/16.
 */
public class Route {
    String routeId;
    double score;
    double consumedBudget;
    ArrayList<Poi> pois;

    Route(Poi startPoi, Poi finishPoi) {
        this.pois = new ArrayList<Poi>();
        this.pois.add(startPoi);
        this.pois.add(finishPoi);
        this.consumedBudget = computeTotalConsumedBudget(this.pois);
        this.score = computeTotalScore(this.pois);
    }

    public void insertPoi(Poi insertPoi, int position) {
        double cost = getInsertionCost(this.pois, insertPoi, position);
        insertPoi(insertPoi, position, cost);
    }

    public void insertPoi(Poi insertPoi, int position, double cost) {
        this.pois.add(position,insertPoi);
        this.consumedBudget += cost;
        this.score += insertPoi.score;
        insertPoi.route = this;
    }

    public void removePoi(Poi removePoi) {
        double gain = getDelitionGain(removePoi);
        removePoi(removePoi,gain);
    }

    public void removePoi(Poi removePoi, double gain) {
        this.pois.remove(removePoi);
        this.consumedBudget -= gain;
        this.score -= removePoi.score;
        removePoi.route = null;
    }

    public double[] findCheapestInsertion(Poi insertPoi) {
        double insertCost = Double.MAX_VALUE;
        int insertPosition = 0;
        for (int i = 1; i < this.pois.size(); i++) {
            double newCost = getInsertionCost(this.pois,insertPoi,i);
            if (newCost < insertCost) {
                insertCost = newCost;
                insertPosition = i;

            }
        }

        double[] returnArray = new double[]{insertCost, (double)insertPosition};
        return returnArray;
    }

    public double[] findCheapestReplace(Poi removePoi, Poi insertPoi) {

        double removeGain = getDelitionGain(removePoi);

        // Temporarily remove removePoi from the list to calculate replace cost (remember original position)
        int originalPosition = this.pois.indexOf(removePoi);
        this.pois.remove(removePoi);

        double[] r = findCheapestInsertion(insertPoi);
        double insertCost = r[0];
        int insertPosition = (int) r[1];

        // Put removePoi back to list
        this.pois.add(originalPosition, removePoi);

        double[] returnArray = new double[]{removeGain, insertCost, (double) insertPosition};
        return returnArray;
    }

    public double computeTotalScore(ArrayList<Poi> pois) {
        double score = 0.0;
        for (Poi includedPoi : pois) {
            score += includedPoi.score;
        }
        return score;
    }

    public double computeTotalConsumedBudget(ArrayList<Poi> pois) {
        double consumedBudget = 0.0;
        for (int i = 0; i < pois.size()-1; i++) {
            Poi currentPoi = pois.get(i);
            Poi nextPoi = pois.get(i+1);
            consumedBudget += currentPoi.consumingBudget + currentPoi.getDistanceFromPoi(nextPoi.poiId);
        }
        Poi lastPoi = pois.get(pois.size()-1);
        consumedBudget += lastPoi.consumingBudget;
        return consumedBudget;
    }

    public double getInsertionCost(ArrayList<Poi> pois, Poi insertPoi, int position) {
        Poi previousPoi = pois.get(position-1);
        Poi nextPoi = pois.get(position);
        double cost = previousPoi.getDistanceFromPoi(insertPoi.poiId) +
                      insertPoi.getDistanceFromPoi(nextPoi.poiId) -
                      previousPoi.getDistanceFromPoi(nextPoi.poiId) +
                      insertPoi.consumingBudget;
        return cost;
    }

    public double getDelitionGain(Poi removePoi) {
        int position = this.pois.indexOf(removePoi);
        Poi previousPoi = this.pois.get(position-1);
        Poi nextPoi = this.pois.get(position+1);
        double gain = previousPoi.getDistanceFromPoi(removePoi.poiId) +
                      removePoi.getDistanceFromPoi(nextPoi.poiId) +
                      removePoi.consumingBudget -
                      previousPoi.getDistanceFromPoi(nextPoi.poiId);
        return gain;
    }

    public double[] computeRouteCOG() {
        double cogX = 0.0;
        double cogY = 0.0;

        for (Poi includedPoi : this.pois) {
            cogX += includedPoi.score * includedPoi.lat;
            cogY += includedPoi.score * includedPoi.lng;
        }
        double[] cog = new double[]{cogX/this.score, cogY/this.score};
        if (this.score == 0.0) {
            cog[0] = 0.0;
            cog[1] = 0.0;
        }
        return cog;
    }

    public void tsp() {
        double consumedBudget = computeTotalConsumedBudget(this.pois);
        ArrayList<Poi> tmpPois = (ArrayList<Poi>) this.pois.clone();
        boolean edgeSwaped = true;

        while (edgeSwaped) {
            edgeSwaped = false;
            for (int i = 1; i < tmpPois.size()-1; i++) {
                for (int k = i+1; k < tmpPois.size()-1; k++) {
                    double oldDistance = tmpPois.get(i-1).getDistanceFromPoi(tmpPois.get(i).poiId) +
                                         tmpPois.get(k).getDistanceFromPoi(tmpPois.get(k+1).poiId);
                    double newDistance = tmpPois.get(i-1).getDistanceFromPoi(tmpPois.get(k).poiId) +
                                         tmpPois.get(i).getDistanceFromPoi(tmpPois.get(k+1).poiId);

                    if (newDistance < oldDistance) {
                        twoOpt(tmpPois,i,k);
                        edgeSwaped = true;
                    }
                }
            }
        }

        double tempBudget = computeTotalConsumedBudget(tmpPois);
        if (tempBudget < consumedBudget) {
            this.pois = tmpPois;
        }

    }

    public ArrayList<Poi> disturb(double percentage, boolean fromStart) {
        int removeCount = (int)((this.pois.size()-2)*percentage);
        ArrayList<Poi> removedPois = new ArrayList<Poi>();
        Poi removePoi;

        int counter = 0;
        while (counter < removeCount) {
            if (fromStart) {
                removePoi = this.pois.get(1);
            } else {
                removePoi = this.pois.get(this.pois.size() - 2);
            }
            removedPois.add(removePoi);
            this.pois.remove(removePoi);
            this.score -= removePoi.score;
            counter++;
        }

        return removedPois;
    }

    public void twoOpt(ArrayList<Poi> pois, int i, int k) {
        int l = k;
        int limit = i + ((k - i) / 2);
        for (int j = i; j <= limit; j++) {
            Poi tmpPoi = pois.get(j);
            pois.set(j,pois.get(l));
            pois.set(l,tmpPoi);
            l--;
        }
    }
}
