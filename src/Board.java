import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class Board {
    private Node head;
    private Node tail;
    public final int TOTAL_SQUARES = 64;

    // VARIABLE BARU: Peta lokasi poin (ID Kotak -> Jumlah Poin)
    private Map<Integer, Integer> pointBoxes;

    public Board() {
        initializeBoard();
        generateLadders();
        generateRandomPoints(); // Generate Poin
    }

    private void initializeBoard() {
        head = null;
        tail = null;
        for (int i = 1; i <= TOTAL_SQUARES; i++) {
            Node newNode = new Node(i);
            if (head == null) {
                head = newNode;
                tail = newNode;
            } else {
                tail.next = newNode;
                tail = newNode;
            }
        }
    }

    private void generateLadders() {
        Random rand = new Random();
        Set<Integer> occupied = new HashSet<>();
        int count = 0;
        while (count < 5) {
            int startId = rand.nextInt(TOTAL_SQUARES - 10) + 2;
            int endId = startId + rand.nextInt(20) + 8;
            if (endId < TOTAL_SQUARES && !occupied.contains(startId) && !occupied.contains(endId)) {
                Node startNode = getNodeById(startId);
                Node endNode = getNodeById(endId);
                if (startNode != null && endNode != null) {
                    startNode.shortcut = endNode;
                    occupied.add(startId);
                    occupied.add(endId);
                    count++;
                }
            }
        }
    }

    // --- LOGIKA UPDATE: GENERATE 20 KOTAK BERPOIN ---
    private void generateRandomPoints() {
        pointBoxes = new HashMap<>();
        Random rand = new Random();
        int count = 0;

        // Ubah batas loop menjadi 20
        while (count < 20) {
            // Random kotak 2 sampai 64
            int id = rand.nextInt(TOTAL_SQUARES - 1) + 2;

            // Pastikan kotak belum ada poinnya
            if (!pointBoxes.containsKey(id)) {
                // Random poin 1 - 10
                int points = rand.nextInt(10) + 1;
                pointBoxes.put(id, points);
                count++;
            }
        }
        System.out.println("Points Generated: " + pointBoxes);
    }

    // Cek apakah kotak ini punya poin
    public int getPointsAt(int id) {
        return pointBoxes.getOrDefault(id, 0);
    }

    // --- METHOD BARU: HAPUS POIN SETELAH DIAMBIL ---
    public void removePoints(int id) {
        if (pointBoxes.containsKey(id)) {
            pointBoxes.remove(id);
        }
    }

    public Node getStartNode() { return head; }

    public Node getNodeById(int id) {
        Node current = head;
        while (current != null) {
            if (current.id == id) return current;
            current = current.next;
        }
        return null;
    }

    public boolean isPrime(int n) {
        if (n <= 1) return false;
        if (n == 2) return true;
        if (n % 2 == 0) return false;
        for (int i = 3; i <= Math.sqrt(n); i += 2) {
            if (n % i == 0) return false;
        }
        return true;
    }
}