import java.util.Arrays;

/**
 * Misst ParallelCounter mit verschiedenen Thread-Anzahlen innerhalb EINER JVM.
 *
 * Aufruf: java Benchmark <Ordner> <Regex> [Wiederholungen]
 *
 * Vor den Messungen gibt es Aufwärmläufe, damit der JIT-Compiler den Code
 * bereits übersetzt hat und die Dateien im Page-Cache des Betriebssystems
 * liegen. Pro Thread-Anzahl wird der Median aus mehreren Läufen ausgegeben.
 */
public class Benchmark {

	public static void main(String[] args) throws Exception {
		String folder = args[0];
		String regex = args[1];
		int reps = args.length >= 3 ? Integer.parseInt(args[2]) : 5;
		int[] threadCounts = { 1, 2, 4, 8, 16, 32, 64 };

		System.out.println("Kerne laut JVM: "
				+ Runtime.getRuntime().availableProcessors());

		// Aufwärmen
		for (int i = 0; i < 3; i++) {
			ParallelCounter.countLinesInAllFiles(folder, regex, 2);
		}

		double base = 0;
		System.out.println("Threads | Median [ms] | Speedup");
		for (int t : threadCounts) {
			double[] times = new double[reps];
			long lines = 0;
			for (int r = 0; r < reps; r++) {
				long start = System.nanoTime();
				lines = ParallelCounter.countLinesInAllFiles(folder, regex, t);
				times[r] = (System.nanoTime() - start) / 1_000_000.0;
			}
			Arrays.sort(times);
			double median = times[reps / 2];
			if (t == 1) {
				base = median;
			}
			System.out.printf("%7d | %11.0f | %7.2f   (Zeilen: %d)%n", t,
					median, base / median, lines);
		}
	}
}
