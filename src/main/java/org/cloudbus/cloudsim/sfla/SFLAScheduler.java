package org.cloudbus.cloudsim.sfla;

import java.util.*;

/**
 * Shuffled Frog-Leaping Algorithm (SFLA) untuk penjadwalan tugas cloud.
 * Referensi: Eusuff, M. M., & Lansey, K. E. (2003).
 *
 * Setiap "katak" merepresentasikan satu solusi penjadwalan: vektor berukuran n (jumlah cloudlet)
 * dengan nilai kontinu [0, vmCount), di mana floor(nilai) = indeks VM yang ditugaskan.
 */
public class SFLAScheduler {

    private final int populationSize;
    private final int memeplexCount;
    private final int localIterations;
    private final int maxIterations;
    private final int numCloudlets;
    private final int numVms;
    private final double[] cloudletLengths; // MI per cloudlet
    private final double[] vmMips;          // MIPS per VM
    private final Random rng;

    private double[][] population;  // [populationSize][numCloudlets]
    private double[] fitness;
    private double[] bestSolution;
    private double bestFitness;

    private final List<double[]> convergenceHistory = new ArrayList<>();

    public SFLAScheduler(int populationSize, int memeplexCount, int localIterations,
                         int maxIterations, double[] cloudletLengths, double[] vmMips, long seed) {
        this.populationSize = populationSize;
        this.memeplexCount = memeplexCount;
        this.localIterations = localIterations;
        this.maxIterations = maxIterations;
        this.numCloudlets = cloudletLengths.length;
        this.numVms = vmMips.length;
        this.cloudletLengths = cloudletLengths;
        this.vmMips = vmMips;
        this.rng = new Random(seed);
    }

    public int[] run() {
        initPopulation();

        for (int iter = 0; iter < maxIterations; iter++) {
            sortPopulation();

            int[][] memeplexes = partitionMemeplexes();

            for (int k = 0; k < memeplexCount; k++) {
                evolveMemeplex(memeplexes[k]);
            }

            mergeMemeplexes(memeplexes);

            convergenceHistory.add(new double[]{iter + 1, bestFitness});
        }

        return decodeSchedule(bestSolution);
    }

    private void initPopulation() {
        population = new double[populationSize][numCloudlets];
        fitness = new double[populationSize];
        double upper = numVms - 1e-6;

        for (int i = 0; i < populationSize; i++) {
            for (int j = 0; j < numCloudlets; j++) {
                population[i][j] = rng.nextDouble() * upper;
            }
            fitness[i] = evaluateFitness(population[i]);
        }

        bestFitness = Double.MAX_VALUE;
        bestSolution = new double[numCloudlets];
        updateGlobalBest();
    }

    private double evaluateFitness(double[] solution) {
        double[] vmLoads = new double[numVms];
        int[] assignment = decodeSchedule(solution);

        for (int i = 0; i < numCloudlets; i++) {
            int vmIdx = assignment[i];
            vmLoads[vmIdx] += cloudletLengths[i] / vmMips[vmIdx];
        }

        double makespan = 0;
        double sumLoad = 0;
        for (double load : vmLoads) {
            if (load > makespan) makespan = load;
            sumLoad += load;
        }

        double meanLoad = sumLoad / numVms;
        double variance = 0;
        for (double load : vmLoads) {
            variance += (load - meanLoad) * (load - meanLoad);
        }
        double imbalanceStd = Math.sqrt(variance / numVms);

        return makespan + 0.001 * imbalanceStd;
    }

    private int[] decodeSchedule(double[] solution) {
        int[] assignment = new int[numCloudlets];
        int maxIdx = numVms - 1;
        for (int i = 0; i < numCloudlets; i++) {
            int idx = (int) Math.floor(solution[i]);
            if (idx < 0) idx = 0;
            if (idx > maxIdx) idx = maxIdx;
            assignment[i] = idx;
        }
        return assignment;
    }

    private void sortPopulation() {
        Integer[] indices = new Integer[populationSize];
        for (int i = 0; i < populationSize; i++) indices[i] = i;

        Arrays.sort(indices, Comparator.comparingDouble(a -> fitness[a]));

        double[][] sortedPop = new double[populationSize][];
        double[] sortedFit = new double[populationSize];
        for (int i = 0; i < populationSize; i++) {
            sortedPop[i] = population[indices[i]];
            sortedFit[i] = fitness[indices[i]];
        }
        population = sortedPop;
        fitness = sortedFit;

        updateGlobalBest();
    }

