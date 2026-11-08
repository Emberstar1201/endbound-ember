package net.minecraft;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceImmutableList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SingleKeyCache;
import net.minecraft.util.TimeSource;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;

public class Util {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_MAX_THREADS = 255;
    private static final int DEFAULT_SAFE_FILE_OPERATION_RETRIES = 10;
    private static final String MAX_THREADS_SYSTEM_PROPERTY = "max.bg.threads";
    private static final ExecutorService BACKGROUND_EXECUTOR = makeExecutor("Main");
    private static final ExecutorService IO_POOL = makeIoExecutor("IO-Worker-", false);
    private static final ExecutorService DOWNLOAD_POOL = makeIoExecutor("Download-", true);
    private static final DateTimeFormatter FILENAME_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);
    public static final int LINEAR_LOOKUP_THRESHOLD = 8;
    private static final Set<String> ALLOWED_UNTRUSTED_LINK_PROTOCOLS = Set.of("http", "https");
    public static final long NANOS_PER_MILLI = 1000000L;
    public static TimeSource.NanoTimeSource timeSource = System::nanoTime;
    public static final Ticker TICKER = new Ticker() {
        @Override
        public long read() {
            return Util.timeSource.getAsLong();
        }
    };
    public static final UUID NIL_UUID = new UUID(0L, 0L);
    public static final FileSystemProvider ZIP_FILE_SYSTEM_PROVIDER = FileSystemProvider.installedProviders()
        .stream()
        .filter(p_201865_ -> p_201865_.getScheme().equalsIgnoreCase("jar"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No jar file system provider found"));
    private static Consumer<String> thePauser = p_201905_ -> {
    };

    public static <K, V> Collector<Entry<? extends K, ? extends V>, ?, Map<K, V>> toMap() {
        return Collectors.toMap(Entry::getKey, Entry::getValue);
    }

    public static <T> Collector<T, ?, List<T>> toMutableList() {
        return Collectors.toCollection(Lists::newArrayList);
    }

    public static <T extends Comparable<T>> String getPropertyName(Property<T> property, Object value) {
        return property.getName((T)value);
    }

    public static String makeDescriptionId(String type, @Nullable ResourceLocation id) {
        return id == null
            ? type + ".unregistered_sadface"
            : type + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    public static long getMillis() {
        return getNanos() / 1000000L;
    }

    public static long getNanos() {
        return timeSource.getAsLong();
    }

    public static long getEpochMillis() {
        return Instant.now().toEpochMilli();
    }

    public static String getFilenameFormattedDateTime() {
        return FILENAME_DATE_TIME_FORMATTER.format(ZonedDateTime.now());
    }

    private static ExecutorService makeExecutor(String serviceName) {
        int i = Mth.clamp(Runtime.getRuntime().availableProcessors() - 1, 1, getMaxThreads());
        ExecutorService executorservice;
        if (i <= 0) {
            executorservice = MoreExecutors.newDirectExecutorService();
        } else {
            AtomicInteger atomicinteger = new AtomicInteger(1);
            executorservice = new ForkJoinPool(i, p_314383_ -> {
                ForkJoinWorkerThread forkjoinworkerthread = new ForkJoinWorkerThread(p_314383_) {
                    @Override
                    protected void onTermination(Throwable throwOnTermination) {
                        if (throwOnTermination != null) {
                            Util.LOGGER.warn("{} died", this.getName(), throwOnTermination);
                        } else {
                            Util.LOGGER.debug("{} shutdown", this.getName());
                        }

                        super.onTermination(throwOnTermination);
                    }
                };
                forkjoinworkerthread.setName("Worker-" + serviceName + "-" + atomicinteger.getAndIncrement());
                return forkjoinworkerthread;
            }, Util::onThreadException, true);
        }

        return executorservice;
    }

    private static int getMaxThreads() {
        String s = System.getProperty("max.bg.threads");
        if (s != null) {
            try {
                int i = Integer.parseInt(s);
                if (i >= 1 && i <= 255) {
                    return i;
                }

                LOGGER.error("Wrong {} property value '{}'. Should be an integer value between 1 and {}.", "max.bg.threads", s, 255);
            } catch (NumberFormatException numberformatexception) {
                LOGGER.error("Could not parse {} property value '{}'. Should be an integer value between 1 and {}.", "max.bg.threads", s, 255);
            }
        }

        return 255;
    }

    public static ExecutorService backgroundExecutor() {
        return BACKGROUND_EXECUTOR;
    }

    public static ExecutorService ioPool() {
        return IO_POOL;
    }

    public static ExecutorService nonCriticalIoPool() {
        return DOWNLOAD_POOL;
    }

    public static void shutdownExecutors() {
        shutdownExecutor(BACKGROUND_EXECUTOR);
        shutdownExecutor(IO_POOL);
    }

    private static void shutdownExecutor(ExecutorService service) {
        service.shutdown();

        boolean flag;
        try {
            flag = service.awaitTermination(3L, TimeUnit.SECONDS);
        } catch (InterruptedException interruptedexception) {
            flag = false;
        }

        if (!flag) {
            service.shutdownNow();
        }
    }

    private static ExecutorService makeIoExecutor(String name, boolean daemon) {
        AtomicInteger atomicinteger = new AtomicInteger(1);
        return Executors.newCachedThreadPool(p_314387_ -> {
            Thread thread = new Thread(p_314387_);
            thread.setName(name + atomicinteger.getAndIncrement());
            thread.setDaemon(daemon);
            thread.setUncaughtExceptionHandler(Util::onThreadException);
            return thread;
        });
    }

    public static void throwAsRuntime(Throwable throwable) {
        throw throwable instanceof RuntimeException ? (RuntimeException)throwable : new RuntimeException(throwable);
    }

    private static void onThreadException(Thread thread, Throwable throwable) {
        pauseInIde(throwable);
        if (throwable instanceof CompletionException) {
            throwable = throwable.getCause();
        }

        if (throwable instanceof ReportedException reportedexception) {
            Bootstrap.realStdoutPrintln(reportedexception.getReport().getFriendlyReport(ReportType.CRASH));
            System.exit(-1);
        }

        LOGGER.error(String.format(Locale.ROOT, "Caught exception in thread %s", thread), throwable);
    }

    @Nullable
    public static Type<?> fetchChoiceType(TypeReference type, String choiceName) {
        return !SharedConstants.CHECK_DATA_FIXER_SCHEMA ? null : doFetchChoiceType(type, choiceName);
    }

    @Nullable
    private static Type<?> doFetchChoiceType(TypeReference p_type, String choiceName) {
        Type<?> type = null;

        try {
            type = DataFixers.getDataFixer()
                .getSchema(DataFixUtils.makeKey(SharedConstants.getCurrentVersion().getDataVersion().getVersion()))
                .getChoiceType(p_type, choiceName);
        } catch (IllegalArgumentException illegalargumentexception) {
            LOGGER.debug("No data fixer registered for {}", choiceName);
            if (SharedConstants.IS_RUNNING_IN_IDE && false) {
                throw illegalargumentexception;
            }
        }

        return type;
    }

    public static Runnable wrapThreadWithTaskName(String name, Runnable task) {
        return SharedConstants.IS_RUNNING_IN_IDE ? () -> {
            Thread thread = Thread.currentThread();
            String s = thread.getName();
            thread.setName(name);

            try {
                task.run();
            } finally {
                thread.setName(s);
            }
        } : task;
    }

    public static <V> Supplier<V> wrapThreadWithTaskName(String name, Supplier<V> task) {
        return SharedConstants.IS_RUNNING_IN_IDE ? () -> {
            Thread thread = Thread.currentThread();
            String s = thread.getName();
            thread.setName(name);

            Object object;
            try {
                object = task.get();
            } finally {
                thread.setName(s);
            }

            return (V)object;
        } : task;
    }

    public static <T> String getRegisteredName(Registry<T> registry, T value) {
        ResourceLocation resourcelocation = registry.getKey(value);
        return resourcelocation == null ? "[unregistered]" : resourcelocation.toString();
    }

    public static <T> Predicate<T> allOf(List<? extends Predicate<T>> predicates) {
        return switch (predicates.size()) {
            case 0 -> p_323042_ -> true;
            case 1 -> (Predicate)predicates.get(0);
            case 2 -> predicates.get(0).and((Predicate<? super T>)predicates.get(1));
            default -> {
                Predicate<T>[] predicate = predicates.toArray(Predicate[]::new);
                yield p_352651_ -> {
                    for (Predicate<T> predicate1 : predicate) {
                        if (!predicate1.test((T)p_352651_)) {
                            return false;
                        }
                    }

                    return true;
                };
            }
        };
    }

    public static <T> Predicate<T> anyOf(List<? extends Predicate<T>> predicates) {
        return switch (predicates.size()) {
            case 0 -> p_323047_ -> false;
            case 1 -> (Predicate)predicates.get(0);
            case 2 -> predicates.get(0).or((Predicate<? super T>)predicates.get(1));
            default -> {
                Predicate<T>[] predicate = predicates.toArray(Predicate[]::new);
                yield p_352655_ -> {
                    for (Predicate<T> predicate1 : predicate) {
                        if (predicate1.test((T)p_352655_)) {
                            return true;
                        }
                    }

                    return false;
                };
            }
        };
    }

    public static <T> boolean isSymmetrical(int width, int height, List<T> list) {
        if (width == 1) {
            return true;
        } else {
            int i = width / 2;

            for (int j = 0; j < height; j++) {
                for (int k = 0; k < i; k++) {
                    int l = width - 1 - k;
                    T t = list.get(k + j * width);
                    T t1 = list.get(l + j * width);
                    if (!t.equals(t1)) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    public static Util.OS getPlatform() {
        String s = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (s.contains("win")) {
            return Util.OS.WINDOWS;
        } else if (s.contains("mac")) {
            return Util.OS.OSX;
        } else if (s.contains("solaris")) {
            return Util.OS.SOLARIS;
        } else if (s.contains("sunos")) {
            return Util.OS.SOLARIS;
        } else if (s.contains("linux")) {
            return Util.OS.LINUX;
        } else {
            return s.contains("unix") ? Util.OS.LINUX : Util.OS.UNKNOWN;
        }
    }

    public static URI parseAndValidateUntrustedUri(String p_uri) throws URISyntaxException {
        URI uri = new URI(p_uri);
        String s = uri.getScheme();
        if (s == null) {
            throw new URISyntaxException(p_uri, "Missing protocol in URI: " + p_uri);
        } else {
            String s1 = s.toLowerCase(Locale.ROOT);
            if (!ALLOWED_UNTRUSTED_LINK_PROTOCOLS.contains(s1)) {
                throw new URISyntaxException(p_uri, "Unsupported protocol in URI: " + p_uri);
            } else {
                return uri;
            }
        }
    }

    public static Stream<String> getVmArguments() {
        RuntimeMXBean runtimemxbean = ManagementFactory.getRuntimeMXBean();
        return runtimemxbean.getInputArguments().stream().filter(p_201903_ -> p_201903_.startsWith("-X"));
    }

    public static <T> T lastOf(List<T> list) {
        return list.get(list.size() - 1);
    }

    public static <T> T findNextInIterable(Iterable<T> iterable, @Nullable T element) {
        Iterator<T> iterator = iterable.iterator();
        T t = iterator.next();
        if (element != null) {
            T t1 = t;

            while (t1 != element) {
                if (iterator.hasNext()) {
                    t1 = iterator.next();
                }
            }

            if (iterator.hasNext()) {
                return iterator.next();
            }
        }

        return t;
    }

    public static <T> T findPreviousInIterable(Iterable<T> iterable, @Nullable T current) {
        Iterator<T> iterator = iterable.iterator();
        T t = null;

        while (iterator.hasNext()) {
            T t1 = iterator.next();
            if (t1 == current) {
                if (t == null) {
                    t = iterator.hasNext() ? Iterators.getLast(iterator) : current;
                }
                break;
            }

            t = t1;
        }

        return t;
    }

    public static <T> T make(Supplier<T> supplier) {
        return supplier.get();
    }

    public static <T> T make(T object, Consumer<? super T> consumer) {
        consumer.accept(object);
        return object;
    }

    /**
     * Takes a list of futures and returns a future of list that completes when all of them succeed or any of them error,
     */
    public static <V> CompletableFuture<List<V>> sequence(List<? extends CompletableFuture<V>> futures) {
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        } else if (futures.size() == 1) {
            return futures.get(0).thenApply(List::of);
        } else {
            CompletableFuture<Void> completablefuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            return completablefuture.thenApply(p_203746_ -> futures.stream().map(CompletableFuture::join).toList());
        }
    }

    public static <V> CompletableFuture<List<V>> sequenceFailFast(List<? extends CompletableFuture<? extends V>> completableFutures) {
        CompletableFuture<List<V>> completablefuture = new CompletableFuture<>();
        return fallibleSequence(completableFutures, completablefuture::completeExceptionally).applyToEither(completablefuture, Function.identity());
    }

    public static <V> CompletableFuture<List<V>> sequenceFailFastAndCancel(List<? extends CompletableFuture<? extends V>> completableFutures) {
        CompletableFuture<List<V>> completablefuture = new CompletableFuture<>();
        return fallibleSequence(completableFutures, p_274642_ -> {
            if (completablefuture.completeExceptionally(p_274642_)) {
                for (CompletableFuture<? extends V> completablefuture1 : completableFutures) {
                    completablefuture1.cancel(true);
                }
            }
        }).applyToEither(completablefuture, Function.identity());
    }

    private static <V> CompletableFuture<List<V>> fallibleSequence(List<? extends CompletableFuture<? extends V>> completableFutures, Consumer<Throwable> throwableConsumer) {
        List<V> list = Lists.newArrayListWithCapacity(completableFutures.size());
        CompletableFuture<?>[] completablefuture = new CompletableFuture[completableFutures.size()];
        completableFutures.forEach(p_214641_ -> {
            int i = list.size();
            list.add(null);
            completablefuture[i] = p_214641_.whenComplete((p_214650_, p_214651_) -> {
                if (p_214651_ != null) {
                    throwableConsumer.accept(p_214651_);
                } else {
                    list.set(i, (V)p_214650_);
                }
            });
        });
        return CompletableFuture.allOf(completablefuture).thenApply(p_214626_ -> list);
    }

    public static <T> Optional<T> ifElse(Optional<T> opt, Consumer<T> consumer, Runnable orElse) {
        if (opt.isPresent()) {
            consumer.accept(opt.get());
        } else {
            orElse.run();
        }

        return opt;
    }

    public static <T> Supplier<T> name(Supplier<T> item, Supplier<String> nameSupplier) {
        return item;
    }

    public static Runnable name(Runnable item, Supplier<String> nameSupplier) {
        return item;
    }

    public static void logAndPauseIfInIde(String error) {
        LOGGER.error(error);
        if (SharedConstants.IS_RUNNING_WITH_JDWP) {
            doPause(error);
        }
    }

    public static void logAndPauseIfInIde(String message, Throwable error) {
        LOGGER.error(message, error);
        if (SharedConstants.IS_RUNNING_WITH_JDWP) {
            doPause(message);
        }
    }

    public static <T extends Throwable> T pauseInIde(T throwable) {
        if (SharedConstants.IS_RUNNING_WITH_JDWP) {
            LOGGER.error("Trying to throw a fatal exception, pausing in IDE", throwable);
            doPause(throwable.getMessage());
        }

        return throwable;
    }

    public static void setPause(Consumer<String> p_thePauser) {
        thePauser = p_thePauser;
    }

    private static void doPause(String message) {
        Instant instant = Instant.now();
        LOGGER.warn("Did you remember to set a breakpoint here?");
        boolean flag = Duration.between(instant, Instant.now()).toMillis() > 500L;
        if (!flag) {
            thePauser.accept(message);
        }
    }

    public static String describeError(Throwable throwable) {
        if (throwable.getCause() != null) {
            return describeError(throwable.getCause());
        } else {
            return throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
        }
    }

    public static <T> T getRandom(T[] selections, RandomSource random) {
        return selections[random.nextInt(selections.length)];
    }

    public static int getRandom(int[] selections, RandomSource random) {
        return selections[random.nextInt(selections.length)];
    }

    public static <T> T getRandom(List<T> selections, RandomSource random) {
        return selections.get(random.nextInt(selections.size()));
    }

    public static <T> Optional<T> getRandomSafe(List<T> selections, RandomSource random) {
        return selections.isEmpty() ? Optional.empty() : Optional.of(getRandom(selections, random));
    }

    private static BooleanSupplier createRenamer(final Path filePath, final Path newName) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                try {
                    Files.move(filePath, newName);
                    return true;
                } catch (IOException ioexception) {
                    Util.LOGGER.error("Failed to rename", (Throwable)ioexception);
                    return false;
                }
            }

            @Override
            public String toString() {
                return "rename " + filePath + " to " + newName;
            }
        };
    }

    private static BooleanSupplier createDeleter(final Path filePath) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                try {
                    Files.deleteIfExists(filePath);
                    return true;
                } catch (IOException ioexception) {
                    Util.LOGGER.warn("Failed to delete", (Throwable)ioexception);
                    return false;
                }
            }

            @Override
            public String toString() {
                return "delete old " + filePath;
            }
        };
    }

    private static BooleanSupplier createFileDeletedCheck(final Path filePath) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return !Files.exists(filePath);
            }

            @Override
            public String toString() {
                return "verify that " + filePath + " is deleted";
            }
        };
    }

    private static BooleanSupplier createFileCreatedCheck(final Path filePath) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return Files.isRegularFile(filePath);
            }

            @Override
            public String toString() {
                return "verify that " + filePath + " is present";
            }
        };
    }

    private static boolean executeInSequence(BooleanSupplier... suppliers) {
        for (BooleanSupplier booleansupplier : suppliers) {
            if (!booleansupplier.getAsBoolean()) {
                LOGGER.warn("Failed to execute {}", booleansupplier);
                return false;
            }
        }

        return true;
    }

    private static boolean runWithRetries(int maxTries, String actionName, BooleanSupplier... suppliers) {
        for (int i = 0; i < maxTries; i++) {
            if (executeInSequence(suppliers)) {
                return true;
            }

            LOGGER.error("Failed to {}, retrying {}/{}", actionName, i, maxTries);
        }

        LOGGER.error("Failed to {}, aborting, progress might be lost", actionName);
        return false;
    }

    public static void safeReplaceFile(Path current, Path latest, Path oldBackup) {
        safeReplaceOrMoveFile(current, latest, oldBackup, false);
    }

    public static boolean safeReplaceOrMoveFile(Path current, Path latest, Path oldBackup, boolean p_212228_) {
        if (Files.exists(current)
            && !runWithRetries(
                10, "create backup " + oldBackup, createDeleter(oldBackup), createRenamer(current, oldBackup), createFileCreatedCheck(oldBackup)
            )) {
            return false;
        } else if (!runWithRetries(10, "remove old " + current, createDeleter(current), createFileDeletedCheck(current))) {
            return false;
        } else if (!runWithRetries(10, "replace " + current + " with " + latest, createRenamer(latest, current), createFileCreatedCheck(current))
            && !p_212228_) {
            runWithRetries(10, "restore " + current + " from " + oldBackup, createRenamer(oldBackup, current), createFileCreatedCheck(current));
            return false;
        } else {
            return true;
        }
    }

    public static int offsetByCodepoints(String text, int cursorPos, int direction) {
        int i = text.length();
        if (direction >= 0) {
            for (int j = 0; cursorPos < i && j < direction; j++) {
                if (Character.isHighSurrogate(text.charAt(cursorPos++)) && cursorPos < i && Character.isLowSurrogate(text.charAt(cursorPos))) {
                    cursorPos++;
                }
            }
        } else {
            for (int k = direction; cursorPos > 0 && k < 0; k++) {
                cursorPos--;
                if (Character.isLowSurrogate(text.charAt(cursorPos)) && cursorPos > 0 && Character.isHighSurrogate(text.charAt(cursorPos - 1))) {
                    cursorPos--;
                }
            }
        }

        return cursorPos;
    }

    public static Consumer<String> prefix(String prefix, Consumer<String> expectedSize) {
        return p_214645_ -> expectedSize.accept(prefix + p_214645_);
    }

    public static DataResult<int[]> fixedSize(IntStream stream, int size) {
        int[] aint = stream.limit((long)(size + 1)).toArray();
        if (aint.length != size) {
            Supplier<String> supplier = () -> "Input is not a list of " + size + " ints";
            return aint.length >= size ? DataResult.error(supplier, Arrays.copyOf(aint, size)) : DataResult.error(supplier);
        } else {
            return DataResult.success(aint);
        }
    }

    public static DataResult<long[]> fixedSize(LongStream stream, int expectedSize) {
        long[] along = stream.limit((long)(expectedSize + 1)).toArray();
        if (along.length != expectedSize) {
            Supplier<String> supplier = () -> "Input is not a list of " + expectedSize + " longs";
            return along.length >= expectedSize ? DataResult.error(supplier, Arrays.copyOf(along, expectedSize)) : DataResult.error(supplier);
        } else {
            return DataResult.success(along);
        }
    }

    public static <T> DataResult<List<T>> fixedSize(List<T> list, int expectedSize) {
        if (list.size() != expectedSize) {
            Supplier<String> supplier = () -> "Input is not a list of " + expectedSize + " elements";
            return list.size() >= expectedSize ? DataResult.error(supplier, list.subList(0, expectedSize)) : DataResult.error(supplier);
        } else {
            return DataResult.success(list);
        }
    }

    public static void startTimerHackThread() {
        Thread thread = new Thread("Timer hack thread") {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(2147483647L);
                    } catch (InterruptedException interruptedexception) {
                        Util.LOGGER.warn("Timer hack thread interrupted, that really should not happen");
                        return;
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        thread.start();
    }

    public static void copyBetweenDirs(Path fromDirectory, Path toDirectory, Path filePath) throws IOException {
        Path path = fromDirectory.relativize(filePath);
        Path path1 = toDirectory.resolve(path);
        Files.copy(filePath, path1);
    }

    public static String sanitizeName(String fileName, CharPredicate characterValidator) {
        return fileName.toLowerCase(Locale.ROOT)
            .chars()
            .mapToObj(p_214666_ -> characterValidator.test((char)p_214666_) ? Character.toString((char)p_214666_) : "_")
            .collect(Collectors.joining());
    }

    public static <K, V> SingleKeyCache<K, V> singleKeyCache(Function<K, V> computeValue) {
        return new SingleKeyCache<>(computeValue);
    }

    public static <T, R> Function<T, R> memoize(final Function<T, R> memoFunction) {
        return new Function<T, R>() {
            private final Map<T, R> cache = new ConcurrentHashMap<>();

            @Override
            public R apply(T p_214691_) {
                return this.cache.computeIfAbsent(p_214691_, memoFunction);
            }

            @Override
            public String toString() {
                return "memoize/1[function=" + memoFunction + ", size=" + this.cache.size() + "]";
            }
        };
    }

    public static <T, U, R> BiFunction<T, U, R> memoize(final BiFunction<T, U, R> memoBiFunction) {
        return new BiFunction<T, U, R>() {
            private final Map<Pair<T, U>, R> cache = new ConcurrentHashMap<>();

            @Override
            public R apply(T p_214700_, U p_214701_) {
                return this.cache.computeIfAbsent(Pair.of(p_214700_, p_214701_), p_214698_ -> memoBiFunction.apply(p_214698_.getFirst(), p_214698_.getSecond()));
            }

            @Override
            public String toString() {
                return "memoize/2[function=" + memoBiFunction + ", size=" + this.cache.size() + "]";
            }
        };
    }

    public static <T> List<T> toShuffledList(Stream<T> stream, RandomSource random) {
        ObjectArrayList<T> objectarraylist = stream.collect(ObjectArrayList.toList());
        shuffle(objectarraylist, random);
        return objectarraylist;
    }

    public static IntArrayList toShuffledList(IntStream stream, RandomSource random) {
        IntArrayList intarraylist = IntArrayList.wrap(stream.toArray());
        int i = intarraylist.size();

        for (int j = i; j > 1; j--) {
            int k = random.nextInt(j);
            intarraylist.set(j - 1, intarraylist.set(k, intarraylist.getInt(j - 1)));
        }

        return intarraylist;
    }

    public static <T> List<T> shuffledCopy(T[] array, RandomSource random) {
        ObjectArrayList<T> objectarraylist = new ObjectArrayList<>(array);
        shuffle(objectarraylist, random);
        return objectarraylist;
    }

    public static <T> List<T> shuffledCopy(ObjectArrayList<T> list, RandomSource random) {
        ObjectArrayList<T> objectarraylist = new ObjectArrayList<>(list);
        shuffle(objectarraylist, random);
        return objectarraylist;
    }

    public static <T> void shuffle(List<T> list, RandomSource random) {
        int i = list.size();

        for (int j = i; j > 1; j--) {
            int k = random.nextInt(j);
            list.set(j - 1, list.set(k, list.get(j - 1)));
        }
    }

    public static <T> CompletableFuture<T> blockUntilDone(Function<Executor, CompletableFuture<T>> task) {
        return blockUntilDone(task, CompletableFuture::isDone);
    }

    public static <T> T blockUntilDone(Function<Executor, T> task, Predicate<T> donePredicate) {
        BlockingQueue<Runnable> blockingqueue = new LinkedBlockingQueue<>();
        T t = task.apply(blockingqueue::add);

        while (!donePredicate.test(t)) {
            try {
                Runnable runnable = blockingqueue.poll(100L, TimeUnit.MILLISECONDS);
                if (runnable != null) {
                    runnable.run();
                }
            } catch (InterruptedException interruptedexception) {
                LOGGER.warn("Interrupted wait");
                break;
            }
        }

        int i = blockingqueue.size();
        if (i > 0) {
            LOGGER.warn("Tasks left in queue: {}", i);
        }

        return t;
    }

    public static <T> ToIntFunction<T> createIndexLookup(List<T> list) {
        int i = list.size();
        if (i < 8) {
            return list::indexOf;
        } else {
            Object2IntMap<T> object2intmap = new Object2IntOpenHashMap<>(i);
            object2intmap.defaultReturnValue(-1);

            for (int j = 0; j < i; j++) {
                object2intmap.put(list.get(j), j);
            }

            return object2intmap;
        }
    }

    public static <T> ToIntFunction<T> createIndexIdentityLookup(List<T> list) {
        int i = list.size();
        if (i < 8) {
            ReferenceList<T> referencelist = new ReferenceImmutableList<>(list);
            return referencelist::indexOf;
        } else {
            Reference2IntMap<T> reference2intmap = new Reference2IntOpenHashMap<>(i);
            reference2intmap.defaultReturnValue(-1);

            for (int j = 0; j < i; j++) {
                reference2intmap.put(list.get(j), j);
            }

            return reference2intmap;
        }
    }

    public static <A, B> Typed<B> writeAndReadTypedOrThrow(Typed<A> typed, Type<B> type, UnaryOperator<Dynamic<?>> operator) {
        Dynamic<?> dynamic = (Dynamic<?>)typed.write().getOrThrow();
        return readTypedOrThrow(type, operator.apply(dynamic), true);
    }

    public static <T> Typed<T> readTypedOrThrow(Type<T> type, Dynamic<?> data) {
        return readTypedOrThrow(type, data, false);
    }

    public static <T> Typed<T> readTypedOrThrow(Type<T> type, Dynamic<?> data, boolean partial) {
        DataResult<Typed<T>> dataresult = type.readTyped(data).map(Pair::getFirst);

        try {
            return partial ? dataresult.getPartialOrThrow(IllegalStateException::new) : dataresult.getOrThrow(IllegalStateException::new);
        } catch (IllegalStateException illegalstateexception) {
            CrashReport crashreport = CrashReport.forThrowable(illegalstateexception, "Reading type");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Info");
            crashreportcategory.setDetail("Data", data);
            crashreportcategory.setDetail("Type", type);
            throw new ReportedException(crashreport);
        }
    }

    public static <T> List<T> copyAndAdd(List<T> list, T value) {
        return ImmutableList.<T>builderWithExpectedSize(list.size() + 1).addAll(list).add(value).build();
    }

    public static <T> List<T> copyAndAdd(T value, List<T> list) {
        return ImmutableList.<T>builderWithExpectedSize(list.size() + 1).add(value).addAll(list).build();
    }

    public static <K, V> Map<K, V> copyAndPut(Map<K, V> map, K key, V value) {
        return ImmutableMap.<K, V>builderWithExpectedSize(map.size() + 1).putAll(map).put(key, value).buildKeepingLast();
    }

    public static enum OS {
        LINUX("linux"),
        SOLARIS("solaris"),
        WINDOWS("windows") {
            @Override
            protected String[] getOpenUriArguments(URI p_352177_) {
                return new String[]{"rundll32", "url.dll,FileProtocolHandler", p_352177_.toString()};
            }
        },
        OSX("mac") {
            @Override
            protected String[] getOpenUriArguments(URI p_352407_) {
                return new String[]{"open", p_352407_.toString()};
            }
        },
        UNKNOWN("unknown");

        private final String telemetryName;

        OS(String telemetryName) {
            this.telemetryName = telemetryName;
        }

        public void openUri(URI uri) {
            try {
                Process process = AccessController.doPrivileged(
                    (PrivilegedExceptionAction<Process>)(() -> Runtime.getRuntime().exec(this.getOpenUriArguments(uri)))
                );
                process.getInputStream().close();
                process.getErrorStream().close();
                process.getOutputStream().close();
            } catch (IOException | PrivilegedActionException privilegedactionexception) {
                Util.LOGGER.error("Couldn't open location '{}'", uri, privilegedactionexception);
            }
        }

        public void openFile(File file) {
            this.openUri(file.toURI());
        }

        public void openPath(Path path) {
            this.openUri(path.toUri());
        }

        protected String[] getOpenUriArguments(URI uri) {
            String s = uri.toString();
            if ("file".equals(uri.getScheme())) {
                s = s.replace("file:", "file://");
            }

            return new String[]{"xdg-open", s};
        }

        public void openUri(String uri) {
            try {
                this.openUri(new URI(uri));
            } catch (IllegalArgumentException | URISyntaxException urisyntaxexception) {
                Util.LOGGER.error("Couldn't open uri '{}'", uri, urisyntaxexception);
            }
        }

        public String telemetryName() {
            return this.telemetryName;
        }
    }
}
