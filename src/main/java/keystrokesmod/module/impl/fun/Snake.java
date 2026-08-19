package keystrokesmod.module.impl.fun;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public class Snake extends Module {

    private static final int GRID = 20;
    private static final int CELL = 10;
    private static final int BOARD = GRID * CELL;

    private final SliderSetting speed;

    private final List<int[]> snake = new ArrayList<>();
    private final Deque<int[]> pending = new ArrayDeque<>();
    private final Random random = new Random();

    private int foodX;
    private int foodY;
    private int dirX = 1;
    private int dirY = 0;
    private int score;
    private boolean running;
    private boolean gameOver;
    private long lastMove;

    public Snake() {
        super("Snake", category.fun, 0);
        this.registerSetting(speed = new SliderSetting("Speed", 5.0, 1.0, 10.0, 1.0));
    }

    @Override
    public void onEnable() {
        resetGame();
    }

    @Override
    public void onUpdate() {
        pollInput();
        if (!running || gameOver) {
            return;
        }
        long interval = (long) (1000.0 / speed.getInput());
        long now = System.currentTimeMillis();
        if (now - lastMove >= interval) {
            lastMove = now;
            advance();
        }
    }

    @SubscribeEvent
    public void onRenderTick(RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }
        ScaledResolution res = new ScaledResolution(mc);
        int x = (res.getScaledWidth() - BOARD) / 2;
        int y = (res.getScaledHeight() - BOARD) / 2;

        this.mc.fontRendererObj.drawStringWithShadow("Snake - Score: " + score, x, y - 12, 0xFFFFFF);

        Gui.drawRect(x - 1, y - 1, x + BOARD + 1, y + BOARD + 1, 0xFF333333);
        Gui.drawRect(x, y, x + BOARD, y + BOARD, 0xFF111111);

        for (int i = 0; i < snake.size(); i++) {
            int[] part = snake.get(i);
            int color = i == 0 ? 0xFF55FF55 : 0xFF2E9E4F;
            Gui.drawRect(x + part[0] * CELL, y + part[1] * CELL,
                    x + part[0] * CELL + CELL, y + part[1] * CELL + CELL, color);
        }

        Gui.drawRect(x + foodX * CELL, y + foodY * CELL,
                x + foodX * CELL + CELL, y + foodY * CELL + CELL, 0xFFFF4444);

        if (gameOver) {
            this.mc.fontRendererObj.drawStringWithShadow("Game Over - press a key to restart",
                    x + BOARD / 2 - this.mc.fontRendererObj.getStringWidth("Game Over - press a key to restart") / 2,
                    y + BOARD / 2 - 4, 0xFF5555);
        }
    }

    private void pollInput() {
        int[] pressed = null;
        if (Keyboard.isKeyDown(Keyboard.KEY_UP) || Keyboard.isKeyDown(Keyboard.KEY_W)) {
            pressed = new int[]{0, -1};
        } else if (Keyboard.isKeyDown(Keyboard.KEY_DOWN) || Keyboard.isKeyDown(Keyboard.KEY_S)) {
            pressed = new int[]{0, 1};
        } else if (Keyboard.isKeyDown(Keyboard.KEY_LEFT) || Keyboard.isKeyDown(Keyboard.KEY_A)) {
            pressed = new int[]{-1, 0};
        } else if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT) || Keyboard.isKeyDown(Keyboard.KEY_D)) {
            pressed = new int[]{1, 0};
        }
        if (pressed != null) {
            if (gameOver) {
                resetGame();
                return;
            }
            int[] last = pending.isEmpty() ? new int[]{dirX, dirY} : pending.peekLast();
            if (pressed[0] == -last[0] && pressed[1] == -last[1]) {
                return;
            }
            if (pending.size() < 2 && (pressed[0] != last[0] || pressed[1] != last[1])) {
                pending.add(pressed);
            }
        }
    }

    private void advance() {
        if (!pending.isEmpty()) {
            int[] next = pending.poll();
            dirX = next[0];
            dirY = next[1];
        }
        int headX = snake.get(0)[0] + dirX;
        int headY = snake.get(0)[1] + dirY;
        if (headX < 0 || headY < 0 || headX >= GRID || headY >= GRID || isOnSnake(headX, headY)) {
            gameOver = true;
            return;
        }
        snake.add(0, new int[]{headX, headY});
        if (headX == foodX && headY == foodY) {
            score++;
            spawnFood();
        } else {
            snake.remove(snake.size() - 1);
        }
    }

    private boolean isOnSnake(int x, int y) {
        for (int[] part : snake) {
            if (part[0] == x && part[1] == y) {
                return true;
            }
        }
        return false;
    }

    private void spawnFood() {
        List<int[]> free = new ArrayList<>();
        for (int x = 0; x < GRID; x++) {
            for (int y = 0; y < GRID; y++) {
                if (!isOnSnake(x, y)) {
                    free.add(new int[]{x, y});
                }
            }
        }
        if (free.isEmpty()) {
            gameOver = true;
            return;
        }
        int[] cell = free.get(random.nextInt(free.size()));
        foodX = cell[0];
        foodY = cell[1];
    }

    private void resetGame() {
        snake.clear();
        snake.add(new int[]{GRID / 2, GRID / 2});
        snake.add(new int[]{GRID / 2 - 1, GRID / 2});
        snake.add(new int[]{GRID / 2 - 2, GRID / 2});
        dirX = 1;
        dirY = 0;
        pending.clear();
        score = 0;
        running = true;
        gameOver = false;
        lastMove = System.currentTimeMillis();
        spawnFood();
    }
}