import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import javax.sound.sampled.*;

public class MainGameGUI extends JFrame {

    private Board gameBoard;
    private Queue<Player> turnQueue;

    private GamePanel gamePanel;
    private ControlPanel controlPanel;
    private boolean isGameRunning = false;

    // Timer Animasi
    private Timer diceAnimationTimer;
    private Timer movementAnimationTimer;
    private Timer ladderAnimationTimer;
    private Timer lockAnimationTimer;

    // Timer Animasi Confetti
    private Timer confettiTimer;
    private List<ConfettiParticle> confettiParticles = new ArrayList<>();

    // Variabel Animasi Player
    private Player animatingPlayer = null;
    private Point2D.Double currentAnimPos = null;
    private Point2D.Double targetAnimPos = null;
    private int walkFrame = 0;
    private boolean isFacingRight = true;
    private boolean currentTurnIsForward = true;

    // State Gembok
    private int activeLockNodeId = -1;
    private double lockAngle = 0;
    private boolean isLockOpen = false;
    private boolean isLockShaking = false;
    private int shakeFrame = 0;

    // Audio
    private Clip bgmClip;
    private Clip sfxClip;
    private Clip walkClip;
    private Clip winClip;
    private boolean isSFXMuted = false;

    // --- PALET WARNA LEGO/MARIO ---
    public static final Color MARIO_SKY_BLUE   = new Color(107, 140, 255);
    public static final Color MARIO_RED        = new Color(229, 37, 33);
    public static final Color MARIO_BLUE       = new Color(0, 0, 200);
    public static final Color MARIO_SKIN       = new Color(255, 206, 180);
    public static final Color MARIO_BROWN      = new Color(146, 73, 0);
    public static final Color MARIO_LIGHT_BROWN= new Color(180, 90, 0);
    public static final Color MARIO_GROUND     = new Color(200, 76, 12);
    public static final Color MARIO_YELLOW     = new Color(251, 208, 0);
    public static final Color MARIO_GREEN      = new Color(67, 176, 71);
    public static final Color DICE_GOLD        = new Color(255, 215, 0);
    public static final Color LADDER_DARK      = new Color(184, 134, 11);
    public static final Color LADDER_LIGHT     = new Color(255, 223, 0);
    public static final Color LADDER_SHINE     = new Color(255, 250, 205);

    // File Penyimpanan
    private static final String SAVE_FILE = "last_winner.txt";

