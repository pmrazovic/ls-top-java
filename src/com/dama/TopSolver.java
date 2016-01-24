package com.dama;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Created by pero on 22/01/16.
 */
public class TopSolver {

    int routeCount;
    double availableBudget;
    double walkingSpeed;
    ArrayList<Poi> pois;
    Poi startPoi;
    Poi finishPoi;
    ArrayList<Poi> availablePois;
    ArrayList<Poi> assignedPois;
    ArrayList<Route> routes;
    double solutionScore;
    ArrayList<ArrayList<String>> solutionRoutes;

    TopSolver(int routeCount,
              double availableBudget,
              double walkingSpeed,
              ArrayList<Poi> pois,
              double startLat,
              double startLng,
              double finishLat,
              double finishLng) {

        this.routeCount = routeCount;
        this.availableBudget = availableBudget;
        this.walkingSpeed = walkingSpeed;
        this.pois = pois;
        this.startPoi = new Poi("START", startLat, startLng, 0.0, 0.0);
        this.finishPoi = new Poi("FINISH", finishLat, finishLng, 0.0, 0.0);
        this.pois.add(this.startPoi);
        this.pois.add(this.finishPoi);
        this.availablePois = new ArrayList<Poi>();
        this.assignedPois = new ArrayList<Poi>();
        this.routes = new ArrayList<Route>();
        computeDistancesBetweenPois(this.pois);
    }

    public ArrayList<ArrayList<String>> run(int maxAlgLoop, int maxLSLoop) {

        this.solutionScore = 0.0;
        this.solutionRoutes = new ArrayList<ArrayList<String>>();

        construct();
        int algLoop = 0;
        int disturbCount = 0;

        while (algLoop < maxAlgLoop) {
            algLoop++;
            int lsLoop = 0;
            double solutionScore = 0.0;
            ArrayList<ArrayList<String>> solutionRoutes = new ArrayList<ArrayList<String>>();
            Boolean solutionImproved = true;

            while (solutionImproved && (lsLoop < maxLSLoop)) {
                lsLoop++;
                solutionImproved = false;

                swap();
                tsp();
                swap();
                move();
                insert();
                replace();

                double newSolutionScore = computeSolutionScore();
                if (newSolutionScore > solutionScore) {
                    solutionRoutes.clear();
                    for (Route route : this.routes) {
                        ArrayList<String> routePois = new ArrayList<String>();
                        for (Poi routePoi : route.pois) {
                            routePois.add(routePoi.poiId);
                        }
                        solutionRoutes.add(routePois);
                    }
                    solutionScore = newSolutionScore;
                    solutionImproved = true;
                }
            }

            if (solutionScore > this.solutionScore) {
                this.solutionScore = solutionScore;
                this.solutionRoutes.clear();
                for (ArrayList<String> routePoiIds : solutionRoutes) {
                    this.solutionRoutes.add(routePoiIds);
                }
            } else if (solutionScore == this.solutionScore) {
                if (disturbCount == 0) {
                    disturb(0.7,false);
                    disturbCount++;
                } else if (disturbCount == 1) {
                    disturb(0.7,true);
                    disturbCount++;
                } else {
                    return this.solutionRoutes;
                }
            }

            if (algLoop == maxAlgLoop/2) {
                disturb(0.7,false);
                disturbCount++;
            }

        }

        return this.solutionRoutes;
    }

