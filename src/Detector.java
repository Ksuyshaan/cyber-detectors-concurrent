import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;

public class Detector implements Runnable, Callable<Result> {
    private final int id;
    private final long[] data;
    private final int from;
    private final int to;
    private final CyclicBarrier barrier;

    public Detector(int id, long[] data, int from, int to, CyclicBarrier barrier) {
        this.id = id;
        this.data = data;
        this.from = from;
        this.to = to;
        this.barrier = barrier;
    }

    @Override
    public void run() {
        long sum = 0;
        int anomalies = 0;
        for (int i = from; i < to; i++) {
            sum += data[i];
            if (data[i] > 9000) anomalies++;
        }
        System.out.printf("[Детектор-%d] Сектор %d-%d просканирован. Сумма = %d%n", id, from, to - 1, sum);
    }

    @Override
    public Result call() throws InterruptedException {
        long sum = 0;
        int anomalies = 0;
        for (int i = from; i < to; i++) {
            sum += data[i];
            if (data[i] > 9000) anomalies++;
        }

        // координация через CyclicBarrier
        if (barrier != null) {
            try {
                barrier.await();
                barrier.await();
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        }

        return new Result(sum, anomalies);
    }

    public int getId() { return id; }
}
