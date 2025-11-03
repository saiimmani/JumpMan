import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/**
 * JumpMan — a retro-styled platform jumper in pure Java/Swing.
 *
 * How to run (JDK 8+):
 *   javac JumpMan.java && java JumpMan
 */
public class JumpMan {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameWindow().setVisible(true));
    }
}

class GameWindow extends JFrame {
    GameWindow() {
        setTitle("JumpMan — Retro Java Platformer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        GamePanel panel = new GamePanel(800, 480);
        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
        panel.start();
    }
}

class GamePanel extends JPanel implements ActionListener, KeyListener {
    // ---------- CONFIG ----------
    final int W, H;
    final int FPS = 60;
    final double DT = 1.0 / FPS;

    // Physics
    final double GRAVITY = 1600;   // px/s^2
    final double MOVE_SPEED = 260; // px/s
    final double JUMP_POWER = 600; // px/s

    // Game
    enum State { MENU, PLAYING, PAUSED, LEVEL_COMPLETE, GAME_OVER }
    State state = State.MENU;

    Player player;
    java.util.List<Platform> platforms = new ArrayList<>();
    java.util.List<Coin> coins = new ArrayList<>();
    java.util.List<Enemy> enemies = new ArrayList<>();
    Goal goal;
    HUD hud = new HUD();

    int levelIndex = 0;
    java.util.List<Level> levels = new ArrayList<>();

    javax.swing.Timer timer;
    long lastTimeNs;

    GamePanel(int w, int h) {
        this.W = w; this.H = h;
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addKeyListener(this);
        setBackground(new Color(20, 22, 30));
        setDoubleBuffered(true);
        buildLevels();
        loadLevel(0);
        timer = new javax.swing.Timer(1000 / FPS, this);
    }

    void start() { lastTimeNs = System.nanoTime(); timer.start(); }

    void buildLevels() {
        // LEVEL 1 — gentle intro
        Level l1 = new Level("Beginner Bluffs", W, H);
        l1.platforms.add(new Platform(0, H-40, W, 40)); // ground
        l1.platforms.add(new Platform(60, H-120, 120, 16));
        l1.platforms.add(new Platform(260, H-180, 120, 16));
        l1.platforms.add(new Platform(460, H-140, 120, 16));
        l1.platforms.add(new Platform(650, H-210, 120, 16));
        l1.coins.add(new Coin(110, H-160));
        l1.coins.add(new Coin(310, H-220));
        l1.coins.add(new Coin(510, H-180));
        l1.enemies.add(new Enemy(350, H-60, 300, 500));
        l1.goal = new Goal(W-60, H-250, 30, 60);
        levels.add(l1);

        // LEVEL 2 — moving and gaps
        Level l2 = new Level("Gaps & Goons", W, H);
        l2.platforms.add(new Platform(0, H-40, 230, 40));
        l2.platforms.add(new Platform(280, H-40, 200, 40));
        l2.platforms.add(new Platform(520, H-40, 280, 40));
        l2.platforms.add(new Platform(120, H-160, 120, 16));
        l2.platforms.add(new Platform(340, H-220, 120, 16));
        l2.platforms.add(new Platform(560, H-180, 120, 16));
        l2.coins.add(new Coin(170, H-200));
        l2.coins.add(new Coin(390, H-260));
        l2.coins.add(new Coin(610, H-220));
        l2.enemies.add(new Enemy(140, H-60, 120, 220));
        l2.enemies.add(new Enemy(560, H-60, 520, 760));
        l2.goal = new Goal(W-60, H-230, 30, 60);
        levels.add(l2);

        // LEVEL 3 — tower climb
        Level l3 = new Level("Tower Tangle", W, H);
        l3.platforms.add(new Platform(0, H-40, W, 40));
        l3.platforms.add(new Platform(80, H-120, 80, 16));
        l3.platforms.add(new Platform(180, H-180, 80, 16));
        l3.platforms.add(new Platform(280, H-240, 80, 16));
        l3.platforms.add(new Platform(380, H-300, 80, 16));
        l3.platforms.add(new Platform(500, H-260, 80, 16));
        l3.platforms.add(new Platform(620, H-220, 80, 16));
        l3.coins.add(new Coin(100, H-160));
        l3.coins.add(new Coin(300, H-280));
        l3.coins.add(new Coin(520, H-300));
        l3.coins.add(new Coin(650, H-260));
        l3.enemies.add(new Enemy(420, H-60, 380, 620));
        l3.goal = new Goal(W-70, H-320, 30, 60);
        levels.add(l3);
    }