    // Greedy construction heuristic creates initial solution
    private void construct() {
        // Compute distances to start and finish POI and filter reachable POIs
        ArrayList<Poi> reachablePois = new ArrayList<Poi>();
        for (Poi poi : this.pois) {
            if (poi.poiId != this.startPoi.poiId && poi.poiId != this.finishPoi.poiId) {
                poi.distanceStartEnd = poi.getDistanceFromPoi(this.startPoi.poiId) +
                                       poi.getDistanceFromPoi(this.finishPoi.poiId);
                if (poi.distanceStartEnd + poi.consumingBudget <= this.availableBudget) {
                    this.availablePois.add(poi);
                    reachablePois.add(poi);
                }
            }
        }

        // Sort available POIs by distances to start and end POI, and take first routeCount POIs to intialize routes
        ArrayList<Poi> routeInitPois = (ArrayList<Poi>) this.availablePois.clone();
        Collections.sort(routeInitPois, new Comparator<Poi>() {
            @Override
            public int compare(Poi o1, Poi o2) {
                if (o1.distanceStartEnd > o2.distanceStartEnd)
                    return -1;
                else if (o1.distanceStartEnd < o2.distanceStartEnd)
                    return 1;
                else
                    return 0;
            }
        });

        // Making sure there is enough available POIs to initialize routeCount routes
        int initRouteCount = this.routeCount;
        if (this.routeCount > routeInitPois.size()) {
            initRouteCount = routeInitPois.size();
        }
        routeInitPois = new ArrayList<Poi>(routeInitPois.subList(0, initRouteCount));

        // Initialize routes
        for (Poi initPoi : routeInitPois) {
            Route newRoute = new Route(this.startPoi, this.finishPoi);
            // Add init POI between start and finish POIs, i.e. at the position 1
            newRoute.insertPoi(initPoi,1);
            this.availablePois.remove(initPoi);
            this.routes.add(newRoute);
        }

        // For each of available POIs find cheapest insertion position among all newly created routes
        ArrayList<Poi> includedPois = new ArrayList<Poi>();
        for (Poi insertPoi : this.availablePois) {
            double cheapestCost = Double.MAX_VALUE;
            Route cheapestRoute = null;
            int insertPosition = 0;

            for (Route route : this.routes) {
                double[] r = route.findCheapestInsertion(insertPoi);
                double routeInsertCost = r[0];
                int routeInsertPosition = (int)r[1];

                if ((route.consumedBudget + routeInsertCost <= this.availableBudget) &&
                        (routeInsertCost < cheapestCost)) {
                    cheapestCost = routeInsertCost;
                    insertPosition = routeInsertPosition;
                    cheapestRoute = route;
                }
            }

            // If the cheapest cost is found in the budget
            if (cheapestRoute != null) {
                cheapestRoute.insertPoi(insertPoi,insertPosition,cheapestCost);
                includedPois.add(insertPoi);
            }
        }
        // Remove included POIs
        this.availablePois.removeAll(includedPois);

        // Construct new routes from the remaining available POIs until all points are assigned to routes
        while (this.availablePois.size() > 0) {
            // Initialize new route with most distant available POI
            ArrayList<Poi> sortedInitPois = (ArrayList<Poi>) this.availablePois.clone();
            Collections.sort(sortedInitPois, new Comparator<Poi>() {
                @Override
                public int compare(Poi o1, Poi o2) {
                    if (o1.distanceStartEnd > o2.distanceStartEnd)
                        return -1;
                    else if (o1.distanceStartEnd < o2.distanceStartEnd)
                        return 1;
                    else
                        return 0;
                }
            });
            Poi initPoi = sortedInitPois.get(0);
            Route newRoute = new Route(this.startPoi, this.finishPoi);
            // Add init POI between start and finish POIs, i.e. at the position 1
            newRoute.insertPoi(initPoi,1);
            this.availablePois.remove(initPoi);
            this.routes.add(newRoute);

            // Go through all the available POIs and find the cheapest place for insertion
            ArrayList<Poi> includedPois1 = new ArrayList<Poi>();
            for (Poi insertPoi : this.availablePois) {
                double[] r = newRoute.findCheapestInsertion(insertPoi);
                double insertCost = r[0];
                int insertPosition = (int)r[1];
                if (newRoute.consumedBudget + insertCost <= this.availableBudget) {
                    newRoute.insertPoi(insertPoi,insertPosition,insertCost);
                    includedPois1.add(insertPoi);
                }
            }
            // Remove included POIs
            this.availablePois.removeAll(includedPois1);
        }

        ArrayList<Route> initRoutes = (ArrayList<Route>) this.routes.clone();
        Collections.sort(initRoutes, new Comparator<Route>() {
            @Override
            public int compare(Route o1, Route o2) {
                if (o1.score > o2.score)
                    return -1;
                else if (o1.score < o2.score)
                    return 1;
                else
                    return 0;
            }
        });

        // Making sure there is more than routeCount routes
        int routeCount = this.routeCount;
        if (this.routeCount > initRoutes.size()) {
            routeCount = initRoutes.size();
        }

        // Take first routeCount routes
        this.routes = new ArrayList<Route>(initRoutes.subList(0,routeCount));

        // Assign IDs to routes, and add assinged POIs to corresponding list
        int routeCounter = 0;
        for (Route route : this.routes) {
            route.routeId = Integer.toString(routeCounter);
            routeCounter++;
            for (Poi assignedPoi : route.pois) {
                if (assignedPoi.poiId != this.startPoi.poiId && assignedPoi.poiId != this.finishPoi.poiId) {
                    this.assignedPois.add(assignedPoi);
                }
            }
        }
        // Remove assigned POIs from reachable list
        reachablePois.removeAll(this.assignedPois);
        // Set route to nil for all remaining POIs
        for (Poi reachablePoi : reachablePois) {
            reachablePoi.route = null;
        }
        this.availablePois = (ArrayList<Poi>) reachablePois.clone();
    }

// Method swaps a location between two tours
// This heuristic endeavours to exchange two locations between two tours
    private void swap() {
        boolean swap = true;
        while (swap) {
            swap = false;
            for (Poi poiI : this.assignedPois) {
                for (Poi poiJ : this.assignedPois) {

                    Route routeI = poiI.route;
                    Route routeJ = poiJ.route;
                    if (routeI != routeJ) {

                        double[] rI = routeI.findCheapestReplace(poiI,poiJ);
                        double gainI = rI[0];
                        double costI = rI[1];
                        int insertPositionI = (int) rI[2];

                        double[] rJ = routeJ.findCheapestReplace(poiJ,poiI);
                        double gainJ = rJ[0];
                        double costJ = rJ[1];
                        int insertPositionJ = (int) rJ[2];

                        if ((routeI.consumedBudget - gainI + costI <= this.availableBudget) &&
                                (routeJ.consumedBudget - gainJ + costJ <= this.availableBudget )) {
                            // If the travel time can be reduced in each tour, or if the time saved in one tour
                            // is longer than the extra time needed in the other tour, the swap is carried out
                            if ((gainI > costI && gainJ > costJ) ||
                                    (gainI - costI > costJ - gainJ) ||
                                    (gainJ - costJ > costI - gainI)) {
                                routeI.removePoi(poiI,gainI);
                                routeJ.removePoi(poiJ,gainJ);
                                routeI.insertPoi(poiJ,insertPositionI,costI);
                                routeJ.insertPoi(poiI,insertPositionJ,costJ);
                                swap = true;
                                break;
                            }
                        }

                    }
                }
                if (swap) break;
            }
        }
    }

// A 2-opt heuristic for traveling salesman problem
// https://en.wikipedia.org/wiki/2-opt
    private void tsp() {
        for (Route route : this.routes) {
            route.tsp();
        }
    }

// Move a location from one tour to another
// Methods tries to group together the available time left.
    private void move() {
        ArrayList<Route> shortenedRoutes = new ArrayList<Route>();
        boolean moveMade = true;

        while (moveMade) {
            moveMade = false;
            for (Poi movingPoi : this.assignedPois) {
                for (Route newRoute : this.routes) {
                    Route oldRoute = movingPoi.route;
                    if ((newRoute != oldRoute) && !shortenedRoutes.contains(newRoute)) {

                        double[] r = newRoute.findCheapestInsertion(movingPoi);
                        double insertCost = r[0];
                        int insertPosition = (int) r[1];

                        if (newRoute.consumedBudget + insertCost <= this.availableBudget) {
                            oldRoute.removePoi(movingPoi);
                            newRoute.insertPoi(movingPoi, insertPosition, insertCost);
                            if (!shortenedRoutes.contains(oldRoute)) shortenedRoutes.add(oldRoute);
                            moveMade = true;
                            break;
                        }

                    }
                }
                if (moveMade) break;
            }
        }
    }

// Method attempts to insert new locations in the tours in
// the position where the location consumes the least travel time.
    private void insert() {
        for (Route route : this.routes) {
            boolean insertion = true;
            while (insertion) {
                insertion = false;
                ArrayList<Poi> sortedAvailablePois = sortByAppropriateness(route);
                for (Poi insertPoi : sortedAvailablePois) {

                    double[] r = route.findCheapestInsertion(insertPoi);
                    double insertCost = r[0];
                    int insertPosition = (int) r[1];

                    if (route.consumedBudget + insertCost <= this.availableBudget) {
                        route.insertPoi(insertPoi,insertPosition,insertCost);
                        this.availablePois.remove(insertPoi);
                        this.assignedPois.add(insertPoi);
                        insertion = true;
                        break;
                    }
                }
            }
        }
    }

// Method seeks to replace an included location by a non-included location with a higher score.
    private void replace() {
        for (Route route : this.routes) {
            boolean replacement = true;
            while (replacement) {
                replacement = false;
                ArrayList<Poi> sortedAvailablePois = sortByAppropriateness(route);
                for (Poi insertPoi : sortedAvailablePois) {

                    // First check if there is enough budget to insert POI
                    double[] r = route.findCheapestInsertion(insertPoi);
                    double insertCost = r[0];
                    int insertPosition = (int)r[1];
                    if (route.consumedBudget + insertCost <= this.availableBudget) {
                        route.insertPoi(insertPoi, insertPosition, insertCost);
                        this.availablePois.remove(insertPoi);
                        this.assignedPois.add(insertPoi);
                        replacement = true;
                        break;
                    }

                    // If no avialable budget, try to find it by removing pois with lower scores
                    for (Poi removePoi : new ArrayList<Poi>(route.pois.subList(1,route.pois.size()-1))) {
                        if (removePoi.score < insertPoi.score) {

                            double[] r1 = route.findCheapestReplace(removePoi, insertPoi);
                            double removeGainReplace = r1[0];
                            double insertCostReplace = r1[1];
                            int insertPositionReplace = (int) r1[2];

                            if (route.consumedBudget - removeGainReplace + insertCostReplace <= this.availableBudget) {
                                route.removePoi(removePoi,removeGainReplace);
                                this.availablePois.add(removePoi);
                                this.assignedPois.remove(removePoi);
                                route.insertPoi(insertPoi, insertPositionReplace, insertCostReplace);
                                this.availablePois.remove(insertPoi);
                                this.assignedPois.add(insertPoi);
                                replacement = true;
                                break;
                            }

                        }
                    }
                    if (replacement) break;

                }
            }
        }
    }

