package org.cloudbus.cloudsim.sfla;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudActionTags;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.lists.VmList;

import java.util.ArrayList;
import java.util.List;

/**
 * DatacenterBroker yang menggunakan algoritma SFLA untuk menetapkan cloudlet ke VM.
 * Alih-alih round-robin, broker ini menjalankan SFLA untuk menemukan pemetaan
 * cloudlet-ke-VM yang meminimalkan makespan.
 */
public class SFLADatacenterBroker extends DatacenterBroker {

    private int populationSize = 30;
    private int memeplexCount = 5;
    private int localIterations = 5;
    private int maxIterations = 50;
    private long seed = 42;

    private int[] sflaAssignment;
    private double sflaMakespan;

    public SFLADatacenterBroker(String name) throws Exception {
        super(name);
    }

    public void setSFLAParameters(int populationSize, int memeplexCount,
                                  int localIterations, int maxIterations, long seed) {
        this.populationSize = populationSize;
        this.memeplexCount = memeplexCount;
        this.localIterations = localIterations;
        this.maxIterations = maxIterations;
        this.seed = seed;
    }

    @Override
    protected void submitCloudlets() {
        List<Cloudlet> cloudlets = getCloudletList();
        List<GuestEntity> vmsCreated = getGuestsCreatedList();

        if (cloudlets.isEmpty() || vmsCreated.isEmpty()) {
            super.submitCloudlets();
            return;
        }

        Log.println();
        Log.println("=== SFLA Optimization Starting ===");
        Log.println("Cloudlets: " + cloudlets.size() + ", VMs: " + vmsCreated.size());

        double[] cloudletLengths = new double[cloudlets.size()];
        for (int i = 0; i < cloudlets.size(); i++) {
            cloudletLengths[i] = cloudlets.get(i).getCloudletLength();
        }

        double[] vmMips = new double[vmsCreated.size()];
        for (int i = 0; i < vmsCreated.size(); i++) {
            vmMips[i] = vmsCreated.get(i).getMips();
        }

        SFLAScheduler sfla = new SFLAScheduler(
                populationSize, memeplexCount, localIterations,
                maxIterations, cloudletLengths, vmMips, seed
        );

        long startTime = System.nanoTime();
        sflaAssignment = sfla.run();
        long elapsed = System.nanoTime() - startTime;

        sflaMakespan = sfla.getBestMakespan();

        Log.println("SFLA Optimization Complete!");
        Log.println("  Best Makespan: " + String.format("%.4f", sflaMakespan) + " seconds");
        Log.println("  Best Fitness:  " + String.format("%.6f", sfla.getBestFitness()));
        Log.println("  SFLA Runtime:  " + String.format("%.2f", elapsed / 1_000_000.0) + " ms");
        Log.println("  Iterations:    " + maxIterations);

        Log.println();
        Log.println("SFLA Task Assignment:");
        for (int i = 0; i < sflaAssignment.length; i++) {
            int vmId = vmsCreated.get(sflaAssignment[i]).getId();
            Log.println("  Cloudlet #" + cloudlets.get(i).getCloudletId()
                    + " (MI=" + cloudlets.get(i).getCloudletLength()
                    + ") -> VM #" + vmId
                    + " (MIPS=" + String.format("%.0f", vmMips[sflaAssignment[i]]) + ")");
        }

        printConvergenceHistory(sfla);

        Log.println("=== SFLA Optimization End ===");
        Log.println();

        List<Cloudlet> successfullySubmitted = new ArrayList<>();
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            int vmIndex = sflaAssignment[i];
            GuestEntity vm = vmsCreated.get(vmIndex);

            cloudlet.setGuestId(vm.getId());

            Log.printlnConcat(org.cloudbus.cloudsim.core.CloudSim.clock(), ": ", getName(),
                    ": Sending Cloudlet #", cloudlet.getCloudletId(),
                    " to VM #", vm.getId(), " [SFLA]");

            sendNow(getVmsToDatacentersMap().get(vm.getId()),
                    CloudActionTags.CLOUDLET_SUBMIT, cloudlet);
            cloudletsSubmitted++;
            getCloudletSubmittedList().add(cloudlet);
            successfullySubmitted.add(cloudlet);
        }

        getCloudletList().removeAll(successfullySubmitted);
    }

    private void printConvergenceHistory(SFLAScheduler sfla) {
        List<double[]> history = sfla.getConvergenceHistory();
        Log.println();
        Log.println("Convergence History (every 5 iterations):");
        Log.println("  Iteration | Best Fitness (Makespan)");
        Log.println("  ----------|------------------------");
        for (double[] entry : history) {
            int iter = (int) entry[0];
            if (iter == 1 || iter % 5 == 0 || iter == maxIterations) {
                Log.println("  " + String.format("%9d", iter) + " | " + String.format("%.6f", entry[1]));
            }
        }
        Log.println();
    }

    public int[] getSflaAssignment() {
        return sflaAssignment;
    }

    public double getSflaMakespan() {
        return sflaMakespan;
    }
}