    void loadLevel(int idx) {
        levelIndex = idx;
        Level L = levels.get(levelIndex);
        platforms.clear(); platforms.addAll(L.platforms);
        coins.clear(); coins.addAll(L.coins);
        enemies.clear(); enemies.addAll(L.enemies);
        goal = L.goal;
        player = new Player(24, H-100, 26, 30);
        player.vx = 0; player.vy = 0; player.onGround = false; player.spawnX = 24; player.spawnY = H-100;
        hud.resetTimer();
        state = State.MENU; // show title per level
    }

    @Override public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        double dt = (now - lastTimeNs) / 1e9;
        if (dt > 0.05) dt = 0.05; // clamp to avoid long stalls
        lastTimeNs = now;

        if (state == State.PLAYING) update(dt);
        repaint();
    }

    void update(double dt) {
        hud.update(dt);
        // Input → velocity
        double targetVx = 0;
        if (left) targetVx -= MOVE_SPEED;
        if (right) targetVx += MOVE_SPEED;
        player.vx = targetVx;

        // Jump (press edge)
        if (jump && player.onGround) {
            player.vy = -JUMP_POWER; // upward
            player.onGround = false;
        }

        // Gravity
        player.vy += GRAVITY * dt;

        // Integrate X
        double newX = player.x + player.vx * dt;
        Rect old = player.bounds();
        player.x = newX;
        // Collide X with platforms
        for (Platform p : platforms) {
            if (player.intersects(p)) {
                if (old.right() <= p.x) { // came from left
                    player.x = p.x - player.w - 0.01;
                } else if (old.x >= p.right()) { // from right
                    player.x = p.right() + 0.01;
                }
            }
        }

        // Integrate Y
        double newY = player.y + player.vy * dt;
        player.y = newY;
        player.onGround = false;
        // Collide Y with platforms
        for (Platform p : platforms) {
            if (player.intersects(p)) {
                if (old.bottom() <= p.y) { // landed on top
                    player.y = p.y - player.h - 0.01;
                    player.vy = 0;
                    player.onGround = true;
                } else if (old.y >= p.bottom()) { // hit from below
                    player.y = p.bottom() + 0.01;
                    player.vy = 0;
                }
            }
        }

        // Screen bounds
        if (player.x < 0) player.x = 0;
        if (player.right() > W) player.x = W - player.w;

        // Fall death
        if (player.y > H + 200) {
            loseLifeAndRespawn();
        }

        // Coins
        Iterator<Coin> it = coins.iterator();
        while (it.hasNext()) {
            Coin c = it.next();
            if (player.bounds().intersects(c.bounds())) {
                hud.score += 100;
                it.remove();
            }
        }

        // Enemies
        for (Enemy en : enemies) {
            en.update(dt, platforms);
            if (player.bounds().intersects(en.bounds())) {
                // If hitting enemy from above while falling → stomp
                if (player.vy > 0 && player.bottom() - en.y < 12) {
                    hud.score += 200;
                    en.alive = false;
                    player.vy = -JUMP_POWER * 0.7; // bounce
                } else {
                    loseLifeAndRespawn();
                    break;
                }
            }
        }
        enemies.removeIf(e -> !e.alive);

        // Goal reach
        if (goal != null && player.bounds().intersects(goal.bounds())) {
            state = State.LEVEL_COMPLETE;
            hud.levelTimeStop = true;
        }

        // Win by collecting all coins also opens the goal (visual only here)
    }