    public MainGameGUI() {
        setTitle("Super Board Bros: Pixel Edition");
        setSize(1280, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(MARIO_SKY_BLUE);

        gameBoard = new Board();
        turnQueue = new LinkedList<>();

        JPanel boardContainer = new JPanel(new BorderLayout());
        boardContainer.setBorder(new EmptyBorder(20, 20, 20, 20));
        boardContainer.setBackground(MARIO_SKY_BLUE);

        gamePanel = new GamePanel();
        gamePanel.setBorder(new LineBorder(Color.WHITE, 4));
        boardContainer.add(gamePanel, BorderLayout.CENTER);
        add(boardContainer, BorderLayout.CENTER);

        controlPanel = new ControlPanel();
        add(controlPanel, BorderLayout.EAST);

        setLocationRelativeTo(null);
        setVisible(true);

        SwingUtilities.invokeLater(() -> {
            showRulesDialog();
            setupPlayers();
        });
    }

    // --- HELPER CLASS UNTUK MENYIMPAN DATA (UPDATED) ---
    static class WinnerRecord {
        String name;
        int score;
        long entryOrder; // Variable baru untuk menyimpan urutan baris/waktu

        public WinnerRecord(String name, int score, long entryOrder) {
            this.name = name;
            this.score = score;
            this.entryOrder = entryOrder;
        }
    }
    // --- LOGIKA LOAD & SAVE (MODIFIED) ---

    // --- LOGIKA LOAD & SAVE (UPDATED WITH TIE-BREAKER) ---

    // Membaca file, mengambil SEMUA data, Sorting, ambil Top 5
    private List<WinnerRecord> loadTopWinners() {
        List<WinnerRecord> allWinners = new ArrayList<>();
        File f = new File(SAVE_FILE);

        // Counter untuk melacak urutan baris.
        // Semakin besar angkanya = semakin bawah posisinya di file = semakin baru datanya.
        long lineCounter = 0;

        if (f.exists()) {
            try (Scanner scanner = new Scanner(f)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    lineCounter++; // Naikkan counter setiap membaca baris

                    // Format di file: Nama,Skor
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        try {
                            String name = parts[0].trim();
                            int score = Integer.parseInt(parts[1].trim());

                            // Masukkan lineCounter ke dalam record sebagai entryOrder
                            allWinners.add(new WinnerRecord(name, score, lineCounter));
                        } catch (NumberFormatException e) {
                            // Skip baris rusak
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // LOGIKA SORTING BARU
        Collections.sort(allWinners, new Comparator<WinnerRecord>() {
            @Override
            public int compare(WinnerRecord w1, WinnerRecord w2) {
                // 1. Prioritas Utama: Bandingkan Skor (Descending / Besar ke Kecil)
                if (w1.score != w2.score) {
                    return w2.score - w1.score;
                }

                // 2. Prioritas Kedua (Tie-Breaker): Bandingkan Urutan Masuk
                // Jika Skor SAMA, yang punya entryOrder lebih besar (lebih baru) menang.
                return Long.compare(w2.entryOrder, w1.entryOrder);
            }
        });

        // Ambil Top 5 (atau kurang jika data sedikit)
        List<WinnerRecord> top5 = new ArrayList<>();
        for (int i = 0; i < Math.min(5, allWinners.size()); i++) {
            top5.add(allWinners.get(i));
        }

        return top5;
    }

    // Menyimpan pemenang baru ke file (Append Mode)
    public void appendWinnerToFile(String name, int score) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(SAVE_FILE, true))) { // true = append
            writer.println(name + "," + score);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showRulesDialog() {
        JDialog rulesDialog = new JDialog(this, "HOW TO PLAY", true);
        rulesDialog.setSize(500, 480);
        rulesDialog.setLayout(new BorderLayout());
        rulesDialog.setLocationRelativeTo(this);

        JPanel pnlContent = new JPanel();
        pnlContent.setLayout(new BoxLayout(pnlContent, BoxLayout.Y_AXIS));
        pnlContent.setBackground(new Color(255, 255, 220));
        pnlContent.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("ATURAN PERMAINAN");
        title.setFont(new Font("Segoe UI Black", Font.BOLD, 24));
        title.setForeground(MARIO_RED);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        pnlContent.add(title);
        pnlContent.add(Box.createVerticalStrut(20));

        String htmlRules = "<html><body style='width: 350px; font-family: Segoe UI; font-size: 11px;'>" +
                "<p><b>1. 🎲 LUCKY BLOCK (Bonus Turn):</b><br>" +
                "Jika mendarat di kotak <b>Kelipatan 5</b> (gambar dadu), kamu dapat kesempatan melempar dadu lagi!</p><br>" +
                "<p><b>2. ⭐ SYARAT TANGGA (Prime Access):</b><br>" +
                "Tangga hanya bisa dipanjat jika kamu mulai dari kotak <b>Bintang (Prima)</b>. Jika tidak, tangga terkunci!</p><br>" +
                "<p><b>3. 💰 KOIN ADALAH RAJA:</b><br>" +
                "Pemenang ditentukan oleh <b>Skor Tertinggi</b> (koin yang dikumpulkan), bukan siapa yang finish duluan.</p><br>" +
                "<p><b>4. 🏁 GARIS FINISH:</b><br>" +
                "Permainan berakhir seketika saat salah satu pemain mencapai <b>Kotak 64</b>.</p>" +
                "</body></html>";

        JLabel lblRules = new JLabel(htmlRules);
        lblRules.setAlignmentX(Component.CENTER_ALIGNMENT);
        pnlContent.add(lblRules);
        pnlContent.add(Box.createVerticalStrut(30));

        MarioButton btnUnderstand = new MarioButton("SAYA MENGERTI", MARIO_GREEN);
        btnUnderstand.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnUnderstand.addActionListener(e -> rulesDialog.dispose());
        pnlContent.add(btnUnderstand);
        pnlContent.add(Box.createVerticalStrut(25));

        rulesDialog.add(pnlContent);
        rulesDialog.setVisible(true);
    }

    public void playBackgroundMusic(String filePath) {
        try {
            File musicPath = new File(filePath);
            if (!musicPath.exists()) musicPath = new File(System.getProperty("user.dir"), filePath);
            if (musicPath.exists()) {
                AudioInputStream audioInput = AudioSystem.getAudioInputStream(musicPath);
                bgmClip = AudioSystem.getClip();
                bgmClip.open(audioInput);
                setClipVolume(bgmClip, controlPanel.volumeSlider.getValue());
                bgmClip.start();
                bgmClip.loop(Clip.LOOP_CONTINUOUSLY);
            }
        } catch (Exception ex) {}
    }

    public void playEffect(String filePath) {
        if (isSFXMuted) return;
        try {
            File sfxPath = new File(filePath);
            if (!sfxPath.exists()) sfxPath = new File(System.getProperty("user.dir"), filePath);
            if (sfxPath.exists()) {
                if (sfxClip != null && sfxClip.isRunning()) { sfxClip.stop(); sfxClip.close(); }
                AudioInputStream audioInput = AudioSystem.getAudioInputStream(sfxPath);
                sfxClip = AudioSystem.getClip();
                sfxClip.open(audioInput);
                setClipVolume(sfxClip, 100);
                sfxClip.start();
            }
        } catch (Exception ex) {}
    }

    public void playWalkSound() {
        if (isSFXMuted) return;
        try {
            File walkPath = new File("walk.wav");
            if (!walkPath.exists()) walkPath = new File(System.getProperty("user.dir"), "walk.wav");
            if (walkPath.exists()) {
                stopWalkSound();
                AudioInputStream audioInput = AudioSystem.getAudioInputStream(walkPath);
                walkClip = AudioSystem.getClip();
                walkClip.open(audioInput);
                setClipVolume(walkClip, 200);
                walkClip.loop(Clip.LOOP_CONTINUOUSLY);
                walkClip.start();
            }
        } catch (Exception ex) {}
    }

    public void stopWalkSound() {
        if (walkClip != null && walkClip.isRunning()) {
            walkClip.stop(); walkClip.close();
        }
    }

    public void playWinSound() {
        try {
            File winPath = new File("win.wav");
            if (!winPath.exists()) winPath = new File(System.getProperty("user.dir"), "win.wav");
            if (winPath.exists()) {
                if (winClip != null && winClip.isRunning()) { winClip.stop(); winClip.close(); }
                AudioInputStream audioInput = AudioSystem.getAudioInputStream(winPath);
                winClip = AudioSystem.getClip();
                winClip.open(audioInput);
                setClipVolume(winClip, 100);
                winClip.start();
            }
        } catch (Exception ex) {}
    }

    public void toggleSFXMute() {
        isSFXMuted = !isSFXMuted;
        if (isSFXMuted) {
            if (sfxClip != null && sfxClip.isRunning()) sfxClip.stop();
            stopWalkSound();
        } else {
            if (diceAnimationTimer != null && diceAnimationTimer.isRunning()) playEffect("dice.wav");
            if (movementAnimationTimer != null && movementAnimationTimer.isRunning()) playWalkSound();
        }
        controlPanel.btnMuteSFX.repaint();
    }

    public void setClipVolume(Clip clip, int sliderValue) {
        if (clip != null && clip.isOpen()) {
            try {
                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float maxVol = gainControl.getMaximum();
                float minVol = gainControl.getMinimum();
                float db;
                if (sliderValue <= 0) db = minVol;
                else if (sliderValue > 100) db = maxVol;
                else db = (float) (6.0f + 20.0f * Math.log10(sliderValue / 50.0));
                if (db > maxVol) db = maxVol; if (db < minVol) db = minVol;
                gainControl.setValue(db);
            } catch (Exception e) {}
        }
    }

    public void updateBGMVolume(int sliderValue) { setClipVolume(bgmClip, sliderValue); }
    public void stopMusic() { if (bgmClip != null && bgmClip.isRunning()) { bgmClip.stop(); bgmClip.close(); } }

    private void setupPlayers() {
        UIManager.put("OptionPane.messageFont", new Font("Segoe UI", Font.BOLD, 14));
        UIManager.put("OptionPane.buttonFont", new Font("Segoe UI", Font.BOLD, 12));
        UIManager.put("Panel.background", new Color(255, 255, 200));
        UIManager.put("OptionPane.background", new Color(255, 255, 200));

        String inputCount = JOptionPane.showInputDialog(this, "Number of Players (2-4):");
        if (inputCount == null) System.exit(0);

        try {
            int count = Integer.parseInt(inputCount);
            if (count < 2) count = 2; if (count > 4) count = 4;

            Node startNode = gameBoard.getStartNode();
            Color[] colors = { MARIO_RED, MARIO_GREEN, MARIO_YELLOW, new Color(128, 0, 128) };

            Set<String> usedNames = new HashSet<>();

            for (int i = 0; i < count; i++) {
                String name = "";
                boolean validName = false;
                while (!validName) {
                    name = JOptionPane.showInputDialog(this, "Player " + (i+1) + " Name:");
                    if (name == null) System.exit(0);
                    if (name.trim().isEmpty()) name = "P" + (i+1);
                    name = name.trim();
                    if (usedNames.contains(name)) {
                        JOptionPane.showMessageDialog(this, "Name taken! Choose another.");
                    } else {
                        usedNames.add(name);
                        validName = true;
                    }
                }
                turnQueue.add(new Player(name, startNode, colors[i]));
            }

            controlPanel.updatePlayerList(turnQueue);
            controlPanel.updateScoreboard(turnQueue);
            controlPanel.setStatus("PRESS START");
            gamePanel.repaint();

        } catch (NumberFormatException e) { System.exit(0); }
    }

    private void startGame() {
        if (turnQueue.isEmpty()) return;
        isGameRunning = true;
        controlPanel.btnStart.setEnabled(false);
        controlPanel.btnRoll.setEnabled(true);
        updatePlayerInfoLabels();
        controlPanel.setStatus("GAME STARTED!");
        playBackgroundMusic("backsound.wav");
    }

    private void processTurn() {
        if (!isGameRunning || turnQueue.isEmpty()) return;
        controlPanel.btnRoll.setEnabled(false);
        playEffect("dice.wav");
        startDiceAnimation();
    }

    private void startDiceAnimation() {
        final int[] frames = {0};
        controlPanel.log("Rolling Block...");
        diceAnimationTimer = new Timer(50, e -> {
            int randomVisual = new Random().nextInt(6) + 1;
            boolean randomColor = new Random().nextBoolean();
            controlPanel.updateDiceVisual(randomVisual, randomColor);
            frames[0]++;
            if (frames[0] >= 50) {
                diceAnimationTimer.stop();
                finalizeDiceRoll();
            }
        });
        diceAnimationTimer.start();
    }

    private void finalizeDiceRoll() {
        Player currentPlayer = turnQueue.peek();
        int startPos = currentPlayer.getCurrentPosition().id;
        currentPlayer.setLastPositionId(startPos);

        Random rand = new Random();
        int finalDice = rand.nextInt(6) + 1;
        boolean isGreen = rand.nextDouble() < 0.70;
        currentTurnIsForward = isGreen;

        controlPanel.updateDiceVisual(finalDice, isGreen);
        String colorText = isGreen ? " [FWD]" : " [BACK]";
        controlPanel.log(currentPlayer.getName() + " : " + finalDice + colorText);

        isFacingRight = isGreen;
        playWalkSound();
        startMovementAnimation(currentPlayer, finalDice, isGreen);
    }

    private void startMovementAnimation(Player player, int totalSteps, boolean isGreen) {
        final int[] stepsTaken = {0};
        movementAnimationTimer = new Timer(250, e -> {
            boolean moveSuccess = false;
            walkFrame = (walkFrame + 1) % 2;

            if (isGreen) {
                if (player.getCurrentPosition().next != null) {
                    player.stepForward(); moveSuccess = true;
                }
            } else {
                int currentId = player.getCurrentPosition().id;
                if (currentId > 1) {
                    player.setPosition(gameBoard.getNodeById(currentId - 1)); moveSuccess = true;
                }
            }
            gamePanel.repaint();
            stepsTaken[0]++;

            if (player.getCurrentPosition().id == 64) {
                movementAnimationTimer.stop();
                stopWalkSound();
                handleGameEnd(player);
                return;
            }

            if (stepsTaken[0] >= totalSteps || !moveSuccess) {
                movementAnimationTimer.stop();
                stopWalkSound();
                walkFrame = 0;
                checkAndStartLadderSequence(player);
            }
        });
        movementAnimationTimer.start();
    }

    private void checkAndStartLadderSequence(Player player) {
        Node current = player.getCurrentPosition();
        if (current.shortcut != null) {
            int prevPos = player.getLastPositionId();
            boolean isPrevPrime = gameBoard.isPrime(prevPos);
            boolean canUseLadder = isPrevPrime && currentTurnIsForward;

            activeLockNodeId = current.id;
            lockAngle = 0;
            isLockOpen = false;
            isLockShaking = false;
            shakeFrame = 0;

            if (canUseLadder) {
                controlPanel.log("Unlock Success! (Prime + Forward)");
                startLockOpenAnimation(player, true);
            } else {
                if (!currentTurnIsForward) controlPanel.log("Locked! (Backward Move)");
                else controlPanel.log("Locked! (Not Prime)");
                startLockOpenAnimation(player, false);
            }
        } else {
            endTurn();
        }
    }

    private void startLockOpenAnimation(Player player, boolean success) {
        final int[] frames = {0};
        lockAnimationTimer = new Timer(30, e -> {
            frames[0]++;
            if (success) {
                isLockOpen = true;
                if (lockAngle < 90) lockAngle += 5;
                if (frames[0] > 30) {
                    lockAnimationTimer.stop();
                    prepareClimb(player);
                }
            } else {
                isLockShaking = true;
                shakeFrame = frames[0];
                if (frames[0] > 30) {
                    lockAnimationTimer.stop();
                    activeLockNodeId = -1;
                    controlPanel.setStatus("LOCKED!");
                    endTurn();
                }
            }
            gamePanel.repaint();
        });
        lockAnimationTimer.start();
    }

    private void prepareClimb(Player player) {
        Node current = player.getCurrentPosition();
        controlPanel.log("Climbing Ladder...");
        animatingPlayer = player;
        Point pStart = gamePanel.getCoordinates(current.id);
        Point pEnd = gamePanel.getCoordinates(current.shortcut.id);
        currentAnimPos = new Point2D.Double(pStart.x, pStart.y);
        targetAnimPos = new Point2D.Double(pEnd.x, pEnd.y);
        player.setPosition(current.shortcut);
        startLadderClimb();
    }

    private void startLadderClimb() {
        ladderAnimationTimer = new Timer(20, e -> {
            double speed = 6.0;
            double dx = targetAnimPos.x - currentAnimPos.x;
            double dy = targetAnimPos.y - currentAnimPos.y;
            double dist = Math.sqrt(dx*dx + dy*dy);
            walkFrame = (walkFrame + 1) % 2;

            if (dist < speed) {
                currentAnimPos = targetAnimPos;
                ladderAnimationTimer.stop();
                animatingPlayer = null;
                activeLockNodeId = -1;
                walkFrame = 0;
                gamePanel.repaint();
                endTurn();
            } else {
                double moveX = (dx / dist) * speed;
                double moveY = (dy / dist) * speed;
                currentAnimPos.x += moveX;
                currentAnimPos.y += moveY;
                gamePanel.repaint();
            }
        });
        ladderAnimationTimer.start();
    }

    private void endTurn() {
        Player p = turnQueue.peek();
        int currentId = p.getCurrentPosition().id;

        boolean bonusTurn = false;
        if (currentId % 5 == 0 && currentId != 64) {
            controlPanel.log("LUCKY BLOCK! Bonus Turn!");
            JOptionPane.showMessageDialog(this, "LUCKY BLOCK!\nBonus Turn for " + p.getName());
            bonusTurn = true;
        }

        int points = gameBoard.getPointsAt(currentId);
        if (points > 0) {
            p.addScore(points);
            gameBoard.removePoints(currentId);
            gamePanel.repaint();

            controlPanel.log("Got " + points + " Coins!");
            JOptionPane.showMessageDialog(this, "COIN COLLECTED!\n+" + points + " Coins!");
            controlPanel.updateScoreboard(turnQueue);
        }

        if (bonusTurn) {
            controlPanel.setStatus("BONUS TURN: " + p.getName());
        } else {
            turnQueue.poll();
            turnQueue.add(p);
            controlPanel.setStatus("NEXT PLAYER");
        }

        updatePlayerInfoLabels();
        controlPanel.btnRoll.setEnabled(true);
    }

    // --- LOGIKA GAME END: SIMPAN & UPDATE HALL OF FAME ---
    private void handleGameEnd(Player finishingPlayer) {
        stopMusic();
        playWinSound();
        startConfetti();

        // 1. Tentukan Pemenang Berdasarkan Skor
        List<Player> allPlayers = new ArrayList<>(turnQueue);
        Collections.sort(allPlayers, (p1, p2) -> p2.getScore() - p1.getScore());
        Player trueWinner = allPlayers.get(0);

        // 2. Simpan ke File (Append Mode)
        appendWinnerToFile(trueWinner.getName(), trueWinner.getScore());

        // 3. Update UI Hall of Fame (Reload dari file yang baru diupdate)
        controlPanel.refreshHallOfFame();

        // --- TAMPILAN JENDELA MENANG ---
        JPanel winPanel = new JPanel();
        winPanel.setLayout(new BoxLayout(winPanel, BoxLayout.Y_AXIS));
        winPanel.setBackground(new Color(255, 255, 220));
        winPanel.setBorder(new EmptyBorder(20, 30, 20, 30));

        JLabel lblTitle = new JLabel("GAME OVER!");
        lblTitle.setFont(new Font("Segoe UI Black", Font.BOLD, 28));
        lblTitle.setForeground(MARIO_RED);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        String subText = "";
        if (finishingPlayer != trueWinner) {
            subText = "(" + finishingPlayer.getName() + " reached finish, but...)";
        }
        JLabel lblSub = new JLabel(subText);
        lblSub.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel characterPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                int cx = getWidth() / 2;
                int cy = getHeight() / 2 + 15;
                gamePanel.drawMarioChar(g2, trueWinner, cx, cy);
            }
        };
        characterPanel.setPreferredSize(new Dimension(120, 100));
        characterPanel.setBackground(new Color(255, 255, 220));
        characterPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblWinner = new JLabel("WINNER: " + trueWinner.getName());
        lblWinner.setFont(new Font("Segoe UI Black", Font.BOLD, 20));
        lblWinner.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblScore = new JLabel("Highest Score: " + trueWinner.getScore());
        lblScore.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblScore.setForeground(new Color(0, 150, 0));
        lblScore.setAlignmentX(Component.CENTER_ALIGNMENT);

        winPanel.add(lblTitle);
        if (!subText.isEmpty()) winPanel.add(lblSub);
        winPanel.add(Box.createVerticalStrut(20));
        winPanel.add(characterPanel);
        winPanel.add(Box.createVerticalStrut(15));
        winPanel.add(lblWinner);
        winPanel.add(Box.createVerticalStrut(10));
        winPanel.add(lblScore);

        UIManager.put("OptionPane.background", new Color(255, 255, 220));
        UIManager.put("Panel.background", new Color(255, 255, 220));
        UIManager.put("Button.background", Color.WHITE);
        UIManager.put("Button.font", new Font("Segoe UI Black", Font.BOLD, 14));

        JOptionPane.showMessageDialog(this, winPanel, "CONGRATULATIONS!", JOptionPane.PLAIN_MESSAGE);

        controlPanel.setStatus("WINNER: " + trueWinner.getName());
        controlPanel.btnRoll.setEnabled(false);
        isGameRunning = false;
    }

    private void updatePlayerInfoLabels() {
        if (turnQueue.isEmpty()) return;
        Player current = turnQueue.peek();
        controlPanel.lblCurrentName.setText(current.getName());
        controlPanel.lblCurrentName.setForeground(current.getColor());
        if (turnQueue.size() > 1) {
            Object[] players = turnQueue.toArray();
            Player next = (Player) players[1];
            controlPanel.lblNextName.setText(next.getName());
        } else {
            controlPanel.lblNextName.setText("-");
        }
    }

    private void startConfetti() {
        Random r = new Random();
        Color[] confColors = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA, Color.CYAN};

        for(int i=0; i<300; i++) {
            int x = r.nextInt(gamePanel.getWidth());
            int y = r.nextInt(gamePanel.getHeight()) - 400;
            Color c = confColors[r.nextInt(confColors.length)];
            confettiParticles.add(new ConfettiParticle(x, y, c));
        }

        confettiTimer = new Timer(30, e -> {
            Iterator<ConfettiParticle> it = confettiParticles.iterator();
            while (it.hasNext()) {
                ConfettiParticle p = it.next();
                p.update();
                if (p.y > gamePanel.getHeight()) {
                    p.y = -10;
                    p.x = new Random().nextInt(gamePanel.getWidth());
                }
            }
            gamePanel.repaint();
        });
        confettiTimer.start();
    }

