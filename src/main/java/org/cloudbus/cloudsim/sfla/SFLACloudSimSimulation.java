package org.cloudbus.cloudsim.sfla;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.core.HostEntity;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Simulasi CloudSim dengan penjadwalan tugas menggunakan algoritma SFLA.
 *
 * Skenario:
 * - 1 Datacenter dengan 2 Host
 * - 4 VM dengan kapasitas berbeda (Small, Medium, Large, X-Large)
 * - 16 Cloudlet dengan beban kerja acak
 *
 * Dibandingkan: SFLA vs Round-Robin (default CloudSim)
 */
public class SFLACloudSimSimulation {

    private static final int NUM_VMS = 4;
    private static final int NUM_CLOUDLETS = 16;

    // Konfigurasi VM sesuai demo website SFLA-WCA
    private static final double[] VM_MIPS = {850, 1150, 1450, 1750};
    private static final String[] VM_NAMES = {"VM-1 (Small)", "VM-2 (Medium)", "VM-3 (Large)", "VM-4 (X-Large)"};
    private static final int VM_PES = 1;
    private static final int VM_RAM = 512;
    private static final long VM_BW = 1000;
    private static final long VM_SIZE = 10000;

    // Konfigurasi SFLA
    private static final int SFLA_POPULATION = 30;
    private static final int SFLA_MEMEPLEXES = 5;
    private static final int SFLA_LOCAL_ITER = 5;
    private static final int SFLA_MAX_ITER = 50;
    private static final long SFLA_SEED = 42;

    public static void main(String[] args) {
        Log.println("╔══════════════════════════════════════════════════════════════╗");
        Log.println("║   CloudSim + SFLA Task Scheduling Simulation                ║");
        Log.println("║   Shuffled Frog-Leaping Algorithm untuk Penjadwalan Cloud   ║");
        Log.println("╚══════════════════════════════════════════════════════════════╝");
        Log.println();

        // ============================================================
        // Bagian 1: Simulasi dengan SFLA Broker
        // ============================================================
        Log.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Log.println("  SIMULASI 1: SFLA Scheduling");
        Log.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        List<Cloudlet> sflaResults = runSFLASimulation();

        // ============================================================
        // Bagian 2: Simulasi dengan Round-Robin Broker (default)
        // ============================================================
        Log.println();
        Log.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Log.println("  SIMULASI 2: Round-Robin Scheduling (Default CloudSim)");
        Log.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        List<Cloudlet> rrResults = runRoundRobinSimulation();

        // ============================================================
        // Bagian 3: Perbandingan Hasil
        // ============================================================
        Log.println();
        printComparison(sflaResults, rrResults);
    }