    void loseLifeAndRespawn() {
        hud.lives--;
        if (hud.lives <= 0) {
            state = State.GAME_OVER;
            return;
        }
        player.x = player.spawnX; player.y = player.spawnY; player.vx = 0; player.vy = 0; player.onGround = false;
    }

    // ---------- INPUT ----------
    boolean left, right, jump;
    @Override public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT: case KeyEvent.VK_A: left = true; break;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_D: right = true; break;
            case KeyEvent.VK_UP: case KeyEvent.VK_W: case KeyEvent.VK_SPACE:
                jump = true; break;
            case KeyEvent.VK_P: if (state == State.PLAYING) state = State.PAUSED; else if (state == State.PAUSED) state = State.PLAYING; break;
            case KeyEvent.VK_ENTER:
                if (state == State.MENU) { state = State.PLAYING; hud.levelTimeStop = false; }
                else if (state == State.LEVEL_COMPLETE) {
                    if (levelIndex + 1 < levels.size()) loadLevel(levelIndex + 1); else { state = State.GAME_OVER; }
                }
                else if (state == State.GAME_OVER) { loadLevel(0); hud.resetAll(); state = State.MENU; }
                break;
        }
    }
    @Override public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT: case KeyEvent.VK_A: left = false; break;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_D: right = false; break;
            case KeyEvent.VK_UP: case KeyEvent.VK_W: case KeyEvent.VK_SPACE: jump = false; break;
        }
    }
    @Override public void keyTyped(KeyEvent e) {}

    // ---------- DRAW ----------
    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Parallax background stripes
        g.setColor(new Color(30, 34, 46));
        for (int i = 0; i < W; i += 40) g.fillRect(i, 0, 20, H);

        // Title screens & overlays
        if (state == State.MENU) drawTitle(g, levels.get(levelIndex).name);

        // World
        drawWorld(g);

        // HUD always on top
        drawHUD(g);

        if (state == State.PAUSED) overlay(g, "PAUSED — press P to resume");
        if (state == State.LEVEL_COMPLETE) overlay(g, "LEVEL COMPLETE! Press Enter");
        if (state == State.GAME_OVER) overlay(g, "GAME OVER — Press Enter for New Game");
    }

    void drawTitle(Graphics2D g, String levelName) {
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 28));
        g.setColor(new Color(220, 230, 255));
        centerText(g, "JUMPMAN", H/2 - 80, 2.0);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        centerText(g, "Level: " + levelName, H/2 - 40, 1.0);
        centerText(g, "← → to move, Space/↑ to jump", H/2, 1.0);
        centerText(g, "P to pause — Press Enter to start", H/2 + 30, 1.0);
    }

    void drawWorld(Graphics2D g) {
        // Platforms
        for (Platform p : platforms) {
            p.draw(g);
        }
        // Coins
        for (Coin c : coins) c.draw(g);
        // Goal
        if (goal != null) goal.draw(g, coins.isEmpty());
        // Enemies
        for (Enemy en : enemies) en.draw(g);
        // Player
        player.draw(g);
    }

    void drawHUD(Graphics2D g) {
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g.setColor(new Color(240, 240, 255));
        g.drawString("Score: " + hud.score, 12, 20);
        g.drawString("Lives: " + hud.lives, 12, 38);
        g.drawString(String.format("Time: %.1fs", hud.levelTime), 12, 56);
        g.drawString("Level " + (levelIndex+1) + "/" + levels.size(), W-140, 20);
    }

    void overlay(Graphics2D g, String text) {
        g.setColor(new Color(0,0,0,120));
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(230, 240, 255));
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
        centerText(g, text, H/2, 1.0);
    }

    void centerText(Graphics2D g, String s, int y, double scale) {
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(s);
        g.drawString(s, (W - tw) / 2, y);
    }
}

