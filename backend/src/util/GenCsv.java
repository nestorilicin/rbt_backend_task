import java.io.*;
import java.util.Random;


// generates a CSV file with random valid transactions for import performance tests.
// usage: java src/util/GenCsv.java <rows> <output file>


public class GenCsv {
    public static void main(String[] args) throws IOException {
        int rows = Integer.parseInt(args[0]);
        String[] cats = {"Food", "Eating out", "Transport", "Rent", "Fun", "Other"};   // moraju postojati u bazi
        Random r = new Random(42);
        try (PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(args[1])))) {
            w.println("type,amount,date,description,category");
            for (int i = 1; i <= rows; i++) {
                String type = r.nextInt(10) == 0 ? "income" : "expense";
                String amount = (1 + r.nextInt(50000)) / 100.0 + "";
                if (amount.indexOf('.') == amount.length() - 2) amount += "0";
                String date = "2026-" + String.format("%02d", 1 + r.nextInt(12)) + "-" + String.format("%02d", 1 + r.nextInt(28));
                w.println(type + "," + amount + "," + date + ",Row " + i + "," + cats[r.nextInt(cats.length)]);
            }
        }
    }
}