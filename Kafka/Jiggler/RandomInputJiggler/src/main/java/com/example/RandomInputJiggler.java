package com.example;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.security.SecureRandom;
import java.time.Duration;

public class RandomInputJiggler {

    // ---- Config (edit or pass via args) ----
    private static long minDelayMs = 450;     // min sleep between actions
    private static long maxDelayMs = 2200;    // max sleep between actions
    private static long runForMs   = Duration.ofMinutes(10).toMillis(); // total runtime; 0 = forever

    private static boolean allowCmdTab = true;
    private static boolean allowClicks = false;
    private static boolean allowTab = false;

    // "Swipe" options
    private static boolean allowSwipeLikeScroll = true; // swipe-like bursts of scroll
    private static boolean allowSpaceSwipes = true;     // Ctrl+Left/Right (switch Spaces)
    private static boolean allowMissionControl = true;  // Ctrl+Up/Down (Mission Control / App Exposé)

    private static final SecureRandom rnd = new SecureRandom();

    public static void main(String[] args) throws Exception {
        parseArgs(args);

        System.out.println("RandomInputJiggler starting.");
        System.out.println("Failsafe: move mouse to TOP-LEFT corner (0,0) to stop.");
        System.out.println("Runtime: " + (runForMs == 0 ? "forever" : (runForMs + " ms")));
        System.out.println("Delays:  " + minDelayMs + " .. " + maxDelayMs + " ms");
        System.out.println("allowCmdTab=" + allowCmdTab +
                ", allowClicks=" + allowClicks +
                ", allowSwipeLikeScroll=" + allowSwipeLikeScroll +
                ", allowSpaceSwipes=" + allowSpaceSwipes +
                ", allowMissionControl=" + allowMissionControl);

        Robot robot = new Robot();
        robot.setAutoDelay(5);
        robot.setAutoWaitForIdle(false);

        long start = System.currentTimeMillis();

        // Small initial pause so you can switch away from the terminal
        sleep(800);

        while (true) {
            if (isFailsafeTriggered()) {
                System.out.println("Failsafe triggered. Stopping.");
                break;
            }
            if (runForMs > 0 && System.currentTimeMillis() - start >= runForMs) {
                System.out.println("Time elapsed. Stopping.");
                break;
            }

            doRandomAction(robot);
            sleep(randomBetween(minDelayMs, maxDelayMs));
        }

        System.out.println("Done.");
    }

    private static void doRandomAction(Robot robot) {
        Action action = pickAction();
        try {
            switch (action) {
                case MOUSE_MOVE -> randomMouseMove(robot);
                case SCROLL -> randomScroll(robot);
                case SWIPE_LIKE_SCROLL -> {
                    if (allowSwipeLikeScroll) smoothSwipeLikeScroll(robot);
                }
                case ARROWS -> randomArrows(robot);
                case TAB -> {
                    if (allowTab)
                        pressKey(robot, KeyEvent.VK_TAB, 1 + rnd.nextInt(3));
                }
                case CMD_TAB -> {
                    if (allowCmdTab) cmdTab(robot, 1 + rnd.nextInt(2));
                }
                case SPACE_SWIPE -> {
                    if (allowSpaceSwipes) switchSpace(robot);
                }
                case MISSION_CONTROL -> {
                    if (allowMissionControl) missionControlOrExpose(robot);
                }
                case CLICK -> {
                    if (allowClicks) click(robot);
                }
            }
        } catch (Exception e) {
            System.err.println("Action failed: " + action + " : " + e.getMessage());
        }
    }

    // ---- Actions ----

    private static void randomMouseMove(Robot robot) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Point p = MouseInfo.getPointerInfo().getLocation();

        // Move by a small random delta (more "human" than teleporting)
        int dx = rnd.nextInt(401) - 200; // -200..200
        int dy = rnd.nextInt(401) - 200;

        int x = clamp(p.x + dx, 0, screen.width - 1);
        int y = clamp(p.y + dy, 0, screen.height - 1);

