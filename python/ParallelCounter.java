import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parallelisierte Variante des Line Counters.
 *
 * Aufruf: java ParallelCounter <Ordner> <Regex> [Anzahl Threads]
 * Beispiel: java ParallelCounter ../ ".*\.java" 4
 *
 * Ablauf:
 * 1. Der Verzeichnisbaum wird (sequenziell) durchlaufen und alle passenden
 *    Dateien werden in einer Liste gesammelt.
 * 2. Die Liste wird in so viele Teilstücke aufgeteilt, wie Threads verwendet
 *    werden. Jeder Thread zählt die Zeilen "seiner" Dateien und liefert eine
 *    Teilsumme zurück.
 * 3. Der Haupt-Thread wartet auf alle Teilergebnisse und addiert sie.
 *
 * Da jeder Thread nur auf eine eigene lokale Variable schreibt, ist keine
 * Synchronisation (Lock, synchronized, Atomic...) beim Zählen nötig.
 */
public class ParallelCounter {

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("Aufruf: java ParallelCounter <Ordner> <Regex> [Threads]");
			System.exit(1);
		}
		int threads = args.length >= 3
				? Integer.parseInt(args[2])
				: Runtime.getRuntime().availableProcessors();

		long startTime = System.nanoTime();

		long totalLines = countLinesInAllFiles(args[0], args[1], threads);

		long endTime = System.nanoTime();

		System.out.println("Threads: " + threads);
		System.out.println("Anzahl Zeilen: " + totalLines);
		System.out.println("Benötigte Zeit: "
				+ (endTime - startTime) / 1_000_000.0 + " ms");
	}

	// unverändert aus Counter.java übernommen
	public static long countLines(String fileName) throws IOException {
		try (Stream<String> lines = Files.lines(Paths.get(fileName))) {
			return lines.count();
		}
	}

	public static long countLinesInAllFiles(String folderPath, String regex,
			int threads) throws IOException, InterruptedException,
			ExecutionException {
		Pattern pattern = Pattern.compile(regex);

		// 1. Alle passenden Dateien einsammeln (gleicher Filter wie im Original)
		List<Path> files;
		try (Stream<Path> paths = Files.walk(Paths.get(folderPath))) {
			files = paths
					.filter(Files::isRegularFile)
					.filter(path -> pattern.matcher(path.toString()).matches())
					.collect(Collectors.toList());
		}

		// 2. Arbeit auf einen Thread-Pool fester Größe verteilen
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Future<Long>> results = new ArrayList<>();
			for (int t = 0; t < threads; t++) {
				final int id = t;
				// Thread t bekommt die Dateien mit Index t, t+threads, t+2*threads, ...
				// (verschränkte Aufteilung -> große und kleine Dateien verteilen
				// sich gleichmäßiger als bei zusammenhängenden Blöcken)
				results.add(pool.submit(() -> {
					long sum = 0;
					for (int i = id; i < files.size(); i += threads) {
						try {
							sum += countLines(files.get(i).toString());
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					}
					return sum;
				}));
			}

			// 3. Teilergebnisse einsammeln (get() blockiert, bis der Thread fertig ist)
			long total = 0;
			for (Future<Long> f : results) {
				total += f.get();
			}
			return total;
		} finally {
			pool.shutdown();
		}
	}
}
