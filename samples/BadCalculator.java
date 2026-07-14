import java.util.*;
import java.io.*;

public class BadCalculator {

    // magic number used everywhere
    static double tax = 0.18;
    public static int counter = 0;

    public double calc(int a, int b, String op) {
        counter++;
        double result = 0;
        if (op.equals("+")) {
            result = a + b;
        } else if (op.equals("-")) {
            result = a - b;
        } else if (op.equals("*")) {
            result = a * b;
        } else if (op.equals("/")) {
            result = a / b; // potential division by zero — no check
        } else if (op.equals("tax")) {
            result = a * 0.18; // magic number duplicated
        } else if (op.equals("discount")) {
            result = a - (a * 0.15); // another magic number
        } else if (op.equals("compound")) {
            // deeply nested, overly complex
            for (int i = 0; i < 12; i++) {
                result = result + (a * 0.05); // magic number
                if (result > 10000) {
                    System.out.println("DEBUG: result exceeded 10000 at iteration " + i);
                    if (b > 0) {
                        result = result - b;
                        System.out.println("DEBUG: subtracted b, result now " + result);
                    } else {
                        // do nothing
                    }
                }
            }
        }
        System.out.println("Result: " + result);
        return result;
    }

    // empty catch block — swallows exceptions silently
    public void saveResult(double result, String filename) {
        try {
            FileWriter fw = new FileWriter(filename);
            fw.write(String.valueOf(result));
            fw.close(); // no try-with-resources
        } catch (Exception e) {
            // TODO: handle later
        }
    }

    // overly long method doing too many things
    public String generateReport(List<double[]> data) {
        String report = "";
        report += "=== CALCULATION REPORT ===\n";
        report += "Generated: " + new java.util.Date() + "\n"; // deprecated usage
        report += "Total operations: " + counter + "\n\n";

        double total = 0;
        double min = 999999999; // should use Double.MAX_VALUE
        double max = -999999999;

        for (int i = 0; i < data.size(); i++) { // could use enhanced for
            double[] row = data.get(i);
            double val = row[0];
            total += val;
            if (val < min) min = val;
            if (val > max) max = val;

            // string concatenation in loop — inefficient
            report += "  Operation " + (i + 1) + ": " + val + "\n";
        }

        double avg = total / data.size(); // potential division by zero
        report += "\nSummary:\n";
        report += "  Total: " + total + "\n";
        report += "  Average: " + avg + "\n";
        report += "  Min: " + min + "\n";
        report += "  Max: " + max + "\n";

        return report;
    }

    // poor method naming, unclear parameters
    public int x(int a) {
        if (a <= 1) return a;
        return x(a - 1) + x(a - 2); // exponential time complexity — no memoization
    }

    // mutable shared state, not thread-safe
    public static void resetCounter() {
        counter = 0;
    }
}
