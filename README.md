# CloudSim + SFLA Task Scheduling Simulation

Implementasi algoritma **Shuffled Frog-Leaping Algorithm (SFLA)** untuk penjadwalan tugas (task scheduling) pada lingkungan cloud computing menggunakan framework **CloudSim 7.0.1**.

Referensi algoritma: Eusuff, M. M., & Lansey, K. E. (2003). *"Optimization of Water Distribution Network Design Using the Shuffled Frog Leaping Algorithm."*

---

## Daftar Isi

1. [Latar Belakang](#1-latar-belakang)
2. [Arsitektur Simulasi](#2-arsitektur-simulasi)
3. [Penjelasan Algoritma SFLA](#3-penjelasan-algoritma-sfla)
4. [Struktur Kode](#4-struktur-kode)
5. [Prasyarat](#5-prasyarat)
6. [Cara Menjalankan](#6-cara-menjalankan)
7. [Hasil Simulasi](#7-hasil-simulasi)
8. [Panduan Demo](#8-panduan-demo)

---

## 1. Latar Belakang

### Masalah
Dalam cloud computing, **task scheduling** adalah proses menetapkan tugas komputasi (cloudlet) ke Virtual Machine (VM) yang tersedia. Penjadwalan yang buruk menyebabkan:
- **Makespan tinggi** — waktu total penyelesaian seluruh tugas menjadi lama
- **Beban tidak seimbang** — beberapa VM kelebihan beban sementara yang lain menganggur
- **Throughput rendah** — jumlah tugas yang selesai per detik menjadi sedikit

### Solusi
Algoritma **SFLA** digunakan sebagai metaheuristik untuk mencari pemetaan cloudlet-ke-VM yang optimal, dengan tujuan utama **meminimalkan makespan** (waktu selesai VM terakhir).

### Mengapa SFLA?
- Terinspirasi dari perilaku katak yang mencari makanan secara berkelompok (memeplex)
- Menggabungkan pencarian lokal (dalam memeplex) dan pertukaran informasi global (shuffle)
- Cocok untuk masalah optimasi kombinatorial seperti task scheduling
- Memiliki keseimbangan antara eksplorasi dan eksploitasi

---

## 2. Arsitektur Simulasi

### Komponen CloudSim

```
┌─────────────────────────────────────────────────┐
│                  DATACENTER                      │
│  ┌────────────────┐  ┌────────────────┐         │
│  │    HOST 0       │  │    HOST 1       │        │
│  │  4 PE x 2000   │  │  4 PE x 2000   │        │
│  │  RAM: 8192 MB  │  │  RAM: 8192 MB  │        │
│  │  ┌──────┐┌──────┐  ┌──────┐┌──────┐│        │
│  │  │VM-0  ││VM-2  │  │VM-1  ││VM-3  ││        │
│  │  │850   ││1450  │  │1150  ││1750  ││        │
│  │  │MIPS  ││MIPS  │  │MIPS  ││MIPS  ││        │
│  │  └──────┘└──────┘  └──────┘└──────┘│        │
│  └────────────────┘  └────────────────┘         │
└─────────────────────────────────────────────────┘
                      ▲
                      │ submit cloudlets
        ┌─────────────┴─────────────┐
        │    SFLA DATACENTER BROKER  │
        │    (menjalankan SFLA       │
        │     sebelum submit)        │
        └─────────────┬─────────────┘
                      │
         ┌────────────┴────────────┐
         │  16 CLOUDLETS           │
         │  (MI: 1400 - 6210)     │
         │  beban acak per tugas   │
         └─────────────────────────┘
```

### Konfigurasi VM

| VM | Nama | MIPS | Keterangan |
|----|------|------|------------|
| VM-0 | Small | 850 | Prosesor lambat |
| VM-1 | Medium | 1150 | Prosesor sedang |
| VM-2 | Large | 1450 | Prosesor cepat |
| VM-3 | X-Large | 1750 | Prosesor tercepat |

### Konfigurasi Cloudlet

- Jumlah: **16 cloudlet**
- Beban kerja: **1400 - 6210 MI** (Million Instructions), dibangkitkan secara acak
- Masing-masing membutuhkan 1 PE (Processing Element)

---

## 3. Penjelasan Algoritma SFLA

### Analogi

Bayangkan sekelompok katak yang mencari sumber makanan terbaik di sebuah rawa. Katak-katak ini dibagi menjadi beberapa kelompok kecil (memeplex). Di dalam setiap kelompok, katak terburuk belajar dari katak terbaik dengan cara melompat ke arahnya. Setelah beberapa kali lompatan lokal, seluruh katak dikumpulkan dan diacak ulang (shuffle) ke kelompok baru untuk bertukar informasi.

### Representasi Solusi

Setiap "katak" merepresentasikan satu solusi penjadwalan lengkap:
- Katak = vektor berukuran `n` (jumlah cloudlet)
- Setiap elemen bernilai kontinu `[0, jumlah_VM)`
- Pemetaan ke VM: `VM_index = floor(nilai_elemen)`

Contoh untuk 6 cloudlet dan 4 VM:
```
Katak:   [1.7, 0.3, 3.1, 2.5, 0.8, 1.2]
Decoded: [ 1,   0,   3,   2,   0,   1 ]
           │    │    │    │    │    │
           │    │    │    │    │    └─ Cloudlet 5 → VM-1
           │    │    │    │    └───── Cloudlet 4 → VM-0
           │    │    │    └────────── Cloudlet 3 → VM-2
           │    │    └─────────────── Cloudlet 2 → VM-3
           │    └──────────────────── Cloudlet 1 → VM-0
           └───────────────────────── Cloudlet 0 → VM-1
```

### Fungsi Fitness

```
fitness = makespan + 0.001 × imbalance_std
```

- **Makespan** = max(waktu_selesai_VM_i) untuk semua VM — metrik utama
- **Imbalance penalty** = standar deviasi beban antar VM — penalti kecil untuk mendorong keseimbangan beban ketika makespan sama

### Langkah-langkah Algoritma

```
┌─────────────────────────────────────────────┐
│ 1. INISIALISASI                             │
│    Bangkitkan 30 katak (solusi) secara acak │
│    Evaluasi fitness setiap katak             │
└──────────────────┬──────────────────────────┘
                   ▼
┌─────────────────────────────────────────────┐
│ 2. SORT (Urutkan)                           │
│    Urutkan katak dari fitness terbaik       │
│    ke terburuk                              │
└──────────────────┬──────────────────────────┘
                   ▼
┌─────────────────────────────────────────────┐
│ 3. PARTISI MEMEPLEX                         │
│    Bagi 30 katak ke 5 memeplex              │
│    secara round-robin berdasarkan peringkat │
│    (#1→M1, #2→M2, #3→M3, #4→M4, #5→M5,    │
│     #6→M1, #7→M2, ...)                     │
└──────────────────┬──────────────────────────┘
                   ▼
┌─────────────────────────────────────────────┐
│ 4. EVOLUSI LOKAL (5 iterasi per memeplex)   │
│    Untuk setiap memeplex:                   │
│    a) Identifikasi Xb (terbaik) dan        │
│       Xw (terburuk) dalam memeplex          │
│                                             │
│    b) Percobaan 1: Lompat Xw → Xb          │
│       X_baru = Xw + r × 1.6 × (Xb - Xw)   │
│       Jika fitness membaik → terima         │
│                                             │
│    c) Percobaan 2: Lompat Xw → Xg          │
│       (jika percobaan 1 gagal)              │
│       X_baru = Xw + r × 1.6 × (Xg - Xw)   │
│       Jika fitness membaik → terima         │
│                                             │
│    d) Percobaan 3: Random reset             │
│       (jika percobaan 2 juga gagal)         │
│       X_baru = posisi acak baru             │
└──────────────────┬──────────────────────────┘
                   ▼
┌─────────────────────────────────────────────┐
│ 5. SHUFFLE (Campuran Global)                │
│    Gabung semua katak, urutkan ulang        │
│    Kembali ke langkah 3                     │
│    Ulangi sampai 50 iterasi tercapai        │
└─────────────────────────────────────────────┘
```

Keterangan variabel:
- **Xw** = katak terburuk dalam memeplex
- **Xb** = katak terbaik dalam memeplex
- **Xg** = katak terbaik secara global (seluruh populasi)
- **r** = bilangan acak [0, 1]

### Parameter SFLA

| Parameter | Nilai | Penjelasan |
|-----------|-------|------------|
| Population Size | 30 | Jumlah katak (solusi kandidat) |
| Memeplex Count | 5 | Jumlah kelompok katak |
| Local Iterations | 5 | Iterasi evolusi dalam setiap memeplex |
| Max Iterations | 50 | Jumlah siklus global (shuffle) |
| Seed | 42 | Seed random untuk reprodusibilitas |

---

## 4. Struktur Kode

```
cloudsim-sfla-simulation/
├── pom.xml                          # Maven project (dependency: CloudSim 7.0.1)
├── README.md                        # Dokumentasi ini
└── src/main/java/org/cloudbus/cloudsim/sfla/
    ├── SFLAScheduler.java           # Implementasi algoritma SFLA murni
    ├── SFLADatacenterBroker.java    # Custom broker CloudSim yang pakai SFLA
    └── SFLACloudSimSimulation.java  # Main class: jalankan simulasi & bandingkan
```

### SFLAScheduler.java
Implementasi algoritma SFLA murni tanpa dependensi CloudSim. Menerima input berupa array panjang cloudlet (MI) dan kapasitas VM (MIPS), mengembalikan array pemetaan cloudlet → VM yang optimal.

Method utama:
- `run()` — jalankan SFLA, return array assignment
- `evaluateFitness()` — hitung makespan + penalty
- `evolveMemeplex()` — evolusi lokal (3 strategi lompatan)
- `computeLeap()` — hitung posisi lompatan baru
- `getBestMakespan()` — ambil makespan terbaik

### SFLADatacenterBroker.java
Extends `DatacenterBroker` dari CloudSim. Meng-override method `submitCloudlets()` agar sebelum mengirim cloudlet ke VM, broker menjalankan SFLA terlebih dahulu untuk menentukan pemetaan optimal.

Alur:
1. Ambil daftar cloudlet dan VM yang sudah dibuat
2. Ekstrak panjang cloudlet (MI) dan kapasitas VM (MIPS)
3. Jalankan `SFLAScheduler` untuk mendapat assignment optimal
4. Bind setiap cloudlet ke VM sesuai hasil SFLA
5. Submit cloudlet ke datacenter

### SFLACloudSimSimulation.java
Main class yang menjalankan **dua simulasi** secara berurutan:
1. **SFLA Scheduling** — menggunakan `SFLADatacenterBroker`
2. **Round-Robin Scheduling** — menggunakan `DatacenterBroker` default CloudSim

Kemudian membandingkan hasil keduanya dalam tabel perbandingan.

---

## 5. Prasyarat

- **Java JDK 21** atau lebih tinggi
- **Apache Maven 3.8+**
- **CloudSim 7.0.1** sudah ter-install di local Maven repository

### Install CloudSim ke Local Maven Repository

Jika belum pernah build CloudSim:

```bash
cd D:\SOKA\cloudsim
mvn install -DskipTests
```

Proses ini akan meng-install artifact `org.cloudbus.cloudsim:cloudsim:7.0.1` ke `~/.m2/repository/`.

---

## 6. Cara Menjalankan

### Opsi A: Maven (Direkomendasikan)

```bash
cd D:\SOKA\cloudsim-sfla-simulation

# Compile
mvn compile

# Jalankan simulasi
mvn exec:java
```

### Opsi B: Compile dan Run Manual

```bash
cd D:\SOKA\cloudsim-sfla-simulation

# Compile
mvn compile

# Run langsung dengan classpath Maven
mvn exec:java -Dexec.mainClass="org.cloudbus.cloudsim.sfla.SFLACloudSimSimulation"
```

### Output yang Diharapkan

Program akan menampilkan:
1. Log pembuatan datacenter, host, dan VM
2. Proses optimasi SFLA (assignment per cloudlet, convergence history)
3. Tabel hasil eksekusi cloudlet per scheduler
4. Tabel perbandingan SFLA vs Round-Robin

---

## 7. Hasil Simulasi

### Perbandingan SFLA vs Round-Robin

| Metrik | SFLA | Round-Robin | Perbaikan |
|--------|------|-------------|-----------|
| **Makespan** | 12.53 detik | 21.44 detik | **41.53%** |
| **Avg Completion Time** | 10.50 detik | 11.57 detik | **9.23%** |
| **Throughput** | 1.28 task/s | 0.75 task/s | - |
| **Load Imbalance (Std Dev)** | 9.82 | 14.00 | - |

### Convergence History SFLA

```
Iteration | Best Fitness
----------|-------------
        1 | 14.5429
        5 | 13.0481
       10 | 12.8232
       30 | 12.6085
       45 | 12.5202
       50 | 12.5202  (konvergen)
```

SFLA konvergen pada sekitar iterasi ke-45, menurunkan fitness dari 14.54 menjadi 12.52.

### Distribusi Beban per VM

| VM | SFLA | Round-Robin |
|----|------|-------------|
| VM-0 (Small, 850 MIPS) | 26.97s | 68.30s |
| VM-1 (Medium, 1150 MIPS) | 45.79s | 47.96s |
| VM-2 (Large, 1450 MIPS) | 41.27s | 37.32s |
| VM-3 (X-Large, 1750 MIPS) | 54.04s | 31.59s |

**Analisis:**
- Round-Robin membagi tugas secara merata berdasarkan jumlah (4 cloudlet per VM), tanpa mempertimbangkan kapasitas VM. VM-0 yang paling lambat (850 MIPS) mendapat beban yang sama banyaknya dengan VM-3 (1750 MIPS), sehingga VM-0 menjadi bottleneck dengan waktu 68.30 detik.
- SFLA secara cerdas memberikan lebih sedikit tugas ke VM lambat dan lebih banyak ke VM cepat, sehingga semua VM selesai pada waktu yang lebih merata.

### Mengapa SFLA Lebih Baik?

1. **Capacity-aware**: SFLA mempertimbangkan MIPS setiap VM saat mengalokasikan tugas
2. **Load balancing**: Fungsi fitness menyertakan penalti ketidakseimbangan beban
3. **Eksplorasi + Eksploitasi**: Kombinasi pencarian lokal (memeplex) dan shuffle global mencegah terjebak di solusi lokal
4. **Round-Robin tidak optimal**: Metode round-robin buta terhadap heterogenitas — membagi tugas rata tanpa melihat kapasitas VM

---

## 8. Panduan Demo

### Urutan Presentasi Demo

#### Langkah 1: Jelaskan Masalah (2-3 menit)
- "Kita punya **16 tugas komputasi** (cloudlet) yang harus dijadwalkan ke **4 VM** dengan kapasitas berbeda-beda."
- "Tantangannya: bagaimana caranya agar semua tugas selesai secepat mungkin? Ini disebut **minimisasi makespan**."
- "Metode default CloudSim menggunakan **Round-Robin** yang membagi tugas secara bergilir tanpa melihat kapasitas VM."

#### Langkah 2: Jelaskan Algoritma SFLA (3-5 menit)
- "SFLA terinspirasi dari perilaku katak. Bayangkan **30 katak** masing-masing punya ide jadwal yang berbeda."
- "Katak dikelompokkan ke **5 memeplex** (kelompok). Dalam setiap kelompok, katak terburuk **belajar dari katak terbaik** dengan melompat ke arahnya."
- "Kalau lompatan ke terbaik kelompok gagal, dia coba lompat ke **terbaik global**. Kalau masih gagal, dia **pindah ke posisi acak** (eksplorasi)."
- "Setelah evolusi lokal, semua katak **dicampur dan diacak ulang** ke kelompok baru untuk bertukar informasi."
- "Proses ini diulang **50 kali** sampai ditemukan jadwal terbaik."

#### Langkah 3: Jalankan Simulasi Live (2-3 menit)
```bash
cd D:\SOKA\cloudsim-sfla-simulation
mvn exec:java
```

Tunjukkan output ke audiens, highlight:
- **SFLA Optimization Starting** — di sini SFLA berjalan
- **Convergence History** — tunjukkan fitness menurun setiap iterasi
- **SFLA Task Assignment** — tunjukkan mana cloudlet yang ditaruh di VM mana
- **Tabel perbandingan akhir** — highlight makespan SFLA vs Round-Robin

#### Langkah 4: Bahas Hasil (3-5 menit)
- "SFLA menurunkan makespan dari **21.44 detik menjadi 12.53 detik** — perbaikan **41.53%**."
- "Kalau dilihat distribusi beban per VM, Round-Robin menaruh beban yang sama ke VM lambat dan VM cepat, sehingga VM-0 (yang paling lambat) jadi bottleneck."
- "SFLA secara cerdas memberikan lebih sedikit tugas ke VM lambat, dan lebih banyak tugas ke VM cepat, sehingga semua VM selesai hampir bersamaan."
- "Throughput juga meningkat: dari **0.75 task/detik** menjadi **1.28 task/detik**."

#### Langkah 5: Jelaskan Implementasi Kode (2-3 menit)
Tunjukkan 3 file utama:
1. **SFLAScheduler.java** — "Ini inti algoritmanya. Method `evolveMemeplex()` berisi 3 strategi lompatan katak."
2. **SFLADatacenterBroker.java** — "Ini custom broker yang kita buat. Dia extends `DatacenterBroker` dan override method `submitCloudlets()` supaya pakai SFLA."
3. **SFLACloudSimSimulation.java** — "Ini main program. Dia jalankan 2 simulasi (SFLA dan Round-Robin) lalu bandingkan hasilnya."

### Pertanyaan yang Mungkin Ditanyakan

**Q: Kenapa pakai representasi kontinu, bukan langsung integer?**
A: Representasi kontinu `[0, vmCount)` memungkinkan operator SFLA (lompatan) bekerja secara smooth. Konversi ke integer dilakukan dengan `floor()` saat evaluasi fitness. Ini teknik standar untuk memetakan algoritma optimasi kontinu ke masalah diskrit.

**Q: Apa bedanya SFLA dengan algoritma metaheuristik lain seperti PSO atau GA?**
A: SFLA unik karena menggabungkan pencarian lokal (memeplex) dengan pertukaran informasi global (shuffle). PSO semua partikel bergerak secara global, GA menggunakan crossover/mutasi. SFLA punya mekanisme khusus: katak terburuk yang belajar dari terbaik lokal → terbaik global → random, yang memberikan keseimbangan eksplorasi-eksploitasi yang baik.

**Q: Bagaimana fitness dihitung?**
A: `fitness = makespan + 0.001 × std_dev(beban_VM)`. Komponen utama adalah makespan (waktu VM terakhir selesai). Penalti kecil (0.001) untuk standar deviasi beban VM membantu memecah ikatan ketika dua solusi punya makespan sama — solusi dengan beban lebih seimbang akan dipilih.

**Q: Apakah SFLA selalu lebih baik dari Round-Robin?**
A: Untuk VM heterogen (kapasitas berbeda-beda), SFLA hampir selalu lebih baik karena dia capacity-aware. Untuk VM homogen (semua sama), perbedaannya lebih kecil karena Round-Robin sudah cukup seimbang.

**Q: Apa kelemahan SFLA?**
A: SFLA membutuhkan waktu komputasi tambahan untuk optimasi (di simulasi ini sekitar 50 ms). Untuk skenario real-time dengan tugas datang terus-menerus, overhead ini perlu dipertimbangkan. Namun untuk batch scheduling (semua tugas diketahui di awal), SFLA sangat efektif.

**Q: Berapa jumlah evaluasi fitness total?**
A: Populasi 30 × 50 iterasi × 5 memeplex × 5 local_iter × (1-3 percobaan per evolusi) = sekitar 7.500 - 22.500 evaluasi fitness. Setiap evaluasi sangat ringan (hanya kalkulasi makespan), sehingga total runtime hanya puluhan milidetik.