    class ConfettiParticle {
        double x, y;
        double speedY, speedX;
        Color color;
        double angle = 0;

        public ConfettiParticle(int x, int y, Color c) {
            this.x = x; this.y = y; this.color = c;
            this.speedY = 2 + Math.random() * 5;
            this.speedX = -2 + Math.random() * 4;
        }
        public void update() {
            y += speedY;
            x += Math.sin(y / 20.0);
            angle += 0.1;
        }
    }

    // --- VISUAL BOARD ---
    class GamePanel extends JPanel {
        public Point getCoordinates(int id) {
            int cols = 8; int rows = 8;
            int w = getWidth() / cols; int h = getHeight() / rows;
            int mathRow = (id - 1) / 8; int visualRow = 7 - mathRow;
            int col = (mathRow % 2 == 0) ? (id - 1) % 8 : 7 - ((id - 1) % 8);
            return new Point(col * w + w/2, visualRow * h + h/2);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cols = 8, rows = 8;
            int w = getWidth() / cols; int h = getHeight() / rows;

            // 1. Render Blocks
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int x = c * w; int y = r * h;
                    int mathRow = 7 - r;
                    int id = (mathRow % 2 == 0) ? (mathRow * 8) + c + 1 : (mathRow * 8) + (7 - c) + 1;

                    drawLegoBlock(g2, x, y, w, h, id);
                }
            }

