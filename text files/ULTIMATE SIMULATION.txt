import java.util.*;

public class Main {

    private static final Map<Character, String> CONTROLS =
            new LinkedHashMap<>();

    static {
        CONTROLS.put('W', "Move Up");
        CONTROLS.put('A', "Move Left");
        CONTROLS.put('S', "Move Down");
        CONTROLS.put('D', "Move Right");
        CONTROLS.put('J', "Jump");
        CONTROLS.put('R', "Run");
        CONTROLS.put('Q', "Dodge");
        CONTROLS.put('F', "Block");
    }

    static class Movement {
        char key;
        String action;
        long timestampMs;

        Movement(char key, String action, long timestampMs) {
            this.key = key;
            this.action = action;
            this.timestampMs = timestampMs;
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        Deque<Movement> movementStack = new ArrayDeque<>();
        List<Movement> recording = new ArrayList<>();

        long startTime = System.nanoTime();

        System.out.println("=== Java Movement Recorder ===");
        System.out.println("W = Up | A = Left | S = Down | D = Right");
        System.out.println("J = Jump | R = Run | Q = Dodge | F = Block");
        System.out.println("Enter sequences like WASSSA or WASSDA.");
        System.out.println("Commands: PLAY, UNDO, STOP");
        System.out.println("Note: J represents Jump and R represents Run.");
        System.out.println();

        while (scanner.hasNextLine()) {
            System.out.print("Sequence: ");

            String input = scanner.nextLine()
                    .trim()
                    .toUpperCase(Locale.ROOT);

            if (input.equals("STOP")) {
                break;
            }

            if (input.equals("PLAY")) {
                playRecording(recording);
                continue;
            }

            if (input.equals("UNDO")) {
                if (!movementStack.isEmpty()) {
                    Movement removed = movementStack.pop();
                    recording.remove(removed);

                    System.out.println(
                            "Removed: " + removed.key
                                    + " -> " + removed.action
                    );
                } else {
                    System.out.println("Nothing to undo.");
                }
                continue;
            }

            if (input.isEmpty()) {
                continue;
            }

            // Validate the entire sequence before recording it.
            boolean valid = true;

            for (char key : input.toCharArray()) {
                if (!CONTROLS.containsKey(key)) {
                    System.out.println(
                            "Unknown key: " + key
                                    + ". Use W, A, S, D, J, R, Q, F."
                    );
                    valid = false;
                    break;
                }
            }

            if (!valid) {
                continue;
            }

            // Record each character separately, in order.
            for (char key : input.toCharArray()) {
                long elapsedMs =
                        (System.nanoTime() - startTime) / 1_000_000;

                Movement movement = new Movement(
                        key,
                        CONTROLS.get(key),
                        elapsedMs
                );

                movementStack.push(movement);
                recording.add(movement);

                System.out.println(
                        "Recorded: " + elapsedMs + " ms | "
                                + key + " -> " + movement.action
                );
            }
        }

        System.out.println("\n=== Final Recording ===");
        displayRecording(recording);

        System.out.println(
                "\nTotal movements: " + recording.size()
        );

        scanner.close();
    }

    private static void playRecording(List<Movement> recording) {
        if (recording.isEmpty()) {
            System.out.println("Nothing to play back.");
            return;
        }

        System.out.println("\n=== Playback Simulation ===");

        long previous = recording.get(0).timestampMs;

        for (int i = 0; i < recording.size(); i++) {
            Movement movement = recording.get(i);

            long delay = movement.timestampMs - previous;

            try {
                if (i > 0 && delay > 0) {
                    Thread.sleep(Math.min(delay, 3000));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Playback interrupted.");
                return;
            }

            System.out.println(
                    "Step " + (i + 1)
                            + " | +" + Math.max(0, delay) + " ms"
                            + " | " + movement.key
                            + " -> " + movement.action
            );

            previous = movement.timestampMs;
        }

        System.out.println("=== Playback Complete ===\n");
    }

    private static void displayRecording(List<Movement> recording) {
        for (int i = 0; i < recording.size(); i++) {
            Movement movement = recording.get(i);

            System.out.println(
                    (i + 1) + ". "
                            + movement.timestampMs + " ms | "
                            + movement.key + " -> " + movement.action
            );
        }
    }
}