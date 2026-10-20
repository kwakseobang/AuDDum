package org.kwakmunsu.auddum.vote;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 같은 순간에 출발하는 스레드 경합 도우미. 모든 스레드가 준비되면 한꺼번에 시작시키고 결과(값 또는 예외)를 모은다.
 */
public final class ConcurrentRace {

    private static final long TIMEOUT_SECONDS = 60;

    private ConcurrentRace() {
    }

    /** 한 스레드의 결과. 성공이면 value, 예외로 끝났으면 error가 채워진다. */
    public record Outcome<T>(T value, Throwable error) {

        public boolean succeeded() {
            return error == null;
        }

    }

    /** 작업들을 각자 스레드에서 동시에 출발시켜 모든 결과를 기다린다. */
    public static <T> List<Outcome<T>> run(List<Callable<T>> tasks) throws Exception {
        var executor = Executors.newFixedThreadPool(tasks.size());
        var ready = new CountDownLatch(tasks.size());
        var start = new CountDownLatch(1);
        try {
            List<Future<Outcome<T>>> futures = new ArrayList<>();
            for (var task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return new Outcome<>(task.call(), null);
                    } catch (Throwable e) {
                        return new Outcome<>(null, e);
                    }
                }));
            }
            if (!ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("스레드가 제때 준비되지 않았다");
            }
            start.countDown();

            List<Outcome<T>> outcomes = new ArrayList<>();
            for (var future : futures) {
                outcomes.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }

            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

}
