package me.saro.commons;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.SneakyThrows;
import me.saro.commons.function.StreamReadConsumer;
import me.saro.commons.function.ThrowableConsumer;
import me.saro.commons.function.ThrowableFunction;

/**
 * util class
 * @author		PARK Yong Seo
 * @since		0.1
 */
public class Utils {

    private Utils() {
    }

    final static long TIME_MILLIS_UNIT_DAY = 86_400_000;
    final static char[] BASE62_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    /**
     * Null Value Logic
     * 
     * @param list
     * nullable data
     * @return
     *  - first not null data
     *  <br>
     *  - if has not null data return null
     */
    @SafeVarargs
    public static <T> T nvl(T... list) {
        for (T t : list) {
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    /**
     * Empty Value Logic
     * 
     * @param list
     * nullable String
     * @return
     *  - first not null and not empty string
     *  <br>
     *  - if not found return null
     */
    public static String evl(String... list) {
        for (String val : list) {
            if (val != null && !val.isEmpty()) {
                return val;
            }
        }
        return null;
    }
    
    /**
     * zerofill
     * @param number
     * @param length
     * @return
     */
    public static String zerofill(String number, int length) {
        if (length < 1) {
            throw new IllegalArgumentException("length must more then 1");
        }
        if (!number.matches("[\\d]+")) {
            throw new IllegalArgumentException("number must unsigned integer([0-9]+)");
        }
        int needFillSize = length - number.length();
        
        if (needFillSize == 0) {
            return number;
        } else if (needFillSize > 0) {
            char[] fills = new char[needFillSize];
            Arrays.fill(fills, '0');
            return new String(fills) + number;
        } else {
            throw new IllegalArgumentException("["+number+"] is over length then "+length);
        }
    }
    
    /**
     * zerofill
     * @param val
     * @param length
     * @return
     */
    public static String zerofill(long val, int length) {
        return zerofill(Long.toString(val), length);
    }
    
    /**
     * zerofill
     * @param val
     * @param length
     * @return
     */
    public static String zerofill(int val, int length) {
        return zerofill(Integer.toString(val), length);
    }
    
    /**
     * link input to output stream<br>
     * <b>WARNING : </b> is not auto closed
     * @param is
     * @param os
     * @throws IOException 
     */
    public static void linkStream(InputStream is, OutputStream os) throws IOException {
        int bufsize = 8192;
        byte[] buf = new byte[bufsize];
        int len;
        while ((len = is.read(buf, 0, bufsize)) != -1) {
            os.write(buf, 0, len);
        }
        os.flush();
    }

    /**
     * create random string
     * @param mold
     * base mold for create random string
     * @param len
     * create langth
     * @return
     * random string
     */
    public static String createRandomString(char[] mold, int len) {
        char[] rv = new char[len];
        int charLen = mold.length;

        for (int i = 0 ;i < len ; i++) {
            rv[i] = mold[(int)(Math.random() * charLen)];
        }

        return new String(rv);
    }

    /**
     * create random string
     * @param mold
     * base mold for create random string
     * @param min
     * min length
     * @param max
     * max length
     * @return
     * create random string
     * min &lt;= return value &lt;= max
     */
    public static String createRandomString(char[] mold, int min, int max) {
        return createRandomString(mold, (int)random(min, max));
    }

    /**
     * create random base62 string
     * <br>
     * base62 : [ A-Z a-z 0-9 ]
     * @param min
     * min length
     * @param max
     * max length
     * @return
     * 
     */
    public static String createRandomBase62String(int min, int max) {
        return createRandomString(BASE62_CHARS, (int)random(min, max));
    }

    /**
     * get random
     * @param min
     * min length
     * @param max
     * max length
     * @return
     * min &lt;= return value &lt;= max
     */
    public static long random(long min, long max) {
        if (min == max) {
            return min;
        } else if (min > max) {
            throw new IllegalArgumentException("'lessThen' have to over the value then 'min'");
        }
        return min + (int)(Math.random() * ((max + 1) - min));
    }

    /**
     * InputStream Reader<br>
     * <b>WARNING : </b> is not auto closed
     * @param inputStream
     * @param callback
     * stream read callback
     * @throws Exception
     */
    public static void inputStreamReader(InputStream inputStream, StreamReadConsumer callback) throws Exception {
        int size = Math.min(inputStream.available(), 8192);
        byte[] buf = new byte[size];
        int len;
        while ( (len = inputStream.read(buf, 0, size)) != -1 ) {
            callback.accept(buf, len);
        }
    }
    
    /**
     * inputStream line reader<br>
     * <b>WARNING : </b> is not auto closed
     * @param charset
     * @param inputStream
     * @param lineReader
     * @return
     * return of the lineReader
     * @throws Exception
     */
    public static <T> T inputStreamLineReader(InputStream inputStream, String charset, ThrowableFunction<Stream<String>, T> lineReader) throws Exception {
        try ( InputStreamReader isr = new InputStreamReader(inputStream, charset) ; BufferedReader br = new BufferedReader(isr) ) {
            return lineReader.apply(br.lines());
        } catch (IOException e) {
            throw e;
        }
    }
    
    /**
     * execute all threads
     * <b>WARNING : </b>this method does not shutdown to ExecutorService instance
     * @param executorService
     * @param list
     * @param map
     * @return
     * map result list
     * @since 0.3
     */
    public static <T, R> List<R> executeAllThreads(ExecutorService executorService, List<T> list, ThrowableFunction<T, R> map) {
        try {
            return executorService
                    .invokeAll(list.parallelStream().<Callable<R>>map(e -> () -> map.apply(e)).collect(Collectors.toList()))
                    .parallelStream()
                    .map(ThrowableFunction.runtime(x -> x.get()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * execute all threads
     * @param nThreads
     * @param list
     * @param map
     * @return
     * map result list
     * @since 0.3
     */
    public static <T, R> List<R> executeAllThreads(int nThreads, List<T> list, ThrowableFunction<T, R> map) {
        ExecutorService executorService = Executors.newFixedThreadPool(nThreads);
        List<R> rv = executeAllThreads(executorService, list, map);
        executorService.shutdown();
        return rv;
    }
    
    /**
     * forced close without exception 
     * @param closeable
     */
    public static void kill(Closeable closeable) {
        if (closeable != null) {
            try (Closeable tmp = closeable) {
            } catch (Exception e) {
            }
        }
    }
    
    /**
     * kill thread without exception 
     * @param thread
     */
    public static void kill(Thread thread) {
        if (thread != null) {
            try {
                if (thread.isInterrupted()) {
                    thread.interrupt();
                }
            } catch (Exception e) {
            }
        }
    }
    
    /**
     * timertask
     * @param task
     * @return
     */
    public static TimerTask timerTask(ThrowableConsumer<TimerTask> task) {
        return new TimerTask() {
            @Override @SneakyThrows public void run() {
                task.accept(this);
            }
        };
    }
}
