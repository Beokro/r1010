package search10;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import java.util.List;
import java.util.ArrayList;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Seraph_Roy
 */
public class DFA {
    final private List<Double> data;
    final private double avg;
    final private List<Double> cumSum = new ArrayList<Double>();
    DFA(List<Double> data) {
        this.data = data;
        double sum = 0;
        for(Double item : data) {
            sum += item;
        }
        avg = sum / data.size();
        cumSum.add(data.get(0) - avg);
        for(int i = 1; i < data.size(); i++) {
            cumSum.add(cumSum.get(i - 1) + data.get(i) - avg);
        }
    }
    // n being window size
    private double getFluctuation(int n) {
        List<Double> Yt = new ArrayList<Double>();
        int numWindows = (cumSum.size() + n - 1) / n;
        for(int i = 0; i < numWindows; i++) {
            SimpleRegression reg = new SimpleRegression(true);
            List<Double> window = cumSum.subList(i * n, Math.min((i + 1) * n, cumSum.size()));
            for(int j = 0; j < window.size(); j++) {
                reg.addData(i * n + j, window.get(j));
            }
            for(int j = 0; j < window.size(); j++) {
                double predict = reg.predict(i * n + j);
                if(window.size() != 1) {
                    Yt.add(predict);
                } else {
                    Yt.add(cumSum.get(i * n + j));
                }
            }
        }
        double diff2 = 0;
        for(int i = 0; i < cumSum.size(); i++) {
            diff2 += Math.pow(cumSum.get(i) - Yt.get(i), 2);
        }
        return Math.pow(diff2 / cumSum.size(), 0.5);

    }

    public double dfa() {
        SimpleRegression reg = new SimpleRegression(true);
        int size = data.size();
        int n = 4;
        while(n <= size / 4) {
            reg.addData(Math.log10(n), Math.log10(getFluctuation(n)));
            n += 1;
        }
        return reg.getSlope();
    }
}