    private static List<Cloudlet> runSFLASimulation() {
        try {
            CloudSim.init(1, Calendar.getInstance(), false);

            SFLADatacenterBroker broker = new SFLADatacenterBroker("SFLA_Broker");
            broker.setSFLAParameters(SFLA_POPULATION, SFLA_MEMEPLEXES,
                    SFLA_LOCAL_ITER, SFLA_MAX_ITER, SFLA_SEED);
            int brokerId = broker.getId();

            createDatacenter("Datacenter_SFLA");

            List<GuestEntity> vmList = createVMs(brokerId);
            broker.submitGuestList(vmList);

            List<Cloudlet> cloudletList = createCloudlets(brokerId);
            broker.submitCloudletList(cloudletList);

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            List<Cloudlet> results = broker.getCloudletReceivedList();
            printCloudletList(results, "SFLA");
            return results;

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private static List<Cloudlet> runRoundRobinSimulation() {
        try {
            CloudSim.init(1, Calendar.getInstance(), false);

            DatacenterBroker broker = new DatacenterBroker("RR_Broker");
            int brokerId = broker.getId();

            createDatacenter("Datacenter_RR");

            List<GuestEntity> vmList = createVMs(brokerId);
            broker.submitGuestList(vmList);

            List<Cloudlet> cloudletList = createCloudlets(brokerId);
            broker.submitCloudletList(cloudletList);

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            List<Cloudlet> results = broker.getCloudletReceivedList();
            printCloudletList(results, "Round-Robin");
            return results;

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private static Datacenter createDatacenter(String name) {
        List<HostEntity> hostList = new ArrayList<>();

        // Host 1: High-performance
        List<Pe> peList1 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            peList1.add(new Pe(i, new PeProvisionerSimple(2000)));
        }
        hostList.add(new Host(0,
                new RamProvisionerSimple(8192),
                new BwProvisionerSimple(10000),
                1000000,
                peList1,
                new VmSchedulerTimeShared(peList1)));

        // Host 2: Standard
        List<Pe> peList2 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            peList2.add(new Pe(i, new PeProvisionerSimple(2000)));
        }
        hostList.add(new Host(1,
                new RamProvisionerSimple(8192),
                new BwProvisionerSimple(10000),
                1000000,
                peList2,
                new VmSchedulerTimeShared(peList2)));

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double timeZone = 7.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<>();

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, timeZone, cost, costPerMem, costPerStorage, costPerBw);

        try {
            return new Datacenter(name, characteristics,
                    new VmAllocationPolicySimple(hostList), storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static List<GuestEntity> createVMs(int brokerId) {
        List<GuestEntity> vmList = new ArrayList<>();

        for (int i = 0; i < NUM_VMS; i++) {
            Vm vm = new Vm(i, brokerId, VM_MIPS[i], VM_PES, VM_RAM, VM_BW, VM_SIZE,
                    "Xen", new CloudletSchedulerTimeShared());
            vmList.add(vm);
        }

        return vmList;
    }

    private static List<Cloudlet> createCloudlets(int brokerId) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        UtilizationModel utilizationModel = new UtilizationModelFull();

        Random rng = new Random(2026);

        for (int i = 0; i < NUM_CLOUDLETS; i++) {
            long length = 1400 + rng.nextInt(4810); // 1400-6210 MI (scaled from 140-621)
            Cloudlet cloudlet = new Cloudlet(i, length, VM_PES, 300, 300,
                    utilizationModel, utilizationModel, utilizationModel);
            cloudlet.setUserId(brokerId);
            cloudletList.add(cloudlet);
        }

        return cloudletList;
    }

    private static void printCloudletList(List<Cloudlet> list, String schedulerName) {
        DecimalFormat dft = new DecimalFormat("###.##");
        String indent = "    ";

        Log.println();
        Log.println("========== HASIL " + schedulerName.toUpperCase() + " ==========");
        Log.println(String.format("%-12s %-10s %-14s %-8s %-12s %-12s %-12s %-10s",
                "Cloudlet ID", "Status", "Datacenter ID", "VM ID", "Length(MI)", "Time(s)",
                "Start Time", "Finish Time"));
        Log.println("─".repeat(96));

        for (Cloudlet cloudlet : list) {
            if (cloudlet.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                Log.println(String.format("%-12d %-10s %-14d %-8d %-12d %-12s %-12s %-10s",
                        cloudlet.getCloudletId(),
                        "SUCCESS",
                        cloudlet.getResourceId(),
                        cloudlet.getGuestId(),
                        cloudlet.getCloudletLength(),
                        dft.format(cloudlet.getActualCPUTime()),
                        dft.format(cloudlet.getExecStartTime()),
                        dft.format(cloudlet.getExecFinishTime())));
            }
        }
    }

    private static void printComparison(List<Cloudlet> sflaResults, List<Cloudlet> rrResults) {
        Log.println("╔══════════════════════════════════════════════════════════════╗");
        Log.println("║            PERBANDINGAN HASIL SIMULASI                      ║");
        Log.println("║            SFLA vs Round-Robin                              ║");
        Log.println("╚══════════════════════════════════════════════════════════════╝");
        Log.println();

        double sflaMakespan = computeMakespan(sflaResults);
        double rrMakespan = computeMakespan(rrResults);

        double sflaAvgTime = computeAvgTime(sflaResults);
        double rrAvgTime = computeAvgTime(rrResults);

        double sflaThroughput = sflaResults.size() / sflaMakespan;
        double rrThroughput = rrResults.size() / rrMakespan;

        double[] sflaVmLoads = computeVmLoads(sflaResults);
        double[] rrVmLoads = computeVmLoads(rrResults);

        double sflaImbalance = computeImbalance(sflaVmLoads);
        double rrImbalance = computeImbalance(rrVmLoads);

        DecimalFormat df = new DecimalFormat("###.####");

        Log.println(String.format("%-30s %-20s %-20s %-15s", "Metrik", "SFLA", "Round-Robin", "Perbaikan"));
        Log.println("─".repeat(85));

        double makespanImprovement = ((rrMakespan - sflaMakespan) / rrMakespan) * 100;
        Log.println(String.format("%-30s %-20s %-20s %-15s",
                "Makespan (detik)", df.format(sflaMakespan), df.format(rrMakespan),
                df.format(makespanImprovement) + "%"));

        double avgImprovement = ((rrAvgTime - sflaAvgTime) / rrAvgTime) * 100;
        Log.println(String.format("%-30s %-20s %-20s %-15s",
                "Avg Completion Time (detik)", df.format(sflaAvgTime), df.format(rrAvgTime),
                df.format(avgImprovement) + "%"));

        Log.println(String.format("%-30s %-20s %-20s %-15s",
                "Throughput (task/detik)", df.format(sflaThroughput), df.format(rrThroughput), "-"));

        Log.println(String.format("%-30s %-20s %-20s %-15s",
                "Load Imbalance (Std Dev)", df.format(sflaImbalance), df.format(rrImbalance), "-"));

        Log.println();
        Log.println("Beban per VM (detik):");
        Log.println(String.format("%-20s %-15s %-15s", "VM", "SFLA", "Round-Robin"));
        Log.println("─".repeat(50));
        for (int i = 0; i < NUM_VMS; i++) {
            Log.println(String.format("%-20s %-15s %-15s",
                    VM_NAMES[i],
                    df.format(i < sflaVmLoads.length ? sflaVmLoads[i] : 0),
                    df.format(i < rrVmLoads.length ? rrVmLoads[i] : 0)));
        }

        Log.println();
        Log.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        if (makespanImprovement > 0) {
            Log.println("  SFLA berhasil menurunkan makespan sebesar "
                    + df.format(makespanImprovement) + "% dibanding Round-Robin.");
        } else {
            Log.println("  Round-Robin memiliki makespan yang lebih baik pada skenario ini.");
        }
        Log.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Parameter SFLA
        Log.println();
        Log.println("Parameter SFLA:");
        Log.println("  Population Size:    " + SFLA_POPULATION);
        Log.println("  Memeplex Count:     " + SFLA_MEMEPLEXES);
        Log.println("  Local Iterations:   " + SFLA_LOCAL_ITER);
        Log.println("  Max Iterations:     " + SFLA_MAX_ITER);
        Log.println("  Seed:               " + SFLA_SEED);
        Log.println();

        // Konfigurasi simulasi
        Log.println("Konfigurasi Simulasi:");
        Log.println("  Jumlah VM:         " + NUM_VMS);
        for (int i = 0; i < NUM_VMS; i++) {
            Log.println("    " + VM_NAMES[i] + " - " + (int) VM_MIPS[i] + " MIPS");
        }
        Log.println("  Jumlah Cloudlet:   " + NUM_CLOUDLETS);
        Log.println("  Cloudlet PEs:      " + VM_PES);
        Log.println();
    }

    private static double computeMakespan(List<Cloudlet> results) {
        double maxFinish = 0;
        for (Cloudlet c : results) {
            if (c.getExecFinishTime() > maxFinish) {
                maxFinish = c.getExecFinishTime();
            }
        }
        return maxFinish;
    }

    private static double computeAvgTime(List<Cloudlet> results) {
        double sum = 0;
        for (Cloudlet c : results) {
            sum += c.getActualCPUTime();
        }
        return sum / Math.max(1, results.size());
    }

    private static double[] computeVmLoads(List<Cloudlet> results) {
        double[] loads = new double[NUM_VMS];
        for (Cloudlet c : results) {
            int vmId = c.getGuestId();
            if (vmId >= 0 && vmId < NUM_VMS) {
                loads[vmId] += c.getActualCPUTime();
            }
        }
        return loads;
    }

    private static double computeImbalance(double[] loads) {
        double mean = 0;
        for (double l : loads) mean += l;
        mean /= loads.length;

        double variance = 0;
        for (double l : loads) {
            variance += (l - mean) * (l - mean);
        }
        return Math.sqrt(variance / loads.length);
    }
}
