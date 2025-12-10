import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class CyberDefenseSystem {

    private static final int DATA_SIZE = 100_000;
    private static final int SEGMENTS = 10;
    private static final int THREADS = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        long[] data = generateData();

        System.out.println("=== Этап 1: Ручные потоки ===");
        long t1 = System.nanoTime();
        long sum1 = runWithRawThreads(data);
        long time1 = (System.nanoTime() - t1) / 1_000_000;
        System.out.printf("⏱ Время(ручные потоки): %d мс%n%n", time1);

        System.out.println("=== Этап 2–3–4: ExecutorService + AtomicLong ===");
        t1 = System.nanoTime();
        Result res2 = runWithExecutorService(data, false);
        long time2 = (System.nanoTime() - t1) / 1_000_000;
        System.out.printf("[Центральный ИИ] Общая сумма = %d%n", res2.sum);
        System.out.printf("[Центральный ИИ] Найдено аномалий = %d%n", res2.anomalies);
        System.out.printf("⏱ Время(ExecutorService): %d мс%n%n", time2);

        System.out.println("=== Этап 5: CompletionService ===");
        t1 = System.nanoTime();
        Result res5 = runWithCompletionService(data);
        long time5 = (System.nanoTime() - t1) / 1_000_000;
        System.out.printf("[Центральный ИИ] Итоговая сумма = %d%n", res5.sum);
        System.out.printf("[Центральный ИИ] Общие аномалии = %d%n", res5.anomalies);
        System.out.printf("⏱ Время(CompletionService): %d мс%n%n", time5);

        System.out.println("=== Этап 6: CyclicBarrier (2 фазы) ===");
        runWithCyclicBarrier(data);
    }

    private static long[] generateData() {
        long[] data = new long[DATA_SIZE];
        Random r = new Random(42);
        for (int i = 0; i < data.length; i++) {
            data[i] = r.nextInt(10_000);
        }
        return data;
    }

    // ручные потоки + synchronized
    private static long runWithRawThreads(long[] data) throws InterruptedException {
        int segmentSize = DATA_SIZE / SEGMENTS;
        Thread[] threads = new Thread[SEGMENTS];
        final long[] partialSums = new long[SEGMENTS];
        final Object lock = new Object();
        final AtomicLong total = new AtomicLong(0);

        for (int i = 0; i < SEGMENTS; i++) {
            final int id = i + 1;
            final int from = i * segmentSize;
            final int to = (i == SEGMENTS - 1) ? DATA_SIZE : (i + 1) * segmentSize;

            threads[i] = new Thread(() -> {
                long sum = 0;
                for (int j = from; j < to; j++) sum += data[j];
                partialSums[id - 1] = sum;

                synchronized (lock) {
                    total.addAndGet(sum);
                    System.out.printf("[Детектор-%d] Сектор %d-%d просканирован. Сумма = %d%n",
                            id, from, to - 1, sum);
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) t.join();
        System.out.printf("[Центральный ИИ] Общая сумма данных = %d%n", total.get());
        return total.get();
    }

    // ExecutorService + Callable<Result>
    private static Result runWithExecutorService(long[] data, boolean withBarrier) throws InterruptedException, ExecutionException {
        int segmentSize = DATA_SIZE / THREADS;
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<Future<Result>> futures = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            int id = i + 1;
            int from = i * segmentSize;
            int to = (i == THREADS - 1) ? DATA_SIZE : (i + 1) * segmentSize;
            CyclicBarrier barrier = withBarrier ? new CyclicBarrier(THREADS) : null;
            Callable<Result> task = new Detector(id, data, from, to, barrier);
            futures.add(executor.submit(task));
        }

        long totalSum = 0;
        int totalAnomalies = 0;
        for (Future<Result> f : futures) {
            Result r = f.get();
            totalSum += r.sum;
            totalAnomalies += r.anomalies;
        }

        executor.shutdown();
        return new Result(totalSum, totalAnomalies);
    }

    // CompletionService — обработка по мере готовности
    private static Result runWithCompletionService(long[] data) throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        ExecutorCompletionService<Result> ecs = new ExecutorCompletionService<>(executor);

        int segmentSize = DATA_SIZE / THREADS;
        for (int i = 0; i < THREADS; i++) {
            int id = i + 1;
            int from = i * segmentSize;
            int to = (i == THREADS - 1) ? DATA_SIZE : (i + 1) * segmentSize;
            ecs.submit(new Detector(id, data, from, to, null));
        }

        long totalSum = 0;
        int totalAnomalies = 0;
        for (int i = 0; i < THREADS; i++) {
            Future<Result> future = ecs.take(); // блокируется до первого завершившегося
            Result r = future.get();
            totalSum += r.sum;
            totalAnomalies += r.anomalies;
            System.out.printf("[Центральный ИИ] Получен отчёт: сумма = %d, аномалий = %d%n",
                    r.sum, r.anomalies);
        }

        executor.shutdown();
        return new Result(totalSum, totalAnomalies);
    }

    // CyclicBarrier — 2 фазы сканирования
    private static void runWithCyclicBarrier(long[] data) throws InterruptedException {
        int segmentSize = DATA_SIZE / THREADS;
        CyclicBarrier barrier = new CyclicBarrier(THREADS, () ->
                System.out.println("[ИИ-координатор] Все детекторы завершили фазу. Переход к следующей.")
        );

        Thread[] threads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            int id = i + 1;
            int from = i * segmentSize;
            int to = (i == THREADS - 1) ? DATA_SIZE : (i + 1) * segmentSize;

            threads[i] = new Thread(() -> {
                try {
                    // Фаза 1
                    long sum1 = 0;
                    for (int j = from; j < (from + (to - from) / 2); j++) sum1 += data[j];
                    System.out.printf("[Детектор-%d] Фаза 1 завершена. Сумма = %d%n", id, sum1);

                    barrier.await();

                    // Фаза 2
                    long sum2 = 0;
                    for (int j = from + (to - from) / 2; j < to; j++) sum2 += data[j];
                    System.out.printf("[Детектор-%d] Фаза 2 завершена. Сумма = %d%n", id, sum2);

                    barrier.await();
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) t.join();
    }
}
