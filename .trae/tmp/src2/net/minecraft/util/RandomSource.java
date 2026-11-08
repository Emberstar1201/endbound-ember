package net.minecraft.util;

import io.netty.util.internal.ThreadLocalRandom;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource;

/**
 * A basic interface for random number generation. This mirrors the same methods in {@link java.util.Random}, however it does not make any guarantee that these are thread-safe, unlike {@code Random}.
 * The notable difference is that {@link #setSeed(long)} is not {@code synchronized} and should not be accessed from multiple threads.
 * The documentation for each individual method can be assumed to be otherwise the same as the identically named method in {@link java.util.Random}.
 * @see java.util.Random
 * @see net.minecraft.world.level.levelgen.SimpleRandomSource
 */
public interface RandomSource {
    @Deprecated
    double GAUSSIAN_SPREAD_FACTOR = 2.297;

    static RandomSource create() {
        return create(RandomSupport.generateUniqueSeed());
    }

    @Deprecated
    static RandomSource createThreadSafe() {
        return new ThreadSafeLegacyRandomSource(RandomSupport.generateUniqueSeed());
    }

    static RandomSource create(long seed) {
        return new LegacyRandomSource(seed);
    }

    static RandomSource createNewThreadLocalInstance() {
        return new SingleThreadedRandomSource(ThreadLocalRandom.current().nextLong());
    }

    RandomSource fork();

    PositionalRandomFactory forkPositional();

    void setSeed(long seed);

    int nextInt();

    int nextInt(int bound);

    default int nextIntBetweenInclusive(int min, int max) {
        return this.nextInt(max - min + 1) + min;
    }

    long nextLong();

    boolean nextBoolean();

    float nextFloat();

    double nextDouble();

    double nextGaussian();

    default double triangle(double center, double maxDeviation) {
        return center + maxDeviation * (this.nextDouble() - this.nextDouble());
    }

    default void consumeCount(int count) {
        for (int i = 0; i < count; i++) {
            this.nextInt();
        }
    }

    default int nextInt(int origin, int bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException("bound - origin is non positive");
        } else {
            return origin + this.nextInt(bound - origin);
        }
    }
}
