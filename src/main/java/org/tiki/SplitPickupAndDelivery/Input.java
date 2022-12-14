package org.tiki.SplitPickupAndDelivery;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

class Request {
    public int from_hub;
    public int to_hub;
    public double quantity;

    public Request(int from, int to, double quantity) {
        this.from_hub = from;
        this.to_hub = to;
        this.quantity = quantity;
    }
}

public class Input {

    public static int N;                        // number of hubs
    public static int K;                        // number of trucks
    public static ArrayList<Double> capacity;
    public static ArrayList<Request> requests;

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
                requests.add(new Request(in.nextInt(), in.nextInt(), in.nextDouble()));
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
        System.out.println("Number of requests: " + requests.size());

        for (int i=0; i<requests.size(); i++) {
            System.out.println("\tHUB " + requests.get(i).from_hub + " -> HUB " + requests.get(i).to_hub + ": " + requests.get(i).quantity);
        }
    }
}