            // 2. Tangga & Gembok
            for (int i = 1; i <= 64; i++) {
                Node n = gameBoard.getNodeById(i);
                if (n != null && n.shortcut != null) {
                    Point p1 = getCoordinates(n.id);
                    Point p2 = getCoordinates(n.shortcut.id);

                    drawPremiumLadder(g2, p1.x, p1.y, p2.x, p2.y);

                    boolean isOpen = false;
                    double angle = 0;
                    int shakeX = 0;
                    Color lockColor = Color.BLACK;
                    if (n.id == activeLockNodeId) {
                        isOpen = isLockOpen;
                        angle = lockAngle;
                        if (isLockShaking) {
                            shakeX = (int) (Math.sin(shakeFrame * 2) * 5);
                            lockColor = MARIO_RED;
                        }
                    }
                    drawKeyhole(g2, p1.x - 10 + shakeX, p1.y - 15, isOpen, angle, lockColor);
                }
            }

            // 3. Pemain
            if (turnQueue != null) {
                Map<Integer, List<Player>> playersOnNode = new HashMap<>();
                for (Player p : turnQueue) {
                    int id = p.getCurrentPosition().id;
                    if (!playersOnNode.containsKey(id)) {
                        playersOnNode.put(id, new ArrayList<>());
                    }
                    playersOnNode.get(id).add(p);
                }

                for (Integer nodeId : playersOnNode.keySet()) {
                    List<Player> occupants = playersOnNode.get(nodeId);
                    int count = occupants.size();
                    Point pos = getCoordinates(nodeId);
                    int spacing = 20;
                    int startX = pos.x - ((count - 1) * spacing) / 2;

                    for (int i = 0; i < count; i++) {
                        Player p = occupants.get(i);
                        if (p == animatingPlayer && currentAnimPos != null) continue;
                        drawMarioChar(g2, p, startX + (i * spacing), pos.y);
                    }
                }

                if (animatingPlayer != null && currentAnimPos != null) {
                    drawMarioChar(g2, animatingPlayer, (int)currentAnimPos.x, (int)currentAnimPos.y);
                }
            }