    private int[][] partitionMemeplexes() {
        int perMemeplex = populationSize / memeplexCount;
        int remainder = populationSize % memeplexCount;
        int[][] memeplexes = new int[memeplexCount][];

        List<List<Integer>> groups = new ArrayList<>();
        for (int k = 0; k < memeplexCount; k++) {
            groups.add(new ArrayList<>());
        }

        for (int i = 0; i < populationSize; i++) {
            groups.get(i % memeplexCount).add(i);
        }

        for (int k = 0; k < memeplexCount; k++) {
            memeplexes[k] = groups.get(k).stream().mapToInt(Integer::intValue).toArray();
        }

        return memeplexes;
    }

    private void evolveMemeplex(int[] memeplex) {
        for (int step = 0; step < localIterations; step++) {
            Arrays.sort(memeplex);
            sortMemeplexByFitness(memeplex);

            int bestIdx = memeplex[0];
            int worstIdx = memeplex[memeplex.length - 1];

            double oldFitness = fitness[worstIdx];
            double[] xw = population[worstIdx];
            double[] xb = population[bestIdx];

            // Percobaan 1: Lompat ke arah Xb (terbaik memeplex)
            double[] candidate = computeLeap(xw, xb);
            double candidateFit = evaluateFitness(candidate);

            if (candidateFit < oldFitness) {
                population[worstIdx] = candidate;
                fitness[worstIdx] = candidateFit;
            } else {
                // Percobaan 2: Lompat ke arah Xg (terbaik global)
                candidate = computeLeap(xw, bestSolution);
                candidateFit = evaluateFitness(candidate);

                if (candidateFit < oldFitness) {
                    population[worstIdx] = candidate;
                    fitness[worstIdx] = candidateFit;
                } else {
                    // Percobaan 3: Posisi acak baru
                    double upper = numVms - 1e-6;
                    double[] randSol = new double[numCloudlets];
                    for (int j = 0; j < numCloudlets; j++) {
                        randSol[j] = rng.nextDouble() * upper;
                    }
                    population[worstIdx] = randSol;
                    fitness[worstIdx] = evaluateFitness(randSol);
                }
            }

            if (fitness[worstIdx] < bestFitness) {
                bestFitness = fitness[worstIdx];
                bestSolution = Arrays.copyOf(population[worstIdx], numCloudlets);
            }
        }
    }

    private double[] computeLeap(double[] xw, double[] target) {
        double[] result = new double[numCloudlets];
        double upper = numVms - 1e-6;

        for (int j = 0; j < numCloudlets; j++) {
            double r = rng.nextDouble();
            double newVal = xw[j] + r * 1.6 * (target[j] - xw[j]);
            if (newVal < 0) newVal = 0;
            if (newVal > upper) newVal = upper;
            result[j] = newVal;
        }
        return result;
    }

    private void mergeMemeplexes(int[][] memeplexes) {
        // Population sudah di-update in-place, hanya perlu sort ulang
        sortPopulation();
    }

    private void sortMemeplexByFitness(int[] memeplex) {
        int n = memeplex.length;
        for (int i = 0; i < n - 1; i++) {
            for (int j = i + 1; j < n; j++) {
                if (fitness[memeplex[j]] < fitness[memeplex[i]]) {
                    int tmp = memeplex[i];
                    memeplex[i] = memeplex[j];
                    memeplex[j] = tmp;
                }
            }
        }
    }

    private void updateGlobalBest() {
        for (int i = 0; i < populationSize; i++) {
            if (fitness[i] < bestFitness) {
                bestFitness = fitness[i];
                bestSolution = Arrays.copyOf(population[i], numCloudlets);
            }
        }
    }

    public double getBestFitness() {
        return bestFitness;
    }

    public double getBestMakespan() {
        double[] vmLoads = new double[numVms];
        int[] assignment = decodeSchedule(bestSolution);
        for (int i = 0; i < numCloudlets; i++) {
            vmLoads[assignment[i]] += cloudletLengths[i] / vmMips[assignment[i]];
        }
        double makespan = 0;
        for (double load : vmLoads) {
            if (load > makespan) makespan = load;
        }
        return makespan;
    }

    public List<double[]> getConvergenceHistory() {
        return convergenceHistory;
    }

    public double[] getBestSolution() {
        return bestSolution;
    }
}