        robot.mouseMove(x, y);
    }

    private static void randomScroll(Robot robot) {
        // Positive = scroll down, Negative = scroll up
        int amount = (rnd.nextBoolean() ? 1 : -1) * (1 + rnd.nextInt(8));
        robot.mouseWheel(amount);
    }

    /**
     * "Swipe-like" behavior using a burst of small scroll steps.
     * Works in most apps to move content like a swipe would.
     */
    private static void smoothSwipeLikeScroll(Robot robot) {
        int direction = rnd.nextBoolean() ? 1 : -1; // 1 down, -1 up
        int steps = 10 + rnd.nextInt(26);           // length of the "swipe"
        for (int i = 0; i < steps; i++) {
            robot.mouseWheel(direction);            // small increments feel more swipe-like
            sleep(10 + rnd.nextInt(26));
        }

        // optional tiny drift to look less robotic
        if (rnd.nextInt(4) == 0) {
            randomMouseMove(robot);
        }
    }

    private static void randomArrows(Robot robot) {
        int[] keys = { KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN };
        int times = 1 + rnd.nextInt(4);
        for (int i = 0; i < times; i++) {
            int key = keys[rnd.nextInt(keys.length)];
            pressKey(robot, key, 1);
            sleep(40 + rnd.nextInt(90));
        }
    }

    private static void cmdTab(Robot robot, int cycles) {
        // macOS app switcher: CMD + TAB
        robot.keyPress(KeyEvent.VK_META);
        for (int i = 0; i < cycles; i++) {
            tap(robot, KeyEvent.VK_TAB);
            sleep(80 + rnd.nextInt(120));
        }
        robot.keyRelease(KeyEvent.VK_META);
    }

    /**
     * Switch Spaces (Desktop) like a 3/4-finger swipe would, using keyboard equivalent:
     * Ctrl + Left/Right Arrow (works if enabled in System Settings > Keyboard/Trackpad shortcuts).
     */
    private static void switchSpace(Robot robot) {
        int arrow = rnd.nextBoolean() ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
        ctrlCombo(robot, arrow, 1);
    }

    /**
     * Mission Control / App Exposé equivalents:
     * Ctrl + Up (Mission Control), Ctrl + Down (App Exposé) depending on your macOS settings.
     */
    private static void missionControlOrExpose(Robot robot) {
        int key = rnd.nextBoolean() ? KeyEvent.VK_UP : KeyEvent.VK_DOWN;
        ctrlCombo(robot, key, 1);
    }

    private static void ctrlCombo(Robot robot, int keyCode, int times) {
        robot.keyPress(KeyEvent.VK_CONTROL);
        for (int i = 0; i < times; i++) {
            tap(robot, keyCode);
            sleep(60 + rnd.nextInt(120));
        }
        robot.keyRelease(KeyEvent.VK_CONTROL);
    }

    private static void click(Robot robot) {
        // Small chance right-click; mostly left-click
        boolean right = rnd.nextInt(12) == 0;
        int mask = right ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;

        robot.mousePress(mask);
        sleep(25 + rnd.nextInt(90));
        robot.mouseRelease(mask);
    }

    // ---- Helpers ----

    private enum Action {
        MOUSE_MOVE,
        SCROLL,
        SWIPE_LIKE_SCROLL,
        ARROWS,
        TAB,
        CMD_TAB,
        SPACE_SWIPE,
        MISSION_CONTROL,
        CLICK
    }

    private static Action pickAction() {
        // Weighted choice (tweak these to taste).
        // More mouse/scroll; fewer disruptive actions.
        int roll = rnd.nextInt(100);

        if (roll < 28) return Action.MOUSE_MOVE;         // 28%
        if (roll < 45) return Action.SCROLL;             // 17%
        if (roll < 60) return Action.SWIPE_LIKE_SCROLL;  // 15%
        if (roll < 72) return Action.ARROWS;             // 12%
        if (roll < 82) return Action.TAB;                // 10%
        if (roll < 88) return Action.SPACE_SWIPE;        // 6%
        if (roll < 92) return Action.MISSION_CONTROL;    // 4%
        if (roll < 97) return Action.CMD_TAB;            // 5%
        return Action.CLICK;                              // 3%
    }

    private static boolean isFailsafeTriggered() {
        Point p = MouseInfo.getPointerInfo().getLocation();
        return p.x <= 0 && p.y <= 0;
    }

    private static void pressKey(Robot robot, int keyCode, int times) {
        for (int i = 0; i < times; i++) {
            tap(robot, keyCode);
            sleep(40 + rnd.nextInt(90));
        }
    }

    private static void tap(Robot robot, int keyCode) {
        robot.keyPress(keyCode);
        sleep(10 + rnd.nextInt(30));
        robot.keyRelease(keyCode);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private static long randomBetween(long min, long max) {
        if (max <= min) return min;
        long bound = (max - min) + 1;
        long r = (long) (rnd.nextDouble() * bound);
        return min + r;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static void parseArgs(String[] args) {
        // Example:
        //   --minDelayMs=300 --maxDelayMs=2500 --minutes=30 --forever
        //   --noCmdTab --noClicks --noSwipeLikeScroll --noSpaceSwipes --noMissionControl
        for (String a : args) {
            if (a.startsWith("--minDelayMs=")) minDelayMs = Long.parseLong(a.substring(a.indexOf('=') + 1));
            else if (a.startsWith("--maxDelayMs=")) maxDelayMs = Long.parseLong(a.substring(a.indexOf('=') + 1));
            else if (a.startsWith("--minutes=")) runForMs = Duration.ofMinutes(Long.parseLong(a.substring(a.indexOf('=') + 1))).toMillis();
            else if (a.equals("--forever")) runForMs = 0;

            else if (a.equals("--noCmdTab")) allowCmdTab = false;
            else if (a.equals("--noClicks")) allowClicks = false;

            else if (a.equals("--noSwipeLikeScroll")) allowSwipeLikeScroll = false;
            else if (a.equals("--noSpaceSwipes")) allowSpaceSwipes = false;
            else if (a.equals("--noMissionControl")) allowMissionControl = false;
        }
        if (minDelayMs < 0) minDelayMs = 0;
        if (maxDelayMs < minDelayMs) maxDelayMs = minDelayMs;
    }
}