            // 4. Render Confetti
            if (!confettiParticles.isEmpty()) {
                for (ConfettiParticle p : confettiParticles) {
                    g2.setColor(p.color);
                    AffineTransform old = g2.getTransform();
                    g2.translate(p.x, p.y);
                    g2.rotate(p.angle);
                    g2.fillRect(-4, -2, 8, 4);
                    g2.setTransform(old);
                }
            }
        }

        // --- DRAWING HELPERS ---

        private void drawLegoBlock(Graphics2D g, int x, int y, int w, int h, int id) {
            g.setColor(MARIO_BROWN);
            g.fillRect(x + 2, y + 2, w - 4, h - 4);
            g.setColor(MARIO_LIGHT_BROWN);
            g.fillRect(x + 10, y + 10, 6, 6);
            g.fillRect(x + w - 16, y + h - 16, 6, 6);
            g.fillRect(x + w/2, y + h/2, 4, 4);
            g.setColor(MARIO_GREEN);
            g.fillRect(x + 2, y + 2, w - 4, 10);

            int studSize = w / 6;
            for(int i=1; i<4; i++) {
                int sx = x + (i * w / 4) - studSize/2;
                int sy = y;
                g.setColor(new Color(30, 100, 30));
                g.fillOval(sx, sy, studSize, studSize/2 + 3);
                g.setColor(MARIO_GREEN);
                g.fillOval(sx, sy, studSize, studSize/2);
            }

            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(2));
            g.drawRect(x + 2, y + 2, w - 4, h - 4);

            g.setFont(new Font("Segoe UI Black", Font.PLAIN, 14));
            g.setColor(Color.WHITE);
            // Angka di Kiri Atas
            g.drawString(String.valueOf(id), x + 5, y + 20);

            if (gameBoard.isPrime(id)) {
                g.setColor(Color.YELLOW);
                g.setFont(new Font("Monospaced", Font.BOLD, 16));
                g.drawString("★", x + w - 20, y + 22);
            }

            if (id % 5 == 0 && id != 64) {
                drawGoldenDice(g, x + w - 20, y + h - 18, 14);
            }

            int points = gameBoard.getPointsAt(id);
            if (points > 0) {
                drawCoin(g, x + 25, y + h - 15, points);
            }
        }

        private void drawCoin(Graphics2D g, int cx, int cy, int val) {
            int r = 18;
            g.setColor(new Color(255, 215, 0));
            g.fillOval(cx - r/2, cy - r/2, r, r + 4);

            g.setColor(new Color(218, 165, 32));
            g.setStroke(new BasicStroke(2));
            g.drawOval(cx - r/2 + 2, cy - r/2 + 2, r - 4, r);

            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1));
            g.drawOval(cx - r/2, cy - r/2, r, r + 4);

            g.setColor(Color.BLACK);
            g.setFont(new Font("Monospaced", Font.BOLD, 10));
            String s = String.valueOf(val);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(s, cx - fm.stringWidth(s)/2, cy + fm.getAscent()/2 - 1);
        }

        // Visibility changed to package-private so Win Panel can access it
        void drawMarioChar(Graphics2D g, Player p, int cx, int cy) {
            int scale = 3;
            Color outfitColor = p.getColor();
            int facing = (isFacingRight) ? 1 : -1;

            AffineTransform old = g.getTransform();
            g.translate(cx, cy);
            g.scale(facing, 1);

            g.setColor(new Color(0,0,0, 50));
            g.fillOval(-12, 20, 24, 8);

            g.setColor(MARIO_BLUE);
            if (animatingPlayer == p && walkFrame == 1) {
                g.fillRect(-8, 10, 6, 10);
                g.fillRect(2, 10, 6, 10);
            } else {
                g.fillRect(-6, 10, 12, 10);
            }

            g.setColor(outfitColor);
            g.fillRect(-8, 0, 16, 12);
            g.fillRect(-12, 2, 24, 4);

            g.setColor(MARIO_SKIN);
            g.fillRect(-7, -10, 12, 10);

            g.setColor(outfitColor);
            g.fillRect(-8, -14, 16, 4);
            g.fillRect(-8, -14, 10, 6);

            g.setColor(Color.BLACK);
            g.fillRect(2, -8, 2, 2);
            g.fillRect(3, -6, 4, 2);
            g.fillRect(0, -3, 2, 4);

            g.setTransform(old);
        }

        // --- TANGGA GOLD (GRADIENT) ---
        private void drawPremiumLadder(Graphics2D g, int x1, int y1, int x2, int y2) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(0, 0, 0, 60));
            g2.setStroke(new BasicStroke(8));
            g2.draw(new Line2D.Double(x1+3, y1+3, x2+3, y2+3));

            double angle = Math.atan2(y2 - y1, x2 - x1);
            double railDist = 12.0;
            double perpX = Math.cos(angle + Math.PI/2);
            double perpY = Math.sin(angle + Math.PI/2);

            double lx1 = x1 - perpX * railDist; double ly1 = y1 - perpY * railDist;
            double lx2 = x2 - perpX * railDist; double ly2 = y2 - perpY * railDist;
            double rx1 = x1 + perpX * railDist; double ry1 = y1 + perpY * railDist;
            double rx2 = x2 + perpX * railDist; double ry2 = y2 + perpY * railDist;

            double dist = Math.sqrt(Math.pow(x2-x1, 2) + Math.pow(y2-y1, 2));
            int steps = (int)(dist / 18);
            g2.setStroke(new BasicStroke(6));

            for (int i = 1; i < steps; i++) {
                double t = (double)i / steps;
                double cx = x1 + (x2-x1) * t;
                double cy = y1 + (y2-y1) * t;

                Point2D pLeft = new Point2D.Double(cx - perpX * railDist, cy - perpY * railDist);
                Point2D pRight = new Point2D.Double(cx + perpX * railDist, cy + perpY * railDist);

                GradientPaint rungGrad = new GradientPaint(
                        (float)pLeft.getX(), (float)pLeft.getY(), LADDER_DARK,
                        (float)pRight.getX(), (float)pRight.getY(), LADDER_SHINE
                );
                g2.setPaint(rungGrad);
                g2.draw(new Line2D.Double(pLeft, pRight));
            }

            g2.setStroke(new BasicStroke(5));
            g2.setColor(LADDER_DARK);
            g2.draw(new Line2D.Double(lx1, ly1, lx2, ly2));
            g2.draw(new Line2D.Double(rx1, ry1, rx2, ry2));

            g2.setStroke(new BasicStroke(2));
            g2.setColor(LADDER_LIGHT);
            g2.draw(new Line2D.Double(lx1, ly1, lx2, ly2));
            g2.draw(new Line2D.Double(rx1, ry1, rx2, ry2));

            g2.dispose();
        }

        private void drawGoldenDice(Graphics2D g, int x, int y, int s) {
            Path2D topFace = new Path2D.Double();
            topFace.moveTo(x, y - s);
            topFace.lineTo(x + s, y - s/2.0);
            topFace.lineTo(x, y);
            topFace.lineTo(x - s, y - s/2.0);
            topFace.closePath();
            Path2D leftFace = new Path2D.Double();
            leftFace.moveTo(x, y);
            leftFace.lineTo(x - s, y - s/2.0);
            leftFace.lineTo(x - s, y + s/2.0);
            leftFace.lineTo(x, y + s);
            leftFace.closePath();
            Path2D rightFace = new Path2D.Double();
            rightFace.moveTo(x, y);
            rightFace.lineTo(x + s, y - s/2.0);
            rightFace.lineTo(x + s, y + s/2.0);
            rightFace.lineTo(x, y + s);
            rightFace.closePath();
            g.setColor(DICE_GOLD); g.fill(topFace);
            g.setColor(new Color(218, 165, 32)); g.fill(leftFace);
            g.setColor(new Color(184, 134, 11)); g.fill(rightFace);
            g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1));
            g.draw(topFace); g.draw(leftFace); g.draw(rightFace);
            g.fillOval(x - 2, y - s/2 - 2, 4, 4);
            g.fillOval(x - s/2 - 2, y - 2, 4, 4);
            g.fillOval(x + s/2 - 2, y - 2, 4, 4);
        }

        private void drawKeyhole(Graphics2D g, int x, int y, boolean isOpen, double angle, Color color) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            int w = 20; int h = 16; int thick = 2;
            g2.setStroke(new BasicStroke(thick));
            Color goldBody = MARIO_YELLOW;
            Color darkGoldShadow = new Color(180, 140, 0);
            Color greyShackle = new Color(180, 180, 180);
            Color darkGreyShadow = new Color(120, 120, 120);
            int shackleW = 14; int shackleH = 10;
            int shackleX = x + (w - shackleW) / 2;
            int shackleY = y - shackleH + thick/2;
            AffineTransform oldXform = g2.getTransform();
            if (isOpen) g2.rotate(Math.toRadians(angle), shackleX + shackleW - thick, shackleY + shackleH);
            g2.setColor(Color.BLACK);
            g2.drawRect(shackleX, shackleY, shackleW, shackleH + thick);
            g2.setColor(greyShackle);
            g2.fillRect(shackleX + thick/2, shackleY + thick/2, shackleW - thick, shackleH);
            g2.setColor(darkGreyShadow);
            g2.fillRect(shackleX + shackleW - thick, shackleY + thick/2, thick/2, shackleH);
            g2.setComposite(AlphaComposite.Clear);
            g2.fillRect(shackleX + thick, shackleY + thick, shackleW - thick*2, shackleH);
            g2.setComposite(AlphaComposite.SrcOver);
            if (isOpen) g2.setTransform(oldXform);
            g2.setColor(Color.BLACK);
            g2.drawRect(x, y, w, h);
            g2.setColor(goldBody);
            g2.fillRect(x + thick/2, y + thick/2, w - thick, h - thick);
            g2.setColor(darkGoldShadow);
            g2.fillRect(x + w - thick, y + thick/2, thick/2, h - thick);
            g2.fillRect(x + thick/2, y + h - thick, w - thick, thick/2);
            g2.setColor(Color.BLACK);
            int khW = 4; int khH = 8;
            int khX = x + (w - khW) / 2;
            int khY = y + (h - khH) / 2 + 1;
            g2.fillRect(khX, khY, khW, khW/2 + 2);
            g2.fillRect(khX + 1, khY + khW/2, khW - 2, khH - khW/2);
            g2.dispose();
        }
    }

    // --- PANEL CONTROL ---
    class ControlPanel extends JPanel {
        JLabel lblCurrentName, lblNextName, lblStatus;
        JPanel pnlPlayerList; JTextArea txtLog;
        JPanel pnlLeaderboard; // New Leaderboard Panel

        // --- GANTI LAST CHAMPION MENJADI HALL OF FAME ---
        JPanel pnlHallOfFameDisplay;

        MarioButton btnStart, btnRoll; JPanel dicePanel;
        JSlider volumeSlider;
        SoundButton btnMuteSFX;

        int lastDiceVal = 1; Color lastDiceColor = MARIO_YELLOW;

        public ControlPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setPreferredSize(new Dimension(320, 800));
            setBorder(new EmptyBorder(20, 20, 20, 20));
            setBackground(new Color(92, 148, 252));

            JLabel title = new JLabel("WORLD 1-1");
            title.setFont(new Font("Segoe UI Black", Font.BOLD, 24));
            title.setForeground(Color.WHITE);
            title.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(title); add(Box.createVerticalStrut(10));

            // --- SECTION HALL OF FAME (PERSISTENT DATA) ---
            JLabel lblChamp = createLabel("HALL OF FAME (Top 5):");
            lblChamp.setForeground(Color.YELLOW);
            add(lblChamp);

            pnlHallOfFameDisplay = new JPanel();
            pnlHallOfFameDisplay.setLayout(new BoxLayout(pnlHallOfFameDisplay, BoxLayout.Y_AXIS));
            pnlHallOfFameDisplay.setBackground(new Color(92, 148, 252));
            pnlHallOfFameDisplay.setBorder(new LineBorder(Color.YELLOW, 2));

            JScrollPane scrollHoF = new JScrollPane(pnlHallOfFameDisplay);
            scrollHoF.setPreferredSize(new Dimension(280, 100)); // Cukup untuk 5 baris
            scrollHoF.setBorder(null);

            add(scrollHoF);
            add(Box.createVerticalStrut(15));

            // Initial Load
            refreshHallOfFame();
            // ----------------------------------------------

            add(createLabel("MUSIC VOL:"));
            JPanel pnlSlider = new JPanel(new BorderLayout());
            pnlSlider.setBackground(new Color(92, 148, 252));
            pnlSlider.setMaximumSize(new Dimension(280, 40));

            JLabel lblMin = new JLabel("0% "); lblMin.setForeground(Color.WHITE);
            JLabel lblMax = new JLabel(" 100%"); lblMax.setForeground(Color.WHITE);

            volumeSlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 50);
            volumeSlider.setBackground(new Color(92, 148, 252));
            volumeSlider.setForeground(MARIO_YELLOW);
            volumeSlider.addChangeListener(e -> updateBGMVolume(volumeSlider.getValue()));

            pnlSlider.add(lblMin, BorderLayout.WEST);
            pnlSlider.add(volumeSlider, BorderLayout.CENTER);
            pnlSlider.add(lblMax, BorderLayout.EAST);
            add(pnlSlider);
            add(Box.createVerticalStrut(10));

            JPanel pnlMute = new JPanel();
            pnlMute.setLayout(new BoxLayout(pnlMute, BoxLayout.X_AXIS));
            pnlMute.setBackground(new Color(92, 148, 252));
            JLabel lblMute = new JLabel("MUTE SFX:  ");
            lblMute.setForeground(Color.WHITE);
            lblMute.setFont(new Font("Segoe UI", Font.BOLD, 12));
            pnlMute.add(lblMute);

            btnMuteSFX = new SoundButton();
            btnMuteSFX.addActionListener(e -> toggleSFXMute());
            pnlMute.add(btnMuteSFX);
            add(pnlMute);
            add(Box.createVerticalStrut(15));

            add(createLabel("CURRENT GAME:"));

            // --- Custom Leaderboard Panel (Current Game) ---
            pnlLeaderboard = new JPanel();
            pnlLeaderboard.setLayout(new BoxLayout(pnlLeaderboard, BoxLayout.Y_AXIS));
            pnlLeaderboard.setBackground(new Color(240, 240, 255));
            pnlLeaderboard.setBorder(new LineBorder(MARIO_BLUE, 2));

            JScrollPane scrollScore = new JScrollPane(pnlLeaderboard);
            scrollScore.setPreferredSize(new Dimension(280, 120));
            add(scrollScore); add(Box.createVerticalStrut(15));

            btnStart = new MarioButton("START GAME", MARIO_GREEN);
            btnStart.addActionListener(e -> startGame());
            add(btnStart); add(Box.createVerticalStrut(10));

            JPanel pnlTurn = new JPanel();
            pnlTurn.setLayout(new BoxLayout(pnlTurn, BoxLayout.Y_AXIS));
            pnlTurn.setBackground(new Color(255, 204, 204));
            pnlTurn.setBorder(new LineBorder(Color.BLACK, 2));

            lblCurrentName = new JLabel("-");
            lblCurrentName.setFont(new Font("Segoe UI Black", Font.BOLD, 22));
            lblCurrentName.setForeground(MARIO_RED);
            lblCurrentName.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel spacer = new JLabel(" ");
            spacer.setAlignmentX(Component.CENTER_ALIGNMENT);
            pnlTurn.add(spacer);

            pnlTurn.add(lblCurrentName);

            lblNextName = new JLabel("NEXT: -");
            lblNextName.setFont(new Font("Monospaced", Font.BOLD, 14));
            lblNextName.setAlignmentX(Component.CENTER_ALIGNMENT);
            pnlTurn.add(lblNextName);
            add(pnlTurn); add(Box.createVerticalStrut(10));

            dicePanel = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(lastDiceColor);
                    g2.fillRect(95, 5, 90, 90);
                    g2.setColor(new Color(200, 150, 0));
                    g2.setStroke(new BasicStroke(3));
                    g2.drawRect(95, 5, 90, 90);
                    g2.setColor(Color.BLACK);
                    g2.fillOval(100, 10, 6, 6); g2.fillOval(174, 10, 6, 6);
                    g2.fillOval(100, 84, 6, 6); g2.fillOval(174, 84, 6, 6);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Segoe UI Black", Font.BOLD, 48));
                    String s = String.valueOf(lastDiceVal);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(s, 95 + (90 - fm.stringWidth(s))/2, 5 + (90 + fm.getAscent())/2 - 10);
                }
            };
            dicePanel.setPreferredSize(new Dimension(280, 100));
            dicePanel.setBackground(new Color(92, 148, 252));
            add(dicePanel);

            btnRoll = new MarioButton("HIT BLOCK!", MARIO_YELLOW);
            btnRoll.setForeground(Color.BLACK);
            btnRoll.setEnabled(false);
            btnRoll.addActionListener(e -> processTurn());
            add(btnRoll); add(Box.createVerticalStrut(10));

            lblStatus = new JLabel("INSERT COIN");
            lblStatus.setForeground(Color.WHITE);
            lblStatus.setFont(new Font("Monospaced", Font.BOLD, 14));
            lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(lblStatus);

            txtLog = new JTextArea(); txtLog.setEditable(false);
            txtLog.setFont(new Font("Monospaced", Font.BOLD, 12));
            txtLog.setBackground(Color.BLACK);
            txtLog.setForeground(Color.GREEN);
            JScrollPane scrollLog = new JScrollPane(txtLog);
            scrollLog.setPreferredSize(new Dimension(280, 100));
            add(scrollLog);
        }

        public void updatePlayerList(Queue<Player> players) {}

        // --- METHOD REFRESH HALL OF FAME UI ---
        public void refreshHallOfFame() {
            pnlHallOfFameDisplay.removeAll();

            // 1. Load & Sort (sudah dihandle di loadTopWinners)
            List<WinnerRecord> top5 = loadTopWinners();

            // 2. Tampilkan di UI
            if (top5.isEmpty()) {
                JLabel lblEmpty = new JLabel("No Records Yet");
                lblEmpty.setForeground(Color.WHITE);
                lblEmpty.setAlignmentX(Component.CENTER_ALIGNMENT);
                pnlHallOfFameDisplay.add(lblEmpty);
            } else {
                int rank = 1;
                for (WinnerRecord w : top5) {
                    JPanel row = new JPanel(new BorderLayout());
                    row.setBackground(new Color(92, 148, 252));
                    row.setMaximumSize(new Dimension(260, 20));

                    JLabel lblRankName = new JLabel(" #" + rank + " " + w.name);
                    lblRankName.setForeground(Color.WHITE);
                    lblRankName.setFont(new Font("Segoe UI", Font.BOLD, 12));

                    JLabel lblScore = new JLabel(String.valueOf(w.score) + " pts ");
                    lblScore.setForeground(Color.YELLOW);
                    lblScore.setFont(new Font("Monospaced", Font.BOLD, 12));

                    row.add(lblRankName, BorderLayout.WEST);
                    row.add(lblScore, BorderLayout.EAST);

                    pnlHallOfFameDisplay.add(row);
                    rank++;
                }
            }

            pnlHallOfFameDisplay.revalidate();
            pnlHallOfFameDisplay.repaint();
        }

        // --- CUSTOM LEADERBOARD UPDATE (CURRENT GAME) ---
        public void updateScoreboard(Queue<Player> players) {
            pnlLeaderboard.removeAll();

            List<Player> sorted = new ArrayList<>(players);
            Collections.sort(sorted, (p1, p2) -> p2.getScore() - p1.getScore());

            int rank = 1;
            for (Player p : sorted) {
                JPanel card = new JPanel(new BorderLayout());
                card.setBackground(Color.WHITE);
                card.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(Color.LIGHT_GRAY, 1),
                        new EmptyBorder(5, 5, 5, 5)
                ));
                card.setMaximumSize(new Dimension(260, 40));

                JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
                left.setOpaque(false);
                JLabel lblRank = new JLabel("#" + rank);
                lblRank.setFont(new Font("Segoe UI", Font.BOLD, 14));

                JPanel icon = new JPanel() {
                    @Override protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                        int cx = getWidth() / 2;
                        int cy = getHeight() / 2 + 4;
                        Color outfitColor = p.getColor();
                        double scale = 1.5;
                        AffineTransform old = g2.getTransform();
                        g2.translate(cx, cy);
                        g2.scale(scale, scale);
                        g2.setColor(MARIO_BLUE);
                        g2.fillRect(-6, 10, 12, 10);
                        g2.setColor(outfitColor);
                        g2.fillRect(-8, 0, 16, 12);
                        g2.fillRect(-12, 2, 24, 4);
                        g2.setColor(MARIO_SKIN);
                        g2.fillRect(-7, -10, 12, 10);
                        g2.setColor(outfitColor);
                        g2.fillRect(-8, -14, 16, 4);
                        g2.fillRect(-8, -14, 10, 6);
                        g2.setColor(Color.BLACK);
                        g2.fillRect(2, -8, 2, 2);
                        g2.fillRect(3, -6, 4, 2);
                        g2.fillRect(0, -3, 2, 4);
                        g2.setTransform(old);
                    }
                };
                icon.setPreferredSize(new Dimension(30, 30));

                left.add(lblRank);
                left.add(icon);

                JLabel lblName = new JLabel(p.getName());
                lblName.setFont(new Font("Segoe UI", Font.PLAIN, 12));

                JLabel lblScore = new JLabel(String.valueOf(p.getScore()));
                lblScore.setFont(new Font("Segoe UI Black", Font.BOLD, 14));
                lblScore.setForeground(new Color(0, 150, 0));

                card.add(left, BorderLayout.WEST);
                card.add(lblName, BorderLayout.CENTER);
                card.add(lblScore, BorderLayout.EAST);

                pnlLeaderboard.add(card);
                pnlLeaderboard.add(Box.createVerticalStrut(2));
                rank++;
            }
            pnlLeaderboard.revalidate();
            pnlLeaderboard.repaint();
        }

        public boolean isSFXMuted() { return btnMuteSFX.isMuted(); }

        private JLabel createLabel(String text) {
            JLabel l = new JLabel(text);
            l.setAlignmentX(Component.CENTER_ALIGNMENT);
            l.setFont(new Font("Segoe UI Black", Font.BOLD, 12));
            l.setForeground(Color.WHITE);
            return l;
        }

        public void setStatus(String text) { lblStatus.setText(text); }
        public void log(String text) { txtLog.append(text + "\n"); txtLog.setCaretPosition(txtLog.getDocument().getLength()); }
        public void updateDiceVisual(int val, boolean isGreen) {
            this.lastDiceVal = val;
            this.lastDiceColor = isGreen ? MARIO_GREEN : MARIO_RED;
            this.dicePanel.repaint();
        }
    }

    class MarioButton extends JButton {
        private Color bgCol;
        public MarioButton(String text, Color c) {
            super(text);
            this.bgCol = c;
            setContentAreaFilled(false); setFocusPainted(false); setBorderPainted(false);
            setForeground(Color.WHITE); setFont(new Font("Segoe UI Black", Font.BOLD, 16));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setMaximumSize(new Dimension(280, 50));
            setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(bgCol);
            g2.fillRect(4, 4, getWidth()-8, getHeight()-8);
            g2.setStroke(new BasicStroke(4));
            g2.setColor(Color.WHITE);
            g2.drawLine(4, 4, getWidth()-4, 4);
            g2.drawLine(4, 4, 4, getHeight()-4);
            g2.setColor(Color.BLACK);
            g2.drawLine(getWidth()-4, getHeight()-4, getWidth()-4, 4);
            g2.drawLine(getWidth()-4, getHeight()-4, 4, getHeight()-4);
            g2.setColor(Color.BLACK);
            g2.fillRect(8, 8, 4, 4);
            g2.fillRect(getWidth()-12, 8, 4, 4);
            g2.fillRect(8, getHeight()-12, 4, 4);
            g2.fillRect(getWidth()-12, getHeight()-12, 4, 4);
            super.paintComponent(g);
        }
    }

    class SoundButton extends JButton {
        public SoundButton() {
            setPreferredSize(new Dimension(40, 40));
            setContentAreaFilled(false); setFocusPainted(false); setBorderPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }
        public boolean isMuted() { return isSFXMuted; }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(isSFXMuted ? MARIO_RED : MARIO_YELLOW);
            g2.fillOval(0,0,w,h);
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(2));
            g2.drawOval(0,0,w,h);

            g2.setColor(Color.BLACK);
            int cx = w/2, cy = h/2;
            Path2D spk = new Path2D.Double();
            spk.moveTo(cx-4, cy-4); spk.lineTo(cx-8, cy-4); spk.lineTo(cx-8, cy+4);
            spk.lineTo(cx-4, cy+4); spk.lineTo(cx+2, cy+8); spk.lineTo(cx+2, cy-8); spk.closePath();
            g2.fill(spk);
            if (!isSFXMuted) {
                g2.drawArc(cx-2, cy-6, 12, 12, -45, 90);
                g2.drawArc(cx+1, cy-9, 12, 18, -45, 90);
            } else {
                g2.setStroke(new BasicStroke(3));
                g2.drawLine(cx+4, cy-4, cx+10, cy+4);
                g2.drawLine(cx+10, cy-4, cx+4, cy+4);
            }
        }
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        new MainGameGUI();
    }
}