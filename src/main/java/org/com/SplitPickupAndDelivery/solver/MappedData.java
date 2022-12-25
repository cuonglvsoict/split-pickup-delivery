package org.com.SplitPickupAndDelivery.solver;

import org.apache.commons.math3.util.Pair;
import org.com.SplitPickupAndDelivery.models.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

class MappedRequest {

    public String id;
    public int from_hub;
    public long pickupTime;
    public int to_hub;
    public long deliveryTime;
    public double quantity;

    public MappedRequest() {

    }

    public MappedRequest(int from, int to, double quantity) {
        this.from_hub = from;
        this.to_hub = to;
        this.quantity = quantity;
    }
}

public class MappedData {

    public static InputData data;
    public static int N;                        // number of hubs
    public static int K;                        // number of trucks
    public static double[][] travel_time;       // travel time between hubs, travel_time[i][j] = -1 if there is no direct path from i to j
    public static ArrayList<Double> capacity;
    public static ArrayList<ArrayList<Integer>> forbiddenPoints;
    public static ArrayList<MappedRequest> requests;

    public static HashMap<String, Integer> _hubID2HubIndex;
    public static HashMap<String, Integer> _truckID2TruckIndex;
    public static HashMap<Pair<Integer, Integer>, Request> _mapRequest;

    public static void parseInput(InputData input_data) {
        data = input_data;

        N = input_data.hubs.size();
        _hubID2HubIndex = new HashMap<>();
        for (int i=0; i<input_data.hubs.size(); i++) {
            _hubID2HubIndex.put(input_data.hubs.get(i).getHubID(), i);
        }

        K = input_data.trucks.size();
        _truckID2TruckIndex = new HashMap<>();
        capacity = new ArrayList<>();
        for (int i=0; i<input_data.trucks.size(); i++) {
            _truckID2TruckIndex.put(input_data.trucks.get(i).getTruckID(), i);
            capacity.add(input_data.trucks.get(i).getCapacity());
        }

        forbiddenPoints = new ArrayList<>();
        for (int i=0; i<K; i++) {
            if (input_data.trucks.get(i).getForbiddenPoints() == null) {
                forbiddenPoints.add(null);
            } else {
                ArrayList<Integer> fbp = new ArrayList<>();
                for (String point: input_data.trucks.get(i).getForbiddenPoints()) {
                    fbp.add(_hubID2HubIndex.get(point));
                }

                forbiddenPoints.add(fbp);
            }
        }

        travel_time = new double[N][N];
        for (int i=0; i<N; i++) {
            for (int j=0; j<N; j++) {
                if (i == j) {
                    travel_time[i][j] = 0;
                } else {
                    Pair<String, String> key = new Pair<>(input_data.hubs.get(i).getHubID(), input_data.hubs.get(j).getHubID());
                    Long time = input_data.travel_time.get(key);

                    if (time == null) {
                        travel_time[i][j] = -1;
                    } else {
                        travel_time[i][j] = time;
                    }
                }
            }
        }

        requests = new ArrayList<>();
        _mapRequest = new HashMap<>();
        for (Request req: input_data.requests) {
            MappedRequest mapped_req = new MappedRequest();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:SS");

            mapped_req.id = req.getRequestID();
            mapped_req.from_hub = _hubID2HubIndex.get(req.getPickupPoint());
            mapped_req.to_hub = _hubID2HubIndex.get(req.getDeliveryPoint());
            mapped_req.quantity = req.getDemand();

            try {
                mapped_req.pickupTime = formatter.parse(req.getPickupDateTime()).getTime();
                mapped_req.deliveryTime = formatter.parse(req.getDeliveryDateTime()).getTime();
            } catch (ParseException e) {
                e.printStackTrace();
            }

            requests.add(mapped_req);
            _mapRequest.put(new Pair<>(mapped_req.from_hub, mapped_req.to_hub), req);
        }
    }

    public static Solution resolveOutput(MappedSolution raw_solution) {
        Solution solution = new Solution();

        ArrayList<ArrayList<Integer>> raw_routes = raw_solution.routes;
        ArrayList<ArrayList<ArrayList<MappedRequest>>> raw_pick = raw_solution.pickup;
        ArrayList<ArrayList<ArrayList<MappedRequest>>> raw_delivery = raw_solution.delivery;

        ArrayList<Route> routes = new ArrayList<>();
        for (int r=0; r<raw_routes.size(); r++) {
            if (raw_routes.get(r).size() == 0) {
                // truck is not used
                routes.add(null);
            } else {
                ArrayList<Hub> path = new ArrayList<>();
                ArrayList<ArrayList<Request>> pick_operations = new ArrayList<>();
                ArrayList<ArrayList<Request>> drop_operations = new ArrayList<>();

                for (int i=0; i<raw_routes.get(r).size(); i++) {
                    path.add(data.hubs.get(raw_routes.get(r).get(i)));

                    ArrayList<Request> pick = new ArrayList<>();
                    for (MappedRequest rp: raw_pick.get(r).get(i)) {
                        Pair<Integer, Integer> key = new Pair<>(rp.from_hub, rp.to_hub);
                        Request req = _mapRequest.get(key).clone();
                        req.setDemand(rp.quantity);
                        pick.add(req);
                    }

                    ArrayList<Request> drop = new ArrayList<>();
                    for (MappedRequest rp: raw_delivery.get(r).get(i)) {
                        Pair<Integer, Integer> key = new Pair<>(rp.from_hub, rp.to_hub);
                        Request req = _mapRequest.get(key).clone();
                        req.setDemand(rp.quantity);
                        drop.add(req);
                    }

                    pick_operations.add(pick);
                    drop_operations.add(drop);
                }

                Route route = new Route();
                route.truckID = data.trucks.get(r).getTruckID();
                route.path = path;
                route.pickup = pick_operations;
                route.drop = drop_operations;

                routes.add(route);
            }
        }

        solution.routes = routes;
        return solution;
    }

    public static boolean read_input_data(String filepath) {
        try {
            Scanner in = new Scanner(new File(filepath));

            N = in.nextInt();
            K = in.nextInt();

            capacity = new ArrayList<>();
            for (int k=0; k<K; k++) {
                capacity.add(in.nextDouble());
            }

            int req_no = in.nextInt();
            requests = new ArrayList<>();
            for (int i=0; i<req_no; i++) {
                requests.add(new MappedRequest(in.nextInt(), in.nextInt(), in.nextDouble()));
            }

            in.close();
            return true;
        } catch (FileNotFoundException e) {
            return false;
        }
    }

    public static void display() {
        System.out.println("Number of HUBs: " + N);
        System.out.println("Number of Trucks: " + K);

        System.out.print("\tTruck capacity: ");
        for (double cap: capacity) {
            System.out.print(cap + ", ");
        }
        System.out.println();

        System.out.println("Number of requests: " + requests.size());

        for (int i=0; i<requests.size(); i++) {
            System.out.println("\tHUB " + requests.get(i).from_hub + " (" + requests.get(i).pickupTime + ") -> HUB " + requests.get(i).to_hub + " (" + requests.get(i).deliveryTime + "): " + requests.get(i).quantity);
        }
    }
}