    private void disturb(double percentage, boolean fromStart) {
        for (Route route : this.routes) {
            for (Poi removedPoi : route.disturb(percentage,fromStart)) {
                removedPoi.route = null;
                this.availablePois.add(removedPoi);
                this.assignedPois.remove(removedPoi);
            }
        }
    }

    private ArrayList<Poi> sortByAppropriateness(Route route) {
        double[] cog = route.computeRouteCOG();
        HashMap<String,Double> apprDict = new HashMap<String,Double>();
        for (Poi availablePoi : this.availablePois) {
            apprDict.put(availablePoi.poiId, availablePoi.distanceFrom(cog[0],cog[1],this.walkingSpeed));
        }

        ArrayList<Poi> sortedArray = (ArrayList<Poi>) this.availablePois.clone();
        Collections.sort(sortedArray, new Comparator<Poi>() {
            @Override
            public int compare(Poi o1, Poi o2) {
                if (apprDict.get(o1.poiId) > apprDict.get(o2.poiId))
                    return -1;
                else if (apprDict.get(o1.poiId) < apprDict.get(o2.poiId))
                    return 1;
                else
                    return 0;
            }
        });

        return sortedArray;
    }

    private void computeDistancesBetweenPois(ArrayList<Poi> pois) {
        for (Poi poiI : pois) {
            for (Poi poiJ : pois) {
                poiI.setDistanceFromPoi(poiJ.poiId, poiJ.lat, poiJ.lng, this.walkingSpeed);
            }
        }
    }

    private double computeSolutionScore() {
        double solutionScore = 0.0;
        for (Route route : this.routes) {
            solutionScore += route.score;
        }
        return solutionScore;
    }


}
