package org.com.SplitPickupAndDelivery.utils;

import org.apache.commons.math3.util.Pair;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.com.SplitPickupAndDelivery.models.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.stream.Collectors;

public class IOExcelHandling {

    public static InputData readInputData(String filePath) {
        try {
            FileInputStream file = new FileInputStream(new File(filePath));
            XSSFWorkbook workbook = new XSSFWorkbook(file);

            InputData data = new InputData();

            // read request info
            ArrayList<Request> req_list = new ArrayList<>();
            XSSFSheet request_sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = request_sheet.iterator();
            rowIterator.next(); // ignore the header row
            while (rowIterator.hasNext())
            {
                Row row = rowIterator.next();
                Request req = new Request();

                req.setRequestID(row.getCell(0).getStringCellValue());
                req.setPickupPoint(row.getCell(1).getStringCellValue());
                req.setPickupDateTime(row.getCell(2).getStringCellValue());
                req.setDeliveryPoint(row.getCell(3).getStringCellValue());
                req.setDeliveryDateTime(row.getCell(4).getStringCellValue());

                if (row.getCell(5).getCellType() == CellType.NUMERIC) {
                    req.setDemand(row.getCell(2).getNumericCellValue());
                } else {
                    req.setDemand(Double.parseDouble(row.getCell(5).getStringCellValue()));
                }

                req_list.add(req);
            }
            data.requests = req_list;

            // read hubs info
            ArrayList<Hub> hub_list = new ArrayList<Hub>();
            XSSFSheet hub_sheet = workbook.getSheetAt(1);
            rowIterator = hub_sheet.iterator();
            rowIterator.next(); // ignore the header row
            while (rowIterator.hasNext())
            {
                Row row = rowIterator.next();
                Hub hub = new Hub();

                hub.setHubID(row.getCell(0).getStringCellValue());
                hub.setHubName(row.getCell(1).getStringCellValue());

                hub_list.add(hub);
            }
            data.hubs = hub_list;

            // read hubs info
            HashMap<Pair<String, String>, Long> travel_time = new HashMap<Pair<String, String>, Long>();
            XSSFSheet distance_sheet = workbook.getSheetAt(2);
            rowIterator = distance_sheet.iterator();
            rowIterator.next(); // ignore the header row
            while (rowIterator.hasNext())
            {
                Row row = rowIterator.next();

                String src_hub = row.getCell(0).getStringCellValue();
                String des_hub = row.getCell(1).getStringCellValue();

                long time = -1;
                if (row.getCell(2) == null) {
                    // the path does not exist
                    continue;
                }

                if (row.getCell(2).getCellType() == CellType.NUMERIC) {
                    time = (long) row.getCell(2).getNumericCellValue();
                } else {
                    time = Long.parseLong(row.getCell(2).getStringCellValue());
                }
                travel_time.put(new Pair<>(src_hub, des_hub), time);
            }
            data.travel_time = travel_time;

            // read truck info
            ArrayList<Truck> truck_list = new ArrayList<Truck>();
            XSSFSheet truck_sheet = workbook.getSheetAt(3);
            rowIterator = truck_sheet.iterator();
            rowIterator.next(); // ignore the header row
            while (rowIterator.hasNext())
            {
                Row row = rowIterator.next();
                Truck truck = new Truck();

                truck.setTruckID(row.getCell(0).getStringCellValue());
                truck.setLocation(row.getCell(1).getStringCellValue());

                if (row.getCell(2).getCellType() == CellType.NUMERIC) {
                    truck.setCapacity(row.getCell(2).getNumericCellValue());
                } else {
                    truck.setCapacity(Double.parseDouble(row.getCell(2).getStringCellValue()));
                }

                if (row.getCell(3) != null) {
                    ArrayList<String> fp = new ArrayList<>();
                    String[] arr_fb = row.getCell(3).getStringCellValue().split(",");
                    fp.addAll(Arrays.stream(arr_fb).collect(Collectors.toList()));
                    truck.setForbiddenPoints(fp);
                }

                truck_list.add(truck);
            }
            data.trucks = truck_list;

            file.close();
            return data;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void exportSolution(String filePath, Solution output) {
        XSSFWorkbook workbook = new XSSFWorkbook();

        // saver route info
        XSSFSheet sheet = workbook.createSheet("Solution");
        int row_num = 0;

        // header row
        Row row = sheet.createRow(row_num++);
        int cell_num = 0;
        Cell cel = row.createCell(cell_num++);
        cel.setCellValue("TruckID");

        cel = row.createCell(cell_num++);
        cel.setCellValue("HUB");

        cel = row.createCell(cell_num++);
        cel.setCellValue("Seq");

        cel = row.createCell(cell_num++);
        cel.setCellValue("Request");

        cel = row.createCell(cell_num++);
        cel.setCellValue("Pickup");

        cel = row.createCell(cell_num++);
        cel.setCellValue("Delivery");

        for (Route r: output.routes) {
            if (r == null) {
                continue;
            }

            for (int i=0; i<r.path.size(); i++) {
                Hub h = r.path.get(i);

                for (int j=0; j<r.pickup.get(i).size(); j++) {
                    // pickup operation at hub h
                    row = sheet.createRow(row_num++);
                    cell_num = 0;
                    cel = row.createCell(cell_num++);
                    cel.setCellValue(r.truckID);

                    cel = row.createCell(cell_num++);
                    cel.setCellValue(h.getHubID());

                    cel = row.createCell(cell_num++);
                    cel.setCellValue(i+1);

                    cel = row.createCell(cell_num++);
                    cel.setCellValue(r.pickup.get(i).get(j).getRequestID());

                    cel = row.createCell(cell_num++);
                    cel.setCellValue(r.pickup.get(i).get(j).getDemand());

                    cel = row.createCell(cell_num++);
                    cel.setCellValue(" ");
                }

                for (int j=0; j<r.drop.get(i).size(); j++) {
                    // drop operation at hub h
                    row = sheet.createRow(row_num++);
                    cell_num = 0;
                    cel = row.createCell(cell_num++);
                    cel.setCellValue(r.truckID);

                    cel = row.createCell(cell_num++);
                    cel.setCellValue(h.getHubID());

                    cel = row.createCell(cell_num++);
                    cel.setCellValue(i+1);

                    cel = row.createCell(cell_num++);
                    cel.setCellValue(r.drop.get(i).get(j).getRequestID());

                    cel = row.createCell(cell_num++);
                    cel.setCellValue(" ");

                    cel = row.createCell(cell_num++);
                    cel.setCellValue(r.drop.get(i).get(j).getDemand());
                }
            }
        }

        FileOutputStream fout = null;
        try {
            fout = new FileOutputStream(new File(filePath));
            workbook.write(fout);
            fout.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