// ---------- WORLD & ENTITIES ----------
class Rect {
    double x, y, w, h; // x,y = top-left
    Rect(double x, double y, double w, double h) { this.x=x; this.y=y; this.w=w; this.h=h; }
    double right() { return x + w; }
    double bottom() { return y + h; }
    boolean intersects(Rect r) {
        return x < r.right() && right() > r.x && y < r.bottom() && bottom() > r.y;
    }
}

class Player extends Rect {
    double vx = 0, vy = 0;
    boolean onGround = false;
    double spawnX, spawnY;
    Player(double x, double y, double w, double h) { super(x,y,w,h); }
    Rect bounds() { return new Rect(x, y, w, h); }
    double right() { return x + w; }
    double bottom() { return y + h; }
    boolean intersects(Rect r) { return super.intersects(r); }

    void draw(Graphics2D g) {
        // Retro player: body + visor
        g.setColor(new Color(255, 202, 88));
        g.fillRect((int) x, (int) y, (int) w, (int) h);
        g.setColor(new Color(60, 70, 90));
        g.fillRect((int) x + 4, (int) y + 6, (int) w - 8, 8);
    }
}

class Platform extends Rect {
    Platform(double x, double y, double w, double h) { super(x,y,w,h); }
    void draw(Graphics2D g) {
        g.setColor(new Color(90, 110, 140));
        g.fillRect((int)x, (int)y, (int)w, (int)h);
        g.setColor(new Color(70, 85, 110));
        g.drawRect((int)x, (int)y, (int)w, (int)h);
    }
}

class Coin extends Rect {
    Coin(double x, double y) { super(x, y, 14, 14); }
    Rect bounds() { return new Rect(x, y, w, h); }
    void draw(Graphics2D g) {
        g.setColor(new Color(255, 226, 120));
        g.fillOval((int)x, (int)y, (int)w, (int)h);
        g.setColor(new Color(230, 170, 70));
        g.drawOval((int)x, (int)y, (int)w, (int)h);
    }
}

class Enemy extends Rect {
    double minX, maxX;
    double speed = 80; // px/s
    boolean alive = true;
    Enemy(double x, double y, double minX, double maxX) { super(x, y-20, 26, 20); this.minX=minX; this.maxX=maxX; }
    Rect bounds() { return new Rect(x, y, w, h); }
    void update(double dt, java.util.List<Platform> platforms) {
        x += speed * dt;
        if (x < minX) { x = minX; speed = Math.abs(speed); }
        if (x + w > maxX) { x = maxX - w; speed = -Math.abs(speed); }
    }
    void draw(Graphics2D g) {
        g.setColor(new Color(255, 96, 96));
        g.fillRect((int)x, (int)y, (int)w, (int)h);
        g.setColor(new Color(120, 30, 30));
        g.drawRect((int)x, (int)y, (int)w, (int)h);
    }
}

class Goal extends Rect {
    Goal(double x, double y, double w, double h) { super(x,y,w,h); }
    Rect bounds() { return new Rect(x, y, w, h); }
    void draw(Graphics2D g, boolean open) {
        // A simple flag pole; opens (bright) once all coins are collected
        g.setColor(new Color(190, 190, 210));
        g.fillRect((int)x, (int)y, 4, (int)h);
        g.setColor(open ? new Color(120, 240, 160) : new Color(140, 160, 180));
        g.fillRect((int)x + 4, (int)y, (int)w - 4, 20);
    }
}

class Level {
    String name;
    int W, H;
    java.util.List<Platform> platforms = new ArrayList<>();
    java.util.List<Coin> coins = new ArrayList<>();
    java.util.List<Enemy> enemies = new ArrayList<>();
    Goal goal;
    Level(String name, int W, int H) { this.name=name; this.W=W; this.H=H; }
}

class HUD {
    int score = 0;
    int lives = 3;
    double levelTime = 0;
    boolean levelTimeStop = true;
    void update(double dt) { if (!levelTimeStop) levelTime += dt; }
    void resetTimer() { levelTime = 0; levelTimeStop = true; }
    void resetAll() { score = 0; lives = 3; levelTime = 0; levelTimeStop = true; }
}
