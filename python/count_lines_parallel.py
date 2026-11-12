"""Parallelisierte Variante von count_lines_in_files.py mit Threads.

Aufruf: python count_lines_parallel.py <Ordner> <Regex> [--threads N]
"""
import os
import re
import argparse
import time
from concurrent.futures import ThreadPoolExecutor


def count_lines(file_path):
    # unverändert aus count_lines_in_files.py übernommen
    with open(file_path, 'r', encoding='utf-8', errors='ignore') as file:
        line_count = sum(1 for line in file)
    return line_count


def collect_files(folder_path, regex_str):
    """Sammelt alle passenden Dateien (gleicher Filter wie im Original)."""
    regex = re.compile(regex_str)
    result = []
    for root, dirs, files in os.walk(folder_path):
        for file_name in files:
            if regex.match(file_name):
                result.append(os.path.join(root, file_name))
    return result


def count_lines_in_all_files(folder_path, regex_str, threads):
    files = collect_files(folder_path, regex_str)

    def worker(thread_id):
        # Thread i zählt die Dateien i, i+threads, i+2*threads, ...
        return sum(count_lines(f) for f in files[thread_id::threads])

    with ThreadPoolExecutor(max_workers=threads) as pool:
        # map liefert die Teilsummen der einzelnen Threads
        return sum(pool.map(worker, range(threads)))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description='Count lines in all files matching regex in a directory tree (multithreaded).'
    )
    parser.add_argument('folder_path', help='Path to the folder')
    parser.add_argument('regex_str', help='Regular expression string to match files')
    parser.add_argument('--threads', type=int, default=os.cpu_count(),
                        help='Number of threads (default: number of CPU cores)')

    args = parser.parse_args()

    start_time = time.perf_counter()

    total_lines = count_lines_in_all_files(args.folder_path, args.regex_str, args.threads)

    end_time = time.perf_counter()

    print(f'Threads: {args.threads}')
    print(f'Total number of lines: {total_lines}')
    print(f'Time taken: {(end_time - start_time) * 1000:.3f} ms')
